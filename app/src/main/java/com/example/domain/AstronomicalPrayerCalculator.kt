package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class DailySolarTimes(
    val fajrHours: Double,
    val sunriseHours: Double,
    val dhuhrHours: Double,
    val asrHours: Double,
    val maghribHours: Double,
    val ishaHours: Double
)

object AstronomicalPrayerCalculator {

    private fun degToRad(d: Double): Double = d * Math.PI / 180.0
    private fun radToDeg(r: Double): Double = r * 180.0 / Math.PI

    private fun fixAngle(a: Double): Double {
        var res = a % 360.0
        if (res < 0) res += 360.0
        return res
    }

    private fun fixHour(a: Double): Double {
        var res = a % 24.0
        if (res < 0) res += 24.0
        return res
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(degToRad(g)) + 0.020 * sin(degToRad(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = radToDeg(atan2(cos(degToRad(e)) * sin(degToRad(l)), cos(degToRad(l)))) / 15.0
        val decl = radToDeg(asin(sin(degToRad(e)) * sin(degToRad(l))))
        val eqt = q / 15.0 - fixHour(ra)
        return decl to eqt
    }

    private fun midDay(jd: Double, longitude: Double, tzHours: Double): Double {
        val (_, eqt) = sunPosition(jd)
        return fixHour(12.0 - eqt + tzHours - longitude / 15.0)
    }

    private fun sunAngleTime(
        jd: Double,
        angle: Double,
        latitude: Double,
        longitude: Double,
        tzHours: Double,
        ccw: Boolean
    ): Double {
        val (decl, _) = sunPosition(jd)
        val noon = midDay(jd, longitude, tzHours)
        val num = -sin(degToRad(angle)) - sin(degToRad(decl)) * sin(degToRad(latitude))
        val den = cos(degToRad(decl)) * cos(degToRad(latitude))
        val ratio = (num / den).coerceIn(-0.9999, 0.9999)
        val t = radToDeg(acos(ratio)) / 15.0
        return noon + if (ccw) -t else t
    }

    private fun asrTime(
        jd: Double,
        factor: Int,
        latitude: Double,
        longitude: Double,
        tzHours: Double
    ): Double {
        val (decl, _) = sunPosition(jd)
        val angle = -radToDeg(atan(1.0 / (factor + tan(degToRad(kotlin.math.abs(latitude - decl))))))
        return sunAngleTime(jd, angle, latitude, longitude, tzHours, ccw = false)
    }

    fun calculateForDate(
        calendar: Calendar,
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        asrMethod: AsrJuristicMethod
    ): DailySolarTimes {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val tzHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0
        val jd = julianDay(year, month, day) - longitude / (15.0 * 24.0)

        val fajr = sunAngleTime(jd + 5.0 / 24.0, method.fajrAngle, latitude, longitude, tzHours, ccw = true)
        val sunrise = sunAngleTime(jd + 6.0 / 24.0, 0.833, latitude, longitude, tzHours, ccw = true)
        val dhuhr = midDay(jd + 12.0 / 24.0, longitude, tzHours) + (2.0 / 60.0) // +2 min safety margin after zenith
        val asr = asrTime(jd + 13.0 / 24.0, asrMethod.shadowFactor, latitude, longitude, tzHours)
        val maghrib = sunAngleTime(jd + 18.0 / 24.0, 0.833, latitude, longitude, tzHours, ccw = false)
        val isha = if (method.ishaFixedMinutesAfterMaghrib != null) {
            maghrib + method.ishaFixedMinutesAfterMaghrib / 60.0
        } else {
            sunAngleTime(jd + 18.0 / 24.0, method.ishaAngle, latitude, longitude, tzHours, ccw = false)
        }

        return DailySolarTimes(
            fajrHours = fajr,
            sunriseHours = sunrise,
            dhuhrHours = dhuhr,
            asrHours = asr,
            maghribHours = maghrib,
            ishaHours = isha
        )
    }
}

object PrayerScheduleResolver {
    private val displayTimeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    fun resolveForDay(
        dayCalendar: Calendar,
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        asrMethod: AsrJuristicMethod,
        masjidSchedule: MasjidSchedule?
    ): List<ResolvedPrayerTiming> {
        val baseDay = (dayCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val solar = AstronomicalPrayerCalculator.calculateForDate(
            calendar = baseDay,
            latitude = latitude,
            longitude = longitude,
            method = method,
            asrMethod = asrMethod
        )

        return PrayerName.entries.map { prayer ->
            val autoHours = when (prayer) {
                PrayerName.FAJR -> solar.fajrHours
                PrayerName.DHUHR -> solar.dhuhrHours
                PrayerName.ASR -> solar.asrHours
                PrayerName.MAGHRIB -> solar.maghribHours
                PrayerName.ISHA -> solar.ishaHours
            }

            val overrideStr = masjidSchedule?.azanOverrideFor(prayer)?.trim().orEmpty()
            val parsedOverride = parseHm(overrideStr)
            val isCloudOverride = parsedOverride != null

            val azanMillis = if (parsedOverride != null) {
                calendarWithTime(baseDay, parsedOverride.first, parsedOverride.second).timeInMillis
            } else {
                calendarWithDecimalHours(baseDay, autoHours).timeInMillis
            }

            val jamaatOverrideStr = masjidSchedule?.jamaatOverrideFor(prayer)?.trim().orEmpty()
            val parsedJamaat = parseHm(jamaatOverrideStr)
            val offsetMinutes = masjidSchedule?.jamaatOffsetFor(prayer) ?: prayer.defaultJamaatOffsetMin

            val jamaatMillis = if (parsedJamaat != null) {
                val explicit = calendarWithTime(baseDay, parsedJamaat.first, parsedJamaat.second).timeInMillis
                if (explicit >= azanMillis) explicit else azanMillis + offsetMinutes * 60_000L
            } else {
                azanMillis + offsetMinutes * 60_000L
            }

            val computedOffset = ((jamaatMillis - azanMillis) / 60_000L).toInt().coerceAtLeast(0)
            val fmt = displayTimeFormatter.get()!!

            ResolvedPrayerTiming(
                prayer = prayer,
                azanEpochMillis = azanMillis,
                jamaatEpochMillis = jamaatMillis,
                azanFormatted = fmt.format(Date(azanMillis)),
                jamaatFormatted = fmt.format(Date(jamaatMillis)),
                jamaatOffsetMinutes = computedOffset,
                isCustomOverride = isCloudOverride
            )
        }
    }

    fun computeHeroState(
        nowMillis: Long,
        todayTimings: List<ResolvedPrayerTiming>,
        tomorrowTimings: List<ResolvedPrayerTiming>
    ): PrayerHeroState? {
        val sortedToday = todayTimings.sortedBy { it.azanEpochMillis }
        val sortedAll = (todayTimings + tomorrowTimings).sortedBy { it.azanEpochMillis }

        // 1. Check if Azan has started and we are currently counting down to Jamaat!
        val activeJamaat = sortedToday.firstOrNull {
            nowMillis >= it.azanEpochMillis && nowMillis < it.jamaatEpochMillis
        }
        if (activeJamaat != null) {
            val total = (activeJamaat.jamaatEpochMillis - activeJamaat.azanEpochMillis).coerceAtLeast(1L)
            val remaining = (activeJamaat.jamaatEpochMillis - nowMillis).coerceAtLeast(0L)
            val elapsed = (total - remaining).coerceIn(0L, total)
            return PrayerHeroState.JamaatCountdown(
                prayer = activeJamaat.prayer,
                azanTimeMillis = activeJamaat.azanEpochMillis,
                jamaatTimeMillis = activeJamaat.jamaatEpochMillis,
                remainingMillis = remaining,
                progress = elapsed.toFloat() / total.toFloat(),
                jamaatFormatted = activeJamaat.jamaatFormatted
            )
        }

        // 2. Otherwise find the next upcoming Azan today or tomorrow in chronological order
        val nextPrayer = sortedAll.firstOrNull { it.azanEpochMillis > nowMillis }
            ?: return null

        return PrayerHeroState.NextAzanCountdown(
            prayer = nextPrayer.prayer,
            azanTimeMillis = nextPrayer.azanEpochMillis,
            jamaatTimeMillis = nextPrayer.jamaatEpochMillis,
            remainingMillis = (nextPrayer.azanEpochMillis - nowMillis).coerceAtLeast(0L),
            azanFormatted = nextPrayer.azanFormatted,
            jamaatFormatted = nextPrayer.jamaatFormatted
        )
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun parseHm(hm: String): Pair<Int, Int>? {
        if (hm.isBlank()) return null
        val match = Regex("^([01]?[0-9]|2[0-3]):([0-5][0-9])$").matchEntire(hm.trim()) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        return h to m
    }

    private fun calendarWithTime(baseDay: Calendar, hour: Int, minute: Int): Calendar =
        (baseDay.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun calendarWithDecimalHours(baseDay: Calendar, decimalHours: Double): Calendar {
        val totalMinutes = (decimalHours * 60.0).roundToInt()
        val hour = (totalMinutes / 60).coerceIn(0, 23)
        val minute = (totalMinutes % 60).coerceIn(0, 59)
        return calendarWithTime(baseDay, hour, minute)
    }
}

data class FallbackGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

object TimezoneFallbackCoordinates {
    fun forDeviceTimezone(): FallbackGeoPoint {
        val tzId = TimeZone.getDefault().id
        return when {
            tzId.contains("Los_Angeles") || tzId.contains("Vancouver") ->
                FallbackGeoPoint(37.3382, -121.8863, "Pacific Region (Est.)")
            tzId.contains("New_York") || tzId.contains("Toronto") ->
                FallbackGeoPoint(40.7128, -74.0060, "Eastern Region (Est.)")
            tzId.contains("Chicago") ->
                FallbackGeoPoint(41.8781, -87.6298, "Central Region (Est.)")
            tzId.contains("London") ->
                FallbackGeoPoint(51.5074, -0.1278, "London (Est.)")
            tzId.contains("Dubai") ->
                FallbackGeoPoint(25.2048, 55.2708, "Dubai (Est.)")
            tzId.contains("Riyadh") ->
                FallbackGeoPoint(24.7136, 46.6753, "Riyadh (Est.)")
            tzId.contains("Karachi") ->
                FallbackGeoPoint(24.8607, 67.0011, "Karachi (Est.)")
            tzId.contains("Kolkata") || tzId.contains("Calcutta") ->
                FallbackGeoPoint(28.6139, 77.2090, "New Delhi (Est.)")
            tzId.contains("Dhaka") ->
                FallbackGeoPoint(23.8103, 90.4125, "Dhaka (Est.)")
            tzId.contains("Kuala_Lumpur") || tzId.contains("Singapore") ->
                FallbackGeoPoint(3.1390, 101.6869, "Kuala Lumpur (Est.)")
            tzId.contains("Istanbul") ->
                FallbackGeoPoint(41.0082, 28.9784, "Istanbul (Est.)")
            else -> {
                val offsetHours = TimeZone.getDefault().rawOffset / 3_600_000.0
                FallbackGeoPoint(25.0, (offsetHours * 15.0).coerceIn(-180.0, 180.0), "Local Timezone (Est.)")
            }
        }
    }
}
