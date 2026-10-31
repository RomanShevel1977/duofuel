package app.duofuel.domain.model

import java.math.BigDecimal

/** OPEN-DIESEL, OPEN-SHIFTS: дизеля и сущности смены нет. Набор топлив — PETROL, LPG, CNG. */
enum class FuelType { PETROL, LPG, CNG }

enum class Quality { FACTUAL, CALCULATED, ESTIMATED, INSUFFICIENT }

fun Quality.worseThan(other: Quality): Boolean = rank() > other.rank()

fun Quality.rank(): Int = when (this) {
    Quality.FACTUAL -> 0
    Quality.CALCULATED -> 1
    Quality.ESTIMATED -> 2
    Quality.INSUFFICIENT -> 3
}

enum class OdometerKind { READING, RESET }

enum class InputMethod { ABSOLUTE, TRIP }

/** IMPORT и OBD зарезервированы. Экран MVP принимает только MANUAL. */
enum class RecordSource { MANUAL, IMPORT, OBD }

enum class MethodId(val id: String) {
    FUEL_CYCLE_FULL_TO_FULL("fuel_cycle_full_to_full"),
    FUEL_BALANCE("fuel_balance"),
    FUEL_CASHFLOW("fuel_cashflow"),
    FUEL_COST("fuel_cost"),
    OPERATING_COST("operating_cost"),
    BASELINE_PETROL("baseline_petrol"),
    MANUAL_ML_PER_START("manual_ml_per_start"),
    GBO_ROI("gbo_roi"),
}

enum class WarningCode {
    DISTANCE_NON_POSITIVE,
    SCALE_BREAK,
    MIXED_UNITS,
    MIXED_CURRENCY,
    UNKNOWN_LEVEL_NOT_ZERO,
    CYCLES_NOT_ALIGNED,
    UNCLOSED_CYCLE,
    ROI_DOES_NOT_PAY_OFF,
    ROI_ENTIRE_HISTORY,
    PERIODS_DIFFER,
    PARTIAL_COVERAGE,
    MISSING_CONSUMED_COST,
    NO_EVENTS,
    INVESTMENT_MISSING,
}

enum class PeriodKind { CALENDAR_MONTH, CUSTOM, LAST_N_KM, CONTROL_CYCLE, ALL_HISTORY }

enum class CostScope { FUEL, FUEL_PLUS_GBO, OPERATING, OWNERSHIP }

object FormulaVersion {
    // OPEN-FORMULA-FREEZE: кэш хранит версию, агрегаты пересчитываются текущими правилами.
    const val CURRENT = "srs-2.0-open-cycle-1"
}

data class SortKey(
    val occurredAtMs: Long,
    val odometerKm: Int,
    val createdAtMs: Long,
    val id: String,
) : Comparable<SortKey> {
    override fun compareTo(other: SortKey): Int = compareValuesBy(
        this,
        other,
        { it.occurredAtMs },
        { it.odometerKm },
        { it.createdAtMs },
        { it.id },
    )
}

data class FuelEntryCalc(
    val id: String,
    val vehicleId: String,
    val fuelType: FuelType,
    val occurredAtMs: Long,
    val odometerKm: Int,
    val quantity: Double,
    val unit: String,
    val unitPriceMinor: Long?,
    val totalCostMinor: Long,
    val currency: String,
    val isFull: Boolean,
    val createdAtMs: Long,
    val station: String? = null,
    val notes: String? = null,
    val receiptUri: String? = null,
    val extraAttachmentUris: List<String> = emptyList(),
) {
    fun sortKey(): SortKey = SortKey(occurredAtMs, odometerKm, createdAtMs, id)
}

data class OdometerEventCalc(
    val id: String,
    val vehicleId: String,
    val occurredAtMs: Long,
    val odometerKm: Int,
    val kind: OdometerKind,
    val inputMethod: InputMethod,
    val source: RecordSource,
    val linkedFuelEntryId: String?,
    val createdAtMs: Long,
) {
    fun sortKey(): SortKey = SortKey(occurredAtMs, odometerKm, createdAtMs, id)
}

