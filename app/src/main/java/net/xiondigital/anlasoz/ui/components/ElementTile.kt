package net.xiondigital.anlasoz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Sora
import net.xiondigital.anlasoz.ui.theme.Xion

/**
 * The AnlaSöz element mark: a fictional periodic-table tile in the Kryvex
 * grammar, rendered with the xiondigital.net palette.
 *
 * Sizes follow the brand rules: below 56dp the tile keeps only the symbol,
 * below 240dp it adds the designation, and the full plate adds the name block.
 */
@Composable
fun ElementTile(
    size: Dp,
    modifier: Modifier = Modifier,
    full: Boolean = false,
    designation: String = "x07",
    symbol: String = "An",
) {
    val shape = RoundedCornerShape(size * 0.164f)
    val showDesignation = size >= 56.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Xion.BgRaised)
            .border(1.dp, Xion.LineStrong, shape),
    ) {
        if (showDesignation) {
            Text(
                text = designation,
                style = MonoLabel.copy(
                    fontSize = fixedSp(size, 0.062f),
                    letterSpacing = fixedSp(size, 0.006f),
                ),
                color = Xion.Blue,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = size * 0.083f, top = size * 0.070f),
            )
        }

        if (full) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = size * 0.083f, top = size * 0.083f),
                horizontalArrangement = Arrangement.spacedBy(size * 0.019f),
            ) {
                listOf(0.35f, 0.6f, 1f).forEach { alpha ->
                    Box(
                        Modifier
                            .size(size * 0.021f)
                            .clip(RoundedCornerShape(size * 0.004f))
                            .background(Xion.Blue.copy(alpha = alpha)),
                    )
                }
            }
        }

        Text(
            text = symbol,
            fontFamily = Sora,
            fontWeight = FontWeight.SemiBold,
            fontSize = fixedSp(size, if (full) 0.335f else 0.40f),
            color = Xion.Ink,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = if (full) size * 0.16f else 0.dp),
        )

        if (full) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = size * 0.083f, end = size * 0.083f, bottom = size * 0.145f),
                verticalArrangement = Arrangement.spacedBy(size * 0.022f),
            ) {
                Box(
                    Modifier
                        .width(size * 0.125f)
                        .height(size * 0.008f)
                        .background(Xion.Blue),
                )
                Text(
                    text = "ANLASÖZ",
                    fontFamily = Sora,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fixedSp(size, 0.062f),
                    letterSpacing = fixedSp(size, 0.016f),
                    color = Xion.Blue,
                )
                Text(
                    text = "TURKISH LANGUAGE SYSTEM",
                    style = MonoLabel.copy(
                        fontSize = fixedSp(size, 0.027f),
                        letterSpacing = fixedSp(size, 0.006f),
                    ),
                    color = Xion.Muted,
                )
            }
        }
    }
}

/**
 * Converts a fraction of the tile's dp size into a fixed text size, so the mark
 * never grows or shrinks with the user's system font scale.
 */
@Composable
private fun fixedSp(size: Dp, fraction: Float): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    return (size.value * fraction / fontScale).sp
}
