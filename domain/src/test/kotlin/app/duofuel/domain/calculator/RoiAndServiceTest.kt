package app.duofuel.domain.calculator

import app.duofuel.domain.model.CalculationInput
import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FuelEntryCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.RecordSource
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.model.WarningCode
import app.duofuel.domain.validation.FieldCode
import app.duofuel.domain.validation.FuelEntryValidator
import app.duofuel.domain.validation.InputParsers
import app.duofuel.domain.validation.IssueCode
import app.duofuel.domain.validation.MoneyMath
import app.duofuel.domain.validation.OdometerValidator
import app.duofuel.domain.validation.Parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RoiAndServiceTest {
    private val dashboard = DashboardCalculator()
    private val roi = GboRoiCalculator()
    private val maintenance = MaintenanceCostCalculator()
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun nonPositiveSaving_buildsNoForecastAndNoDate() {
        val report = dashboard.calculate(
            sample(consumedClose = 20_000, baselineL100 = 10.0, price = 1_000, investment = 12_000),
            PeriodRequest.AllHistory,
            zone,
            LocalDate.of(2026, 6, 1),
            5_000,
        )
        assertTrue(report.roi.doesNotPayOff)
        assertFalse(report.roi.buildsNewForecast)
        assertFalse(report.roi.showDate)
        assertEquals(Quality.INSUFFICIENT, report.roi.remainingKm.quality)
        assertNull(report.roi.remainingKm.remainingKm)
        assertTrue(report.roi.saving.netPerKm.warnings.contains(WarningCode.ROI_DOES_NOT_PAY_OFF))
    }

    @Test
    fun positiveSaving_showsRemainingKmWithoutDate() {
        val report = dashboard.calculate(
            sample(consumedClose = 4_000, baselineL100 = 10.0, price = 1_000, investment = 12_000),
            PeriodRequest.AllHistory,
            zone,
            LocalDate.of(2026, 6, 1),
            5_000,
        )
        assertFalse(report.roi.doesNotPayOff)
        assertFalse(report.roi.showDate)
        assertTrue(report.roi.buildsNewForecast)
        assertEquals(BigDecimal("100.0000"), report.roi.remainingKm.remainingKm)
        assertEquals(6_000L, report.roi.progress.ratioNumerator)
        assertEquals(12_000L, report.roi.progress.ratioDenominator)
    }

    @Test
    fun progress_isNotCapped_andZeroRemainderDoesNotContinueForecast() {
        val report = dashboard.calculate(
            sample(consumedClose = 4_000, baselineL100 = 10.0, price = 1_000, investment = 5_000),
            PeriodRequest.AllHistory,
            zone,
            LocalDate.of(2026, 6, 1),
            5_000,
        )
        assertEquals(6_000L, report.roi.progress.ratioNumerator)
        assertEquals(5_000L, report.roi.progress.ratioDenominator)
        assertTrue(report.roi.progress.ratioNumerator!! > report.roi.progress.ratioDenominator!!)
        assertEquals(0L, report.roi.unreimbursedMinor)
        assertEquals(BigDecimal.ZERO, report.roi.remainingKm.remainingKm)
        assertFalse(report.roi.buildsNewForecast)
    }

    @Test
    fun negativeSaving_doesNotIncreaseInvestment() {
        val report = dashboard.calculate(
            sample(consumedClose = 20_000, baselineL100 = 10.0, price = 1_000, investment = 5_000),
            PeriodRequest.AllHistory,
            zone,
            LocalDate.of(2026, 6, 1),
            5_000,
        )
        assertEquals(5_000L, report.roi.unreimbursedMinor)
        assertTrue(report.roi.progress.ratioNumerator!! < 0)
    }

    @Test
    fun missingInstallDate_marksEntireHistory() {
        val input = sample(4_000, 10.0, 1_000, 5_000).copy(installedAtMs = null)
        val report = dashboard.calculate(input, PeriodRequest.AllHistory, zone, LocalDate.of(2026, 6, 1), 5_000)
        assertEquals(app.duofuel.domain.model.RoiWindow.ENTIRE_HISTORY, report.roi.window)
        assertTrue(report.roi.saving.baseline.warnings.contains(WarningCode.ROI_ENTIRE_HISTORY))
    }

    @Test
    fun manualBaseline_isEstimated_notFactual() {
        val report = dashboard.calculate(
            sample(4_000, 10.0, 1_000, 12_000),
            PeriodRequest.AllHistory,
            zone,
            LocalDate.of(2026, 6, 1),
            5_000,
        )
        assertEquals(Quality.ESTIMATED, report.roi.saving.baseline.quality)
        assertTrue(report.roi.saving.priceIsScenario)
        assertEquals(Quality.ESTIMATED, report.roi.saving.net.quality)
    }

    @Test
    fun fuelCostFromFactualVolume_staysCalculated() {
        val entries = listOf(
            entry("open", 1, 0, 40.0, true, 100),
            entry("close", 2, 100, 10.0, true, 4_000),
        )
        val cycle = FuelCycleCalculator().cycles(entries, emptyList(), FuelType.LPG).single()
        assertEquals(Quality.FACTUAL, cycle.volume.quality)
        assertEquals(Quality.CALCULATED, cycle.consumedMoney.quality)
    }

    @Test
    fun soon_staysOffUntilUserSetsLead() {
        val schedule = ServiceScheduleCalc(
            id = "s",
            vehicleId = "v",
            serviceType = "liquid-filter",
            intervalKm = 8_000,
            intervalDays = null,
            dueOdometerKm = null,
            dueDateEpochDay = null,
            leadKm = null,
            leadDays = null,
            snoozeUntilEpochDay = null,
            remindersEnabled = true,
        )
        val done = ServiceRecordCalc("w", "v", "liquid-filter", 1_000, 1_000, 0, "UAH", true, "s", 1_000)
        val status = maintenance.statuses(
            listOf(schedule),
            listOf(done),
            listOf(reading("o", 2_000, 8_500)),
            LocalDate.of(2026, 6, 1),
            zone,
            2_000,
        ).single()
        assertEquals(app.duofuel.domain.model.ServiceDueStatus.PENDING, status.status)
        assertEquals(9_000, status.dueOdometerKm)
        val withLead = maintenance.statuses(
            listOf(schedule.copy(leadKm = 600)),
            listOf(done),
            listOf(reading("o", 2_000, 8_500)),
            LocalDate.of(2026, 6, 1),
            zone,
            2_000,
        ).single()
        assertEquals(app.duofuel.domain.model.ServiceDueStatus.SOON, withLead.status)
    }

    @Test
    fun scheduleWithoutAnchor_isInsufficient() {
        val schedule = ServiceScheduleCalc(
            id = "s", vehicleId = "v", serviceType = "reducer",
            intervalKm = null, intervalDays = null, dueOdometerKm = null, dueDateEpochDay = null,
            leadKm = null, leadDays = null, snoozeUntilEpochDay = null, remindersEnabled = false,
        )
        val status = maintenance.statuses(listOf(schedule), emptyList(), emptyList(), LocalDate.of(2026, 6, 1), zone, 0).single()
        assertEquals(app.duofuel.domain.model.ServiceDueStatus.INSUFFICIENT, status.status)
        assertNull(status.dueEpochDay)
    }

    @Test
    fun noDistanceInWindow_forecastIsInsufficient() {
        val schedule = ServiceScheduleCalc(
            id = "s", vehicleId = "v", serviceType = "reducer",
            intervalKm = 1000, intervalDays = null, dueOdometerKm = null, dueDateEpochDay = null,
            leadKm = null, leadDays = null, snoozeUntilEpochDay = null, remindersEnabled = false,
        )
        val done = ServiceRecordCalc("w", "v", "reducer", 1, 10, 0, "UAH", true, "s", 1)
        val status = maintenance.statuses(
            listOf(schedule),
            listOf(done),
            listOf(reading("o", 1, 10)),
            LocalDate.of(2026, 6, 1),
            zone,
            ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli(),
        ).single()
        assertEquals(Quality.INSUFFICIENT, status.forecast30.quality)
        assertNull(status.forecast30.averageKmPerDay)
        assertTrue(status.forecast30.isForecast)
    }

    @Test
    fun regularService_isOutsideGboCost() {
        val regular = ServiceRecordCalc("car", "v", "oil", 10, 10, 500, "UAH", false, null, 10)
        val gbo = ServiceRecordCalc("gas", "v", "filter", 10, 10, 700, "UAH", true, null, 11)
        val idle = ExpenseCalc("e", "v", "wash", 10, 10, 50, "UAH", false, false, 12)
        assertEquals(700L, maintenance.gboMinor(listOf(regular, gbo), listOf(idle), "UAH"))
    }

    @Test
    fun dst_keepsUtcInstant_andDoesNotDuplicateLocalDay() {
        val instant = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone).toInstant()
        val stored = instant.toEpochMilli()
        assertEquals(stored, instant.toEpochMilli())
        val day = LocalDate.of(2026, 3, 8)
        val next = day.plusDays(1)
        assertEquals(LocalDate.of(2026, 3, 9), next)
        assertEquals(1, java.time.temporal.ChronoUnit.DAYS.between(day, next))
    }

    @Test
    fun zeroTotal_isAllowed_negativeAndFractionAreNot() {
        assertNull(FuelEntryValidator.total(0))
        assertEquals(IssueCode.MUST_BE_NON_NEGATIVE, FuelEntryValidator.total(-1)!!.code)
        val zero = MoneyMath.parseMinor("0", "UAH") as Parse.Ok
        assertEquals(0L, zero.value)
        val extra = MoneyMath.parseMinor("1.234", "UAH") as Parse.Err
        assertEquals(IssueCode.EXTRA_FRACTION_DIGITS, extra.issue.code)
        val km = InputParsers.odometerKm("10.5") as Parse.Err
        assertEquals(IssueCode.WHOLE_KILOMETERS, km.issue.code)
    }

    @Test
    fun decreaseWithoutConfirmation_isBlocked() {
        val existing = reading("prev", 1, 1000)
        val candidate = reading("next", 2, 900)
        val blocked = OdometerValidator.check(candidate, listOf(existing), confirmDecrease = false, confirmChronology = false)
        assertTrue(blocked.any { it.code == IssueCode.DECREASE_NEEDS_CONFIRMATION && it.blocking })
        val reset = candidate.copy(kind = OdometerKind.RESET)
        val allowed = OdometerValidator.check(reset, listOf(existing), confirmDecrease = true, confirmChronology = false)
        assertTrue(allowed.none { it.field == FieldCode.ODOMETER && it.code == IssueCode.DECREASE_NEEDS_CONFIRMATION })
    }

    @Test
    fun manualMlPerStart_isEstimatedAndNotInferred() {
        val period = app.duofuel.domain.model.Period(
            app.duofuel.domain.model.PeriodKind.ALL_HISTORY, null, null, null, null, "all",
        )
        assertEquals(Quality.INSUFFICIENT, roi.manualMlPerStart(null, null, period).quality)
        val estimated = roi.manualMlPerStart(50.0, 4, period)
        assertEquals(Quality.ESTIMATED, estimated.quality)
        assertEquals("manual_ml_per_start", estimated.methodId)
        assertEquals(0.2, estimated.volume!!, 1e-9)
    }

    private fun sample(
        consumedClose: Long,
        baselineL100: Double,
        price: Long,
        investment: Long,
    ): CalculationInput {
        val entries = listOf(
            entry("open", 1_000, 0, 40.0, true, 100),
            entry("close", 2_000, 100, 10.0, true, consumedClose),
        )
        return CalculationInput(
            vehicleId = "v",
            installedAtMs = 500,
            installCostMinor = investment,
            installCostCurrency = "UAH",
            configuredFuels = setOf(FuelType.LPG),
            fuelEntries = entries,
            odometerEvents = listOf(reading("o1", 1_000, 0), reading("o2", 2_000, 100)),
            serviceRecords = emptyList(),
            schedules = emptyList(),
            expenses = emptyList(),
            settings = CalculationSettingsCalc(
                baselinePetrolL100 = baselineL100,
                petrolPriceMinor = price,
                petrolPriceCurrency = "UAH",
                petrolPriceIsScenario = true,
                baselineFromFactualHistory = false,
            ),
        )
    }

    private fun entry(
        id: String,
        at: Long,
        odo: Int,
        qty: Double,
        full: Boolean,
        total: Long,
    ) = FuelEntryCalc(
        id, "v", FuelType.LPG, at, odo, qty, "L", null, total, "UAH", full, at,
    )

    private fun reading(id: String, at: Long, km: Int) = OdometerEventCalc(
        id, "v", at, km, OdometerKind.READING, InputMethod.ABSOLUTE, RecordSource.MANUAL, null, at,
    )
}
