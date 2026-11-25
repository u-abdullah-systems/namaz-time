package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.MasjidSchedule
import com.example.domain.PrayerName
import com.example.domain.ScheduleShareCodec

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageScheduleScreen(
    data: NamazDashboardData,
    isSaving: Boolean,
    onSaveSchedule: (MasjidSchedule) -> Unit,
    onResetToAutoLocation: () -> Unit,
    onImportScheduleCode: (String) -> Unit,
    onBackToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToToday)

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var masjidName by remember { mutableStateOf(data.activeMasjid.masjidName) }
    var cityLabel by remember { mutableStateOf(data.activeMasjid.cityLabel) }
    var announcement by remember { mutableStateOf(data.activeMasjid.announcement) }
    var fajrAzan by remember { mutableStateOf(data.activeMasjid.fajrAzan) }
    var dhuhrAzan by remember { mutableStateOf(data.activeMasjid.dhuhrAzan) }
    var asrAzan by remember { mutableStateOf(data.activeMasjid.asrAzan) }
    var maghribAzan by remember { mutableStateOf(data.activeMasjid.maghribAzan) }
    var ishaAzan by remember { mutableStateOf(data.activeMasjid.ishaAzan) }

    var fajrOffset by remember { mutableIntStateOf(data.activeMasjid.fajrJamaatOffsetMin) }
    var dhuhrOffset by remember { mutableIntStateOf(data.activeMasjid.dhuhrJamaatOffsetMin) }
    var asrOffset by remember { mutableIntStateOf(data.activeMasjid.asrJamaatOffsetMin) }
    var maghribOffset by remember { mutableIntStateOf(data.activeMasjid.maghribJamaatOffsetMin) }
    var ishaOffset by remember { mutableIntStateOf(data.activeMasjid.ishaJamaatOffsetMin) }

    var fajrJamaatOv by remember { mutableStateOf(data.activeMasjid.fajrJamaatOverride) }
    var dhuhrJamaatOv by remember { mutableStateOf(data.activeMasjid.dhuhrJamaatOverride) }
    var asrJamaatOv by remember { mutableStateOf(data.activeMasjid.asrJamaatOverride) }
    var maghribJamaatOv by remember { mutableStateOf(data.activeMasjid.maghribJamaatOverride) }
    var ishaJamaatOv by remember { mutableStateOf(data.activeMasjid.ishaJamaatOverride) }

    var importCodeInput by remember { mutableStateOf("") }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(data.activeMasjid) {
        val src = data.activeMasjid
        masjidName = src.masjidName
        cityLabel = src.cityLabel
        announcement = src.announcement
        fajrAzan = src.fajrAzan
        dhuhrAzan = src.dhuhrAzan
        asrAzan = src.asrAzan
        maghribAzan = src.maghribAzan
        ishaAzan = src.ishaAzan
        fajrOffset = src.fajrJamaatOffsetMin
        dhuhrOffset = src.dhuhrJamaatOffsetMin
        asrOffset = src.asrJamaatOffsetMin
        maghribOffset = src.maghribJamaatOffsetMin
        ishaOffset = src.ishaJamaatOffsetMin
        fajrJamaatOv = src.fajrJamaatOverride
        dhuhrJamaatOv = src.dhuhrJamaatOverride
        asrJamaatOv = src.asrJamaatOverride
        maghribJamaatOv = src.maghribJamaatOverride
        ishaJamaatOv = src.ishaJamaatOverride
    }

    val draftSchedule = MasjidSchedule(
        masjidName = masjidName,
        cityLabel = cityLabel,
        announcement = announcement,
        fajrAzan = fajrAzan,
        dhuhrAzan = dhuhrAzan,
        asrAzan = asrAzan,
        maghribAzan = maghribAzan,
        ishaAzan = ishaAzan,
        fajrJamaatOffsetMin = fajrOffset,
        dhuhrJamaatOffsetMin = dhuhrOffset,
        asrJamaatOffsetMin = asrOffset,
        maghribJamaatOffsetMin = maghribOffset,
        ishaJamaatOffsetMin = ishaOffset,
        fajrJamaatOverride = fajrJamaatOv,
        dhuhrJamaatOverride = dhuhrJamaatOv,
        asrJamaatOverride = asrJamaatOv,
        maghribJamaatOverride = maghribJamaatOv,
        ishaJamaatOverride = ishaJamaatOv
    )

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
                    text = "Update Prayer Timings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customize Azan overrides, Jamaat wait minutes, and announcements. Leave Azan blank to use automatic GPS timings for your location.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Maghrib Note Banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.maghrib_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Share / Import Schedule Code Card (for sharing timings without sign-in)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Share or Import Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Copy your schedule code to share with family or mosque members, or paste a code below. (Automatic cloud sync can be wired to your Firebase project later.)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val code = ScheduleShareCodec.encode(draftSchedule)
                                    clipboardManager.setText(AnnotatedString(code))
                                    copiedFeedback = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (copiedFeedback) "Copied!" else "Copy Code")
                            }
                            OutlinedButton(
                                onClick = onResetToAutoLocation,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Auto")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = importCodeInput,
                                onValueChange = { importCodeInput = it },
                                label = { Text("Paste Schedule Code") },
                                placeholder = { Text("NAMAZ1:...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_schedule_input")
                            )
                            OutlinedButton(
                                onClick = {
                                    if (importCodeInput.isNotBlank()) {
                                        onImportScheduleCode(importCodeInput)
                                        importCodeInput = ""
                                    }
                                },
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import")
                            }
                        }
                    }
                }
            }

            // Masjid Metadata
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = masjidName,
                            onValueChange = { masjidName = it },
                            label = { Text("Masjid / Schedule Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("masjid_name_input")
                        )

                        OutlinedTextField(
                            value = cityLabel,
                            onValueChange = { cityLabel = it },
                            label = { Text("City / Area (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = announcement,
                            onValueChange = { announcement = it },
                            label = { Text("Announcement Banner (optional)") },
                            placeholder = { Text("e.g. Jummah Khutbah at 1:15 PM") },
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("announcement_input")
                        )
                    }
                }
            }

            // Per-Prayer Editors
            item {
                PrayerOverrideEditorCard(
                    prayer = PrayerName.FAJR,
                    azanHm = fajrAzan,
                    onAzanChange = { fajrAzan = it },
                    offsetMin = fajrOffset,
                    onOffsetChange = { fajrOffset = it },
                    jamaatOverrideHm = fajrJamaatOv,
                    onJamaatOverrideChange = { fajrJamaatOv = it }
                )
            }
            item {
                PrayerOverrideEditorCard(
                    prayer = PrayerName.DHUHR,
                    azanHm = dhuhrAzan,
                    onAzanChange = { dhuhrAzan = it },
                    offsetMin = dhuhrOffset,
                    onOffsetChange = { dhuhrOffset = it },
                    jamaatOverrideHm = dhuhrJamaatOv,
                    onJamaatOverrideChange = { dhuhrJamaatOv = it }
                )
            }
            item {
                PrayerOverrideEditorCard(
                    prayer = PrayerName.ASR,
                    azanHm = asrAzan,
                    onAzanChange = { asrAzan = it },
                    offsetMin = asrOffset,
                    onOffsetChange = { asrOffset = it },
                    jamaatOverrideHm = asrJamaatOv,
                    onJamaatOverrideChange = { asrJamaatOv = it }
                )
            }
            item {
                PrayerOverrideEditorCard(
                    prayer = PrayerName.MAGHRIB,
                    azanHm = maghribAzan,
                    onAzanChange = { maghribAzan = it },
                    offsetMin = maghribOffset,
                    onOffsetChange = { maghribOffset = it },
                    jamaatOverrideHm = maghribJamaatOv,
                    onJamaatOverrideChange = { maghribJamaatOv = it },
                    isMaghrib = true
                )
            }
            item {
                PrayerOverrideEditorCard(
                    prayer = PrayerName.ISHA,
                    azanHm = ishaAzan,
                    onAzanChange = { ishaAzan = it },
                    offsetMin = ishaOffset,
                    onOffsetChange = { ishaOffset = it },
                    jamaatOverrideHm = ishaJamaatOv,
                    onJamaatOverrideChange = { ishaJamaatOv = it }
                )
            }

            // Save Button
            item {
                Button(
                    onClick = { onSaveSchedule(draftSchedule) },
                    enabled = !isSaving && masjidName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_schedule_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Updated Timings",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrayerOverrideEditorCard(
    prayer: PrayerName,
    azanHm: String,
    onAzanChange: (String) -> Unit,
    offsetMin: Int,
    onOffsetChange: (Int) -> Unit,
    jamaatOverrideHm: String,
    onJamaatOverrideChange: (String) -> Unit,
    isMaghrib: Boolean = false
) {
    val presetOffsets = if (isMaghrib) listOf(3, 5, 7, 10, 15) else listOf(10, 15, 20, 25, 30)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isMaghrib) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${prayer.displayName} (${prayer.arabicName})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (jamaatOverrideHm.isNotBlank()) "Jamaat: $jamaatOverrideHm" else "Jamaat: +${offsetMin}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = azanHm,
                    onValueChange = onAzanChange,
                    label = { Text("Azan (HH:mm)") },
                    placeholder = { Text("Auto GPS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = jamaatOverrideHm,
                    onValueChange = onJamaatOverrideChange,
                    label = { Text("Exact Jamaat") },
                    placeholder = { Text("+${offsetMin}m") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Jamaat Wait After Azan (minutes)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetOffsets.forEach { preset ->
                    FilterChip(
                        selected = offsetMin == preset && jamaatOverrideHm.isBlank(),
                        onClick = {
                            onJamaatOverrideChange("")
                            onOffsetChange(preset)
                        },
                        label = { Text("${preset}m") }
                    )
                }
            }
        }
    }
}
