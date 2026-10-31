package app.duofuel.gasoline.domain.repository

import app.duofuel.gasoline.domain.model.CalculationInput
import app.duofuel.gasoline.domain.model.CalculationSettingsCalc
import app.duofuel.gasoline.domain.model.ExpenseCalc
import app.duofuel.gasoline.domain.model.FuelEntryCalc
import app.duofuel.gasoline.domain.model.FuelType
import app.duofuel.gasoline.domain.model.InputMethod
import app.duofuel.gasoline.domain.model.OdometerKind
import app.duofuel.gasoline.domain.model.ServiceRecordCalc
import app.duofuel.gasoline.domain.model.ServiceScheduleCalc
import kotlinx.coroutines.flow.Flow

data class VehicleProfile(
    val id: String,
    val name: String,
    val make: String?,
    val model: String?,
    val year: Int?,
    val engine: String?,
    val injectionType: String?,
    val gasFuelType: FuelType?,
    val gboGeneration: String?,
    val gboManufacturer: String?,
    val gboEquipmentModel: String?,
    val installedAtMs: Long?,
    val installCostMinor: Long?,
    val installCostCurrency: String?,
    val usefulVolumePercent: Double?,
    val switchTempC: Double?,
    val archivedAtMs: Long?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class FuelConfigProfile(
    val id: String,
    val fuelType: FuelType,
    val unit: String,
    val capacity: Double?,
    val currency: String,
    val defaultPriceMinor: Long?,
)

data class VehicleSnapshot(
    val vehicle: VehicleProfile,
    val fuels: List<FuelConfigProfile>,
    val entries: List<FuelEntryCalc>,
    val odometer: List<app.duofuel.gasoline.domain.model.OdometerEventCalc>,
    val serviceRecords: List<ServiceRecordCalc>,
    val schedules: List<ServiceScheduleCalc>,
    val expenses: List<ExpenseCalc>,
    val settings: CalculationSettingsCalc,
    val prefillFull: Boolean,
    val remindersEnabled: Boolean,
    val archived: List<VehicleProfile>,
) {
    fun toInput(): CalculationInput = CalculationInput(
        vehicleId = vehicle.id,
        installedAtMs = vehicle.installedAtMs,
        installCostMinor = vehicle.installCostMinor,
        installCostCurrency = vehicle.installCostCurrency,
        configuredFuels = fuels.map { it.fuelType }.toSet(),
        fuelEntries = entries,
        odometerEvents = odometer,
        serviceRecords = serviceRecords,
        schedules = schedules,
        expenses = expenses,
        settings = settings,
    )
}

data class FuelEntryDraft(
    val id: String?,
    val vehicleId: String,
    val fuelType: FuelType,
    val occurredAtMs: Long,
    val odometerKm: Int,
    val inputMethod: InputMethod,
    val odometerKind: OdometerKind,
    val quantity: Double,
    val unit: String,
    val unitPriceMinor: Long?,
    val totalCostMinor: Long,
    val currency: String,
    val isFull: Boolean,
    val station: String?,
    val notes: String?,
    val receiptUri: String?,
    val extraAttachmentUris: List<String>,
)

data class AppState(
    val active: VehicleSnapshot?,
    val archived: List<VehicleProfile>,
    val prefillFull: Boolean,
    val remindersEnabled: Boolean,
)

interface GasoLineRepository {
    fun observeApp(): Flow<AppState>
    suspend fun saveVehicle(profile: VehicleProfile, fuels: List<FuelConfigProfile>, settings: CalculationSettingsCalc)
    suspend fun archiveVehicle(id: String)
    suspend fun restoreVehicle(id: String)
    suspend fun deleteVehicle(id: String)
    suspend fun saveFuel(draft: FuelEntryDraft): String
    suspend fun deleteFuel(id: String)
    suspend fun undoLastFuelSave(): Boolean
    suspend fun saveService(record: ServiceRecordCalc, provider: String?, notes: String?)
    suspend fun saveSchedule(schedule: ServiceScheduleCalc)
    suspend fun saveExpense(expense: ExpenseCalc, notes: String?)
    suspend fun deleteExpense(id: String)
    suspend fun deleteService(id: String)
    suspend fun setPrefillFull(isFull: Boolean)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun exportCsvZip(): ByteArray
    suspend fun exportArchive(includeAttachments: Boolean): ByteArray
    suspend fun previewArchive(bytes: ByteArray): ArchivePreview
    suspend fun applyArchive(bytes: ByteArray)
}

data class ArchiveConflict(val id: String, val entity: String)

data class ArchivePreview(
    val schemaVersion: Int,
    val exportedAt: String,
    val checksumMatches: Boolean,
    val conflicts: List<ArchiveConflict>,
    val newRowCount: Int,
)
