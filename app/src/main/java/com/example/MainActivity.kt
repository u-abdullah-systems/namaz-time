package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.LocalScheduleCache
import com.example.data.LocationProvider
import com.example.data.RemoteScheduleSource
import com.example.ui.AlertsAndSettingsScreen
import com.example.ui.MainViewModel
import com.example.ui.TodayScreen
import com.example.ui.theme.NamazTimeTheme

enum class AppTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TODAY(R.string.nav_today, Icons.Rounded.Schedule, Icons.Outlined.Schedule, "tab_today"),
    ALERTS(R.string.nav_alerts, Icons.Rounded.Notifications, Icons.Outlined.Notifications, "tab_alerts")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NamazTimeTheme {
                NamazDashboard()
            }
        }
    }
}

@Composable
private fun NamazDashboard() {
    val viewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                MainViewModel(
                    localCache = LocalScheduleCache(app),
                    remoteSource = RemoteScheduleSource(app),
                    locationProvider = LocationProvider(app),
                    appContext = app
                )
            }
        }
    )

    val dashboardData by viewModel.dashboardState.collectAsStateWithLifecycle()
    val hasCustomAudioClip by viewModel.hasCustomAudioClip.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFromRemote()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val banner = dashboardData?.statusBanner
    LaunchedEffect(banner) {
        if (!banner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(banner)
            viewModel.clearBanner()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val data = dashboardData
                    if (data == null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Calculating prayer timings…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        when (selectedTab) {
                            AppTab.TODAY -> TodayScreen(
                                data = data,
                                onRefreshLocation = { viewModel.refreshLocationFromDevice() },
                                onTogglePrayerAlert = viewModel::togglePrayerAlert,
                                onTestAzan = viewModel::triggerTestAzanAndCountdown,
                                onStopAzan = viewModel::stopTestOrActiveAzan
                            )

                            AppTab.ALERTS -> AlertsAndSettingsScreen(
                                data = data,
                                hasCustomAudioClip = hasCustomAudioClip,
                                onImportCustomAudioClip = viewModel::importCustomAzanClip,
                                onClearCustomAudioClip = viewModel::clearCustomAzanClip,
                                onToggleAzanVoice = viewModel::toggleAzanVoice,
                                onTogglePrayerAlert = viewModel::togglePrayerAlert,
                                onUpdateCalculationMethod = viewModel::updateCalculationMethod,
                                onUpdateAsrMethod = viewModel::updateAsrMethod,
                                onRefreshLocation = { viewModel.refreshLocationFromDevice() },
                                onTestAzan = viewModel::triggerTestAzanAndCountdown,
                                onStopAzan = viewModel::stopTestOrActiveAzan,
                                onBackToToday = { selectedTab = AppTab.TODAY }
                            )
                        }
                    }
                }
            }
        }

        val pendingUpdate = availableUpdate
        if (pendingUpdate != null) {
            val versionTag = pendingUpdate.displayVersion()
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdatePrompt(versionTag) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("${stringResource(R.string.update_available_title)} (v$versionTag)")
                },
                text = {
                    Text(
                        text = pendingUpdate.notes.ifBlank {
                            stringResource(R.string.update_default_notes)
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUpdate.apkUrl))
                                context.startActivity(intent)
                            }.onFailure {
                                viewModel.showMessage("Could not open update download link.")
                            }
                            viewModel.dismissUpdatePrompt(versionTag)
                        },
                        modifier = Modifier.testTag("app_update_download_button")
                    ) {
                        Text(stringResource(R.string.update_now_label))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissUpdatePrompt(versionTag) },
                        modifier = Modifier.testTag("app_update_later_button")
                    ) {
                        Text(stringResource(R.string.update_later_label))
                    }
                }
            )
        }
    }
}
