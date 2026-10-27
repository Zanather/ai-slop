package io.github.zanather.clicker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.zanather.clicker.ui.apps.AppsScreen
import io.github.zanather.clicker.ui.devices.DevicesScreen
import io.github.zanather.clicker.ui.info.DeviceInfoScreen
import io.github.zanather.clicker.ui.remote.RemoteScreen
import io.github.zanather.clicker.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object RemoteRoute

@Serializable
internal data object AppsRoute

@Serializable
internal data object DevicesRoute

@Serializable
internal data class InfoRoute(val serialNumber: String?)

@Serializable
internal data object SettingsRoute

/**
 * The remote is home; everything else is one level deep. A screen on its way out is still drawn and
 * tappable, so its back and launch taps only count while it's resumed: a double tap can't pop the remote.
 */
@Composable
fun ClickerNavHost(viewModel: ClickerViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = RemoteRoute) {
        composable<RemoteRoute> {
            val state by viewModel.remote.collectAsStateWithLifecycle()
            RemoteScreen(
                state = state,
                actions = viewModel,
                onOpenApps = { nav.navigate(AppsRoute) { launchSingleTop = true } },
                onOpenDevices = { nav.navigate(DevicesRoute) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<AppsRoute> { entry ->
            val state by viewModel.apps.collectAsStateWithLifecycle()
            AppsScreen(
                state = state,
                onLaunch = { appId ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        viewModel.launch(appId)
                        nav.popBackStack(RemoteRoute, inclusive = false)
                    }
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onRefresh = viewModel::refreshApps,
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable<DevicesRoute> {
            val state by viewModel.devices.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.scan() }
            DevicesScreen(
                state = state,
                onSelect = { row ->
                    viewModel.select(row)
                    nav.popBackStack(RemoteRoute, inclusive = false)
                },
                onForget = viewModel::forget,
                onShowInfo = { row -> nav.navigate(InfoRoute(row.serialNumber)) },
                onScan = viewModel::scan,
                onAddByAddress = viewModel::addByAddress,
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable<InfoRoute> { entry ->
            val serial = entry.toRoute<InfoRoute>().serialNumber
            val state by viewModel.info.collectAsStateWithLifecycle()
            LaunchedEffect(serial) { viewModel.loadInfo(serial) }
            DeviceInfoScreen(state = state, onBack = dropUnlessResumed { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            val state by viewModel.settingsUi.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onChange = viewModel::updateSettings,
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
    }
}
