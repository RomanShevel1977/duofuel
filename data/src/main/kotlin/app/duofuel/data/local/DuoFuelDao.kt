package app.duofuel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DuoFuelDao {
    @Query("SELECT * FROM vehicles WHERE archivedAt IS NULL ORDER BY createdAt LIMIT 1")
    fun observeActiveVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE archivedAt IS NOT NULL ORDER BY name")
    fun observeArchived(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun vehicle(id: String): VehicleEntity?

    @Query("SELECT * FROM vehicles")
    suspend fun allVehicles(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicle(entity: VehicleEntity)

    @Query("UPDATE vehicles SET archivedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun archive(id: String, at: Long)

    @Query("UPDATE vehicles SET archivedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: String)

    @Query("SELECT * FROM fuel_type_configs WHERE vehicleId = :vehicleId ORDER BY fuelType")
    fun observeFuels(vehicleId: String): Flow<List<FuelTypeConfigEntity>>

    @Query("SELECT * FROM fuel_type_configs WHERE vehicleId = :vehicleId")
    suspend fun fuels(vehicleId: String): List<FuelTypeConfigEntity>

    @Query("SELECT * FROM fuel_type_configs")
    suspend fun allFuels(): List<FuelTypeConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFuels(entities: List<FuelTypeConfigEntity>)

    @Query("DELETE FROM fuel_type_configs WHERE vehicleId = :vehicleId")
    suspend fun deleteFuels(vehicleId: String)

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY occurredAt, odometerKm, createdAt, id")
    fun observeFuel(vehicleId: String): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId")
    suspend fun fuel(vehicleId: String): List<FuelEntryEntity>

    @Query("SELECT * FROM fuel_entries")
    suspend fun allFuel(): List<FuelEntryEntity>

    @Query("SELECT * FROM fuel_entries WHERE id = :id")
    suspend fun fuelById(id: String): FuelEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFuel(entity: FuelEntryEntity)

    @Query("DELETE FROM fuel_entries WHERE id = :id")
    suspend fun deleteFuel(id: String)

    @Query("SELECT * FROM odometer_events WHERE vehicleId = :vehicleId ORDER BY occurredAt, odometerKm, createdAt, id")
    fun observeOdometer(vehicleId: String): Flow<List<OdometerEventEntity>>

    @Query("SELECT * FROM odometer_events WHERE vehicleId = :vehicleId")
    suspend fun odometer(vehicleId: String): List<OdometerEventEntity>

    @Query("SELECT * FROM odometer_events")
    suspend fun allOdometer(): List<OdometerEventEntity>

    @Query("SELECT * FROM odometer_events WHERE id = :id")
    suspend fun odometerById(id: String): OdometerEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOdometer(entity: OdometerEventEntity)

    @Query("DELETE FROM odometer_events WHERE linkedFuelEntryId = :fuelId")
    suspend fun deleteOdometerForFuel(fuelId: String)

    @Query("SELECT * FROM service_records WHERE vehicleId = :vehicleId ORDER BY occurredAt, odometerKm, createdAt, id")
    fun observeService(vehicleId: String): Flow<List<ServiceRecordEntity>>

    @Query("SELECT * FROM service_records WHERE vehicleId = :vehicleId")
    suspend fun service(vehicleId: String): List<ServiceRecordEntity>

    @Query("SELECT * FROM service_records")
    suspend fun allService(): List<ServiceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(entity: ServiceRecordEntity)

    @Query("DELETE FROM service_records WHERE id = :id")
    suspend fun deleteService(id: String)

    @Query("SELECT * FROM service_schedules WHERE vehicleId = :vehicleId")
    fun observeSchedules(vehicleId: String): Flow<List<ServiceScheduleEntity>>

    @Query("SELECT * FROM service_schedules WHERE vehicleId = :vehicleId")
    suspend fun schedules(vehicleId: String): List<ServiceScheduleEntity>

    @Query("SELECT * FROM service_schedules")
    suspend fun allSchedules(): List<ServiceScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(entity: ServiceScheduleEntity)

    @Query("SELECT * FROM expense_entries WHERE vehicleId = :vehicleId ORDER BY occurredAt, createdAt, id")
    fun observeExpenses(vehicleId: String): Flow<List<ExpenseEntryEntity>>

    @Query("SELECT * FROM expense_entries WHERE vehicleId = :vehicleId")
    suspend fun expenses(vehicleId: String): List<ExpenseEntryEntity>

    @Query("SELECT * FROM expense_entries")
    suspend fun allExpenses(): List<ExpenseEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(entity: ExpenseEntryEntity)

    @Query("DELETE FROM expense_entries WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT * FROM calculation_settings WHERE vehicleId = :vehicleId LIMIT 1")
    fun observeSettings(vehicleId: String): Flow<CalculationSettingsEntity?>

    @Query("SELECT * FROM calculation_settings WHERE vehicleId = :vehicleId LIMIT 1")
    suspend fun settings(vehicleId: String): CalculationSettingsEntity?

    @Query("SELECT * FROM calculation_settings")
    suspend fun allSettings(): List<CalculationSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(entity: CalculationSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(entity: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun attachments(ownerType: String, ownerId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    fun observeAttachments(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteAttachments(ownerType: String, ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(entity: AggregateCacheEntity)

    @Query("SELECT * FROM aggregate_cache WHERE vehicleId = :vehicleId")
    suspend fun cache(vehicleId: String): List<AggregateCacheEntity>

    @Query("SELECT * FROM user_prefs WHERE id = 'local'")
    fun observePrefs(): Flow<UserPrefsEntity?>

    @Query("SELECT * FROM user_prefs WHERE id = 'local'")
    suspend fun prefs(): UserPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrefs(entity: UserPrefsEntity)

    @Transaction
    suspend fun replaceFuels(vehicleId: String, fuels: List<FuelTypeConfigEntity>) {
        deleteFuels(vehicleId)
        upsertFuels(fuels)
    }
}
