package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.CycleResult
import app.duofuel.gasoline.domain.model.FuelEntryCalc
import app.duofuel.gasoline.domain.model.FuelType
import app.duofuel.gasoline.domain.model.Metric
import app.duofuel.gasoline.domain.model.MethodId
import app.duofuel.gasoline.domain.model.OdometerEventCalc
import app.duofuel.gasoline.domain.model.Period
import app.duofuel.gasoline.domain.model.PeriodKind
import app.duofuel.gasoline.domain.model.Quality
import app.duofuel.gasoline.domain.model.UnclosedCycle
import app.duofuel.gasoline.domain.model.WarningCode

class FuelCycleCalculator {
    private val entryOrder = compareBy<FuelEntryCalc>(
        { it.occurredAtMs },
        { it.odometerKm },
        { it.createdAtMs },
        { it.id },
    )

    fun cycles(
        entries: List<FuelEntryCalc>,
        odometer: List<OdometerEventCalc>,
        fuel: FuelType,
    ): List<CycleResult> {
        val sorted = entries.filter { it.fuelType == fuel }.sortedWith(entryOrder)
        val fullIndexes = sorted.indices.filter { sorted[it].isFull }
        return (0 until fullIndexes.size - 1).map { pair ->
            close(sorted, fullIndexes[pair], fullIndexes[pair + 1], odometer)
        }
    }

    fun unclosed(entries: List<FuelEntryCalc>, fuel: FuelType): UnclosedCycle? {
        val sorted = entries.filter { it.fuelType == fuel }.sortedWith(entryOrder)
        if (sorted.isEmpty()) return null
        val lastFull = sorted.indexOfLast { it.isFull }
        val tail = if (lastFull < 0) sorted else sorted.drop(lastFull + 1)
        if (tail.isEmpty()) return null
        return UnclosedCycle(
            fuel = fuel,
            openingBoundaryId = if (lastFull >= 0) sorted[lastFull].id else null,
            partialIds = tail.map { it.id },
        )
    }

