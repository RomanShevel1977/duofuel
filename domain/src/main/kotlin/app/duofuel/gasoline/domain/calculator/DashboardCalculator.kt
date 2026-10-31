package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.CalculationInput
import app.duofuel.gasoline.domain.model.CostScope
import app.duofuel.gasoline.domain.model.CycleResult
import app.duofuel.gasoline.domain.model.DashboardReport
import app.duofuel.gasoline.domain.model.FuelConsumptionCard
import app.duofuel.gasoline.domain.model.FuelType
import app.duofuel.gasoline.domain.model.MethodId
import app.duofuel.gasoline.domain.model.Metric
import app.duofuel.gasoline.domain.model.Period
import app.duofuel.gasoline.domain.model.PeriodKind
import app.duofuel.gasoline.domain.model.Quality
import app.duofuel.gasoline.domain.model.RecentEventRef
import app.duofuel.gasoline.domain.model.ScheduleStatus
import app.duofuel.gasoline.domain.model.ServiceDueStatus
import app.duofuel.gasoline.domain.model.WarningCode
import java.time.LocalDate
import java.time.ZoneId

// OPEN-PERIODS: месяц, контрольный цикл и вся история — основные; произвольный интервал и последние N км тоже считаются.
sealed interface PeriodRequest {
    data class CalendarMonth(val year: Int, val month: Int) : PeriodRequest
    data class Custom(val start: LocalDate, val endInclusive: LocalDate) : PeriodRequest
    data class LastNKm(val kilometers: Int) : PeriodRequest
    data object LatestControlCycles : PeriodRequest
    data object AllHistory : PeriodRequest
}

