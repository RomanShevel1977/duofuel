package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.CalculationInput
import app.duofuel.gasoline.domain.model.CycleResult
import app.duofuel.gasoline.domain.model.Metric
import app.duofuel.gasoline.domain.model.MethodId
import app.duofuel.gasoline.domain.model.Period
import app.duofuel.gasoline.domain.model.PeriodKind
import app.duofuel.gasoline.domain.model.Quality
import app.duofuel.gasoline.domain.model.RoiReport
import app.duofuel.gasoline.domain.model.RoiWindow
import app.duofuel.gasoline.domain.model.SavingReport
import app.duofuel.gasoline.domain.model.WarningCode
import java.math.BigDecimal
import java.math.RoundingMode

class GboRoiCalculator(
    private val maintenance: MaintenanceCostCalculator = MaintenanceCostCalculator(),
) {
    fun manualMlPerStart(mlPerStart: Double?, starts: Int?, period: Period): Metric {
        // OPEN-STARTS: режим остаётся в модели, в приёмку MVP не входит. Запуски не детектируются.
        if (mlPerStart == null || starts == null || mlPerStart <= 0.0 || starts <= 0) {
            return Metric.insufficient(
                methodId = MethodId.MANUAL_ML_PER_START.id,
                period = period,
            )
        }
        val liters = mlPerStart * starts.toDouble() / 1000.0
        return metric(
            quality = Quality.ESTIMATED,
            methodId = MethodId.MANUAL_ML_PER_START.id,
            period = period,
            ids = emptyList(),
            warnings = emptyList(),
            volume = liters,
            unit = "L",
        )
    }

    fun evaluate(
        input: CalculationInput,
        cycles: List<CycleResult>,
        zoneStartMs: Long?,
    ): RoiReport {
        val lastEventMs = (
            input.fuelEntries.map { it.occurredAtMs } +
                input.odometerEvents.map { it.occurredAtMs } +
                input.serviceRecords.map { it.occurredAtMs }
            ).maxOrNull()
        // OPEN-ROI-WINDOW: с даты установки; без даты — вся история, без подмены сегодняшним днём.
        val fromInstall = input.installedAtMs != null
        val windowStart = input.installedAtMs ?: zoneStartMs
        val period = Period(
            kind = PeriodKind.ALL_HISTORY,
            startInclusiveMs = windowStart,
            endExclusiveMs = lastEventMs?.plus(1),
            startOdometerKm = null,
            endOdometerKm = DistanceCalculator.sorted(input.odometerEvents).lastOrNull()?.odometerKm,
            labelKey = if (fromInstall) "roi_install" else "roi_all",
        )
        val windowCycles = cycles.filter { cycle ->
            val start = windowStart
            (start == null || cycle.startMs >= start) &&
                (lastEventMs == null || cycle.endMs <= lastEventMs)
        }
        val saving = savingFor(input, windowCycles, period, entireHistory = !fromInstall)
        // OPEN-INVEST: вся инвестиция — одна стоимость установки FR-VEH-03.
        val investment = input.installCostMinor
        val investmentCurrency = input.installCostCurrency
        val net = saving.net.moneyMinor
        val distance = saving.net.distanceKm
        val currency = saving.net.currency ?: saving.actual.currency
        val currencyMatches = investmentCurrency == null || currency == null || investmentCurrency == currency
        val doesNotPayOff = net != null && distance != null && distance > 0 && net <= 0L
        val unreimbursedPreview = if (investment != null && net != null && currencyMatches) {
            if (net <= 0L) investment else maxOf(0L, investment - net)
        } else {
            null
        }
        val forecastSuppressed = doesNotPayOff || net == null || distance == null || distance <= 0 ||
            unreimbursedPreview == null || unreimbursedPreview == 0L
        val progress = progressMetric(net, investment, currencyMatches, period, saving.net.inputEventIds)
        val unreimbursed = unreimbursedPreview
        val remaining = remainingMetric(
            unreimbursed = unreimbursed,
            net = net,
            distance = distance,
            doesNotPayOff = doesNotPayOff,
            period = period,
            ids = saving.net.inputEventIds,
        )
        return RoiReport(
            window = if (fromInstall) RoiWindow.FROM_INSTALL else RoiWindow.ENTIRE_HISTORY,
            period = period,
            saving = saving,
            investmentMinor = investment,
            investmentCurrency = investmentCurrency,
            progress = progress,
            unreimbursedMinor = unreimbursed,
            remainingKm = remaining,
            forecastSuppressed = forecastSuppressed,
            buildsNewForecast = !forecastSuppressed,
            doesNotPayOff = doesNotPayOff,
            // OPEN-ROI-DATE: дату не показываем, даже при положительной экономии.
            showDate = false,
        )
    }

    fun savingFor(
        input: CalculationInput,
        cycles: List<CycleResult>,
        period: Period,
        entireHistory: Boolean,
    ): SavingReport {
        val usable = cycles.filter { it.consumedMoney.moneyMinor != null && it.distanceKm != null && it.distanceKm > 0 }
        val currencies = usable.mapNotNull { it.consumedMoney.currency }.distinct()
        val currency = currencies.singleOrNull()
        val consumed = if (usable.isNotEmpty() && currency != null) usable.sumOf { it.consumedMoney.moneyMinor!! } else null
        val covered = if (usable.isNotEmpty() && currency != null) usable.sumOf { it.distanceKm!! } else null
        val ids = usable.flatMap { it.consumedEntryIds }.distinct()
        val gbo = if (currency != null) {
            val records = input.serviceRecords.filter { inWindow(it.occurredAtMs, period) }
            val expenses = input.expenses.filter { inWindow(it.occurredAtMs, period) }
            maintenance.gboMinor(records, expenses, currency)
        } else {
            null
        }
        val actualMinor = if (consumed != null && gbo != null) consumed + gbo else null
        val actualQualityInputs = buildList {
            if (usable.isEmpty() || currency == null || gbo == null) add(Quality.INSUFFICIENT) else add(Quality.FACTUAL)
        }
        val warnings = buildList {
            if (entireHistory) add(WarningCode.ROI_ENTIRE_HISTORY)
            if (currencies.size > 1) add(WarningCode.MIXED_CURRENCY)
            if (consumed == null) add(WarningCode.MISSING_CONSUMED_COST)
            val windowDistance = DistanceCalculator.inTimeRange(
                input.odometerEvents,
                period.startInclusiveMs,
                period.endExclusiveMs,
            )
            if (covered != null && windowDistance != null && windowDistance > covered) add(WarningCode.PARTIAL_COVERAGE)
        }
        val actual = if (actualMinor == null || currency == null) {
            Metric.insufficient(
                methodId = MethodId.GBO_ROI.id,
                period = period,
                inputEventIds = ids,
                warnings = warnings,
            )
        } else {
            metric(
                quality = QualityRules.formulaQuality(actualQualityInputs),
                methodId = MethodId.GBO_ROI.id,
                period = period,
                ids = ids,
                warnings = warnings.filter { it != WarningCode.MISSING_CONSUMED_COST },
                moneyMinor = actualMinor,
                currency = currency,
                distanceKm = covered,
            )
        }
        val baseline = baseline(input, covered, period, ids, entireHistory)
        val netMinor = if (baseline.moneyMinor != null && actual.moneyMinor != null &&
            baseline.currency == actual.currency
        ) {
            baseline.moneyMinor - actual.moneyMinor
        } else {
            null
        }
        val net = if (netMinor == null || covered == null || covered <= 0) {
            Metric.insufficient(
                methodId = MethodId.GBO_ROI.id,
                period = period,
                inputEventIds = ids,
                warnings = if (netMinor == null) warnings else warnings + WarningCode.DISTANCE_NON_POSITIVE,
            )
        } else {
            metric(
                quality = QualityRules.formulaQuality(
                    listOf(baseline.quality, actual.quality).filter { it != Quality.INSUFFICIENT },
                ),
                methodId = MethodId.GBO_ROI.id,
                period = period,
                ids = ids,
                warnings = warnings,
                moneyMinor = netMinor,
                currency = actual.currency,
                distanceKm = covered,
            )
        }
        val netPerKm = if (net.moneyMinor != null && net.distanceKm != null && net.distanceKm > 0) {
            metric(
                quality = net.quality,
                methodId = MethodId.GBO_ROI.id,
                period = period,
                ids = ids,
                warnings = if (net.moneyMinor <= 0L) warnings + WarningCode.ROI_DOES_NOT_PAY_OFF else warnings,
                moneyMinor = net.moneyMinor,
                currency = net.currency,
                distanceKm = net.distanceKm,
            )
        } else {
            Metric.insufficient(
                methodId = MethodId.GBO_ROI.id,
                period = period,
                inputEventIds = ids,
                warnings = warnings + WarningCode.ROI_DOES_NOT_PAY_OFF,
            )
        }
        return SavingReport(
            baseline = baseline,
            actual = actual,
            net = net,
            netPerKm = netPerKm,
            factualAveragePetrolPrice = factualPetrolPrice(cycles, period),
            priceIsScenario = input.settings.petrolPriceIsScenario || input.settings.petrolPriceMinor == null,
        )
    }

    private fun baseline(
        input: CalculationInput,
        distanceKm: Int?,
        period: Period,
        ids: List<String>,
        entireHistory: Boolean,
    ): Metric {
        val l100 = input.settings.baselinePetrolL100
        val price = input.settings.petrolPriceMinor
        val currency = input.settings.petrolPriceCurrency
        if (l100 == null || price == null || currency == null || distanceKm == null || distanceKm <= 0 || l100 <= 0.0) {
            return Metric.insufficient(
                methodId = MethodId.BASELINE_PETROL.id,
                period = period,
                inputEventIds = ids,
                scenarioPrice = input.settings.petrolPriceIsScenario,
                warnings = if (entireHistory) listOf(WarningCode.ROI_ENTIRE_HISTORY) else emptyList(),
            )
        }
        val minor = BigDecimal.valueOf(distanceKm.toLong())
            .multiply(BigDecimal.valueOf(l100))
            .multiply(BigDecimal.valueOf(price))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .longValueExact()
        val quality = if (input.settings.baselineFromFactualHistory && !input.settings.petrolPriceIsScenario) {
            Quality.CALCULATED
        } else {
            Quality.ESTIMATED
        }
        return metric(
            quality = quality,
            methodId = MethodId.BASELINE_PETROL.id,
            period = period,
            ids = ids,
            warnings = if (entireHistory) listOf(WarningCode.ROI_ENTIRE_HISTORY) else emptyList(),
            moneyMinor = minor,
            currency = currency,
            distanceKm = distanceKm,
            scenarioPrice = input.settings.petrolPriceIsScenario,
        )
    }

    private fun factualPetrolPrice(cycles: List<CycleResult>, period: Period): Metric {
        val petrol = cycles.filter {
            it.fuel == app.duofuel.gasoline.domain.model.FuelType.PETROL &&
                it.volume.volume != null &&
                it.consumedMoney.moneyMinor != null &&
                it.volume.volume > 0.0
        }
        if (petrol.isEmpty()) {
            return Metric.insufficient(MethodId.FUEL_COST.id, period)
        }
        val currencies = petrol.mapNotNull { it.consumedMoney.currency }.distinct()
        if (currencies.size != 1) {
            return Metric.insufficient(
                MethodId.FUEL_COST.id,
                period,
                warnings = listOf(WarningCode.MIXED_CURRENCY),
            )
        }
        val money = petrol.sumOf { it.consumedMoney.moneyMinor!! }
        val volume = petrol.sumOf { it.volume.volume!! }
        if (volume <= 0.0) return Metric.insufficient(MethodId.FUEL_COST.id, period)
        val perUnit = BigDecimal.valueOf(money).divide(BigDecimal.valueOf(volume), 0, RoundingMode.HALF_UP)
        return metric(
            quality = Quality.CALCULATED,
            methodId = MethodId.FUEL_COST.id,
            period = period,
            ids = petrol.flatMap { it.consumedEntryIds },
            warnings = emptyList(),
            moneyMinor = perUnit.longValueExact(),
            currency = currencies.single(),
            volume = volume,
            unit = petrol.first().volume.volumeUnit,
        )
    }

    private fun progressMetric(
        net: Long?,
        investment: Long?,
        currencyMatches: Boolean,
        period: Period,
        ids: List<String>,
    ): Metric {
        if (net == null || investment == null || investment == 0L || !currencyMatches) {
            return Metric.insufficient(
                methodId = MethodId.GBO_ROI.id,
                period = period,
                inputEventIds = ids,
                warnings = listOf(WarningCode.INVESTMENT_MISSING),
            )
        }
        // OPEN-ROI-CAP: прогресс без потолка, числитель может быть больше знаменателя и меньше нуля.
        return metric(
            quality = Quality.CALCULATED,
            methodId = MethodId.GBO_ROI.id,
            period = period,
            ids = ids,
            warnings = emptyList(),
            ratioNumerator = net,
            ratioDenominator = investment,
        )
    }

    private fun remainingMetric(
        unreimbursed: Long?,
        net: Long?,
        distance: Int?,
        doesNotPayOff: Boolean,
        period: Period,
        ids: List<String>,
    ): Metric {
        if (doesNotPayOff || net == null || net <= 0L || distance == null || distance <= 0 || unreimbursed == null) {
            return Metric.insufficient(
                methodId = MethodId.GBO_ROI.id,
                period = period,
                inputEventIds = ids,
                warnings = listOf(WarningCode.ROI_DOES_NOT_PAY_OFF),
            )
        }
        if (unreimbursed == 0L) {
            return metric(
                quality = Quality.CALCULATED,
                methodId = MethodId.GBO_ROI.id,
                period = period,
                ids = ids,
                warnings = emptyList(),
                remainingKm = BigDecimal.ZERO,
            )
        }
        val remaining = BigDecimal.valueOf(unreimbursed)
            .multiply(BigDecimal.valueOf(distance.toLong()))
            .divide(BigDecimal.valueOf(net), 4, RoundingMode.HALF_UP)
        return metric(
            quality = Quality.CALCULATED,
            methodId = MethodId.GBO_ROI.id,
            period = period,
            ids = ids,
            warnings = emptyList(),
            remainingKm = remaining,
        )
    }

    private fun inWindow(at: Long, period: Period): Boolean {
        val start = period.startInclusiveMs
        val end = period.endExclusiveMs
        return (start == null || at >= start) && (end == null || at < end)
    }
}