data class ServiceRecordCalc(
    val id: String,
    val vehicleId: String,
    val serviceType: String,
    val occurredAtMs: Long,
    val odometerKm: Int,
    val costMinor: Long,
    val currency: String,
    val isGbo: Boolean,
    val scheduleId: String?,
    val createdAtMs: Long,
    val provider: String? = null,
    val notes: String? = null,
    val attachmentUris: List<String> = emptyList(),
)

data class ServiceScheduleCalc(
    val id: String,
    val vehicleId: String,
    val serviceType: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val dueOdometerKm: Int?,
    val dueDateEpochDay: Long?,
    val leadKm: Int?,
    val leadDays: Int?,
    val snoozeUntilEpochDay: Long?,
    val remindersEnabled: Boolean,
)

data class ExpenseCalc(
    val id: String,
    val vehicleId: String,
    val category: String,
    val occurredAtMs: Long,
    val odometerKm: Int?,
    val amountMinor: Long,
    val currency: String,
    val includeInOperating: Boolean,
    val includeInGbo: Boolean,
    val createdAtMs: Long,
    val notes: String? = null,
)

data class LevelMeasurementCalc(
    val id: String,
    val vehicleId: String,
    val fuelType: FuelType,
    val occurredAtMs: Long,
    val odometerKm: Int,
    val level: Double?,
    val unit: String,
    val confirmed: Boolean,
)

data class CalculationSettingsCalc(
    val baselinePetrolL100: Double?,
    val petrolPriceMinor: Long?,
    val petrolPriceCurrency: String?,
    val petrolPriceIsScenario: Boolean,
    val baselineFromFactualHistory: Boolean,
)

data class CalculationInput(
    val vehicleId: String,
    val installedAtMs: Long?,
    val installCostMinor: Long?,
    val installCostCurrency: String?,
    val configuredFuels: Set<FuelType>,
    val fuelEntries: List<FuelEntryCalc>,
    val odometerEvents: List<OdometerEventCalc>,
    val serviceRecords: List<ServiceRecordCalc>,
    val schedules: List<ServiceScheduleCalc>,
    val expenses: List<ExpenseCalc>,
    val settings: CalculationSettingsCalc,
    val levelMeasurements: List<LevelMeasurementCalc> = emptyList(),
)

data class Period(
    val kind: PeriodKind,
    val startInclusiveMs: Long?,
    val endExclusiveMs: Long?,
    val startOdometerKm: Int?,
    val endOdometerKm: Int?,
    val labelKey: String,
)

data class Metric(
    val quality: Quality,
    val methodId: String,
    val formulaVersion: String,
    val period: Period,
    val inputEventIds: List<String>,
    val warnings: List<WarningCode>,
    val scope: CostScope?,
    val volume: Double?,
    val volumeUnit: String?,
    val ratePer100: Double?,
    val moneyMinor: Long?,
    val currency: String?,
    val distanceKm: Int?,
    val ratioNumerator: Long?,
    val ratioDenominator: Long?,
    val remainingKm: BigDecimal?,
    val scenarioPrice: Boolean,
) {
    init {
        if (quality == Quality.INSUFFICIENT) {
            require(volume == null && ratePer100 == null && moneyMinor == null)
            require(ratioNumerator == null && ratioDenominator == null && remainingKm == null)
        }
        if (ratePer100 != null) {
            require(ratePer100.isFinite())
        }
    }

    companion object {
        fun insufficient(
            methodId: String,
            period: Period,
            inputEventIds: List<String> = emptyList(),
            warnings: List<WarningCode> = emptyList(),
            scope: CostScope? = null,
            scenarioPrice: Boolean = false,
        ): Metric = Metric(
            quality = Quality.INSUFFICIENT,
            methodId = methodId,
            formulaVersion = FormulaVersion.CURRENT,
            period = period,
            inputEventIds = inputEventIds,
            warnings = warnings,
            scope = scope,
            volume = null,
            volumeUnit = null,
            ratePer100 = null,
            moneyMinor = null,
            currency = null,
            distanceKm = null,
            ratioNumerator = null,
            ratioDenominator = null,
            remainingKm = null,
            scenarioPrice = scenarioPrice,
        )
    }
}

