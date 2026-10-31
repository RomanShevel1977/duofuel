package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.Metric
import app.duofuel.gasoline.domain.model.MethodId
import app.duofuel.gasoline.domain.model.Period
import app.duofuel.gasoline.domain.model.Quality
import app.duofuel.gasoline.domain.model.WarningCode

class FuelBalanceCalculator {
    /**
     * OPEN-LEVEL: формула §6.2 сохранена. Неизвестный остаток не заменяется нулём,
     * вместимостью или 80% из FR-VEH-05. В приёмку MVP баланс не входит.
     */
    fun consumed(
        startLevel: Double?,
        purchases: Double,
        endLevel: Double?,
        unit: String,
        startConfirmed: Boolean,
        endConfirmed: Boolean,
        period: Period,
        inputEventIds: List<String> = emptyList(),
        purchaseUnitsConsistent: Boolean = true,
    ): Metric {
        if (!purchaseUnitsConsistent || startLevel == null || endLevel == null) {
            return Metric.insufficient(
                methodId = MethodId.FUEL_BALANCE.id,
                period = period,
                inputEventIds = inputEventIds,
                warnings = listOf(WarningCode.UNKNOWN_LEVEL_NOT_ZERO),
            )
        }
        val volume = startLevel + purchases - endLevel
        val quality = if (startConfirmed && endConfirmed) Quality.FACTUAL else Quality.ESTIMATED
        return metric(
            quality = quality,
            methodId = MethodId.FUEL_BALANCE.id,
            period = period,
            ids = inputEventIds,
            warnings = emptyList(),
            volume = volume,
            unit = unit,
        )
    }
}
