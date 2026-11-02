package net.xiondigital.anlasoz.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.xiondigital.anlasoz.AppContainer
import net.xiondigital.anlasoz.audio.SpeechController
import net.xiondigital.anlasoz.ui.components.BrandBottomBar
import net.xiondigital.anlasoz.ui.components.BrandSnackbar
import net.xiondigital.anlasoz.ui.components.NavItem
import net.xiondigital.anlasoz.ui.components.StatusBarScrim
import net.xiondigital.anlasoz.ui.history.HistoryScreen
import net.xiondigital.anlasoz.ui.history.HistoryViewModel
import net.xiondigital.anlasoz.ui.learn.LearnScreen
import net.xiondigital.anlasoz.ui.learn.LearnViewModel
import net.xiondigital.anlasoz.ui.reply.ReplyScreen
import net.xiondigital.anlasoz.ui.reply.ReplyViewModel
import net.xiondigital.anlasoz.ui.settings.SettingsScreen
import net.xiondigital.anlasoz.ui.settings.SettingsViewModel
import net.xiondigital.anlasoz.ui.theme.Xion
import net.xiondigital.anlasoz.ui.translate.TranslateScreen
import net.xiondigital.anlasoz.ui.translate.TranslateViewModel

object Routes {
    const val TRANSLATE = "translate"
    const val REPLY = "reply"
    const val LEARN = "learn"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private val navItems = listOf(
    NavItem(Routes.TRANSLATE, "Translate", Icons.Outlined.Translate),
    NavItem(Routes.REPLY, "Reply", Icons.Outlined.Forum),
    NavItem(Routes.LEARN, "Learn", Icons.Outlined.MenuBook),
    NavItem(Routes.HISTORY, "History", Icons.Outlined.History),
)

@Composable
fun AnlaSozApp(
    container: AppContainer,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    val ttsState by container.speech.state.collectAsStateWithLifecycle()
    LaunchedEffect(ttsState.message) {
        val message = ttsState.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Voice settings",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            try {
                context.startActivity(SpeechController.ttsSettingsIntent())
            } catch (e: ActivityNotFoundException) {
                // Device without a TTS settings screen: nothing else to offer here.
            }
        }
        container.speech.clearMessage()
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank() && currentRoute != Routes.TRANSLATE) {
            navController.navigate(Routes.TRANSLATE) { launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalSpeech provides container.speech) {
        Scaffold(
            containerColor = Xion.Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    BrandSnackbar(data.visuals.message)
                }
            },
            bottomBar = {
                if (currentRoute != Routes.SETTINGS) {
                    BrandBottomBar(
                        items = navItems,
                        currentRoute = currentRoute,
                        onNavigate = { route -> navController.navigateToTab(route) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.TRANSLATE,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Xion.Bg)
                    .padding(padding),
            ) {
                composable(Routes.TRANSLATE) {
                    TranslateScreen(
                        viewModel = viewModel(
                            factory = simpleVmFactory { TranslateViewModel(container) },
                        ),
                        snackbarHostState = snackbarHostState,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        sharedText = sharedText,
                        onSharedTextConsumed = onSharedTextConsumed,
                    )
                }
                composable(Routes.REPLY) {
                    ReplyScreen(
                        viewModel = viewModel(
                            factory = simpleVmFactory { ReplyViewModel(container) },
                        ),
                        snackbarHostState = snackbarHostState,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    )
                }
                composable(Routes.LEARN) {
                    LearnScreen(
                        viewModel = viewModel(
                            factory = simpleVmFactory { LearnViewModel(container) },
                        ),
                        snackbarHostState = snackbarHostState,
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        viewModel = viewModel(
                            factory = simpleVmFactory { HistoryViewModel(container) },
                        ),
                        snackbarHostState = snackbarHostState,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel(
                            factory = simpleVmFactory { SettingsViewModel(container) },
                        ),
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            StatusBarScrim(Modifier.align(androidx.compose.ui.Alignment.TopCenter))
            }
        }
    }
}

/** Tab navigation that keeps each tab's state and avoids stacking duplicates. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TRANSLATE) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
