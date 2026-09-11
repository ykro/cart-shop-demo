package dev.ykro.bugreporter.agent

import android.content.Context
import android.os.Build
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dev.ykro.bugreporter.BuildConfig
import dev.ykro.bugreporter.data.CartRepository
import dev.ykro.bugreporter.data.CouponType
import dev.ykro.bugreporter.data.FakeCatalogApi
import dev.ykro.bugreporter.data.User
import dev.ykro.bugreporter.instrumentation.Breadcrumbs
import dev.ykro.bugreporter.instrumentation.LogBuffer
import java.util.Locale

/** Snapshot of the cart as the agent sees it: already redacted, no user email. */
data class CartStateDump(
  val items: List<CartItemDump>,
  val activeCoupon: String?,
  val couponRule: String?,
  val subtotalCents: Long,
  val discountCents: Long,
  val totalCents: Long,
  val totalIsNegative: Boolean,
  val user: String,
) {
  /** The KSP tool processor serializes maps and primitives, so tools return this map form. */
  fun toMap(): Map<String, Any?> =
    mapOf(
      "items" to items.map { it.toMap() },
      "activeCoupon" to activeCoupon,
      "couponRule" to couponRule,
      "subtotalCents" to subtotalCents,
      "discountCents" to discountCents,
      "totalCents" to totalCents,
      "totalIsNegative" to totalIsNegative,
      "user" to user,
    )
}

data class CartItemDump(val productId: Int, val name: String, val unitPriceCents: Long, val quantity: Int, val lineTotalCents: Long) {
  fun toMap(): Map<String, Any?> = mapOf("productId" to productId, "name" to name, "unitPriceCents" to unitPriceCents, "quantity" to quantity, "lineTotalCents" to lineTotalCents)
}

data class EnvironmentInfo(
  val deviceModel: String,
  val manufacturer: String,
  val androidVersion: String,
  val apiLevel: Int,
  val appVersion: String,
  val buildType: String,
  val locale: String,
  val isEmulator: Boolean,
) {
  fun toMap(): Map<String, Any?> =
    mapOf(
      "deviceModel" to deviceModel,
      "manufacturer" to manufacturer,
      "androidVersion" to androidVersion,
      "apiLevel" to apiLevel,
      "appVersion" to appVersion,
      "buildType" to buildType,
      "locale" to locale,
      "isEmulator" to isEmulator,
    )
}