class DashboardCalculator(
    private val cyclesCalc: FuelCycleCalculator = FuelCycleCalculator(),
    private val balanceCalc: FuelBalanceCalculator = FuelBalanceCalculator(),
    private val costCalc: FuelCostCalculator = FuelCostCalculator(),
    private val mixedCalc: MixedFuelReportCalculator = MixedFuelReportCalculator(),
    private val roiCalc: GboRoiCalculator = GboRoiCalculator(),
    private val maintenance: MaintenanceCostCalculator = MaintenanceCostCalculator(),
) {
    /**
     * Дата «сегодня» и момент «сейчас» передаются снаружи.
     * Модуль не читает часы устройства, сеть и базу.
     */
    fun calculate(
        input: CalculationInput,
        request: PeriodRequest,
        zone: ZoneId,
        today: LocalDate,
        nowMs: Long,
    ): DashboardReport {
        val fuels = fuelsOf(input)
        val allCycles = fuels.associateWith { fuel ->
            cyclesCalc.cycles(input.fuelEntries, input.odometerEvents, fuel)
        }
        val period = resolvePeriod(input, request, zone, allCycles)
        val selected = selectCycles(request, period, allCycles)
        val mixed = mixedCalc.report(selected, fuels)
        val cards = fuels.map { fuel -> card(fuel, selected[fuel], period) }
        val cashflow = costCalc.cashflow(input.fuelEntries, period)
        val travelKm = DistanceCalculator.inTimeRange(
            input.odometerEvents,
            period.startInclusiveMs,
            period.endExclusiveMs,
        )
        val cashflowPerKm = costCalc.perKm(
            moneyMinor = cashflow.total.moneyMinor,
            currency = cashflow.total.currency,
            distanceKm = travelKm,
            methodId = MethodId.FUEL_CASHFLOW.id,
            inputQuality = Quality.CALCULATED,
            period = period,
            inputEventIds = cashflow.total.inputEventIds,
            extraWarnings = cashflow.total.warnings,
        )
        val combined = combinedConsumed(mixed, selected, fuels)
        val records = input.serviceRecords.filter { inPeriod(it.occurredAtMs, period) }
        val expenses = input.expenses.filter { inPeriod(it.occurredAtMs, period) }
        val currency = combined.currency
        val gbo = maintenance.gboMinor(records, expenses, currency)
        val selectedExtra = maintenance.selectedOperatingMinor(expenses, currency)
        val allService = maintenance.allServiceMinor(records, currency)
        val allExpense = maintenance.allExpenseMinor(expenses, currency)
        val fuelPlus = sumMoney(combined.money, gbo)
        val operating = sumMoney(fuelPlus, selectedExtra)
        val ownership = sumMoney(sumMoney(combined.money, allService), allExpense)
        val consumedQuality = if (combined.money != null) Quality.FACTUAL else Quality.INSUFFICIENT
        val alignWarnings = mixed.warnings
        val fuelCost = costCalc.perKm(
            moneyMinor = combined.money,
            currency = currency,
            distanceKm = combined.distance,
            methodId = MethodId.FUEL_COST.id,
            inputQuality = consumedQuality,
            period = combined.period ?: period,
            inputEventIds = combined.ids,
            extraWarnings = alignWarnings,
            scope = CostScope.FUEL,
        )
        val fuelPlusGbo = costCalc.perKm(
            moneyMinor = fuelPlus,
            currency = currency,
            distanceKm = combined.distance,
            methodId = MethodId.OPERATING_COST.id,
            inputQuality = consumedQuality,
            period = combined.period ?: period,
            inputEventIds = combined.ids + records.filter { it.isGbo }.map { it.id },
            extraWarnings = alignWarnings,
            scope = CostScope.FUEL_PLUS_GBO,
        )
        val operatingPerKm = costCalc.perKm(
            moneyMinor = operating,
            currency = currency,
            distanceKm = combined.distance,
            methodId = MethodId.OPERATING_COST.id,
            inputQuality = consumedQuality,
            period = combined.period ?: period,
            inputEventIds = fuelPlusGbo.inputEventIds + expenses.filter { it.includeInOperating }.map { it.id },
            extraWarnings = alignWarnings,
            scope = CostScope.OPERATING,
        )
        val ownershipPerKm = costCalc.perKm(
            moneyMinor = ownership,
            currency = currency,
            distanceKm = combined.distance,
            methodId = MethodId.OPERATING_COST.id,
            inputQuality = consumedQuality,
            period = combined.period ?: period,
            inputEventIds = combined.ids + records.map { it.id } + expenses.map { it.id },
            extraWarnings = alignWarnings,
            scope = CostScope.OWNERSHIP,
        )
        val saving = roiCalc.savingFor(
            input = input,
            cycles = selected.values.filterNotNull().filter { cycleInside(it, period) || request is PeriodRequest.LatestControlCycles },
            period = combined.period ?: period,
            entireHistory = false,
        )
        val roi = roiCalc.evaluate(input, allCycles.values.flatten(), null)
        val statuses = maintenance.statuses(
            input.schedules,
            input.serviceRecords,
            input.odometerEvents,
            today,
            zone,
            nowMs,
        )
        val mismatch = fuelCost.distanceKm != null && cashflowPerKm.distanceKm != null &&
            fuelCost.distanceKm != cashflowPerKm.distanceKm
        return DashboardReport(
            period = period,
            cashflow = cashflow,
            cashflowPerKm = cashflowPerKm,
            consumptionByFuel = cards,
            fuelCostPerKm = fuelCost,
            fuelPlusGboPerKm = fuelPlusGbo,
            operatingPerKm = operatingPerKm,
            ownershipPerKm = ownershipPerKm,
            saving = saving,
            roi = roi,
            nearestService = nearest(statuses),
            serviceStatuses = statuses,
            recentEvents = recent(input),
            mixed = mixed,
            periodMismatch = mismatch,
            unclosed = fuels.mapNotNull { cyclesCalc.unclosed(input.fuelEntries, it) },
            closedCycles = allCycles.values.flatten(),
            // OPEN-LEVEL: без подтверждённых остатков баланс не считается и не становится нулём.
            balance = balanceCalc.consumed(
                startLevel = null,
                purchases = 0.0,
                endLevel = null,
                unit = "",
                startConfirmed = false,
                endConfirmed = false,
                period = period,
            ),
        )
    }

    private fun fuelsOf(input: CalculationInput): Set<FuelType> {
        if (input.configuredFuels.isNotEmpty()) return input.configuredFuels
        return input.fuelEntries.map { it.fuelType }.toSet()
    }

    private fun resolvePeriod(
        input: CalculationInput,
        request: PeriodRequest,
        zone: ZoneId,
        allCycles: Map<FuelType, List<CycleResult>>,
    ): Period = when (request) {
        is PeriodRequest.CalendarMonth -> {
            val startDate = LocalDate.of(request.year, request.month, 1)
            val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = startDate.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Period(PeriodKind.CALENDAR_MONTH, start, end, null, null, "month")
        }
        is PeriodRequest.Custom -> {
            val start = request.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = request.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Period(PeriodKind.CUSTOM, start, end, null, null, "custom")
        }
        PeriodRequest.AllHistory -> Period(PeriodKind.ALL_HISTORY, null, null, null, null, "all")
        is PeriodRequest.LastNKm -> lastN(input, request.kilometers)
        PeriodRequest.LatestControlCycles -> {
            val latest = allCycles.mapNotNull { it.value.lastOrNull() }
            if (latest.isEmpty()) {
                Period(PeriodKind.CONTROL_CYCLE, null, null, null, null, "cycle")
            } else {
                Period(
                    kind = PeriodKind.CONTROL_CYCLE,
                    startInclusiveMs = latest.minOf { it.startMs },
                    endExclusiveMs = latest.maxOf { it.endMs } + 1,
                    startOdometerKm = latest.minOf { it.openingOdometerKm },
                    endOdometerKm = latest.maxOf { it.closingOdometerKm },
                    labelKey = "cycle",
                )
            }
        }
    }

    private fun lastN(input: CalculationInput, kilometers: Int): Period {
        val sorted = DistanceCalculator.sorted(input.odometerEvents)
        if (sorted.isEmpty() || kilometers <= 0) {
            return Period(PeriodKind.LAST_N_KM, null, null, null, null, "last_n")
        }
        val last = sorted.last()
        var startEvent = last
        var accumulated = 0
        for (index in sorted.lastIndex downTo 1) {
            val current = sorted[index]
            val previous = sorted[index - 1]
            if (current.kind == app.duofuel.gasoline.domain.model.OdometerKind.RESET) break
            val delta = current.odometerKm - previous.odometerKm
            if (delta < 0) break
            accumulated += delta
            startEvent = previous
            if (accumulated >= kilometers) break
        }
        return Period(
            kind = PeriodKind.LAST_N_KM,
            startInclusiveMs = startEvent.occurredAtMs,
            endExclusiveMs = last.occurredAtMs + 1,
            startOdometerKm = startEvent.odometerKm,
            endOdometerKm = last.odometerKm,
            labelKey = "last_n",
        )
    }

    private fun selectCycles(
        request: PeriodRequest,
        period: Period,
        allCycles: Map<FuelType, List<CycleResult>>,
    ): Map<FuelType, CycleResult?> {
        if (request is PeriodRequest.LatestControlCycles) {
            return allCycles.mapValues { it.value.lastOrNull() }
        }
        return allCycles.mapValues { (_, cycles) ->
            cycles.lastOrNull { cycleInside(it, period) }
        }
    }

    private fun cycleInside(cycle: CycleResult, period: Period): Boolean {
        val start = period.startInclusiveMs
        val end = period.endExclusiveMs
        val timeOk = (start == null || cycle.startMs >= start) && (end == null || cycle.endMs < end)
        val odoStart = period.startOdometerKm
        val odoEnd = period.endOdometerKm
        val odoOk = if (period.kind == PeriodKind.LAST_N_KM && odoStart != null && odoEnd != null) {
            cycle.openingOdometerKm >= odoStart && cycle.closingOdometerKm <= odoEnd
        } else {
            true
        }
        return timeOk && odoOk
    }

    private fun card(fuel: FuelType, cycle: CycleResult?, period: Period): FuelConsumptionCard {
        if (cycle == null || cycle.rate.quality != Quality.FACTUAL) {
            return FuelConsumptionCard(
                fuel = fuel,
                volume = cycle?.volume ?: Metric.insufficient(MethodId.FUEL_CYCLE_FULL_TO_FULL.id, period),
                rate = Metric.insufficient(
                    methodId = MethodId.FUEL_CYCLE_FULL_TO_FULL.id,
                    period = period,
                    inputEventIds = cycle?.consumedEntryIds.orEmpty(),
                    warnings = listOf(WarningCode.UNCLOSED_CYCLE),
                ),
                consumedMoney = cycle?.consumedMoney ?: Metric.insufficient(
                    MethodId.FUEL_COST.id,
                    period,
                    warnings = listOf(WarningCode.MISSING_CONSUMED_COST),
                ),
                cycle = cycle,
            )
        }
        return FuelConsumptionCard(fuel, cycle.volume, cycle.rate, cycle.consumedMoney, cycle)
    }

    private data class Combined(
        val money: Long?,
        val currency: String?,
        val distance: Int?,
        val ids: List<String>,
        val period: Period?,
    )

    private fun combinedConsumed(
        mixed: app.duofuel.gasoline.domain.model.MixedReport,
        selected: Map<FuelType, CycleResult?>,
        required: Set<FuelType>,
    ): Combined {
        if (!mixed.combinedAvailable) {
            return Combined(null, null, null, emptyList(), null)
        }
        val cycles = required.mapNotNull { selected[it] }
        val currencies = cycles.mapNotNull { it.consumedMoney.currency }.distinct()
        if (currencies.size != 1 || cycles.any { it.consumedMoney.moneyMinor == null || it.distanceKm == null }) {
            return Combined(null, currencies.singleOrNull(), cycles.firstOrNull()?.distanceKm, emptyList(), cycles.firstOrNull()?.rate?.period)
        }
        return Combined(
            money = cycles.sumOf { it.consumedMoney.moneyMinor!! },
            currency = currencies.single(),
            distance = cycles.first().distanceKm,
            ids = cycles.flatMap { it.consumedEntryIds },
            period = cycles.first().rate.period,
        )
    }

    private fun sumMoney(base: Long?, extra: Long?): Long? = if (base == null || extra == null) null else base + extra

    private fun inPeriod(at: Long, period: Period): Boolean {
        val start = period.startInclusiveMs
        val end = period.endExclusiveMs
        return (start == null || at >= start) && (end == null || at < end)
    }

    private fun nearest(statuses: List<ScheduleStatus>): ScheduleStatus? {
        val rank = mapOf(
            ServiceDueStatus.OVERDUE to 0,
            ServiceDueStatus.DUE to 1,
            ServiceDueStatus.SOON to 2,
            ServiceDueStatus.PENDING to 3,
        )
        return statuses.filter { it.status != ServiceDueStatus.INSUFFICIENT }
            .minWithOrNull(compareBy({ rank[it.status] ?: 9 }, { it.dueEpochDay ?: Long.MAX_VALUE }))
    }

    private fun recent(input: CalculationInput): List<RecentEventRef> {
        val fuel = input.fuelEntries.map {
            RecentEventRef(it.id, "FUEL", it.occurredAtMs, it.odometerKm, it.fuelType) to
                app.duofuel.gasoline.domain.model.SortKey(it.occurredAtMs, it.odometerKm, it.createdAtMs, it.id)
        }
        val service = input.serviceRecords.map {
            RecentEventRef(it.id, "SERVICE", it.occurredAtMs, it.odometerKm, null) to
                app.duofuel.gasoline.domain.model.SortKey(it.occurredAtMs, it.odometerKm, it.createdAtMs, it.id)
        }
        val expense = input.expenses.map {
            RecentEventRef(it.id, "EXPENSE", it.occurredAtMs, it.odometerKm, null) to
                app.duofuel.gasoline.domain.model.SortKey(it.occurredAtMs, it.odometerKm ?: 0, it.createdAtMs, it.id)
        }
        return (fuel + service + expense).sortedBy { it.second }.takeLast(8).map { it.first }
    }
}
