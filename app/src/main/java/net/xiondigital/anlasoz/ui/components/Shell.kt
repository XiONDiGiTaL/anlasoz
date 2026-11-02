package net.xiondigital.anlasoz.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xiondigital.anlasoz.data.model.MessageThread
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Sora
import net.xiondigital.anlasoz.ui.theme.Xion

@Composable
fun BrandTopBar(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElementTile(size = 38.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "ANLASÖZ",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = Sora,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.4.sp,
                ),
                color = Xion.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "TURKISH COMPANION",
                style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 1.5.sp),
                color = Xion.MutedDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        actions()
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = Xion.Ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Xion.Muted,
        )
    }
}

/** Conversation switcher, doubling as the context indicator. */
@Composable
fun ThreadMenu(
    currentThread: String,
    threads: List<MessageThread>,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .widthIn(max = 138.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Xion.PanelHi)
                .border(1.dp, Xion.Line, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { open = true }
                .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Forum,
                contentDescription = null,
                tint = Xion.Muted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = currentThread.take(16),
                style = MaterialTheme.typography.labelMedium,
                color = Xion.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = "Switch conversation",
                tint = Xion.MutedDim,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Xion.PanelHi,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Xion.LineStrong),
        ) {
            threads.forEach { thread ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = thread.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Xion.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        if (thread.title == currentThread) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Xion.BlueHi,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(thread.id)
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = "New conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Xion.BlueHi,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = Xion.BlueHi,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = {
                    open = false
                    onNew()
                },
            )
        }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun BrandBottomBar(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Xion.BgRaised),
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val tint by animateColorAsState(
                    targetValue = if (selected) Xion.BlueHi else Xion.Muted,
                    label = "navTint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .clickable(role = Role.Tab) { onNavigate(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = Sora,
                            letterSpacing = 0.3.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 10.5.sp,
                        ),
                        color = tint,
                    )
                }
            }
        }
    }
}

@Composable
fun BrandSnackbar(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Xion.PanelHi)
            .border(1.dp, Xion.LineStrong, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Xion.Blue),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Xion.Ink,
        )
    }
}
