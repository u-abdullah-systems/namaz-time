package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.azan.AzanAlertState
import com.example.azan.AzanAudioCache
import com.example.azan.AzanPlaybackService
import com.example.azan.AzanScheduler
import com.example.azan.CustomAzanClipStore
import com.example.data.CachedAlarmSnapshot
import com.example.data.LocalScheduleCache
import com.example.data.LocationProvider
import com.example.data.RemoteScheduleSource
import com.example.domain.AppUpdateInfo
import com.example.domain.AsrJuristicMethod
import com.example.domain.CalculationMethod
import com.example.domain.MasjidSchedule
import com.example.domain.PrayerHeroState
import com.example.domain.PrayerName
import com.example.domain.PrayerScheduleResolver
import com.example.domain.ResolvedPrayerTiming
import com.example.domain.ScheduleShareCodec
import com.example.domain.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class NamazDashboardData(
    val activeMasjid: MasjidSchedule,
    val preferences: UserPreferences,
    val todayTimings: List<ResolvedPrayerTiming>,
    val heroState: PrayerHeroState?,
    val enabledPrayers: Set<PrayerName>,
    val activeAzanPlaying: PrayerName?,
    val isSimulatingJamaat: Boolean,
    val isRefreshingLocation: Boolean,
    val statusBanner: String?
)

