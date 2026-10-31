package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.CycleResult
import app.duofuel.gasoline.domain.model.FuelType
import app.duofuel.gasoline.domain.model.MixedReport
import app.duofuel.gasoline.domain.model.Quality
import app.duofuel.gasoline.domain.model.WarningCode

class MixedFuelReportCalculator {
    /**
     * OPEN-MIX: сводка только при одном и том же интервале пробега и достаточном качестве
     * каждого включённого топлива. Допуска «почти совпало» нет. Литражи не складываются.
     */
    fun report(
        cyclesByFuel: Map<FuelType, CycleResult?>,
        requiredFuels: Set<FuelType>,
    ): MixedReport {
        val present = cyclesByFuel.values.filterNotNull()
        if (requiredFuels.isEmpty() || present.isEmpty()) {
            return MixedReport(
                combinedAvailable = false,
                preciseMixedVolume = null,
                cycles = present,
                warnings = listOf(WarningCode.CYCLES_NOT_ALIGNED),
            )
        }
        val covering = requiredFuels.all { fuel ->
            val cycle = cyclesByFuel[fuel]
            cycle != null && cycle.rate.quality == Quality.FACTUAL && cycle.distanceKm != null
        }
        if (!covering) {
            return MixedReport(
                combinedAvailable = false,
                preciseMixedVolume = null,
                cycles = present,
                warnings = listOf(WarningCode.CYCLES_NOT_ALIGNED),
            )
        }
        val cycles = requiredFuels.map { fuel -> checkNotNull(cyclesByFuel[fuel]) }
        val sameInterval = cycles.map { it.openingOdometerKm to it.closingOdometerKm }.distinct().size == 1
        return MixedReport(
            combinedAvailable = sameInterval,
            preciseMixedVolume = null,
            cycles = cycles,
            warnings = if (sameInterval) emptyList() else listOf(WarningCode.CYCLES_NOT_ALIGNED),
        )
    }
}
