package dev.ykro.bugreporter.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ykro.bugreporter.data.Catalog
import dev.ykro.bugreporter.data.Product
import dev.ykro.bugreporter.data.asMoney
import dev.ykro.bugreporter.ui.components.ProductImage
import dev.ykro.bugreporter.ui.components.ShopTopBar
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(
  cartCount: Int,
  onOpenProduct: (Int) -> Unit,
  onAddToCart: (Product) -> Unit,
  onOpenCart: () -> Unit,
  onReportBug: () -> Unit,
  onSettings: () -> Unit,
) {
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  Scaffold(
    topBar = { ShopTopBar("Cart Shop", cartCount, onCart = onOpenCart, onReportBug = onReportBug, onSettings = onSettings) },
    snackbarHost = { SnackbarHost(snackbar) },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(span = { GridItemSpan(2) }) {
        Column(Modifier.padding(bottom = 4.dp)) {
          Text("Desk essentials", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          Text("Four products, three coupons, one bug.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      items(Catalog.products, key = { it.id }) { product ->
        Card(
          onClick = { onOpenProduct(product.id) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(0.dp),
        ) {
          Column(Modifier.padding(10.dp)) {
            ProductImage(product.imageRes, Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp)
            Spacer(Modifier.height(10.dp))
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(product.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text(product.priceCents.asMoney(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              FilledTonalIconButton(onClick = { onAddToCart(product); scope.launch { snackbar.showSnackbar("${product.name} added to cart") } }) {
                Icon(Icons.Default.Add, "Add to cart")
              }
            }
          }
        }
      }
    }
  }
}
