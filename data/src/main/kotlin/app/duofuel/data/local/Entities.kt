package app.duofuel.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val make: String?,
    val model: String?,
    val year: Int?,
    val engine: String?,
    val injectionType: String?,
    val gasFuelType: String?,
    val gboGeneration: String?,
    val gboManufacturer: String?,
    val gboEquipmentModel: String?,
    val installedAt: Long?,
    val installCostMinor: Long?,
    val installCostCurrency: String?,
    val usefulVolumePercent: Double?,
    val switchTempC: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
    val source: String,
)

@Entity(
    tableName = "fuel_type_configs",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId", "fuelType", unique = true)],
)
data class FuelTypeConfigEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val fuelType: String,
    val unit: String,
    val capacity: Double?,
    val currency: String,
    val defaultPriceMinor: Long?,
)

@Entity(
    tableName = "fuel_entries",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("vehicleId", "occurredAt"),
        Index("vehicleId", "odometerKm"),
        Index("fuelType", "occurredAt"),
    ],
)
data class FuelEntryEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val fuelType: String,
    val occurredAt: Long,
    val odometerKm: Int,
    val quantity: Double, // OPEN-QUANTITY-TYPE: REAL, не целые минимальные единицы.
    val unit: String,
    val unitPriceMinor: Long?,
    val totalCostMinor: Long,
    val currency: String,
    val isFull: Boolean,
    val stationName: String?,
    val receiptUri: String?,
    val notes: String?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** OPEN-LEVEL: таблица зарезервирована. Экран остатка в MVP не строится. */
@Entity(
    tableName = "fuel_level_measurements",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId", "occurredAt"), Index("fuelType", "occurredAt")],
)
data class FuelLevelMeasurementEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val fuelType: String,
    val occurredAt: Long,
    val odometerKm: Int,
    val level: Double?,
    val unit: String,
    val isFull: Boolean,
    val confidence: Double?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "odometer_events",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId", "occurredAt"), Index("vehicleId", "odometerKm")],
)
data class OdometerEventEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val occurredAt: Long,
    val odometerKm: Int,
    val inputMethod: String,
    val kind: String,
    val source: String,
    val linkedFuelEntryId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "service_records",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId", "occurredAt"), Index("vehicleId", "odometerKm")],
)
data class ServiceRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val serviceType: String,
    val occurredAt: Long,
    val odometerKm: Int,
    val costMinor: Long,
    val currency: String,
    val provider: String?,
    val notes: String?,
    val isGbo: Boolean,
    val scheduleId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "service_schedules",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class ServiceScheduleEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val serviceType: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val dueOdometer: Int?,
    val dueDateEpochDay: Long?,
    val leadKm: Int?,
    val leadDays: Int?,
    val snoozeUntilEpochDay: Long?,
    val remindersEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "expense_entries",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId", "occurredAt")],
)
data class ExpenseEntryEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val category: String,
    val occurredAt: Long,
    val odometerKm: Int?,
    val amountMinor: Long,
    val currency: String,
    val includeInOperating: Boolean,
    val includeInGbo: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "calculation_settings",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId", unique = true)],
)
data class CalculationSettingsEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val baselinePetrolL100: Double?,
    val petrolPriceMinor: Long?,
    val petrolPriceCurrency: String?,
    val petrolPriceIsScenario: Boolean,
    val petrolModel: String?,
    val manualEstimate: String?,
    val baselineFromFactualHistory: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "attachments",
    indices = [Index("ownerType", "ownerId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val localUri: String,
    val mimeType: String?,
    val createdAt: Long,
)

@Entity(tableName = "aggregate_cache", indices = [Index("vehicleId")])
data class AggregateCacheEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val methodId: String,
    val formulaVersion: String,
    val quality: String,
    val periodKey: String,
    val updatedAt: Long,
)

@Entity(tableName = "user_prefs")
data class UserPrefsEntity(
    @PrimaryKey val id: String = "local",
    val prefillFull: Boolean,
    val remindersEnabled: Boolean,
)
