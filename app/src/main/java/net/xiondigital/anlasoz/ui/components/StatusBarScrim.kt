package net.xiondigital.anlasoz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import net.xiondigital.anlasoz.ui.theme.Xion

/**
 * Keeps scrolled content from colliding with the status bar without adding a
 * hard divider: a short brand-background fade behind the clock and icons.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + 16.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Xion.Bg, Xion.Bg, Xion.Bg.copy(alpha = 0f)),
                ),
            ),
    )
}
