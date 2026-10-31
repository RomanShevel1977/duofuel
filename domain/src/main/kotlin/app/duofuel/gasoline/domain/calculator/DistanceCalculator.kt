package app.duofuel.gasoline.domain.calculator

import app.duofuel.gasoline.domain.model.OdometerEventCalc
import app.duofuel.gasoline.domain.model.OdometerKind
import app.duofuel.gasoline.domain.model.SortKey

object DistanceCalculator {
    // OPEN-SORT: occurred_at, odometer, created_at, id — все по возрастанию.
    private val order = compareBy<OdometerEventCalc>(
        { it.occurredAtMs },
        { it.odometerKm },
        { it.createdAtMs },
        { it.id },
    )

    fun sorted(events: List<OdometerEventCalc>): List<OdometerEventCalc> = events.sortedWith(order)

    /**
     * Сумма положительных отрезков. RESET не прибавляет скачок шкалы.
     * Нет событий — null (неизвестная дистанция, не ноль). Нулевая разность — 0.
     */
    fun summed(events: List<OdometerEventCalc>): Int? {
        val sorted = sorted(events)
        if (sorted.isEmpty()) return null
        if (sorted.size == 1) return 0
        var total = 0
        for (index in 1 until sorted.size) {
            val previous = sorted[index - 1]
            val current = sorted[index]
            if (current.kind == OdometerKind.RESET) continue
            val delta = current.odometerKm - previous.odometerKm
            if (delta > 0) total += delta
        }
        return total
    }

    fun inTimeRange(
        events: List<OdometerEventCalc>,
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
    ): Int? {
        val sorted = sorted(events)
        if (sorted.isEmpty()) return null
        val start = startInclusiveMs ?: Long.MIN_VALUE
        val end = endExclusiveMs ?: Long.MAX_VALUE
        val inside = sorted.filter { it.occurredAtMs in start until end }
        val anchor = sorted.lastOrNull { it.occurredAtMs < start }
        val chain = if (anchor != null) listOf(anchor) + inside else inside
        if (chain.isEmpty()) return null
        return summed(chain)
    }

    fun scaleBroken(
        opening: SortKey,
        closing: SortKey,
        events: List<OdometerEventCalc>,
        openingFuelId: String,
        closingFuelId: String,
    ): Boolean = events.any { event ->
        if (event.kind != OdometerKind.RESET) return@any false
        if (event.linkedFuelEntryId == openingFuelId || event.linkedFuelEntryId == closingFuelId) {
            return@any false
        }
        val key = event.sortKey()
        key > opening && key < closing
    }
}
