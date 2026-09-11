package dev.ykro.bugreporter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ykro.bugreporter.ui.theme.Canvas

/** Illustrations are generated on a solid canvas color, so they sit in a card of the same color. */
@Composable
fun ProductImage(@DrawableRes res: Int, modifier: Modifier = Modifier, corner: Dp = 20.dp, padding: Dp = 0.dp) {
  Box(modifier.clip(RoundedCornerShape(corner)).background(Canvas), contentAlignment = Alignment.Center) {
    Image(painter = painterResource(res), contentDescription = null, modifier = Modifier.fillMaxSize().padding(padding), contentScale = ContentScale.Fit)
  }
}
