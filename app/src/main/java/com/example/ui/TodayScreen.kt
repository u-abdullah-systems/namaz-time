package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.IslamicCalendar
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.azan.AzanScheduler
import com.example.domain.PrayerHeroState
import com.example.domain.PrayerName
import com.example.domain.PrayerScheduleResolver
import com.example.domain.ResolvedPrayerTiming
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
    data: NamazDashboardData,
    onRefreshLocation: () -> Unit,
    onTogglePrayerAlert: (PrayerName, Boolean) -> Unit,
    onTestAzan: (PrayerName) -> Unit,
    onStopAzan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    var exactAlarmBannerDismissed by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exactAlarmsGranted = remember(resumeTick) {
        AzanScheduler.canScheduleExactAlarms(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRefreshLocation()
    }

    val dateHeader = remember {
        val gregorian = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
        val hijri = formatHijriDate()
        "$gregorian • $hijri"
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Date & Location Bar
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = dateHeader,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    onRefreshLocation()
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            }
                            .padding(vertical = 4.dp)
                            .testTag("location_refresh_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Current location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = data.preferences.locationLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (data.isRefreshingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = "Detect my location",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. Hero Countdown Card (Live Jamaat Countdown when Azan active, else Next Azan)
            item {
                HeroPrayerCountdownCard(
                    heroState = data.heroState,
                    activeAzanPlaying = data.activeAzanPlaying,
                    isSimulatingJamaat = data.isSimulatingJamaat,
                    onTestAzan = {
                        val nextPrayer = when (data.heroState) {
                            is PrayerHeroState.JamaatCountdown -> data.heroState.prayer
                            is PrayerHeroState.NextAzanCountdown -> data.heroState.prayer
                            null -> PrayerName.MAGHRIB
                        }
                        onTestAzan(nextPrayer)
                    },
                    onStopAzan = onStopAzan
                )
            }

            if (data.preferences.azanVoiceEnabled && !exactAlarmsGranted && !exactAlarmBannerDismissed) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AlarmOn,
                                contentDescription = "Exact alarm permission",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Allow exact alarms so Azan starts on the exact second even when locked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            setData(Uri.parse("package:${context.packageName}"))
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.testTag("today_allow_exact_alarm_button")
                            ) {
                                Text("Allow")
                            }
                            IconButton(
                                onClick = { exactAlarmBannerDismissed = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss exact alarm reminder",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // 3. Announcement Banner (if configured)
            val announcement = data.activeMasjid.announcement.trim()
            if (announcement.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Campaign,
                                contentDescription = "Masjid announcement",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = announcement,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // 4. Section Header for Today's 5 Prayers
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Prayer Timings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Azan / Jamaat",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. All 5 Daily Prayer Cards
            items(data.todayTimings, key = { it.prayer.name }) { timing ->
                val isCurrentJamaat = data.heroState is PrayerHeroState.JamaatCountdown &&
                    data.heroState.prayer == timing.prayer
                val isNextAzan = data.heroState is PrayerHeroState.NextAzanCountdown &&
                    data.heroState.prayer == timing.prayer
                val alertEnabled = timing.prayer in data.enabledPrayers

                PrayerTimingCard(
                    timing = timing,
                    isCurrentJamaat = isCurrentJamaat,
                    isNextAzan = isNextAzan,
                    alertEnabled = alertEnabled,
                    onToggleAlert = { onTogglePrayerAlert(timing.prayer, !alertEnabled) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeroPrayerCountdownCard(
    heroState: PrayerHeroState?,
    activeAzanPlaying: PrayerName?,
    isSimulatingJamaat: Boolean,
    onTestAzan: () -> Unit,
    onStopAzan: () -> Unit
) {
    val isJamaatActive = heroState is PrayerHeroState.JamaatCountdown
    val isAlertRinging = activeAzanPlaying != null || isSimulatingJamaat

    Card(
        shape = MaterialTheme.shapes.large,
        border = if (isJamaatActive) BorderStroke(2.dp, Color(0xFFE5B85C)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_mosque_hero),
                contentDescription = "Mosque arch header illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            if (isJamaatActive) {
                                listOf(
                                    Color(0xFF1F1604).copy(alpha = 0.72f),
                                    Color(0xFF0A2E29).copy(alpha = 0.94f)
                                )
                            } else {
                                listOf(
                                    Color(0xFF062521).copy(alpha = 0.65f),
                                    Color(0xFF062521).copy(alpha = 0.92f)
                                )
                            }
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (heroState) {
                    is PrayerHeroState.JamaatCountdown -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = Color(0xFFE5B85C),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = null,
                                        tint = Color(0xFF2B1A00),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${heroState.prayer.displayName} Azan • Hayya 'alas-salah",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF2B1A00)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.jamaat_countdown_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE5B85C)
                        )

                        Text(
                            text = PrayerScheduleResolver.formatDuration(heroState.remainingMillis),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            modifier = Modifier.testTag("hero_countdown_timer")
                        )

                        LinearProgressIndicator(
                            progress = { heroState.progress.coerceIn(0f, 1f) },
                            color = Color(0xFFE5B85C),
                            trackColor = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )

                        Text(
                            text = "${heroState.prayer.displayName} Jamaat at ${heroState.jamaatFormatted}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }

                    is PrayerHeroState.NextAzanCountdown -> {
                        Text(
                            text = "UPCOMING PRAYER • ${heroState.prayer.arabicName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFE5B85C)
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = heroState.prayer.displayName,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Azan ${heroState.azanFormatted}  •  Jamaat ${heroState.jamaatFormatted}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.next_azan_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Text(
                            text = PrayerScheduleResolver.formatDuration(heroState.remainingMillis),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            modifier = Modifier.testTag("hero_countdown_timer")
                        )
                    }

                    null -> {
                        Text(
                            text = "Loading today's prayer schedule…",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }

                // Quick action row to preview/test "Hayya 'alas-salah" + Jamaat countdown or stop active playback
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAlertRinging) {
                        Button(
                            onClick = onStopAzan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE5B85C),
                                contentColor = Color(0xFF2B1A00)
                            ),
                            modifier = Modifier.testTag("stop_azan_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.stop_azan_label))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onTestAzan,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("test_azan_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.test_azan_label))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerTimingCard(
    timing: ResolvedPrayerTiming,
    isCurrentJamaat: Boolean,
    isNextAzan: Boolean,
    alertEnabled: Boolean,
    onToggleAlert: () -> Unit
) {
    val containerColor = when {
        isCurrentJamaat -> MaterialTheme.colorScheme.secondaryContainer
        isNextAzan -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        isCurrentJamaat -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        isNextAzan -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prayer_card_${timing.prayer.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timing.prayer.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timing.prayer.arabicName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (timing.isCustomOverride) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Jamaat +${timing.jamaatOffsetMinutes}m",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (timing.prayer == PrayerName.MAGHRIB) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    AnimatedVisibility(visible = isCurrentJamaat) {
                        Text(
                            text = "  •  Countdown active",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = timing.azanFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Jamaat ${timing.jamaatFormatted}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onToggleAlert,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_alert_${timing.prayer.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = if (alertEnabled) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.NotificationsOff
                        },
                        contentDescription = if (alertEnabled) {
                            "Disable ${timing.prayer.displayName} Azan alert"
                        } else {
                            "Enable ${timing.prayer.displayName} Azan alert"
                        },
                        tint = if (alertEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        }
                    )
                }
            }
        }
    }
}

private fun formatHijriDate(): String {
    return try {
        val cal = IslamicCalendar()
        val day = cal.get(IslamicCalendar.DAY_OF_MONTH)
        val monthIdx = cal.get(IslamicCalendar.MONTH)
        val year = cal.get(IslamicCalendar.YEAR)
        val months = listOf(
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
        val monthName = months.getOrElse(monthIdx) { "Hijri" }
        "$day $monthName $year AH"
    } catch (_: Exception) {
        "Islamic Calendar"
    }
}
