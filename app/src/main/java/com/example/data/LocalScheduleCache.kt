package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AsrJuristicMethod
import com.example.domain.CalculationMethod
import com.example.domain.MasjidSchedule
import com.example.domain.PrayerName
import com.example.domain.TimezoneFallbackCoordinates
import com.example.domain.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.namazDataStore: DataStore<Preferences> by preferencesDataStore(name = "namaz_local_cache")

data class CachedAlarmSnapshot(
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String,
    val calculationMethod: CalculationMethod,
    val asrMethod: AsrJuristicMethod,
    val azanVoiceEnabled: Boolean,
    val enabledPrayers: Set<PrayerName>,
    val cachedMasjidSchedule: MasjidSchedule?
)

class LocalScheduleCache(private val context: Context) {

    private object Keys {
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
        val CALC_METHOD = stringPreferencesKey("calc_method")
        val ASR_METHOD = stringPreferencesKey("asr_method")
        val AZAN_VOICE_ENABLED = booleanPreferencesKey("azan_voice_enabled")
        val FAJR_ENABLED = booleanPreferencesKey("prayer_fajr_enabled")
        val DHUHR_ENABLED = booleanPreferencesKey("prayer_dhuhr_enabled")
        val ASR_ENABLED = booleanPreferencesKey("prayer_asr_enabled")
        val MAGHRIB_ENABLED = booleanPreferencesKey("prayer_maghrib_enabled")
        val ISHA_ENABLED = booleanPreferencesKey("prayer_isha_enabled")

        val MASJID_NAME = stringPreferencesKey("cached_masjid_name")
        val CITY_LABEL = stringPreferencesKey("cached_city_label")
        val ANNOUNCEMENT = stringPreferencesKey("cached_announcement")
        val FAJR_AZAN = stringPreferencesKey("cached_fajr_azan")
        val DHUHR_AZAN = stringPreferencesKey("cached_dhuhr_azan")
        val ASR_AZAN = stringPreferencesKey("cached_asr_azan")
        val MAGHRIB_AZAN = stringPreferencesKey("cached_maghrib_azan")
        val ISHA_AZAN = stringPreferencesKey("cached_isha_azan")
        val FAJR_OFFSET = intPreferencesKey("cached_fajr_offset")
        val DHUHR_OFFSET = intPreferencesKey("cached_dhuhr_offset")
        val ASR_OFFSET = intPreferencesKey("cached_asr_offset")
        val MAGHRIB_OFFSET = intPreferencesKey("cached_isha_maghrib_offset")
        val ISHA_OFFSET = intPreferencesKey("cached_isha_offset")
        val FAJR_JAMAAT_OV = stringPreferencesKey("cached_fajr_jamaat_ov")
        val DHUHR_JAMAAT_OV = stringPreferencesKey("cached_dhuhr_jamaat_ov")
        val ASR_JAMAAT_OV = stringPreferencesKey("cached_asr_jamaat_ov")
        val MAGHRIB_JAMAAT_OV = stringPreferencesKey("cached_maghrib_jamaat_ov")
        val ISHA_JAMAAT_OV = stringPreferencesKey("cached_isha_jamaat_ov")
    }

    val snapshotFlow: Flow<CachedAlarmSnapshot> = context.applicationContext.namazDataStore.data.map { prefs ->
        val fallback = TimezoneFallbackCoordinates.forDeviceTimezone()
        val enabled = buildSet {
            if (prefs[Keys.FAJR_ENABLED] ?: true) add(PrayerName.FAJR)
            if (prefs[Keys.DHUHR_ENABLED] ?: true) add(PrayerName.DHUHR)
            if (prefs[Keys.ASR_ENABLED] ?: true) add(PrayerName.ASR)
            if (prefs[Keys.MAGHRIB_ENABLED] ?: true) add(PrayerName.MAGHRIB)
            if (prefs[Keys.ISHA_ENABLED] ?: true) add(PrayerName.ISHA)
        }
        val cachedSchedule = MasjidSchedule(
            masjidName = prefs[Keys.MASJID_NAME] ?: "My Masjid",
            cityLabel = prefs[Keys.CITY_LABEL] ?: "",
            announcement = prefs[Keys.ANNOUNCEMENT] ?: "",
            fajrAzan = prefs[Keys.FAJR_AZAN] ?: "",
            dhuhrAzan = prefs[Keys.DHUHR_AZAN] ?: "13:15",
            asrAzan = prefs[Keys.ASR_AZAN] ?: "",
            maghribAzan = prefs[Keys.MAGHRIB_AZAN] ?: "17:34",
            ishaAzan = prefs[Keys.ISHA_AZAN] ?: "19:00",
            fajrJamaatOffsetMin = prefs[Keys.FAJR_OFFSET] ?: 15,
            dhuhrJamaatOffsetMin = prefs[Keys.DHUHR_OFFSET] ?: 15,
            asrJamaatOffsetMin = prefs[Keys.ASR_OFFSET] ?: 15,
            maghribJamaatOffsetMin = prefs[Keys.MAGHRIB_OFFSET] ?: 5,
            ishaJamaatOffsetMin = prefs[Keys.ISHA_OFFSET] ?: 15,
            fajrJamaatOverride = prefs[Keys.FAJR_JAMAAT_OV] ?: "",
            dhuhrJamaatOverride = prefs[Keys.DHUHR_JAMAAT_OV] ?: "",
            asrJamaatOverride = prefs[Keys.ASR_JAMAAT_OV] ?: "",
            maghribJamaatOverride = prefs[Keys.MAGHRIB_JAMAAT_OV] ?: "",
            ishaJamaatOverride = prefs[Keys.ISHA_JAMAAT_OV] ?: ""
        )

        CachedAlarmSnapshot(
            latitude = prefs[Keys.LATITUDE] ?: fallback.latitude,
            longitude = prefs[Keys.LONGITUDE] ?: fallback.longitude,
            locationLabel = prefs[Keys.LOCATION_LABEL] ?: fallback.label,
            calculationMethod = CalculationMethod.fromRaw(prefs[Keys.CALC_METHOD]),
            asrMethod = AsrJuristicMethod.fromRaw(prefs[Keys.ASR_METHOD]),
            azanVoiceEnabled = prefs[Keys.AZAN_VOICE_ENABLED] ?: true,
            enabledPrayers = enabled,
            cachedMasjidSchedule = cachedSchedule
        )
    }

