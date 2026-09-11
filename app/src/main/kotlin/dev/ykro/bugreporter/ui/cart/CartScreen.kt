package dev.ykro.bugreporter.ui.cart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.bugreporter.R
import dev.ykro.bugreporter.cart.CartUiState
import dev.ykro.bugreporter.cart.CartViewModel
import dev.ykro.bugreporter.data.CartLine
import dev.ykro.bugreporter.data.asMoney
import dev.ykro.bugreporter.ui.components.ProductImage
import dev.ykro.bugreporter.ui.components.ShopTopBar
import dev.ykro.bugreporter.ui.theme.AmberSoft
import dev.ykro.bugreporter.ui.theme.Danger
import dev.ykro.bugreporter.ui.theme.DangerSoft
import dev.ykro.bugreporter.ui.theme.Success

@Composable
fun CartScreen(viewModel: CartViewModel, onBack: () -> Unit, onPay: () -> Unit, onBrowse: () -> Unit, onReportBug: () -> Unit, onSettings: () -> Unit) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
    topBar = { ShopTopBar("Your cart", state.itemCount, onBack = onBack, onReportBug = onReportBug, onSettings = onSettings) },
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = { if (state.lines.isNotEmpty()) PayBar(state, onPay) },
  ) { padding ->
    if (state.lines.isEmpty()) {
      EmptyCart(Modifier.padding(padding), onBrowse)
      return@Scaffold
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      items(state.lines, key = { it.product.id }) { line -> CartLineCard(line, onQty = { viewModel.changeQuantity(line.product.id, it) }) }
      item { CouponCard(state, viewModel) }
      item { TotalsCard(state) }
      item { TextButton(onClick = viewModel::clearCart) { Text("Clear cart") } }
    }
  }
}

@Composable
private fun CartLineCard(line: CartLine, onQty: (Int) -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      ProductImage(line.product.imageRes, Modifier.size(72.dp), corner = 14.dp)
      Column(Modifier.weight(1f)) {
        Text(line.product.name, style = MaterialTheme.typography.titleMedium)
        Text("${line.product.priceCents.asMoney()} each", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(line.lineTotalCents.asMoney(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = { onQty(line.quantity - 1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, "Less") }
        Text("${line.quantity}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        FilledTonalIconButton(onClick = { onQty(line.quantity + 1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, "More") }
      }
    }
  }
}

@Composable
private fun CouponCard(state: CartUiState, viewModel: CartViewModel) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(12.dp)) {
      val active = state.activeCoupon
      if (active != null) {
        Row(Modifier.fillMaxWidth().background(AmberSoft, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.LocalOffer, null)
          Spacer(Modifier.size(8.dp))
          Text("Coupon ${active.code} applied", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
          IconButton(onClick = viewModel::removeCoupon, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Remove coupon") }
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = state.couponInput,
            onValueChange = viewModel::onCouponInput,
            label = { Text("Coupon code") },
            singleLine = true,
            isError = state.couponError != null,
            supportingText = state.couponError?.let { { Text(it) } },
            modifier = Modifier.weight(1f),
          )
          FilledTonalButton(onClick = viewModel::applyCoupon, enabled = state.couponInput.isNotBlank() && !state.applyingCoupon) { Text(if (state.applyingCoupon) "…" else "Apply") }
        }
        Text("Try HALF, SAVE10 or OFF100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun TotalsCard(state: CartUiState) {
  val negative = state.totalCents < 0
  Card(colors = CardDefaults.cardColors(containerColor = if (negative) DangerSoft else MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      TotalsRow("Subtotal", state.subtotalCents.asMoney())
      TotalsRow("Discount", if (state.discountCents > 0) "−${state.discountCents.asMoney()}" else "—", color = if (state.discountCents > 0) Success else null)
      HorizontalDivider()
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(state.totalCents.asMoney(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (negative) Danger else MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
private fun TotalsRow(label: String, value: String, color: Color? = null) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun PayBar(state: CartUiState, onPay: () -> Unit) {
  Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
    Button(onClick = onPay, modifier = Modifier.fillMaxWidth().height(56.dp)) {
      Text("Pay ${state.totalCents.asMoney()}", style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun EmptyCart(modifier: Modifier, onBrowse: () -> Unit) {
  Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Image(painterResource(R.drawable.empty_cart), null, Modifier.size(220.dp))
    Spacer(Modifier.height(16.dp))
    Text("Your cart is empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Add a product to start the demo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    Button(onClick = onBrowse) { Text("Browse products") }
  }
}
