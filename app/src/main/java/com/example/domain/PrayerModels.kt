package com.example.domain

import android.util.Base64
import org.json.JSONObject

enum class PrayerName(
    val displayName: String,
    val arabicName: String,
    val defaultJamaatOffsetMin: Int
) {
    FAJR("Fajr", "الفجر", 15),
    DHUHR("Zuhr", "الظهر", 15),
    ASR("Asr", "العصر", 15),
    MAGHRIB("Maghrib", "المغرب", 5),
    ISHA("Isha", "العشاء", 15)
}

enum class CalculationMethod(
    val displayName: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaFixedMinutesAfterMaghrib: Int? = null
) {
    ISNA("ISNA (North America)", 15.0, 15.0),
    MWL("Muslim World League", 18.0, 17.0),
    UMM_AL_QURA("Umm al-Qura (Makkah)", 18.5, 0.0, 90),
    KARACHI("Univ. of Islamic Sciences, Karachi", 18.0, 18.0),
    EGYPT("Egyptian General Authority", 19.5, 17.5);

    companion object {
        fun fromRaw(raw: String?): CalculationMethod =
            entries.firstOrNull { it.name == raw } ?: ISNA
    }
}

enum class AsrJuristicMethod(val displayName: String, val shadowFactor: Int) {
    STANDARD("Standard (Shafi / Maliki / Hanbali)", 1),
    HANAFI("Hanafi", 2);

    companion object {
        fun fromRaw(raw: String?): AsrJuristicMethod =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

data class MasjidSchedule(
    val masjidName: String = "My Masjid",
    val cityLabel: String = "",
    val fajrAzan: String = "",
    val dhuhrAzan: String = "13:15",
    val asrAzan: String = "",
    val maghribAzan: String = "17:34",
    val ishaAzan: String = "19:00",
    val fajrJamaatOffsetMin: Int = 15,
    val dhuhrJamaatOffsetMin: Int = 15,
    val asrJamaatOffsetMin: Int = 15,
    val maghribJamaatOffsetMin: Int = 5,
    val ishaJamaatOffsetMin: Int = 15,
    val fajrJamaatOverride: String = "",
    val dhuhrJamaatOverride: String = "",
    val asrJamaatOverride: String = "",
    val maghribJamaatOverride: String = "",
    val ishaJamaatOverride: String = "",
    val announcement: String = ""
) {
    fun azanOverrideFor(prayer: PrayerName): String = when (prayer) {
        PrayerName.FAJR -> fajrAzan
        PrayerName.DHUHR -> dhuhrAzan
        PrayerName.ASR -> asrAzan
        PrayerName.MAGHRIB -> maghribAzan
        PrayerName.ISHA -> ishaAzan
    }

    fun jamaatOffsetFor(prayer: PrayerName): Int = when (prayer) {
        PrayerName.FAJR -> fajrJamaatOffsetMin
        PrayerName.DHUHR -> dhuhrJamaatOffsetMin
        PrayerName.ASR -> asrJamaatOffsetMin
        PrayerName.MAGHRIB -> maghribJamaatOffsetMin
        PrayerName.ISHA -> ishaJamaatOffsetMin
    }

    fun jamaatOverrideFor(prayer: PrayerName): String = when (prayer) {
        PrayerName.FAJR -> fajrJamaatOverride
        PrayerName.DHUHR -> dhuhrJamaatOverride
        PrayerName.ASR -> asrJamaatOverride
        PrayerName.MAGHRIB -> maghribJamaatOverride
        PrayerName.ISHA -> ishaJamaatOverride
    }

    fun hasAnyCustomOverride(): Boolean =
        fajrAzan.isNotBlank() || dhuhrAzan.isNotBlank() || asrAzan.isNotBlank() ||
            maghribAzan.isNotBlank() || ishaAzan.isNotBlank() ||
            fajrJamaatOverride.isNotBlank() || dhuhrJamaatOverride.isNotBlank() ||
            asrJamaatOverride.isNotBlank() || maghribJamaatOverride.isNotBlank() ||
            ishaJamaatOverride.isNotBlank() || announcement.isNotBlank()
}

data class UserPreferences(
    val calculationMethod: String = CalculationMethod.ISNA.name,
    val asrMethod: String = AsrJuristicMethod.STANDARD.name,
    val latitude: Double = 37.3382,
    val longitude: Double = -121.8863,
    val locationLabel: String = "Current Location",
    val azanVoiceEnabled: Boolean = true
)

data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String = "",
    val apkUrl: String = ""
) {
    fun displayVersion(): String = versionName.trim().ifBlank { versionCode.toString() }

    fun isValidDownloadUrl(): Boolean {
        val trimmed = apkUrl.trim().lowercase()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    fun isNewerThan(installedCode: Int, installedName: String): Boolean {
        val remoteName = versionName.trim()
        if (remoteName.isNotEmpty()) {
            val cmp = compareVersionNames(remoteName, installedName)
            if (cmp != 0) return cmp > 0
        }
        return versionCode > installedCode
    }

    companion object {
        fun compareVersionNames(remote: String, installed: String): Int {
            fun parseParts(raw: String): List<Int> =
                raw.trim()
                    .removePrefix("v")
                    .removePrefix("V")
                    .split(".")
                    .map { part ->
                        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                    }

            val a = parseParts(remote)
            val b = parseParts(installed)
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                val va = a.getOrElse(i) { 0 }
                val vb = b.getOrElse(i) { 0 }
                if (va != vb) return va.compareTo(vb)
            }
            return 0
        }
    }
}

data class ResolvedPrayerTiming(
    val prayer: PrayerName,
    val azanEpochMillis: Long,
    val jamaatEpochMillis: Long,
    val azanFormatted: String,
    val jamaatFormatted: String,
    val jamaatOffsetMinutes: Int,
    val isCustomOverride: Boolean
)

sealed interface PrayerHeroState {
    data class JamaatCountdown(
        val prayer: PrayerName,
        val azanTimeMillis: Long,
        val jamaatTimeMillis: Long,
        val remainingMillis: Long,
        val progress: Float,
        val jamaatFormatted: String
    ) : PrayerHeroState

