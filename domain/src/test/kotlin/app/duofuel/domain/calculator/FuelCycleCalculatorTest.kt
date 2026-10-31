package app.duofuel.domain.calculator

import app.duofuel.domain.model.CalculationInput
import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.FuelEntryCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.RecordSource
import app.duofuel.domain.model.WarningCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FuelCycleCalculatorTest {
    private val cycles = FuelCycleCalculator()
    private val dashboard = DashboardCalculator()
    private val balance = FuelBalanceCalculator()
    private val mixed = MixedFuelReportCalculator()
    private val zone = ZoneId.of("Europe/Kyiv")

    @Test
    fun fullToFull_excludesOpening_includesClosing() {
        // OPEN-CYCLE: 20 л — закрывающая граница. 40 л открывающего полного старта в сумму не входят.
        // Ожидаемые 30 л и 10,0 л/100 км — это названные 10 + 20 л на 300 км.
        val entries = listOf(
            fuel("open", 1, 1_000, 40.0, true, 4_000),
            fuel("partial", 2, 1_100, 10.0, false, 1_000),
            fuel("close", 3, 1_300, 20.0, true, 2_000),
        )
        val cycle = cycles.cycles(entries, emptyList(), FuelType.LPG).single()
        assertEquals(listOf("partial", "close"), cycle.consumedEntryIds)
        assertFalse(cycle.consumedEntryIds.contains("open"))
        assertEquals(30.0, cycle.volume.volume!!, 1e-9)
        assertEquals(10.0, cycle.rate.ratePer100!!, 1e-9)
        assertEquals(300, cycle.distanceKm)
        assertEquals(Quality.FACTUAL, cycle.rate.quality)
        assertEquals(3_000L, cycle.consumedMoney.moneyMinor)
        assertEquals("fuel_cycle_full_to_full", cycle.rate.methodId)
    }

    @Test
    fun severalPartials_sumAllNamedLiters() {
        // OPEN-CYCLE: 8 л — закрывающая заправка. Открывающие 50 л не входят в 20 л.
        val entries = listOf(
            fuel("open", 1, 0, 50.0, true, 1),
            fuel("a", 2, 100, 5.0, false, 1),
            fuel("b", 3, 180, 7.0, false, 1),
            fuel("close", 4, 250, 8.0, true, 1),
        )
        val cycle = cycles.cycles(entries, emptyList(), FuelType.LPG).single()
        assertEquals(20.0, cycle.volume.volume!!, 1e-9)
        assertEquals(8.0, cycle.rate.ratePer100!!, 1e-9)
        assertEquals(listOf("a", "b", "close"), cycle.consumedEntryIds)
    }

    @Test
    fun unclosedCycle_hasNoFactualRate() {
        val entries = listOf(
            fuel("open", 1, 0, 40.0, true, 1),
            fuel("partial", 2, 100, 10.0, false, 1),
        )
        assertTrue(cycles.cycles(entries, emptyList(), FuelType.LPG).isEmpty())
        val open = cycles.unclosed(entries, FuelType.LPG)
        assertEquals(listOf("partial"), open!!.partialIds)
        val report = dashboard.calculate(input(entries), PeriodRequest.AllHistory, zone, LocalDate.of(2026, 10, 30), 10)
        val rate = report.consumptionByFuel.single { it.fuel == FuelType.LPG }.rate
        assertEquals(Quality.INSUFFICIENT, rate.quality)
        assertNull(rate.ratePer100)
        assertFalse(rate.quality == Quality.FACTUAL)
    }

    @Test
    fun zeroDistance_isInsufficient_andNotInfinite() {
        val entries = listOf(
            fuel("open", 1, 100, 40.0, true, 1),
            fuel("close", 2, 100, 12.0, true, 1),
        )
        val cycle = cycles.cycles(entries, emptyList(), FuelType.LPG).single()
        assertEquals(Quality.INSUFFICIENT, cycle.rate.quality)
        assertNull(cycle.rate.ratePer100)
        assertTrue(cycle.rate.warnings.contains(WarningCode.DISTANCE_NON_POSITIVE))
        assertEquals(12.0, cycle.volume.volume!!, 1e-9)
    }

    @Test
    fun differentFuelBoundaries_areNotPreciseMixedVolume() {
        val entries = listOf(
            fuel("g-open", 1, 0, 30.0, true, 1, FuelType.LPG),
            fuel("p-open", 1, 0, 20.0, true, 1, FuelType.PETROL),
            fuel("g-close", 2, 300, 15.0, true, 1, FuelType.LPG),
            fuel("p-mid", 3, 100, 5.0, false, 1, FuelType.PETROL),
            fuel("p-close", 4, 400, 6.0, true, 1, FuelType.PETROL),
        )
        val report = mixed.report(
            mapOf(
                FuelType.LPG to cycles.cycles(entries, emptyList(), FuelType.LPG).single(),
                FuelType.PETROL to cycles.cycles(entries, emptyList(), FuelType.PETROL).single(),
            ),
            setOf(FuelType.LPG, FuelType.PETROL),
        )
        assertFalse(report.combinedAvailable)
        assertNull(report.preciseMixedVolume)
        assertTrue(report.warnings.contains(WarningCode.CYCLES_NOT_ALIGNED))
        val dash = dashboard.calculate(
            input(entries, setOf(FuelType.LPG, FuelType.PETROL)),
            PeriodRequest.LatestControlCycles,
            zone,
            LocalDate.of(2026, 10, 30),
            20,
        )
        assertNull(dash.fuelCostPerKm.moneyMinor)
        assertEquals(Quality.INSUFFICIENT, dash.fuelCostPerKm.quality)
        assertTrue(dash.consumptionByFuel.all { it.rate.quality == Quality.FACTUAL || it.rate.quality == Quality.INSUFFICIENT })
        val lpg = dash.consumptionByFuel.single { it.fuel == FuelType.LPG }
        val petrol = dash.consumptionByFuel.single { it.fuel == FuelType.PETROL }
        assertEquals(15.0, lpg.volume.volume!!, 1e-9)
        assertEquals(11.0, petrol.volume.volume!!, 1e-9)
    }

    @Test
    fun paymentsWithoutLevels_showCashflowNotConsumed() {
        val entries = listOf(
            fuel("only", 5, 10, 12.0, false, 2_500),
        )
        val report = dashboard.calculate(input(entries), PeriodRequest.AllHistory, zone, LocalDate.of(2026, 10, 30), 30)
        assertEquals(2_500L, report.cashflow.total.moneyMinor)
        assertEquals("fuel_cashflow", report.cashflow.total.methodId)
        assertEquals(Quality.INSUFFICIENT, report.fuelCostPerKm.quality)
        assertNull(report.fuelCostPerKm.moneyMinor)
        assertNull(report.consumptionByFuel.single().rate.ratePer100)
        assertEquals(Quality.INSUFFICIENT, report.balance.quality)
        assertNull(report.balance.volume)
        assertTrue(report.balance.warnings.contains(WarningCode.UNKNOWN_LEVEL_NOT_ZERO))
    }

    @Test
    fun unknownLevel_isNotZeroOrCapacity() {
        val period = app.duofuel.domain.model.Period(
            app.duofuel.domain.model.PeriodKind.ALL_HISTORY,
            null, null, null, null, "all",
        )
        val missingEnd = balance.consumed(10.0, 5.0, null, "L", true, false, period)
        assertNull(missingEnd.volume)
        assertEquals(Quality.INSUFFICIENT, missingEnd.quality)
        assertFalse(missingEnd.quality == Quality.FACTUAL)
        val known = balance.consumed(10.0, 30.0, 15.0, "L", true, true, period)
        assertEquals(25.0, known.volume!!, 1e-9)
        assertEquals(Quality.FACTUAL, known.quality)
    }

    @Test
    fun cngUnits_areNotSummed() {
        val entries = listOf(
            fuel("open", 1, 0, 5.0, true, 100, FuelType.CNG, unit = "m3"),
            fuel("kg", 2, 50, 2.0, false, 80, FuelType.CNG, unit = "kg"),
            fuel("close", 3, 100, 3.0, true, 90, FuelType.CNG, unit = "m3"),
        )
        val cycle = cycles.cycles(entries, emptyList(), FuelType.CNG).single()
        assertNull(cycle.volume.volume)
        assertEquals(Quality.INSUFFICIENT, cycle.volume.quality)
        assertNull(cycle.rate.ratePer100)
        assertTrue(cycle.volume.warnings.contains(WarningCode.MIXED_UNITS))
        assertNull(cycle.consumedMoney.moneyMinor)
    }

    @Test
    fun editingPastEntry_recalculatesOnlyThatFuel() {
        val original = listOf(
            fuel("g-open", 1, 0, 40.0, true, 1, FuelType.LPG),
            fuel("g-close", 2, 200, 10.0, true, 1, FuelType.LPG),
            fuel("p-open", 1, 0, 40.0, true, 1, FuelType.PETROL),
            fuel("p-close", 2, 200, 8.0, true, 1, FuelType.PETROL),
        )
        val edited = original.map { if (it.id == "g-close") it.copy(quantity = 16.0) else it }
        val beforePetrol = cycles.cycles(original, emptyList(), FuelType.PETROL).single().volume.volume
        val afterPetrol = cycles.cycles(edited, emptyList(), FuelType.PETROL).single().volume.volume
        val afterGas = cycles.cycles(edited, emptyList(), FuelType.LPG).single().volume.volume
        assertEquals(beforePetrol!!, afterPetrol!!, 1e-9)
        assertEquals(16.0, afterGas!!, 1e-9)
    }

    @Test
    fun cashflowIncludesOpening_consumedDoesNot() {
        val entries = listOf(
            fuel("open", 1, 0, 40.0, true, 4_000),
            fuel("partial", 2, 100, 10.0, false, 1_000),
            fuel("close", 3, 300, 20.0, true, 2_000),
        )
        val report = dashboard.calculate(input(entries), PeriodRequest.AllHistory, zone, LocalDate.of(2026, 10, 30), 40)
        assertEquals(7_000L, report.cashflow.total.moneyMinor)
        assertEquals(3_000L, report.consumptionByFuel.single().consumedMoney.moneyMinor)
        assertTrue(report.periodMismatch || report.cashflowPerKm.distanceKm != report.fuelCostPerKm.distanceKm || report.cashflow.total.moneyMinor != report.fuelCostPerKm.moneyMinor)
    }

    @Test
    fun emptyDashboard_doesNotSubstituteZeros() {
        val report = dashboard.calculate(
            input(emptyList()),
            PeriodRequest.CalendarMonth(2026, 10),
            zone,
            LocalDate.of(2026, 10, 30),
            50,
        )
        assertNull(report.cashflow.total.moneyMinor)
        assertNull(report.fuelCostPerKm.moneyMinor)
        assertNull(report.fuelCostPerKm.ratePer100)
        assertEquals(Quality.INSUFFICIENT, report.fuelCostPerKm.quality)
        assertEquals(Quality.INSUFFICIENT, report.saving.net.quality)
        assertNull(report.saving.net.moneyMinor)
        assertTrue(report.consumptionByFuel.isEmpty() || report.consumptionByFuel.all { it.rate.ratePer100 == null })
        assertFalse(report.roi.showDate)
    }

    @Test
    fun sameOdometer_keepsBoth_andDoesNotDoubleDistance() {
        val first = odo("a", 10, 100, 10)
        val second = odo("b", 10, 100, 11)
        val distance = DistanceCalculator.summed(listOf(first, second))
        assertEquals(0, distance)
        val sorted = DistanceCalculator.sorted(listOf(second, first)).map { it.id }
        assertEquals(listOf("a", "b"), sorted)
    }

    @Test
    fun reset_doesNotJoinScales() {
        val events = listOf(
            odo("old", 1, 50_000, 1),
            odo("reset", 2, 10, 2, OdometerKind.RESET),
            odo("new", 3, 110, 3),
        )
        assertEquals(100, DistanceCalculator.summed(events))
    }

    @Test
    fun mixedCurrency_hasNoSingleMoneyTotal() {
        val entries = listOf(
            fuel("open", 1, 0, 10.0, true, 100, currency = "UAH"),
            fuel("a", 2, 100, 5.0, false, 50, currency = "UAH"),
            fuel("close", 3, 200, 5.0, true, 40, currency = "EUR"),
        )
        val cycle = cycles.cycles(entries, emptyList(), FuelType.LPG).single()
        assertEquals(10.0, cycle.volume.volume!!, 1e-9)
        assertNull(cycle.consumedMoney.moneyMinor)
        assertEquals(Quality.INSUFFICIENT, cycle.consumedMoney.quality)
        assertEquals(mapOf("UAH" to 50L, "EUR" to 40L), cycle.moneyByCurrency)
    }

    @Test
    fun backdatedPartial_joinsChronology() {
        val entries = listOf(
            fuel("open", 1, 0, 30.0, true, 1),
            fuel("close", 4, 200, 8.0, true, 1),
            fuel("late-added", 2, 80, 4.0, false, 1),
        )
        val cycle = cycles.cycles(entries, emptyList(), FuelType.LPG).single()
        assertEquals(listOf("late-added", "close"), cycle.consumedEntryIds)
        assertEquals(12.0, cycle.volume.volume!!, 1e-9)
    }

    private fun fuel(
        id: String,
        at: Long,
        odo: Int,
        qty: Double,
        full: Boolean,
        total: Long,
        type: FuelType = FuelType.LPG,
        unit: String = "L",
        currency: String = "UAH",
        created: Long = at,
    ) = FuelEntryCalc(
        id = id,
        vehicleId = "v",
        fuelType = type,
        occurredAtMs = at,
        odometerKm = odo,
        quantity = qty,
        unit = unit,
        unitPriceMinor = null,
        totalCostMinor = total,
        currency = currency,
        isFull = full,
        createdAtMs = created,
    )

    private fun odo(
        id: String,
        at: Long,
        km: Int,
        created: Long,
        kind: OdometerKind = OdometerKind.READING,
    ) = OdometerEventCalc(
        id = id,
        vehicleId = "v",
        occurredAtMs = at,
        odometerKm = km,
        kind = kind,
        inputMethod = InputMethod.ABSOLUTE,
        source = RecordSource.MANUAL,
        linkedFuelEntryId = null,
        createdAtMs = created,
    )

    private fun input(
        entries: List<FuelEntryCalc>,
        fuels: Set<FuelType> = entries.map { it.fuelType }.toSet(),
    ) = CalculationInput(
        vehicleId = "v",
        installedAtMs = null,
        installCostMinor = null,
        installCostCurrency = null,
        configuredFuels = fuels,
        fuelEntries = entries,
        odometerEvents = entries.map {
            odo("odo-" + it.id, it.occurredAtMs, it.odometerKm, it.createdAtMs)
        },
        serviceRecords = emptyList(),
        schedules = emptyList(),
        expenses = emptyList(),
        settings = CalculationSettingsCalc(null, null, null, true, false),
    )
}
