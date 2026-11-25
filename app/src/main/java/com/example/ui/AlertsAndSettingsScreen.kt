package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.R
import com.example.azan.AzanScheduler
import com.example.domain.AsrJuristicMethod
import com.example.domain.CalculationMethod
import com.example.domain.PrayerName

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsAndSettingsScreen(
    data: NamazDashboardData,
    hasCustomAudioClip: Boolean,
    onImportCustomAudioClip: (Uri) -> Unit,
    onClearCustomAudioClip: () -> Unit,
    onToggleAzanVoice: (Boolean) -> Unit,
    onTogglePrayerAlert: (PrayerName, Boolean) -> Unit,
    onUpdateCalculationMethod: (CalculationMethod) -> Unit,
    onUpdateAsrMethod: (AsrJuristicMethod) -> Unit,
    onRefreshLocation: () -> Unit,
    onTestAzan: (PrayerName) -> Unit,
    onStopAzan: () -> Unit,
    onBackToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToToday)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exactAlarmsGranted = remember(data.preferences, resumeTick) {
        AzanScheduler.canScheduleExactAlarms(context)
    }
    val notificationsGranted = remember(data.preferences, resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
    val locationGranted = remember(data.preferences, resumeTick) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRefreshLocation()
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportCustomAudioClip(uri)
        }
    }

    val currentCalc = CalculationMethod.fromRaw(data.preferences.calculationMethod)
    val currentAsr = AsrJuristicMethod.fromRaw(data.preferences.asrMethod)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Alerts & Location",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 1. Lock-Screen Hayya 'alas-salah Voice Alert Controls
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Azan Sound (Hayya 'alas-salah)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (data.preferences.azanVoiceEnabled) {
                                        "Plays only the short Hayya 'alas-salah call and stops. Turn off to mute all sound."
                                    } else {
                                        "Sound disabled — Jamaat countdown runs silently."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = data.preferences.azanVoiceEnabled,
                                onCheckedChange = onToggleAzanVoice,
                                modifier = Modifier.testTag("master_azan_switch")
                            )
                        }

                        if (data.activeAzanPlaying != null || data.isSimulatingJamaat) {
                            Button(
                                onClick = onStopAzan,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.stop_azan_label))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onTestAzan(PrayerName.MAGHRIB) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_test_azan_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.test_azan_label))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("choose_azan_audio_button")
                            ) {
                                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasCustomAudioClip) "Change Audio Clip" else "Use Custom Clip")
                            }
                            if (hasCustomAudioClip) {
                                OutlinedButton(
                                    onClick = onClearCustomAudioClip,
                                    modifier = Modifier.testTag("reset_azan_audio_button")
                                ) {
                                    Text("Built-in")
                                }
                            }
                        }

                        PrayerName.entries.forEach { prayer ->
                            val enabled = prayer in data.enabledPrayers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${prayer.displayName} (${prayer.arabicName})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (data.preferences.azanVoiceEnabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Switch(
                                    checked = enabled,
                                    enabled = data.preferences.azanVoiceEnabled,
                                    onCheckedChange = { onTogglePrayerAlert(prayer, it) }
                                )
                            }
                        }
                    }
                }
            }

            // 2. System Permissions for Reliable Lock-Screen Azan & Location
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Device Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        PermissionActionRow(
                            icon = Icons.Rounded.AlarmOn,
                            title = "Exact Azan Alarms",
                            subtitle = if (exactAlarmsGranted) "Granted — triggers on time when screen is off" else "Recommended for lock-screen Azan",
                            buttonLabel = if (exactAlarmsGranted) "Granted" else "Allow",
                            enabled = !exactAlarmsGranted,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        setData(Uri.parse("package:${context.packageName}"))
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )

                        PermissionActionRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Lock-Screen Notifications",
                            subtitle = if (notificationsGranted) "Granted — shows Jamaat countdown alert" else "Required for Azan alert banner",
                            buttonLabel = if (notificationsGranted) "Granted" else "Allow",
                            enabled = !notificationsGranted,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )

                        PermissionActionRow(
                            icon = Icons.Rounded.LocationOn,
                            title = "Location (${data.preferences.locationLabel})",
                            subtitle = if (locationGranted) "Detected automatically" else "Allow to auto-calculate local timings",
                            buttonLabel = if (locationGranted) "Refresh" else "Allow",
                            enabled = true,
                            onClick = {
                                if (locationGranted) {
                                    onRefreshLocation()
                                } else {
                                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            }
                        )
                    }
                }
            }

            // 3. Calculation & Juristic Method
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Astronomical Calculation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CalculationMethod.entries.forEach { method ->
                                FilterChip(
                                    selected = currentCalc == method,
                                    onClick = { onUpdateCalculationMethod(method) },
                                    label = { Text(method.displayName) }
                                )
                            }
                        }

                        Text(
                            text = "Asr Juristic Method",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsrJuristicMethod.entries.forEach { asr ->
                                FilterChip(
                                    selected = currentAsr == asr,
                                    onClick = { onUpdateAsrMethod(asr) },
                                    label = { Text(asr.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Namaz Time • Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("app_version_label")
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(buttonLabel)
        }
    }
}