enum class ServiceDueStatus { INSUFFICIENT, PENDING, SOON, DUE, OVERDUE }

data class MileageForecast(
    val windowDays: Int,
    val distanceKm: Int?,
    val averageKmPerDay: BigDecimal?,
    val daysToDue: BigDecimal?,
    val quality: Quality,
    val isForecast: Boolean = true,
)

data class ScheduleStatus(
    val scheduleId: String,
    val serviceType: String,
    val status: ServiceDueStatus,
    val dueOdometerKm: Int?,
    val dueEpochDay: Long?,
    val snoozeUntilEpochDay: Long?,
    val forecast30: MileageForecast,
    val forecast90: MileageForecast,
)

data class UnclosedCycle(
    val fuel: FuelType,
    val openingBoundaryId: String?,
    val partialIds: List<String>,
)

data class CycleResult(
    val fuel: FuelType,
    val openingId: String,
    val closingId: String,
    val consumedEntryIds: List<String>,
    val openingOdometerKm: Int,
    val closingOdometerKm: Int,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Int?,
    val volume: Metric,
    val rate: Metric,
    val consumedMoney: Metric,
    val moneyByCurrency: Map<String, Long>,
)

data class MixedReport(
    val combinedAvailable: Boolean,
    val preciseMixedVolume: Double?,
    val cycles: List<CycleResult>,
    val warnings: List<WarningCode>,
)

data class MoneyReport(
    val total: Metric,
    val byCurrency: Map<String, Long>,
)

data class FuelConsumptionCard(
    val fuel: FuelType,
    val volume: Metric,
    val rate: Metric,
    val consumedMoney: Metric,
    val cycle: CycleResult?,
)

data class SavingReport(
    val baseline: Metric,
    val actual: Metric,
    val net: Metric,
    val netPerKm: Metric,
    val factualAveragePetrolPrice: Metric,
    val priceIsScenario: Boolean,
)

enum class RoiWindow { FROM_INSTALL, ENTIRE_HISTORY }

data class RoiReport(
    val window: RoiWindow,
    val period: Period,
    val saving: SavingReport,
    val investmentMinor: Long?,
    val investmentCurrency: String?,
    val progress: Metric,
    val unreimbursedMinor: Long?,
    val remainingKm: Metric,
    val forecastSuppressed: Boolean,
    val buildsNewForecast: Boolean,
    val doesNotPayOff: Boolean,
    val showDate: Boolean,
)

data class RecentEventRef(
    val id: String,
    val kind: String,
    val occurredAtMs: Long,
    val odometerKm: Int?,
    val fuelType: FuelType?,
)

data class DashboardReport(
    val period: Period,
    val cashflow: MoneyReport,
    val cashflowPerKm: Metric,
    val consumptionByFuel: List<FuelConsumptionCard>,
    val fuelCostPerKm: Metric,
    val fuelPlusGboPerKm: Metric,
    val operatingPerKm: Metric,
    val ownershipPerKm: Metric,
    val saving: SavingReport,
    val roi: RoiReport,
    val nearestService: ScheduleStatus?,
    val serviceStatuses: List<ScheduleStatus>,
    val recentEvents: List<RecentEventRef>,
    val mixed: MixedReport,
    val periodMismatch: Boolean,
    val unclosed: List<UnclosedCycle>,
    val balance: Metric,
    val closedCycles: List<CycleResult>,
)
