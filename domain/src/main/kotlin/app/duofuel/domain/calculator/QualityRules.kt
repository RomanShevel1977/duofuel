package app.duofuel.domain.calculator

import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.rank

object QualityRules {
    // OPEN-QUALITY-AGG: худшая обязательная метка. Формула не остаётся фактической.
    fun worst(qualities: List<Quality>): Quality {
        if (qualities.isEmpty()) return Quality.INSUFFICIENT
        return qualities.maxBy { it.rank() }
    }

    fun formulaQuality(inputQualities: List<Quality>): Quality {
        if (inputQualities.any { it == Quality.INSUFFICIENT }) return Quality.INSUFFICIENT
        val withFormulaFloor = inputQualities + Quality.CALCULATED
        return worst(withFormulaFloor)
    }
}
