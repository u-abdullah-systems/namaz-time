package com.example

import com.example.domain.AsrJuristicMethod
import com.example.domain.CalculationMethod
import com.example.domain.MasjidSchedule
import com.example.domain.PrayerHeroState
import com.example.domain.PrayerName
import com.example.domain.PrayerScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PrayerScheduleResolverTest {

    @Test
    fun defaultOffsetsAreFifteenExceptMaghribFive() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).apply {
            set(2026, Calendar.NOVEMBER, 24, 0, 0, 0)
        }
        val timings = PrayerScheduleResolver.resolveForDay(
            dayCalendar = cal,
            latitude = 37.3382,
            longitude = -121.8863,
            method = CalculationMethod.ISNA,
            asrMethod = AsrJuristicMethod.STANDARD,
            masjidSchedule = null
        )

        assertEquals(5, timings.size)
        for (i in 0 until timings.size - 1) {
            assertTrue(timings[i].azanEpochMillis < timings[i + 1].azanEpochMillis)
        }

        val maghrib = timings.first { it.prayer == PrayerName.MAGHRIB }
        val fajr = timings.first { it.prayer == PrayerName.FAJR }
        assertEquals(5, maghrib.jamaatOffsetMinutes)
        assertEquals(15, fajr.jamaatOffsetMinutes)
    }

    @Test
    fun cloudOverrideAndJamaatCountdownStateWork() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).apply {
            set(2026, Calendar.NOVEMBER, 24, 0, 0, 0)
        }
        val schedule = MasjidSchedule(
            masjidName = "Test Masjid",
            fajrAzan = "05:30",
            dhuhrAzan = "",
            maghribAzan = "",
            ishaAzan = "",
            maghribJamaatOffsetMin = 5
        )
        val today = PrayerScheduleResolver.resolveForDay(
            dayCalendar = cal,
            latitude = 37.3382,
            longitude = -121.8863,
            method = CalculationMethod.ISNA,
            asrMethod = AsrJuristicMethod.STANDARD,
            masjidSchedule = schedule
        )
        val tomorrowCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrow = PrayerScheduleResolver.resolveForDay(
            dayCalendar = tomorrowCal,
            latitude = 37.3382,
            longitude = -121.8863,
            method = CalculationMethod.ISNA,
            asrMethod = AsrJuristicMethod.STANDARD,
            masjidSchedule = schedule
        )

        val fajr = today.first { it.prayer == PrayerName.FAJR }
        val zuhr = today.first { it.prayer == PrayerName.DHUHR }
        assertEquals("Zuhr", zuhr.prayer.displayName)
        assertTrue(fajr.isCustomOverride)
        assertFalse(zuhr.isCustomOverride)

        // Simulate 3 minutes after Fajr Azan -> JamaatCountdown must be active with 12m remaining
        val nowDuringJamaatWait = fajr.azanEpochMillis + 3 * 60_000L
        val hero = PrayerScheduleResolver.computeHeroState(nowDuringJamaatWait, today, tomorrow)
        assertTrue(hero is PrayerHeroState.JamaatCountdown)
        val countdown = hero as PrayerHeroState.JamaatCountdown
        assertEquals(PrayerName.FAJR, countdown.prayer)
        assertEquals(12 * 60_000L, countdown.remainingMillis)
    }

    @Test
    fun decimalVersionComparisonWorks() {
        val v11 = com.example.domain.AppUpdateInfo(versionName = "1.1", apkUrl = "https://example.com/app.apk")
        val v110 = com.example.domain.AppUpdateInfo(versionName = "1.10", apkUrl = "https://example.com/app.apk")
        val v10 = com.example.domain.AppUpdateInfo(versionName = "1.0", apkUrl = "https://example.com/app.apk")

        assertTrue(v11.isNewerThan(installedCode = 1, installedName = "1.0"))
        assertFalse(v11.isNewerThan(installedCode = 2, installedName = "1.1"))
        assertFalse(v10.isNewerThan(installedCode = 1, installedName = "1.1"))
        assertTrue(v110.isNewerThan(installedCode = 9, installedName = "1.9"))
    }
}
