package net.xiondigital.anlasoz.ui

import androidx.compose.runtime.staticCompositionLocalOf
import net.xiondigital.anlasoz.audio.SpeechController

/** The app-scoped Turkish speech controller, available to every screen. */
val LocalSpeech = staticCompositionLocalOf<SpeechController> {
    error("SpeechController is not provided")
}
