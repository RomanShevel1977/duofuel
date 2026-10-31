package app.duofuel.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duofuel.domain.calculator.DashboardCalculator
import app.duofuel.domain.calculator.PeriodRequest
import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.DashboardReport
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.RecordSource
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.repository.AppState
import app.duofuel.domain.repository.ArchivePreview
import app.duofuel.domain.repository.FuelConfigProfile
import app.duofuel.domain.repository.FuelEntryDraft
import app.duofuel.domain.repository.DuoFuelRepository
import app.duofuel.domain.repository.VehicleProfile
import app.duofuel.domain.repository.VehicleSnapshot
import app.duofuel.domain.validation.FuelEntryValidator
import app.duofuel.domain.validation.InputParsers
import app.duofuel.domain.validation.IssueCode
import app.duofuel.domain.validation.MoneyMath
import app.duofuel.domain.validation.OdometerValidator
import app.duofuel.domain.validation.Parse
import app.duofuel.domain.validation.ServiceValidator
import app.duofuel.domain.validation.ValidationIssue
import app.duofuel.domain.validation.VehicleValidator
import app.duofuel.ui.formatMinor
import app.duofuel.ui.issueText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class ScreenState(
    val app: AppState = AppState(null, emptyList(), false, false),
    val period: PeriodRequest = currentMonth(),
    val report: DashboardReport? = null,
    val ready: Boolean = false,
    val notice: String? = null,
    val openRefuel: Boolean = false,
)

