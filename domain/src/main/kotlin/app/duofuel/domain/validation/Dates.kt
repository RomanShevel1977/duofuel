package app.duofuel.domain.validation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// FR-FUEL-02: момент записи — календарный день и, если пользователь ввёл, часы и минуты.
object Dates {
    fun parseStartOfDay(text: String, zone: ZoneId): Long? = runCatching {
        LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    fun formatDay(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString()

    fun formatTime(epochMs: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    fun parseClock(text: String): LocalTime? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    fun clockAccepted(text: String): Boolean = text.isBlank() || parseClock(text) != null

    fun parseEpochDay(text: String): Long? = runCatching {
        LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE).toEpochDay()
    }.getOrNull()

    fun formatEpochDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    /**
     * Пустое время: неизменённый день сохраняет исходный момент, новый день — начало суток,
     * новая запись за сегодня оставляет [nowMs].
     * Введённые часы и минуты ставят этот местный момент. Та же минута уже сохранённой записи
     * не обнуляет секунды.
     */
    fun resolveOccurredAt(
        text: String,
        zone: ZoneId,
        existingMs: Long?,
        nowMs: Long,
        timeText: String = "",
    ): Long? {
        val typed = runCatching {
            LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull() ?: return null
        val clock = if (timeText.isBlank()) null else parseClock(timeText) ?: return null
        if (clock != null) {
            if (existingMs != null) {
                val stored = Instant.ofEpochMilli(existingMs).atZone(zone)
                if (stored.toLocalDate() == typed && stored.hour == clock.hour && stored.minute == clock.minute) {
                    return existingMs
                }
            }
            return typed.atTime(clock).atZone(zone).toInstant().toEpochMilli()
        }
        if (existingMs != null) {
            val storedDay = Instant.ofEpochMilli(existingMs).atZone(zone).toLocalDate()
            if (storedDay == typed) return existingMs
        } else if (Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() == typed) {
            return nowMs
        }
        return typed.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