class MainViewModel(
    private val localCache: LocalScheduleCache,
    private val remoteSource: RemoteScheduleSource,
    private val locationProvider: LocationProvider,
    private val appContext: Context
) : ViewModel() {

    private val _isRefreshingLocation = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _hasCustomAudioClip = MutableStateFlow(CustomAzanClipStore.hasCustomClip(appContext))
    val hasCustomAudioClip: StateFlow<Boolean> = _hasCustomAudioClip.asStateFlow()

    private val _remoteUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    private val _dismissedUpdateKey = MutableStateFlow<String?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = combine(
        _remoteUpdateInfo,
        _dismissedUpdateKey
    ) { info, dismissedKey ->
        if (info != null &&
            info.isNewerThan(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME) &&
            info.isValidDownloadUrl() &&
            info.displayVersion() != dismissedKey
        ) {
            info
        } else {
            null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    private val _demoJamaatEndMillis = MutableStateFlow<Long?>(null)
    private val _demoPrayer = MutableStateFlow(PrayerName.MAGHRIB)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    val dashboardState: StateFlow<NamazDashboardData?> = combine(
        localCache.snapshotFlow,
        tickerFlow,
        AzanAlertState.activePrayer,
        _isRefreshingLocation,
        _statusBanner,
        _demoJamaatEndMillis,
        _demoPrayer
    ) { values ->
        val cache = values[0] as CachedAlarmSnapshot
        val nowMillis = values[1] as Long
        val activeAzan = values[2] as PrayerName?
        val refreshingLoc = values[3] as Boolean
        val banner = values[4] as String?
        val demoEnd = values[5] as Long?
        val demoPrayer = values[6] as PrayerName

        val masjid = cache.cachedMasjidSchedule ?: MasjidSchedule()
        val prefs = UserPreferences(
            calculationMethod = cache.calculationMethod.name,
            asrMethod = cache.asrMethod.name,
            latitude = cache.latitude,
            longitude = cache.longitude,
            locationLabel = cache.locationLabel,
            azanVoiceEnabled = cache.azanVoiceEnabled
        )

        val todayCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val todayTimings = PrayerScheduleResolver.resolveForDay(
            dayCalendar = todayCal,
            latitude = cache.latitude,
            longitude = cache.longitude,
            method = cache.calculationMethod,
            asrMethod = cache.asrMethod,
            masjidSchedule = masjid
        )
        val tomorrowTimings = PrayerScheduleResolver.resolveForDay(
            dayCalendar = tomorrowCal,
            latitude = cache.latitude,
            longitude = cache.longitude,
            method = cache.calculationMethod,
            asrMethod = cache.asrMethod,
            masjidSchedule = masjid
        )

        val isDemoActive = demoEnd != null && nowMillis < demoEnd
        val heroState = if (isDemoActive && demoEnd != null) {
            val totalMillis = masjid.jamaatOffsetFor(demoPrayer).coerceAtLeast(1) * 60_000L
            val remaining = (demoEnd - nowMillis).coerceAtLeast(0L)
            val elapsed = (totalMillis - remaining).coerceIn(0L, totalMillis)
            PrayerHeroState.JamaatCountdown(
                prayer = demoPrayer,
                azanTimeMillis = demoEnd - totalMillis,
                jamaatTimeMillis = demoEnd,
                remainingMillis = remaining,
                progress = (elapsed.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f),
                jamaatFormatted = "Live Preview"
            )
        } else {
            PrayerScheduleResolver.computeHeroState(nowMillis, todayTimings, tomorrowTimings)
        }

        // Instant in-app trigger at exact Azan time (0s delay when app is visible)
        if (!isDemoActive && cache.azanVoiceEnabled && heroState is PrayerHeroState.JamaatCountdown) {
            val elapsedSinceAzan = nowMillis - heroState.azanTimeMillis
            if (elapsedSinceAzan in 0L..12_000L &&
                heroState.prayer in cache.enabledPrayers &&
                AzanAlertState.claimPrayerTrigger(appContext, heroState.prayer, heroState.azanTimeMillis)
            ) {
                AzanPlaybackService.triggerNow(
                    context = appContext,
                    prayer = heroState.prayer,
                    jamaatOffsetMin = masjid.jamaatOffsetFor(heroState.prayer),
                    isTest = false
                )
            }
        }

        NamazDashboardData(
            activeMasjid = masjid,
            preferences = prefs,
            todayTimings = todayTimings,
            heroState = heroState,
            enabledPrayers = cache.enabledPrayers,
            activeAzanPlaying = activeAzan,
            isSimulatingJamaat = isDemoActive,
            isRefreshingLocation = refreshingLoc,
            statusBanner = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AzanAudioCache.ensureBoostedWav(appContext)
        }
        viewModelScope.launch {
            localCache.snapshotFlow.collect { snapshot ->
                AzanScheduler.scheduleNextAzan(appContext, snapshot)
            }
        }
        viewModelScope.launch {
            remoteSource.observeMainDocument().collect { remoteDoc ->
                if (remoteDoc != null) {
                    _remoteUpdateInfo.value = remoteDoc.updateInfo
                    val current = localCache.getSnapshot().cachedMasjidSchedule
                    if (current != remoteDoc.schedule) {
                        localCache.saveMasjidSchedule(remoteDoc.schedule)
                    }
                }
            }
        }
        viewModelScope.launch {
            AzanAlertState.playbackNotice.collect { notice ->
                if (!notice.isNullOrBlank()) {
                    _statusBanner.value = notice
                    AzanAlertState.clearNotice()
                }
            }
        }
        if (locationProvider.hasLocationPermission()) {
            refreshLocationFromDevice(silent = true)
        }
    }

    fun refreshFromRemote() {
        viewModelScope.launch {
            val remoteDoc = remoteSource.fetchMainDocumentOnce()
            val snapshot = localCache.getSnapshot()
            if (remoteDoc != null) {
                _remoteUpdateInfo.value = remoteDoc.updateInfo
                if (snapshot.cachedMasjidSchedule != remoteDoc.schedule) {
                    localCache.saveMasjidSchedule(remoteDoc.schedule)
                    return@launch
                }
            }
            AzanScheduler.scheduleNextAzan(appContext, snapshot)
        }
    }

    fun dismissUpdatePrompt(versionKey: String) {
        _dismissedUpdateKey.value = versionKey
    }

    fun showMessage(message: String) {
        _statusBanner.value = message
    }

    fun refreshLocationFromDevice(silent: Boolean = false) {
        viewModelScope.launch {
            _isRefreshingLocation.value = true
            try {
                val geo = locationProvider.fetchCurrentLocation()
                if (geo != null) {
                    localCache.saveLocation(geo.latitude, geo.longitude, geo.label)
                    if (!silent) {
                        _statusBanner.value = "Updated location to ${geo.label}"
                    }
                } else if (!silent) {
                    _statusBanner.value = "Could not detect GPS location; using saved region."
                }
            } finally {
                _isRefreshingLocation.value = false
            }
        }
    }

    fun updateCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch { localCache.saveCalculationMethod(method) }
    }

    fun updateAsrMethod(method: AsrJuristicMethod) {
        viewModelScope.launch { localCache.saveAsrMethod(method) }
    }

    fun toggleAzanVoice(enabled: Boolean) {
        if (!enabled) {
            stopTestOrActiveAzan()
        }
        viewModelScope.launch { localCache.saveAzanVoiceEnabled(enabled) }
    }

    fun togglePrayerAlert(prayer: PrayerName, enabled: Boolean) {
        if (!enabled && AzanAlertState.activePrayer.value == prayer) {
            stopTestOrActiveAzan()
        }
        viewModelScope.launch { localCache.savePrayerAlertToggle(prayer, enabled) }
    }

    fun saveMasjidSchedule(schedule: MasjidSchedule) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val timeFields = listOf(
                    schedule.fajrAzan, schedule.dhuhrAzan, schedule.asrAzan,
                    schedule.maghribAzan, schedule.ishaAzan,
                    schedule.fajrJamaatOverride, schedule.dhuhrJamaatOverride,
                    schedule.asrJamaatOverride, schedule.maghribJamaatOverride,
                    schedule.ishaJamaatOverride
                )
                for (t in timeFields) {
                    val trimmed = t.trim()
                    if (trimmed.isNotEmpty() && PrayerScheduleResolver.parseHm(trimmed) == null) {
                        _statusBanner.value = "Invalid time '$trimmed'. Use 24h HH:mm (e.g. 05:30) or leave blank."
                        return@launch
                    }
                }

                fun normalizeHm(raw: String): String {
                    val parsed = PrayerScheduleResolver.parseHm(raw.trim()) ?: return ""
                    return String.format(Locale.US, "%02d:%02d", parsed.first, parsed.second)
                }

                val normalized = schedule.copy(
                    masjidName = schedule.masjidName.trim().ifBlank { "My Masjid" }.take(80),
                    cityLabel = schedule.cityLabel.trim().take(80),
                    announcement = schedule.announcement.trim().take(200),
                    fajrAzan = normalizeHm(schedule.fajrAzan),
                    dhuhrAzan = normalizeHm(schedule.dhuhrAzan),
                    asrAzan = normalizeHm(schedule.asrAzan),
                    maghribAzan = normalizeHm(schedule.maghribAzan),
                    ishaAzan = normalizeHm(schedule.ishaAzan),
                    fajrJamaatOverride = normalizeHm(schedule.fajrJamaatOverride),
                    dhuhrJamaatOverride = normalizeHm(schedule.dhuhrJamaatOverride),
                    asrJamaatOverride = normalizeHm(schedule.asrJamaatOverride),
                    maghribJamaatOverride = normalizeHm(schedule.maghribJamaatOverride),
                    ishaJamaatOverride = normalizeHm(schedule.ishaJamaatOverride)
                )
                localCache.saveMasjidSchedule(normalized)
                _statusBanner.value = "Prayer schedule saved and alarms updated."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun resetScheduleToAutoLocation() {
        viewModelScope.launch {
            localCache.saveMasjidSchedule(
                MasjidSchedule(
                    fajrAzan = "",
                    dhuhrAzan = "",
                    asrAzan = "",
                    maghribAzan = "",
                    ishaAzan = ""
                )
            )
            _statusBanner.value = "Reset to automatic location prayer timings."
        }
    }

    fun importScheduleCode(rawCode: String) {
        val decoded = ScheduleShareCodec.decode(rawCode)
        if (decoded == null) {
            _statusBanner.value = "Invalid schedule code. Paste a code starting with NAMAZ1:"
            return
        }
        viewModelScope.launch {
            localCache.saveMasjidSchedule(decoded)
            _statusBanner.value = "Imported schedule for ${decoded.masjidName}"
        }
    }

    fun triggerTestAzanAndCountdown(prayer: PrayerName = PrayerName.MAGHRIB) {
        val schedule = dashboardState.value?.activeMasjid ?: MasjidSchedule()
        val offset = schedule.jamaatOffsetFor(prayer)
        _demoPrayer.value = prayer
        _demoJamaatEndMillis.value = System.currentTimeMillis() + (offset * 60_000L)
        AzanPlaybackService.triggerNow(appContext, prayer, offset, isTest = true)
    }

    fun stopTestOrActiveAzan() {
        _demoJamaatEndMillis.value = null
        AzanPlaybackService.stopNow(appContext)
    }

    fun importCustomAzanClip(uri: Uri) {
        viewModelScope.launch {
            val saved = CustomAzanClipStore.saveFromUri(appContext, uri)
            _hasCustomAudioClip.value = CustomAzanClipStore.hasCustomClip(appContext)
            _statusBanner.value = if (saved) {
                "Custom Hayya 'alas-salah audio clip saved."
            } else {
                "Could not read that audio file. Please choose another MP3, WAV, or M4A clip."
            }
        }
    }

    fun clearCustomAzanClip() {
        CustomAzanClipStore.clear(appContext)
        _hasCustomAudioClip.value = false
        _statusBanner.value = "Switched back to built-in Hayya 'alas-salah voice."
    }

    fun clearBanner() {
        _statusBanner.value = null
    }
}
