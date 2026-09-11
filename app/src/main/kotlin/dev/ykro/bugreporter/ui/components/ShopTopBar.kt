package dev.ykro.bugreporter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Every shop screen has the overflow entry point to the bug reporter (plus the shake gesture). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopTopBar(
  title: String,
  cartCount: Int,
  onBack: (() -> Unit)? = null,
  onCart: (() -> Unit)? = null,
  onReportBug: () -> Unit,
  onSettings: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  TopAppBar(
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    actions = {
      if (onCart != null) {
        IconButton(onClick = onCart) {
          BadgedBox(badge = { if (cartCount > 0) Badge { Text("$cartCount") } }) { Icon(Icons.Outlined.ShoppingCart, "Cart") }
        }
      }
      Box {
        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(text = { Text("Report a bug") }, leadingIcon = { Icon(Icons.Outlined.BugReport, null) }, onClick = { menuOpen = false; onReportBug() })
          DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menuOpen = false; onSettings() })
        }
      }
    },
  )
}