/** Everything the agent can read about the app. Each tool passes its output through [Redactor]. */
class ContextTools(
  private val context: Context,
  private val breadcrumbs: Breadcrumbs,
  private val logs: LogBuffer,
  private val cartRepo: CartRepository,
  private val user: User,
  private val api: FakeCatalogApi,
  private val cartSnapshot: () -> CartTotals,
  private val screenshotName: () -> String?,
  private val redactor: Redactor,
) {
  data class CartTotals(val couponCode: String?, val couponRule: String?, val subtotalCents: Long, val discountCents: Long, val totalCents: Long)

  @Tool(name = "get_breadcrumbs", description = "The user's most recent UI and navigation actions, in chronological order, with time in milliseconds since app start.")
  fun getBreadcrumbs(@Param("How many actions to return, max 50") limit: Int? = 30): List<Map<String, Any>> =
    breadcrumbs.last(limit ?: 30).map { mapOf("t_ms" to it.tMs, "action" to redactor.redact(it.action)) }

  @Tool(name = "get_cart_state", description = "Current cart state: items with quantities, active coupon and its rule, subtotal, discount and total in cents.")
  suspend fun getCartState(): Map<String, Any?> = cartState().toMap()

  suspend fun cartState(): CartStateDump {
    val lines = cartRepo.lines()
    val totals = cartSnapshot()
    return CartStateDump(
      items = lines.map { CartItemDump(it.product.id, it.product.name, it.product.priceCents, it.quantity, it.lineTotalCents) },
      activeCoupon = totals.couponCode,
      couponRule = totals.couponRule,
      subtotalCents = totals.subtotalCents,
      discountCents = totals.discountCents,
      totalCents = totals.totalCents,
      totalIsNegative = totals.totalCents < 0,
      user = redactor.redact("${user.name} <${user.email}>"),
    )
  }

  @Tool(name = "get_app_logs", description = "The app's own most recent log lines (not the system log).")
  fun getAppLogs(@Param("How many lines, max 200") limit: Int? = 100): List<String> = redactor.redactAll(logs.last(limit ?: 100))

  @Tool(name = "get_last_network_exchange", description = "The last request and response between the app and the catalog API, if any.")
  fun getLastNetworkExchange(): Map<String, Any?> {
    val ex = api.lastExchange ?: return mapOf("exchange" to null, "note" to "No network calls recorded yet.")
    return mapOf(
      "method" to ex.method,
      "url" to ex.url,
      "request_body" to redactor.redactOrNull(ex.requestBody),
      "status" to ex.status,
      "response_body" to redactor.redact(ex.responseBody),
      "duration_ms" to ex.durationMs,
      "at" to ex.at,
    )
  }

  @Tool(name = "get_environment", description = "Device model, manufacturer, Android version, app version, build type and locale.")
  fun getEnvironment(): Map<String, Any?> = environment().toMap()

  fun environment(): EnvironmentInfo =
    EnvironmentInfo(
      deviceModel = Build.MODEL,
      manufacturer = Build.MANUFACTURER,
      androidVersion = Build.VERSION.RELEASE,
      apiLevel = Build.VERSION.SDK_INT,
      appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
      buildType = BuildConfig.BUILD_TYPE,
      locale = Locale.getDefault().toLanguageTag(),
      isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"),
    )

  @Tool(name = "get_screenshot_artifact_name", description = "Name of the artifact holding the screenshot captured when the report started, or null if none.")
  fun getScreenshotArtifactName(): Map<String, Any?> = mapOf("artifact" to screenshotName())

  /**
   * Everything the six tools would return, as one redacted JSON document. Private mode attaches it
   * to the first message so the small on-device model does not spend a tool round-trip per source.
   */
  suspend fun contextPack(): String {
    val json = kotlinx.serialization.json.Json { prettyPrint = false }
    val cart = cartState()
    val env = environment()
    val pack =
      mapOf(
        "breadcrumbs" to breadcrumbs.last(30).map { "${it.tMs}ms ${redactor.redact(it.action)}" },
        "cart" to
          mapOf(
            "items" to cart.items.map { "${it.quantity} x ${it.name} @ ${it.unitPriceCents} cents = ${it.lineTotalCents} cents" },
            "activeCoupon" to cart.activeCoupon,
            "couponRule" to cart.couponRule,
            "subtotalCents" to cart.subtotalCents,
            "discountCents" to cart.discountCents,
            "totalCents" to cart.totalCents,
            "totalIsNegative" to cart.totalIsNegative,
          ),
        "environment" to "${env.deviceModel} (${env.manufacturer}), Android ${env.androidVersion} (API ${env.apiLevel}), app ${env.appVersion} ${env.buildType}",
        "lastNetworkExchange" to api.lastExchange?.let { "${it.method} ${it.url} -> ${it.status}" },
        "recentLogs" to redactor.redactAll(logs.last(12)),
        "screenshotArtifact" to screenshotName(),
      )
    return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), toJson(pack))
  }

  private fun toJson(value: Any?): kotlinx.serialization.json.JsonElement =
    when (value) {
      null -> kotlinx.serialization.json.JsonNull
      is String -> kotlinx.serialization.json.JsonPrimitive(value)
      is Number -> kotlinx.serialization.json.JsonPrimitive(value)
      is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
      is Map<*, *> -> kotlinx.serialization.json.JsonObject(value.entries.associate { it.key.toString() to toJson(it.value) })
      is List<*> -> kotlinx.serialization.json.JsonArray(value.map { toJson(it) })
      else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
    }

  companion object {
    fun describeRule(type: CouponType?, value: Long?): String? =
      when (type) {
        CouponType.PERCENT -> "$value% off the subtotal"
        CouponType.FIXED -> "${value?.div(100)} dollars off"
        null -> null
      }
  }
}
