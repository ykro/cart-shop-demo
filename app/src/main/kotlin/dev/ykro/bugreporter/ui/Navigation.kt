package dev.ykro.bugreporter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.ykro.bugreporter.CartShopApp
import dev.ykro.bugreporter.cart.CartViewModel
import dev.ykro.bugreporter.data.Catalog
import dev.ykro.bugreporter.ui.bugreport.BugReportScreen
import dev.ykro.bugreporter.ui.bugreport.BugReportViewModel
import dev.ykro.bugreporter.ui.cart.CartScreen
import dev.ykro.bugreporter.ui.catalog.CatalogScreen
import dev.ykro.bugreporter.ui.checkout.CheckoutScreen
import dev.ykro.bugreporter.ui.product.ProductScreen
import dev.ykro.bugreporter.ui.settings.SettingsScreen
import dev.ykro.bugreporter.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable object CatalogRoute
@Serializable data class ProductRoute(val id: Int)
@Serializable object CartRoute
@Serializable object CheckoutRoute
@Serializable object SettingsRoute
@Serializable data class BugReportRoute(val sessionId: String, val screen: String)

/** Human-readable screen name for the agent's kickoff message. */
fun screenName(route: String?): String =
  when {
    route == null -> "Catalog"
    route.contains("ProductRoute") -> "Product"
    route.contains("CartRoute") -> "Cart"
    route.contains("CheckoutRoute") -> "Checkout"
    route.contains("SettingsRoute") -> "Settings"
    route.contains("BugReportRoute") -> "Bug report"
    else -> "Catalog"
  }

@Composable
fun CartShopNavHost(app: CartShopApp, nav: NavHostController, cartViewModel: CartViewModel, onReportBug: () -> Unit) {
  val cart by cartViewModel.uiState.collectAsStateWithLifecycle()
  val toSettings = { nav.navigate(SettingsRoute) }
  NavHost(navController = nav, startDestination = CatalogRoute) {
    composable<CatalogRoute> {
      CatalogScreen(
        cartCount = cart.itemCount,
        onOpenProduct = { nav.navigate(ProductRoute(it)) },
        onAddToCart = { cartViewModel.addToCart(it.id, 1) },
        onOpenCart = { nav.navigate(CartRoute) },
        onReportBug = onReportBug,
        onSettings = toSettings,
      )
    }
    composable<ProductRoute> { entry ->
      val product = Catalog.product(entry.toRoute<ProductRoute>().id) ?: return@composable
      ProductScreen(
        product = product,
        cartCount = cart.itemCount,
        onBack = { nav.popBackStack() },
        onAdd = { p, qty -> cartViewModel.addToCart(p.id, qty) },
        onOpenCart = { nav.navigate(CartRoute) },
        onReportBug = onReportBug,
        onSettings = toSettings,
      )
    }
    composable<CartRoute> {
      CartScreen(cartViewModel, onBack = { nav.popBackStack() }, onPay = { nav.navigate(CheckoutRoute) }, onBrowse = { nav.popBackStack(CatalogRoute, inclusive = false) }, onReportBug = onReportBug, onSettings = toSettings)
    }
    composable<CheckoutRoute> { CheckoutScreen(cartViewModel, onBack = { nav.popBackStack() }, onReportBug = onReportBug, onSettings = toSettings) }
    composable<SettingsRoute> {
      val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app, app.settings, app.gitHub.isConfigured, app.gitHub.repoName) } })
      SettingsScreen(vm, onBack = { nav.popBackStack() })
    }
    composable<BugReportRoute> { entry ->
      val route = entry.toRoute<BugReportRoute>()
      val vm: BugReportViewModel =
        viewModel(key = route.sessionId, factory = viewModelFactory { initializer { BugReportViewModel(route.sessionId, route.screen, app.agentRuntime, app.settings, BugReportViewModel.modelAvailable(app)) } })
      BugReportScreen(vm, onClose = { nav.popBackStack() }, onOpenSettings = toSettings)
    }
  }
}
