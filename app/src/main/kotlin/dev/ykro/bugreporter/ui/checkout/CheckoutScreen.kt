package dev.ykro.bugreporter.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.bugreporter.cart.CartViewModel
import dev.ykro.bugreporter.data.Catalog
import dev.ykro.bugreporter.data.asMoney
import dev.ykro.bugreporter.ui.components.ShopTopBar
import dev.ykro.bugreporter.ui.theme.Danger
import kotlinx.coroutines.launch

/** Checkout is a summary plus a no-op Confirm: the demo is about the cart math, not payments. */
@Composable
fun CheckoutScreen(viewModel: CartViewModel, onBack: () -> Unit, onReportBug: () -> Unit, onSettings: () -> Unit) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  Scaffold(
    topBar = { ShopTopBar("Checkout", state.itemCount, onBack = onBack, onReportBug = onReportBug, onSettings = onSettings) },
    snackbarHost = { SnackbarHost(snackbar) },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Ship to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(Catalog.user.name, style = MaterialTheme.typography.titleMedium)
          Text(Catalog.user.email, style = MaterialTheme.typography.bodyMedium)
        }
      }
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          state.lines.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("${line.quantity} × ${line.product.name}", style = MaterialTheme.typography.bodyLarge)
              Text(line.lineTotalCents.asMoney(), style = MaterialTheme.typography.bodyLarge)
            }
          }
          if (state.discountCents > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Coupon ${state.activeCoupon?.code.orEmpty()}", style = MaterialTheme.typography.bodyLarge)
              Text("−${state.discountCents.asMoney()}", style = MaterialTheme.typography.bodyLarge)
            }
          }
          HorizontalDivider()
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.totalCents.asMoney(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (state.totalCents < 0) Danger else MaterialTheme.colorScheme.primary)
          }
        }
      }
      Spacer(Modifier.weight(1f))
      Button(onClick = { scope.launch { snackbar.showSnackbar("Demo checkout: nothing was charged.") } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Confirm order", style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
