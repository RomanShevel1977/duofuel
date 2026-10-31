package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.ExpenseCalc
import app.duofuel.gasoline.domain.model.MileageForecast
import app.duofuel.gasoline.domain.model.OdometerEventCalc
import app.duofuel.gasoline.domain.model.Quality
import app.duofuel.gasoline.domain.model.ScheduleStatus
import app.duofuel.gasoline.domain.model.ServiceDueStatus
import app.duofuel.gasoline.domain.model.ServiceRecordCalc
import app.duofuel.gasoline.domain.model.ServiceScheduleCalc
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MaintenanceCostCalculator {
    // OPEN-EXPENSE: прочие расходы не входят, пока признак выключен. Обычное ТО не входит в ГБО.
    fun gboMinor(records: List<ServiceRecordCalc>, expenses: List<ExpenseCalc>, currency: String?): Long? {
        if (currency == null) return null
        val service = records.filter { it.isGbo }
        val extra = expenses.filter { it.includeInGbo }
        val rows = service.map { it.currency to it.costMinor } + extra.map { it.currency to it.amountMinor }
        if (rows.isEmpty()) return 0L
        if (rows.any { it.first != currency }) return null
        return rows.sumOf { it.second }
    }

    fun selectedOperatingMinor(expenses: List<ExpenseCalc>, currency: String?): Long? {
        if (currency == null) return null
        val rows = expenses.filter { it.includeInOperating }
        if (rows.isEmpty()) return 0L
        if (rows.any { it.currency != currency }) return null
        return rows.sumOf { it.amountMinor }
    }

    fun allServiceMinor(records: List<ServiceRecordCalc>, currency: String?): Long? {
        if (currency == null) return null
        if (records.isEmpty()) return 0L
        if (records.any { it.currency != currency }) return null
        return records.sumOf { it.costMinor }
    }

    fun allExpenseMinor(expenses: List<ExpenseCalc>, currency: String?): Long? {
        if (currency == null) return null
        if (expenses.isEmpty()) return 0L
        if (expenses.any { it.currency != currency }) return null
        return expenses.sumOf { it.amountMinor }
    }

    fun statuses(
        schedules: List<ServiceScheduleCalc>,
        records: List<ServiceRecordCalc>,
        odometer: List<OdometerEventCalc>,
        today: LocalDate,
        zone: ZoneId,
        nowMs: Long,
    ): List<ScheduleStatus> {
        val currentOdo = DistanceCalculator.sorted(odometer).lastOrNull()?.odometerKm
        return schedules.map { schedule ->
            statusOf(schedule, records, odometer, today, zone, nowMs, currentOdo)
        }
    }

    fun ambiguousSchedule(schedules: List<ServiceScheduleCalc>, serviceType: String, chosenId: String?): Boolean {
        val matches = schedules.filter { it.serviceType == serviceType }
        return matches.size > 1 && chosenId == null
    }

    private fun statusOf(
        schedule: ServiceScheduleCalc,
        records: List<ServiceRecordCalc>,
        odometer: List<OdometerEventCalc>,
        today: LocalDate,
        zone: ZoneId,
        nowMs: Long,
        currentOdo: Int?,
    ): ScheduleStatus {
        val linked = records
            .filter { it.scheduleId == schedule.id }
            .sortedWith(compareBy({ it.occurredAtMs }, { it.odometerKm }, { it.createdAtMs }, { it.id }))
        val last = linked.lastOrNull()
        val dueOdo: Int?
        val dueDay: Long?
        if (last != null) {
            dueOdo = schedule.intervalKm?.let { last.odometerKm + it }
            dueDay = schedule.intervalDays?.let { day ->
                val lastDate = Instant.ofEpochMilli(last.occurredAtMs).atZone(zone).toLocalDate()
                lastDate.plusDays(day.toLong()).toEpochDay()
            }
        } else if (schedule.dueOdometerKm != null || schedule.dueDateEpochDay != null) {
            dueOdo = schedule.dueOdometerKm
            dueDay = schedule.dueDateEpochDay
        } else {
            // Срок не выдумывается от даты установки приложения.
            dueOdo = null
            dueDay = null
        }
        val dueDate = dueDay?.let(LocalDate::ofEpochDay)
        val status = when {
            dueOdo == null && dueDate == null -> ServiceDueStatus.INSUFFICIENT
            isOverdue(currentOdo, dueOdo, today, dueDate) -> ServiceDueStatus.OVERDUE
            isDue(currentOdo, dueOdo, today, dueDate) -> ServiceDueStatus.DUE
            isSoon(currentOdo, dueOdo, today, dueDate, schedule.leadKm, schedule.leadDays) -> ServiceDueStatus.SOON
            else -> ServiceDueStatus.PENDING
        }
        return ScheduleStatus(
            scheduleId = schedule.id,
            serviceType = schedule.serviceType,
            status = status,
            dueOdometerKm = dueOdo,
            dueEpochDay = dueDay,
            snoozeUntilEpochDay = schedule.snoozeUntilEpochDay,
            forecast30 = forecast(odometer, 30, currentOdo, dueOdo, today, zone, nowMs),
            forecast90 = forecast(odometer, 90, currentOdo, dueOdo, today, zone, nowMs),
        )
    }

    private fun isOverdue(current: Int?, dueOdo: Int?, today: LocalDate, due: LocalDate?): Boolean {
        val byOdo = current != null && dueOdo != null && current > dueOdo
        val byDate = due != null && today.isAfter(due)
        return byOdo || byDate
    }

    private fun isDue(current: Int?, dueOdo: Int?, today: LocalDate, due: LocalDate?): Boolean {
        val byOdo = current != null && dueOdo != null && current == dueOdo
        val byDate = due != null && today == due
        return byOdo || byDate
    }

    // OPEN-SOON: «скоро» только если пользователь задал упреждение. Порог сам не включается.
    private fun isSoon(
        current: Int?,
        dueOdo: Int?,
        today: LocalDate,
        due: LocalDate?,
        leadKm: Int?,
        leadDays: Int?,
    ): Boolean {
        val byOdo = current != null && dueOdo != null && leadKm != null &&
            current < dueOdo && dueOdo - current <= leadKm
        val byDate = due != null && leadDays != null && today.isBefore(due) &&
            java.time.temporal.ChronoUnit.DAYS.between(today, due) <= leadDays
        return byOdo || byDate
    }

    private fun forecast(
        odometer: List<OdometerEventCalc>,
        windowDays: Int,
        currentOdo: Int?,
        dueOdo: Int?,
        today: LocalDate,
        zone: ZoneId,
        nowMs: Long,
    ): MileageForecast {
        val start = today.minusDays(windowDays.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val distance = DistanceCalculator.inTimeRange(odometer, start, nowMs + 1)
        if (distance == null || distance <= 0) {
            return MileageForecast(windowDays, distance, null, null, Quality.INSUFFICIENT)
        }
        val average = BigDecimal.valueOf(distance.toLong())
            .divide(BigDecimal.valueOf(windowDays.toLong()), 4, RoundingMode.HALF_UP)
        val days = if (currentOdo != null && dueOdo != null && dueOdo > currentOdo && average.signum() > 0) {
            BigDecimal.valueOf((dueOdo - currentOdo).toLong())
                .divide(average, 4, RoundingMode.HALF_UP)
        } else {
            null
        }
        return MileageForecast(windowDays, distance, average, days, Quality.CALCULATED)
    }
}