    private fun close(
        sorted: List<FuelEntryCalc>,
        openIndex: Int,
        closeIndex: Int,
        odometer: List<OdometerEventCalc>,
    ): CycleResult {
        val opening = sorted[openIndex]
        val closing = sorted[closeIndex]
        // OPEN-CYCLE: объём открывающей заправки не входит; всё строго после неё до закрывающей включительно входит.
        val consumed = sorted.subList(openIndex + 1, closeIndex + 1)
        // OPEN-CNG-FACTOR: разные единицы одного топлива не складываются. OPEN-PETROL-FULL: бензин идёт теми же границами.
        val units = consumed.map { it.unit }.distinct()
        val currencies = consumed.map { it.currency }.distinct()
        val moneyByCurrency = consumed.groupBy { it.currency }.mapValues { (_, rows) ->
            rows.sumOf { it.totalCostMinor }
        }
        val broken = DistanceCalculator.scaleBroken(
            opening.sortKey(),
            closing.sortKey(),
            odometer,
            opening.id,
            closing.id,
        )
        val distance = if (broken) null else closing.odometerKm - opening.odometerKm
        val period = Period(
            kind = PeriodKind.CONTROL_CYCLE,
            startInclusiveMs = opening.occurredAtMs,
            endExclusiveMs = closing.occurredAtMs + 1,
            startOdometerKm = opening.odometerKm,
            endOdometerKm = closing.odometerKm,
            labelKey = "cycle",
        )
        val warnings = buildList {
            if (units.size > 1) add(WarningCode.MIXED_UNITS)
            if (currencies.size > 1) add(WarningCode.MIXED_CURRENCY)
            if (broken) add(WarningCode.SCALE_BREAK)
            if (distance != null && distance <= 0) add(WarningCode.DISTANCE_NON_POSITIVE)
        }
        val volumeValue = if (units.size == 1 && consumed.isNotEmpty()) consumed.sumOf { it.quantity } else null
        val volume = if (volumeValue == null) {
            Metric.insufficient(
                methodId = MethodId.FUEL_CYCLE_FULL_TO_FULL.id,
                period = period,
                inputEventIds = consumed.map { it.id },
                warnings = warnings,
            )
        } else {
            metric(
                quality = Quality.FACTUAL,
                methodId = MethodId.FUEL_CYCLE_FULL_TO_FULL.id,
                period = period,
                ids = consumed.map { it.id },
                warnings = warnings.filter { it == WarningCode.MIXED_CURRENCY },
                volume = volumeValue,
                unit = units.single(),
            )
        }
        val rate = if (volumeValue != null && distance != null && distance > 0) {
            val per100 = volumeValue / distance.toDouble() * 100.0
            metric(
                quality = Quality.FACTUAL,
                methodId = MethodId.FUEL_CYCLE_FULL_TO_FULL.id,
                period = period,
                ids = consumed.map { it.id },
                warnings = emptyList(),
                volume = volumeValue,
                unit = units.single(),
                ratePer100 = per100,
                distanceKm = distance,
            )
        } else {
            Metric.insufficient(
                methodId = MethodId.FUEL_CYCLE_FULL_TO_FULL.id,
                period = period,
                inputEventIds = consumed.map { it.id },
                warnings = warnings.filter {
                    it == WarningCode.DISTANCE_NON_POSITIVE ||
                        it == WarningCode.SCALE_BREAK ||
                        it == WarningCode.MIXED_UNITS
                },
            )
        }
        val moneyMinor = if (volumeValue != null && currencies.size == 1) {
            consumed.sumOf { it.totalCostMinor }
        } else {
            null
        }
        val money = if (moneyMinor == null) {
            Metric.insufficient(
                methodId = MethodId.FUEL_COST.id,
                period = period,
                inputEventIds = consumed.map { it.id },
                warnings = warnings.filter {
                    it == WarningCode.MIXED_CURRENCY || it == WarningCode.MIXED_UNITS
                }.ifEmpty { listOf(WarningCode.MISSING_CONSUMED_COST) },
            )
        } else {
            // OPEN-ACTUAL: стоимость потреблённого — сумма чеков заправок, вошедших в V_consumed.
            metric(
                quality = QualityRules.formulaQuality(listOf(Quality.FACTUAL)),
                methodId = MethodId.FUEL_COST.id,
                period = period,
                ids = consumed.map { it.id },
                warnings = emptyList(),
                moneyMinor = moneyMinor,
                currency = currencies.single(),
                distanceKm = distance?.takeIf { it > 0 },
            )
        }
        return CycleResult(
            fuel = opening.fuelType,
            openingId = opening.id,
            closingId = closing.id,
            consumedEntryIds = consumed.map { it.id },
            openingOdometerKm = opening.odometerKm,
            closingOdometerKm = closing.odometerKm,
            startMs = opening.occurredAtMs,
            endMs = closing.occurredAtMs,
            distanceKm = distance?.takeIf { it > 0 },
            volume = volume,
            rate = rate,
            consumedMoney = money,
            moneyByCurrency = moneyByCurrency,
        )
    }
}

internal fun metric(
    quality: Quality,
    methodId: String,
    period: Period,
    ids: List<String>,
    warnings: List<WarningCode>,
    volume: Double? = null,
    unit: String? = null,
    ratePer100: Double? = null,
    moneyMinor: Long? = null,
    currency: String? = null,
    distanceKm: Int? = null,
    scope: app.duofuel.gasoline.domain.model.CostScope? = null,
    scenarioPrice: Boolean = false,
    ratioNumerator: Long? = null,
    ratioDenominator: Long? = null,
    remainingKm: java.math.BigDecimal? = null,
): Metric = Metric(
    quality = quality,
    methodId = methodId,
    formulaVersion = app.duofuel.gasoline.domain.model.FormulaVersion.CURRENT,
    period = period,
    inputEventIds = ids,
    warnings = warnings,
    scope = scope,
    volume = volume,
    volumeUnit = unit,
    ratePer100 = ratePer100,
    moneyMinor = moneyMinor,
    currency = currency,
    distanceKm = distanceKm,
    ratioNumerator = ratioNumerator,
    ratioDenominator = ratioDenominator,
    remainingKm = remainingKm,
    scenarioPrice = scenarioPrice,
)
