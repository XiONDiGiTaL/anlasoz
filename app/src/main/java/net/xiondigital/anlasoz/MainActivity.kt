package net.xiondigital.anlasoz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import net.xiondigital.anlasoz.ui.AnlaSozApp
import net.xiondigital.anlasoz.ui.theme.AnlaSozTheme

class MainActivity : ComponentActivity() {

    /** Text shared into AnlaSöz from other apps (or the text selection menu). */
    private val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        captureSharedText(intent)

        val container = (application as AnlaSozApplication).container
        setContent {
            AnlaSozTheme {
                val text by sharedText.collectAsStateWithLifecycle()
                AnlaSozApp(
                    container = container,
                    sharedText = text,
                    onSharedTextConsumed = { sharedText.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureSharedText(intent)
    }

    private fun captureSharedText(intent: Intent?) {
        if (intent == null) return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) sharedText.value = text
    }
}
