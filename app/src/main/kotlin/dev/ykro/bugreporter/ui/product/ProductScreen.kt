package dev.ykro.bugreporter.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ykro.bugreporter.data.Product
import dev.ykro.bugreporter.data.asMoney
import dev.ykro.bugreporter.ui.components.ProductImage
import dev.ykro.bugreporter.ui.components.ShopTopBar

@Composable
fun ProductScreen(
  product: Product,
  cartCount: Int,
  onBack: () -> Unit,
  onAdd: (Product, Int) -> Unit,
  onOpenCart: () -> Unit,
  onReportBug: () -> Unit,
  onSettings: () -> Unit,
) {
  var qty by remember { mutableIntStateOf(1) }
  Scaffold(
    topBar = { ShopTopBar(product.name, cartCount, onBack = onBack, onCart = onOpenCart, onReportBug = onReportBug, onSettings = onSettings) },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
      ProductImage(product.imageRes, Modifier.fillMaxWidth().aspectRatio(1f), corner = 28.dp)
      Spacer(Modifier.height(20.dp))
      Text(product.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text(product.tagline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(12.dp))
      Text(product.priceCents.asMoney(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(24.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quantity", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = { if (qty > 1) qty-- }) { Icon(Icons.Default.Remove, "Less") }
        Text("$qty", style = MaterialTheme.typography.titleLarge)
        FilledTonalIconButton(onClick = { if (qty < 9) qty++ }) { Icon(Icons.Default.Add, "More") }
      }
      Spacer(Modifier.height(24.dp))
      Button(onClick = { onAdd(product, qty); onOpenCart() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Icon(Icons.Outlined.ShoppingCart, null)
        Spacer(Modifier.padding(6.dp))
        Text("Add to cart · ${(product.priceCents * qty).asMoney()}", style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
