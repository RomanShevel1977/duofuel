package app.duofuel.domain.validation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// FR-FUEL-06 и FR-SRV-01: момент записи — календарный день, который ввёл пользователь.
object Dates {
    fun parseStartOfDay(text: String, zone: ZoneId): Long? = runCatching {
        LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    fun formatDay(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString()

    fun parseEpochDay(text: String): Long? = runCatching {
        LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE).toEpochDay()
    }.getOrNull()

    fun formatEpochDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    /**
     * Неизменённый день сохраняет исходный момент, включая время.
     * Новый день — начало этих местных суток.
     * Новая запись за сегодня оставляет [nowMs], а не полночь.
     */
    fun resolveOccurredAt(text: String, zone: ZoneId, existingMs: Long?, nowMs: Long): Long? {
        val typed = runCatching {
            LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull() ?: return null
        if (existingMs != null) {
            val storedDay = Instant.ofEpochMilli(existingMs).atZone(zone).toLocalDate()
            if (storedDay == typed) return existingMs
        } else if (Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() == typed) {
            return nowMs
        }
        return typed.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