    suspend fun getSnapshot(): CachedAlarmSnapshot = snapshotFlow.first()

    suspend fun saveLocation(latitude: Double, longitude: Double, label: String) {
        context.applicationContext.namazDataStore.edit { prefs ->
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.LOCATION_LABEL] = label
        }
    }

    suspend fun saveCalculationMethod(method: CalculationMethod) {
        context.applicationContext.namazDataStore.edit { it[Keys.CALC_METHOD] = method.name }
    }

    suspend fun saveAsrMethod(method: AsrJuristicMethod) {
        context.applicationContext.namazDataStore.edit { it[Keys.ASR_METHOD] = method.name }
    }

    suspend fun saveAzanVoiceEnabled(enabled: Boolean) {
        context.applicationContext.namazDataStore.edit { it[Keys.AZAN_VOICE_ENABLED] = enabled }
    }

    suspend fun savePrayerAlertToggle(prayer: PrayerName, enabled: Boolean) {
        val key = when (prayer) {
            PrayerName.FAJR -> Keys.FAJR_ENABLED
            PrayerName.DHUHR -> Keys.DHUHR_ENABLED
            PrayerName.ASR -> Keys.ASR_ENABLED
            PrayerName.MAGHRIB -> Keys.MAGHRIB_ENABLED
            PrayerName.ISHA -> Keys.ISHA_ENABLED
        }
        context.applicationContext.namazDataStore.edit { it[key] = enabled }
    }

    suspend fun saveMasjidSchedule(masjidSchedule: MasjidSchedule) {
        context.applicationContext.namazDataStore.edit { prefs ->
            prefs[Keys.MASJID_NAME] = masjidSchedule.masjidName
            prefs[Keys.CITY_LABEL] = masjidSchedule.cityLabel
            prefs[Keys.ANNOUNCEMENT] = masjidSchedule.announcement
            prefs[Keys.FAJR_AZAN] = masjidSchedule.fajrAzan
            prefs[Keys.DHUHR_AZAN] = masjidSchedule.dhuhrAzan
            prefs[Keys.ASR_AZAN] = masjidSchedule.asrAzan
            prefs[Keys.MAGHRIB_AZAN] = masjidSchedule.maghribAzan
            prefs[Keys.ISHA_AZAN] = masjidSchedule.ishaAzan
            prefs[Keys.FAJR_OFFSET] = masjidSchedule.fajrJamaatOffsetMin
            prefs[Keys.DHUHR_OFFSET] = masjidSchedule.dhuhrJamaatOffsetMin
            prefs[Keys.ASR_OFFSET] = masjidSchedule.asrJamaatOffsetMin
            prefs[Keys.MAGHRIB_OFFSET] = masjidSchedule.maghribJamaatOffsetMin
            prefs[Keys.ISHA_OFFSET] = masjidSchedule.ishaJamaatOffsetMin
            prefs[Keys.FAJR_JAMAAT_OV] = masjidSchedule.fajrJamaatOverride
            prefs[Keys.DHUHR_JAMAAT_OV] = masjidSchedule.dhuhrJamaatOverride
            prefs[Keys.ASR_JAMAAT_OV] = masjidSchedule.asrJamaatOverride
            prefs[Keys.MAGHRIB_JAMAAT_OV] = masjidSchedule.maghribJamaatOverride
            prefs[Keys.ISHA_JAMAAT_OV] = masjidSchedule.ishaJamaatOverride
        }
    }
}