    data class NextAzanCountdown(
        val prayer: PrayerName,
        val azanTimeMillis: Long,
        val jamaatTimeMillis: Long,
        val remainingMillis: Long,
        val azanFormatted: String,
        val jamaatFormatted: String
    ) : PrayerHeroState
}

object ScheduleShareCodec {
    private const val PREFIX = "NAMAZ1:"

    fun encode(schedule: MasjidSchedule): String {
        val json = JSONObject().apply {
            put("n", schedule.masjidName)
            put("c", schedule.cityLabel)
            put("a", schedule.announcement)
            put("fa", schedule.fajrAzan)
            put("da", schedule.dhuhrAzan)
            put("aa", schedule.asrAzan)
            put("ma", schedule.maghribAzan)
            put("ia", schedule.ishaAzan)
            put("fo", schedule.fajrJamaatOffsetMin)
            put("do", schedule.dhuhrJamaatOffsetMin)
            put("ao", schedule.asrJamaatOffsetMin)
            put("mo", schedule.maghribJamaatOffsetMin)
            put("io", schedule.ishaJamaatOffsetMin)
            put("fj", schedule.fajrJamaatOverride)
            put("dj", schedule.dhuhrJamaatOverride)
            put("aj", schedule.asrJamaatOverride)
            put("mj", schedule.maghribJamaatOverride)
            put("ij", schedule.ishaJamaatOverride)
        }.toString()
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$PREFIX$encoded"
    }

    fun decode(raw: String): MasjidSchedule? {
        return try {
            val trimmed = raw.trim()
            val payload = if (trimmed.startsWith(PREFIX)) trimmed.removePrefix(PREFIX) else trimmed
            val jsonStr = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
            val obj = JSONObject(jsonStr)
            MasjidSchedule(
                masjidName = obj.optString("n", "Shared Masjid").ifBlank { "Shared Masjid" },
                cityLabel = obj.optString("c", ""),
                announcement = obj.optString("a", ""),
                fajrAzan = obj.optString("fa", ""),
                dhuhrAzan = obj.optString("da", ""),
                asrAzan = obj.optString("aa", ""),
                maghribAzan = obj.optString("ma", ""),
                ishaAzan = obj.optString("ia", ""),
                fajrJamaatOffsetMin = obj.optInt("fo", 15).coerceIn(0, 120),
                dhuhrJamaatOffsetMin = obj.optInt("do", 15).coerceIn(0, 120),
                asrJamaatOffsetMin = obj.optInt("ao", 15).coerceIn(0, 120),
                maghribJamaatOffsetMin = obj.optInt("mo", 5).coerceIn(0, 120),
                ishaJamaatOffsetMin = obj.optInt("io", 15).coerceIn(0, 120),
                fajrJamaatOverride = obj.optString("fj", ""),
                dhuhrJamaatOverride = obj.optString("dj", ""),
                asrJamaatOverride = obj.optString("aj", ""),
                maghribJamaatOverride = obj.optString("mj", ""),
                ishaJamaatOverride = obj.optString("ij", "")
            )
        } catch (_: Exception) {
            null
        }
    }
}