@HiltViewModel
class DuoFuelViewModel @Inject constructor(
    private val repository: DuoFuelRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val calculator = DashboardCalculator()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeApp().collect { app ->
                _state.update { current ->
                    current.copy(app = app, report = app.active?.let { report(it, current.period) }, ready = true)
                }
            }
        }
    }

    fun setPeriod(period: PeriodRequest) {
        val active = _state.value.app.active
        _state.update { it.copy(period = period, report = active?.let { snapshot -> report(snapshot, period) }) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun saveVehicle(
        profile: VehicleProfile,
        fuels: List<FuelConfigProfile>,
        settings: CalculationSettingsCalc,
    ): List<String> {
        val issues = VehicleValidator.validate(profile.name, fuels.map { app.duofuel.domain.validation.FuelDraft(it.fuelType, it.unit, it.currency) })
        if (issues.isNotEmpty()) return issues.map { issueText(context, it) }
        val creating = _state.value.app.active == null
        viewModelScope.launch {
            repository.saveVehicle(profile, fuels, settings)
            if (creating) _state.update { it.copy(openRefuel = true) }
        }
        return emptyList()
    }

    fun consumeOpenRefuel() = _state.update { it.copy(openRefuel = false) }

    fun saveFuel(draft: FuelEntryDraft, confirmDecrease: Boolean, confirmChronology: Boolean): List<String> {
        val issues = mutableListOf<ValidationIssue>()
        FuelEntryValidator.total(draft.totalCostMinor)?.let(issues::add)
        if (draft.quantity <= 0.0) {
            issues += ValidationIssue(
                app.duofuel.domain.validation.FieldCode.QUANTITY,
                IssueCode.MUST_BE_POSITIVE,
                true,
            )
        }
        val candidate = OdometerEventCalc(
            id = draft.id ?: "draft",
            vehicleId = draft.vehicleId,
            occurredAtMs = draft.occurredAtMs,
            odometerKm = draft.odometerKm,
            kind = if (confirmDecrease) OdometerKind.RESET else draft.odometerKind,
            inputMethod = draft.inputMethod,
            source = RecordSource.MANUAL,
            linkedFuelEntryId = null,
            createdAtMs = System.currentTimeMillis(),
        )
        val existing = _state.value.app.active?.odometer.orEmpty().filter { it.linkedFuelEntryId != draft.id }
        issues += OdometerValidator.check(candidate, existing, confirmDecrease, confirmChronology)
        if (issues.any { it.blocking }) return issues.filter { it.blocking }.map { issueText(context, it) }
        val stored = if (confirmDecrease) draft.copy(odometerKind = OdometerKind.RESET) else draft
        viewModelScope.launch {
            repository.saveFuel(stored)
            _state.update { it.copy(notice = context.getString(app.duofuel.R.string.saved)) }
        }
        return emptyList()
    }

    fun needsDecreaseConfirm(draft: FuelEntryDraft): Boolean {
        val candidate = OdometerEventCalc(
            draft.id ?: "draft", draft.vehicleId, draft.occurredAtMs, draft.odometerKm,
            OdometerKind.READING, draft.inputMethod, RecordSource.MANUAL, null, System.currentTimeMillis(),
        )
        val existing = _state.value.app.active?.odometer.orEmpty().filter { it.linkedFuelEntryId != draft.id }
        return OdometerValidator.check(candidate, existing, false, true)
            .any { it.code == IssueCode.DECREASE_NEEDS_CONFIRMATION }
    }

    fun needsChronologyConfirm(draft: FuelEntryDraft): Boolean {
        val candidate = OdometerEventCalc(
            draft.id ?: "draft", draft.vehicleId, draft.occurredAtMs, draft.odometerKm,
            OdometerKind.READING, draft.inputMethod, RecordSource.MANUAL, null, System.currentTimeMillis(),
        )
        val existing = _state.value.app.active?.odometer.orEmpty().filter { it.linkedFuelEntryId != draft.id }
        return OdometerValidator.check(candidate, existing, true, false)
            .any { it.code == IssueCode.CHRONOLOGY_NEEDS_CONFIRMATION }
    }

    fun saveOdometerReading(vehicleId: String, occurredAtMs: Long, odometerKm: Int, trip: Boolean, panelReset: Boolean): List<String> {
        val base = _state.value.app.active?.odometer?.maxByOrNull { it.sortKey() }?.odometerKm
        val absolute = if (trip) {
            if (base == null) {
                return listOf(issueText(context, ValidationIssue(app.duofuel.domain.validation.FieldCode.TRIP, IssueCode.TRIP_NEEDS_BASE, true)))
            }
            base + odometerKm
        } else {
            odometerKm
        }
        val candidate = OdometerEventCalc(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            occurredAtMs = occurredAtMs,
            odometerKm = absolute,
            kind = if (panelReset) OdometerKind.RESET else OdometerKind.READING,
            inputMethod = if (trip) InputMethod.TRIP else InputMethod.ABSOLUTE,
            source = RecordSource.MANUAL,
            linkedFuelEntryId = null,
            createdAtMs = System.currentTimeMillis(),
        )
        val issues = OdometerValidator.check(
            candidate,
            _state.value.app.active?.odometer.orEmpty(),
            confirmDecrease = panelReset,
            confirmChronology = true,
        )
        if (issues.any { it.blocking }) return issues.filter { it.blocking }.map { issueText(context, it) }
        viewModelScope.launch { repository.saveOdometer(candidate) }
        return emptyList()
    }

    fun undo() {
        viewModelScope.launch { repository.undoLastFuelSave() }
    }

    fun deleteFuel(id: String) = viewModelScope.launch { repository.deleteFuel(id) }

    fun saveSchedule(schedule: ServiceScheduleCalc) = viewModelScope.launch { repository.saveSchedule(schedule) }

    fun saveService(record: ServiceRecordCalc): List<String> {
        val matches = _state.value.app.active?.schedules?.count { it.serviceType == record.serviceType } ?: 0
        val issue = ServiceValidator.ambiguous(matches, record.scheduleId)
        if (issue != null) return listOf(issueText(context, issue))
        viewModelScope.launch { repository.saveService(record, record.provider, record.notes) }
        return emptyList()
    }

    fun saveExpense(expense: ExpenseCalc) = viewModelScope.launch { repository.saveExpense(expense, expense.notes) }

    fun deleteExpense(id: String) = viewModelScope.launch { repository.deleteExpense(id) }
    fun deleteService(id: String) = viewModelScope.launch { repository.deleteService(id) }

    fun setPrefillFull(value: Boolean) = viewModelScope.launch { repository.setPrefillFull(value) }
    fun setReminders(value: Boolean) = viewModelScope.launch { repository.setRemindersEnabled(value) }

    fun archiveActive() {
        val id = _state.value.app.active?.vehicle?.id ?: return
        viewModelScope.launch { repository.archiveVehicle(id) }
    }

    fun restore(id: String) {
        if (_state.value.app.active != null) {
            _state.update { it.copy(notice = context.getString(app.duofuel.R.string.one_vehicle)) }
            return
        }
        viewModelScope.launch { repository.restoreVehicle(id) }
    }

    fun deleteActive() {
        val id = _state.value.app.active?.vehicle?.id ?: return
        viewModelScope.launch { repository.deleteVehicle(id) }
    }

    suspend fun exportCsv(): ByteArray = repository.exportCsvZip()
    suspend fun exportArchive(): ByteArray = repository.exportArchive(includeAttachments = true)
    suspend fun previewArchive(bytes: ByteArray): ArchivePreview = repository.previewArchive(bytes)
    suspend fun applyArchive(bytes: ByteArray) = repository.applyArchive(bytes)

    fun suggestedFuel(): FuelType =
        _state.value.app.active?.entries?.maxByOrNull { it.sortKey() }?.fuelType
            ?: _state.value.app.active?.fuels?.firstOrNull()?.fuelType
            ?: FuelType.LPG

    fun suggestedOdometer(): String =
        _state.value.app.active?.odometer?.maxByOrNull { it.sortKey() }?.odometerKm?.toString().orEmpty()

    // OPEN-PRICE-PREFILL: последняя цена этого топлива, иначе цена по умолчанию, иначе пусто.
    fun suggestedPrice(fuel: FuelType): String {
        val snapshot = _state.value.app.active ?: return ""
        val currency = snapshot.fuels.firstOrNull { it.fuelType == fuel }?.currency ?: return ""
        val last = snapshot.entries.filter { it.fuelType == fuel && it.unitPriceMinor != null }.maxByOrNull { it.sortKey() }
        val minor = last?.unitPriceMinor ?: snapshot.fuels.firstOrNull { it.fuelType == fuel }?.defaultPriceMinor
        return minor?.let { formatMinor(it, currency, java.util.Locale.getDefault()).substringBefore(' ') }.orEmpty()
    }

    private fun report(snapshot: VehicleSnapshot, period: PeriodRequest): DashboardReport =
        calculator.calculate(snapshot.toInput(), period, zone, LocalDate.now(zone), System.currentTimeMillis())
}

fun currentMonth(): PeriodRequest {
    val today = LocalDate.now()
    return PeriodRequest.CalendarMonth(today.year, today.monthValue)
}

fun parseDate(text: String, zone: ZoneId): Long? = runCatching {
    LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant().toEpochMilli()
}.getOrNull()

fun parseQuantity(text: String) = InputParsers.quantity(text)
fun parseKm(text: String) = InputParsers.odometerKm(text)
fun parseMoney(text: String, currency: String) = MoneyMath.parseMinor(text, currency)

fun productOrNull(quantity: String, price: String, currency: String): Long? {
    val priceMinor = (parseMoney(price, currency) as? Parse.Ok)?.value ?: return null
    return MoneyMath.exactProductMinor(quantity, priceMinor)
}
