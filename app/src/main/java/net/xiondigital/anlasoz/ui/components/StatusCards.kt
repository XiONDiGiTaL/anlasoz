package net.xiondigital.anlasoz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xiondigital.anlasoz.ai.AiFailure
import net.xiondigital.anlasoz.ai.detail
import net.xiondigital.anlasoz.ai.headline
import net.xiondigital.anlasoz.ui.theme.Xion

@Composable
fun FailureCard(
    failure: AiFailure,
    detail: String?,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = when (failure) {
        AiFailure.NETWORK, AiFailure.TIMEOUT -> Xion.Warn
        else -> Xion.Danger
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Xion.Panel)
            .border(1.dp, tint.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (failure == AiFailure.NETWORK) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            SectionLabel(failure.headline, color = tint)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = failure.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = Xion.Body,
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail.take(220),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = net.xiondigital.anlasoz.ui.theme.Mono,
                    fontSize = 10.5.sp,
                ),
                color = Xion.MutedDim,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = "Retry", onClick = onRetry)
            QuietTextButton(text = "Model settings", onClick = onOpenSettings)
        }
    }
}

@Composable
fun SetupCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Connect your model",
    body: String = "AnlaSöz uses the key you own. Add an OpenAI-compatible endpoint, a model name and your key once — translation, interpretation and reply generation all run through it.",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Xion.Panel)
            .border(1.dp, Xion.Blue.copy(alpha = 0.28f), MaterialTheme.shapes.large)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Key,
                contentDescription = null,
                tint = Xion.BlueHi,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            SectionLabel("Setup", color = Xion.BlueHi)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Xion.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Xion.Muted)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Open settings", onClick = onOpenSettings)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "The key is stored with the Android Keystore and is never bundled in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = Xion.MutedDim,
        )
    }
}

