package app.duofuel.domain.calculator

import app.duofuel.domain.model.CostScope
import app.duofuel.domain.model.FuelEntryCalc
import app.duofuel.domain.model.Metric
import app.duofuel.domain.model.MethodId
import app.duofuel.domain.model.MoneyReport
import app.duofuel.domain.model.Period
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.WarningCode

class FuelCostCalculator {
    fun cashflow(entries: List<FuelEntryCalc>, period: Period): MoneyReport {
        val inPeriod = entries.filter { entry ->
            val start = period.startInclusiveMs
            val end = period.endExclusiveMs
            (start == null || entry.occurredAtMs >= start) &&
                (end == null || entry.occurredAtMs < end)
        }
        val byCurrency = inPeriod.groupBy { it.currency }.mapValues { (_, rows) ->
            rows.sumOf { it.totalCostMinor }
        }
        if (inPeriod.isEmpty()) {
            return MoneyReport(
                total = Metric.insufficient(
                    methodId = MethodId.FUEL_CASHFLOW.id,
                    period = period,
                    warnings = listOf(WarningCode.NO_EVENTS),
                ),
                byCurrency = emptyMap(),
            )
        }
        val total = if (byCurrency.size == 1) {
            metric(
                quality = Quality.CALCULATED,
                methodId = MethodId.FUEL_CASHFLOW.id,
                period = period,
                ids = inPeriod.map { it.id },
                warnings = emptyList(),
                moneyMinor = byCurrency.values.single(),
                currency = byCurrency.keys.single(),
            )
        } else {
            // OPEN-CURRENCY: разные валюты не склеиваются выдуманным курсом.
            Metric.insufficient(
                methodId = MethodId.FUEL_CASHFLOW.id,
                period = period,
                inputEventIds = inPeriod.map { it.id },
                warnings = listOf(WarningCode.MIXED_CURRENCY),
            )
        }
        return MoneyReport(total = total, byCurrency = byCurrency)
    }

    fun perKm(
        moneyMinor: Long?,
        currency: String?,
        distanceKm: Int?,
        methodId: String,
        inputQuality: Quality,
        period: Period,
        inputEventIds: List<String>,
        extraWarnings: List<WarningCode> = emptyList(),
        scope: CostScope? = null,
    ): Metric {
        if (moneyMinor == null || currency == null) {
            return Metric.insufficient(
                methodId = methodId,
                period = period,
                inputEventIds = inputEventIds,
                warnings = extraWarnings + WarningCode.MISSING_CONSUMED_COST,
                scope = scope,
            )
        }
        if (distanceKm == null || distanceKm <= 0) {
            return Metric.insufficient(
                methodId = methodId,
                period = period,
                inputEventIds = inputEventIds,
                warnings = extraWarnings + WarningCode.DISTANCE_NON_POSITIVE,
                scope = scope,
            )
        }
        val quality = QualityRules.formulaQuality(listOf(inputQuality))
        if (quality == Quality.INSUFFICIENT) {
            return Metric.insufficient(
                methodId = methodId,
                period = period,
                inputEventIds = inputEventIds,
                warnings = extraWarnings,
                scope = scope,
            )
        }
        return metric(
            quality = quality,
            methodId = methodId,
            period = period,
            ids = inputEventIds,
            warnings = extraWarnings,
            moneyMinor = moneyMinor,
            currency = currency,
            distanceKm = distanceKm,
            scope = scope,
        )
    }
}
