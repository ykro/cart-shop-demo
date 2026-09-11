package dev.ykro.bugreporter.data

import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NetworkExchange(
  val method: String,
  val url: String,
  val requestBody: String?,
  val status: Int,
  val responseBody: String,
  val durationMs: Long,
  val at: String,
)

/**
 * A pretend network layer. It never leaves the device, but it keeps a request/response log so the
 * bug-reporting agent has a "last server exchange" to collect, like a real app would.
 */
class FakeCatalogApi {
  private val json = Json { prettyPrint = false }

  @Volatile var lastExchange: NetworkExchange? = null
    private set

  @Serializable private data class ProductDto(val id: Int, val name: String, val price_cents: Long)

  @Serializable private data class CouponDto(val code: String, val type: String, val value: Long)

  suspend fun fetchCatalog(): List<Product> = call("GET", "/v1/catalog", null) {
    json.encodeToString(Catalog.products.map { ProductDto(it.id, it.name, it.priceCents) })
  }.let { Catalog.products }

  suspend fun validateCoupon(code: String): Coupon? {
    val coupon = Catalog.coupons[code.uppercase()]
    call("POST", "/v1/coupons/validate", "{\"code\":\"$code\"}", status = if (coupon == null) 404 else 200) {
      if (coupon == null) "{\"error\":\"unknown_coupon\"}"
      else json.encodeToString(CouponDto(coupon.code, coupon.type.name, coupon.value))
    }
    return coupon
  }

  private suspend fun call(method: String, path: String, body: String?, status: Int = 200, response: () -> String): String {
    val start = System.currentTimeMillis()
    delay(120 + (path.length * 7L) % 200)
    val text = response()
    lastExchange =
      NetworkExchange(
        method = method,
        url = "https://api.cartshop.example$path",
        requestBody = body,
        status = status,
        responseBody = text,
        durationMs = System.currentTimeMillis() - start,
        at = Instant.now().toString(),
      )
    return text
  }
}
