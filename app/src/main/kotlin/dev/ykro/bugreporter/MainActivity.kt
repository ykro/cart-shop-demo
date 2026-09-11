package dev.ykro.bugreporter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ykro.bugreporter.cart.CartViewModel
import dev.ykro.bugreporter.instrumentation.ScreenshotCapture
import dev.ykro.bugreporter.instrumentation.ShakeDetector
import dev.ykro.bugreporter.ui.BugReportRoute
import dev.ykro.bugreporter.ui.CartShopNavHost
import dev.ykro.bugreporter.ui.screenName
import dev.ykro.bugreporter.ui.theme.CartShopTheme
import java.util.UUID
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
  private val app: CartShopApp
    get() = application as CartShopApp

  private val cartViewModel: CartViewModel by viewModels {
    viewModelFactory { initializer { CartViewModel(app.cartRepo, app.api, app.breadcrumbs, totalsSink = { app.cartTotals = it }) } }
  }

  private var nav: NavHostController? = null
  private var starting = false
  private lateinit var shake: ShakeDetector

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    shake = ShakeDetector(this) { startBugReport(fromShake = true) }
    setContent {
      CartShopTheme {
        val controller = rememberNavController().also { nav = it }
        val entry by controller.currentBackStackEntryAsState()
        var resumed by remember { mutableStateOf(false) }
        LaunchedEffect(entry?.destination?.route) { entry?.destination?.route?.let { app.breadcrumbs.record("screen:${screenName(it)}") } }
        LaunchedEffect(Unit) {
          if (!resumed) {
            resumed = true
            val pending = app.settings.pendingSessionIdNow()
            if (pending != null && app.agentRuntime.sessionExists(pending)) {
              Timber.i("Resuming bug report session %s", pending)
              controller.navigate(BugReportRoute(pending, app.settings.pendingScreenNow() ?: "Catalog"))
            } else if (pending != null) {
              app.settings.clearPending()
            }
          }
        }
        CartShopNavHost(app, controller, cartViewModel, onReportBug = { startBugReport(fromShake = false) })
      }
    }
  }

  override fun onResume() {
    super.onResume()
    shake.start()
  }

  override fun onPause() {
    shake.stop()
    super.onPause()
  }

  /** Snapshot first, then open the agent: the screenshot must show the screen the tester complained about. */
  private fun startBugReport(fromShake: Boolean) {
    val controller = nav ?: return
    val currentRoute = controller.currentBackStackEntry?.destination?.route
    if (starting || currentRoute?.contains("BugReportRoute") == true) return
    starting = true
    val screen = screenName(currentRoute)
    app.breadcrumbs.record(if (fromShake) "report:shake" else "report:menu")
    lifecycleScope.launch {
      try {
        val sessionId = "report-" + UUID.randomUUID().toString().take(8)
        val png = runCatching { ScreenshotCapture.capturePng(this@MainActivity) }.getOrNull()
        app.agentRuntime.createSession(sessionId)
        app.screenshotArtifact = png?.let { app.agentRuntime.saveScreenshot(sessionId, it) }
        app.settings.setPending(sessionId, screen)
        Timber.i("Bug report started from %s (session %s, screenshot=%s)", screen, sessionId, app.screenshotArtifact != null)
        controller.navigate(BugReportRoute(sessionId, screen))
      } catch (e: Exception) {
        Timber.e(e, "Could not start bug report")
      } finally {
        starting = false
      }
    }
  }
}
