package dev.ykro.bugreporter

import android.app.Application
import dev.ykro.bugreporter.agent.AgentRuntime
import dev.ykro.bugreporter.agent.ContextTools
import dev.ykro.bugreporter.agent.GitHubClient
import dev.ykro.bugreporter.agent.GitHubTools
import dev.ykro.bugreporter.agent.Redactor
import dev.ykro.bugreporter.data.CartDatabase
import dev.ykro.bugreporter.data.CartRepository
import dev.ykro.bugreporter.data.Catalog
import dev.ykro.bugreporter.data.FakeCatalogApi
import dev.ykro.bugreporter.data.SettingsStore
import dev.ykro.bugreporter.instrumentation.Breadcrumbs
import dev.ykro.bugreporter.instrumentation.LogBuffer
import timber.log.Timber

/** Manual dependency graph; small enough that a DI framework would only hide the wiring. */
class CartShopApp : Application() {
  val logBuffer = LogBuffer()
  val breadcrumbs = Breadcrumbs()
  val api = FakeCatalogApi()
  val settings by lazy { SettingsStore(this) }
  val database by lazy { CartDatabase.create(this) }
  val cartRepo by lazy { CartRepository(database.cartDao()) }
  val redactor = Redactor(knownNames = listOf(Catalog.user.name), knownEmails = listOf(Catalog.user.email))
  val gitHub = GitHubClient(BuildConfig.GITHUB_TOKEN, BuildConfig.GITHUB_REPO)

  /** Live cart totals as computed by the (buggy) view model; the agent reads them through a tool. */
  @Volatile var cartTotals: ContextTools.CartTotals = ContextTools.CartTotals(null, null, 0, 0, 0)
  @Volatile var screenshotArtifact: String? = null

  val agentRuntime by lazy {
    AgentRuntime(
      context = this,
      contextTools =
        ContextTools(
          context = this,
          breadcrumbs = breadcrumbs,
          logs = logBuffer,
          cartRepo = cartRepo,
          user = Catalog.user,
          api = api,
          cartSnapshot = { cartTotals },
          screenshotName = { screenshotArtifact },
          redactor = redactor,
        ),
      gitHubTools = GitHubTools(gitHub),
    )
  }

  override fun onCreate() {
    super.onCreate()
    Timber.plant(logBuffer.Tree())
    Timber.i("Cart Shop started (version %s)", BuildConfig.VERSION_NAME)
  }
}
