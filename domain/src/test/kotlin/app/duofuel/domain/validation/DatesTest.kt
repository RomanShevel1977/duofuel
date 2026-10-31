package app.duofuel.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DatesTest {
    private val zone = ZoneId.of("Europe/Kyiv")

    @Test
    fun newRecord_todayKeepsNow_otherDayStartsAtMidnight() {
        val now = LocalDate.of(2026, 10, 31).atTime(15, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals(now, Dates.resolveOccurredAt("2026-10-31", zone, null, now))
        val yesterday = LocalDate.of(2026, 10, 30).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(yesterday, Dates.resolveOccurredAt("2026-10-30", zone, null, now))
    }

    @Test
    fun edit_sameDayKeepsInstant_changedDayMovesToStart() {
        val now = LocalDate.of(2026, 10, 31).atTime(15, 5).atZone(zone).toInstant().toEpochMilli()
        val stored = LocalDate.of(2026, 10, 30).atTime(8, 15).atZone(zone).toInstant().toEpochMilli()
        assertEquals(stored, Dates.resolveOccurredAt("2026-10-30", zone, stored, now))
        val moved = LocalDate.of(2026, 10, 29).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(moved, Dates.resolveOccurredAt("2026-10-29", zone, stored, now))
    }

    @Test
    fun clock_setsMinute_andSameMinuteKeepsSeconds() {
        val stored = LocalDate.of(2026, 10, 30).atTime(8, 15, 42).atZone(zone).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 10, 31).atTime(15, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals(stored, Dates.resolveOccurredAt("2026-10-30", zone, stored, now, "08:15"))
        val moved = LocalDate.of(2026, 10, 30).atTime(9, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals(moved, Dates.resolveOccurredAt("2026-10-30", zone, stored, now, "9:05"))
        assertNull(Dates.resolveOccurredAt("2026-10-30", zone, stored, now, "25:00"))
        assertEquals(false, Dates.clockAccepted("25:00"))
        assertEquals("08:15", Dates.formatTime(stored, zone))
    }

    @Test
    fun blankOrWrongShape_isRejected() {
        val now = 0L
        assertNull(Dates.resolveOccurredAt("", zone, null, now))
        assertNull(Dates.resolveOccurredAt("31.10.2026", zone, null, now))
        assertNull(Dates.parseEpochDay(" "))
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), Dates.parseEpochDay("2026-10-31"))
    }
}
