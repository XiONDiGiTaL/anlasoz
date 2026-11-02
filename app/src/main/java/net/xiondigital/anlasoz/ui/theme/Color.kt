package net.xiondigital.anlasoz.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The xiondigital.net palette.
 *
 * Values are taken verbatim from the site's design tokens
 * (assets/css/xion.css, `:root`): the site ships a single dark editorial theme,
 * so AnlaSöz does the same instead of inventing a light variant.
 */
object Xion {
    /** Page background. */
    val Bg = Color(0xFF050607)

    /** Raised plate: app bar, element tile. */
    val BgRaised = Color(0xFF0A0B0D)

    /** Card / panel fill. */
    val Panel = Color(0xFF0C0E11)

    /** Higher panel fill: inputs, chips. */
    val PanelHi = Color(0xFF101318)

    /** Hairline: rgba(255,255,255,0.07). */
    val Line = Color(0x12FFFFFF)

    /** Strong hairline: rgba(255,255,255,0.14). */
    val LineStrong = Color(0x24FFFFFF)

    val Ink = Color(0xFFECEDEF)
    val Body = Color(0xFFBDC3CC)
    val Muted = Color(0xFF8B9099)
    val MutedDim = Color(0xFF767D87)

    val Blue = Color(0xFF3F80FF)
    val BlueHi = Color(0xFF79A8FF)
    val BlueSoft = Color(0x1F3F80FF)
    val BlueSofter = Color(0x143F80FF)

    val Danger = Color(0xFFFF6B6B)
    val Warn = Color(0xFFE9B949)
    val Ok = Color(0xFF5FD48A)
}
