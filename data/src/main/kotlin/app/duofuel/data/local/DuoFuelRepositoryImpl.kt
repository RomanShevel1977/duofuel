package app.duofuel.data.local

import android.content.Context
import app.duofuel.domain.calculator.DashboardCalculator
import app.duofuel.domain.calculator.PeriodRequest
import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FormulaVersion
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.repository.AppState
import app.duofuel.domain.repository.ArchiveConflict
import app.duofuel.domain.repository.ArchivePreview
import app.duofuel.domain.repository.FuelConfigProfile
import app.duofuel.domain.repository.FuelEntryDraft
import app.duofuel.domain.repository.DuoFuelRepository
import app.duofuel.domain.repository.VehicleProfile
import app.duofuel.domain.repository.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DuoFuelRepositoryImpl(
    private val dao: DuoFuelDao,
    private val context: Context,
) : DuoFuelRepository {
    private val lastFuelInsert = AtomicReference<String?>(null)

    override fun observeApp(): Flow<AppState> {
        return combine(dao.observeActiveVehicle(), dao.observeArchived(), dao.observePrefs()) { vehicle, archived, prefs ->
            Triple(vehicle, archived, prefs)
        }.flatMapLatest { (vehicle, archived, prefs) ->
            val archivedProfiles = archived.map { it.toProfile() }
            val prefill = prefs?.prefillFull ?: false
            val reminders = prefs?.remindersEnabled ?: false
            if (vehicle == null) {
                flowOf(AppState(null, archivedProfiles, prefill, reminders))
            } else {
                val head = combine(
                    dao.observeFuels(vehicle.id),
                    dao.observeFuel(vehicle.id),
                    dao.observeOdometer(vehicle.id),
                    dao.observeService(vehicle.id),
                    dao.observeSchedules(vehicle.id),
                ) { fuels, entries, odometer, service, schedules ->
                    listOf(fuels, entries, odometer, service, schedules)
                }
                combine(head, dao.observeExpenses(vehicle.id), dao.observeSettings(vehicle.id)) { parts, expenses, settings ->
                    val fuels = parts[0] as List<FuelTypeConfigEntity>
                    val entries = parts[1] as List<FuelEntryEntity>
                    val odometer = parts[2] as List<OdometerEventEntity>
                    val service = parts[3] as List<ServiceRecordEntity>
                    val schedules = parts[4] as List<ServiceScheduleEntity>
                    AppState(
                        active = VehicleSnapshot(
                            vehicle = vehicle.toProfile(),
                            fuels = fuels.map { it.toProfile() },
                            entries = entries.map { it.toCalc() },
                            odometer = odometer.map { it.toCalc() },
                            serviceRecords = service.map { it.toCalc() },
                            schedules = schedules.map { it.toCalc() },
                            expenses = expenses.map { it.toCalc() },
                            settings = settings.toCalc(),
                            prefillFull = prefill,
                            remindersEnabled = reminders,
                            archived = archivedProfiles,
                        ),
                        archived = archivedProfiles,
                        prefillFull = prefill,
                        remindersEnabled = reminders,
                    )
                }
            }
        }
    }

    override suspend fun saveVehicle(
        profile: VehicleProfile,
        fuels: List<FuelConfigProfile>,
        settings: CalculationSettingsCalc,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val created = if (profile.createdAtMs == 0L) now else profile.createdAtMs
        val vehicleId = profile.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsertVehicle(
            VehicleEntity(
                id = vehicleId,
                name = profile.name.trim(),
                make = profile.make,
                model = profile.model,
                year = profile.year,
                engine = profile.engine,
                injectionType = profile.injectionType,
                gasFuelType = profile.gasFuelType?.name,
                gboGeneration = profile.gboGeneration,
                gboManufacturer = profile.gboManufacturer,
                gboEquipmentModel = profile.gboEquipmentModel,
                installedAt = profile.installedAtMs,
                installCostMinor = profile.installCostMinor,
                installCostCurrency = profile.installCostCurrency,
                usefulVolumePercent = profile.usefulVolumePercent,
                switchTempC = profile.switchTempC,
                createdAt = created,
                updatedAt = now,
                archivedAt = profile.archivedAtMs,
                source = "MANUAL",
            ),
        )
        dao.replaceFuels(
            vehicleId,
            fuels.map { fuel ->
                FuelTypeConfigEntity(
                    id = fuel.id.ifBlank { UUID.randomUUID().toString() },
                    vehicleId = vehicleId,
                    fuelType = fuel.fuelType.name,
                    unit = fuel.unit,
                    capacity = fuel.capacity,
                    currency = fuel.currency,
                    defaultPriceMinor = fuel.defaultPriceMinor,
                )
            },
        )
        val existing = dao.settings(vehicleId)
        dao.upsertSettings(
            CalculationSettingsEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                baselinePetrolL100 = settings.baselinePetrolL100,
                petrolPriceMinor = settings.petrolPriceMinor,
                petrolPriceCurrency = settings.petrolPriceCurrency,
                petrolPriceIsScenario = settings.petrolPriceIsScenario,
                petrolModel = null,
                manualEstimate = null,
                baselineFromFactualHistory = settings.baselineFromFactualHistory,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        refreshCache(vehicleId)
    }

    override suspend fun archiveVehicle(id: String) = withContext(Dispatchers.IO) {
        dao.archive(id, System.currentTimeMillis())
    }

    override suspend fun restoreVehicle(id: String) = withContext(Dispatchers.IO) {
        dao.restore(id, System.currentTimeMillis())
    }

    override suspend fun deleteVehicle(id: String) = withContext(Dispatchers.IO) {
        dao.deleteVehicle(id)
    }

    override suspend fun saveFuel(draft: FuelEntryDraft): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.fuelById(it) }
        val id = existing?.id ?: draft.id ?: UUID.randomUUID().toString()
        dao.upsertFuel(
            FuelEntryEntity(
                id = id,
                vehicleId = draft.vehicleId,
                fuelType = draft.fuelType.name,
                occurredAt = draft.occurredAtMs,
                odometerKm = draft.odometerKm,
                quantity = draft.quantity,
                unit = draft.unit,
                unitPriceMinor = draft.unitPriceMinor,
                totalCostMinor = draft.totalCostMinor,
                currency = draft.currency,
                isFull = draft.isFull,
                stationName = draft.station,
                receiptUri = draft.receiptUri,
                notes = draft.notes,
                source = "MANUAL",
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        dao.deleteOdometerForFuel(id)
        dao.upsertOdometer(
            OdometerEventEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = draft.vehicleId,
                occurredAt = draft.occurredAtMs,
                odometerKm = draft.odometerKm,
                inputMethod = draft.inputMethod.name,
                kind = draft.odometerKind.name,
                source = "MANUAL",
                linkedFuelEntryId = id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val receipt = draft.receiptUri
        if (receipt != null) {
            dao.deleteAttachments("FUEL", id)
            dao.upsertAttachment(
                AttachmentEntity(
                    id = UUID.randomUUID().toString(),
                    ownerType = "FUEL",
                    ownerId = id,
                    localUri = receipt,
                    mimeType = "image/*",
                    createdAt = now,
                ),
            )
        }
        draft.extraAttachmentUris.forEach { uri ->
            if (uri != draft.receiptUri) {
                dao.upsertAttachment(
                    AttachmentEntity(UUID.randomUUID().toString(), "FUEL", id, uri, "image/*", now),
                )
            }
        }
        if (existing == null) lastFuelInsert.set(id)
        refreshCache(draft.vehicleId)
        id
    }

    override suspend fun deleteFuel(id: String) = withContext(Dispatchers.IO) {
        val row = dao.fuelById(id) ?: return@withContext
        dao.deleteOdometerForFuel(id)
        dao.deleteAttachments("FUEL", id)
        dao.deleteFuel(id)
        refreshCache(row.vehicleId)
    }

    // OPEN-UNDO: отменяется только последняя вставка заправки, не вся история.
    override suspend fun undoLastFuelSave(): Boolean = withContext(Dispatchers.IO) {
        val id = lastFuelInsert.getAndSet(null) ?: return@withContext false
        deleteFuel(id)
        true
    }

    override suspend fun saveService(record: ServiceRecordCalc, provider: String?, notes: String?) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.upsertService(
                ServiceRecordEntity(
                    id = record.id.ifBlank { UUID.randomUUID().toString() },
                    vehicleId = record.vehicleId,
                    serviceType = record.serviceType,
                    occurredAt = record.occurredAtMs,
                    odometerKm = record.odometerKm,
                    costMinor = record.costMinor,
                    currency = record.currency,
                    provider = provider ?: record.provider,
                    notes = notes ?: record.notes,
                    isGbo = record.isGbo,
                    scheduleId = record.scheduleId,
                    createdAt = record.createdAtMs.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                ),
            )
            refreshCache(record.vehicleId)
        }

    override suspend fun saveSchedule(schedule: ServiceScheduleCalc) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsertSchedule(
            ServiceScheduleEntity(
                id = schedule.id.ifBlank { UUID.randomUUID().toString() },
                vehicleId = schedule.vehicleId,
                serviceType = schedule.serviceType,
                intervalKm = schedule.intervalKm,
                intervalDays = schedule.intervalDays,
                dueOdometer = schedule.dueOdometerKm,
                dueDateEpochDay = schedule.dueDateEpochDay,
                leadKm = schedule.leadKm,
                leadDays = schedule.leadDays,
                snoozeUntilEpochDay = schedule.snoozeUntilEpochDay,
                remindersEnabled = schedule.remindersEnabled,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun saveExpense(expense: ExpenseCalc, notes: String?) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsertExpense(
            ExpenseEntryEntity(
                id = expense.id.ifBlank { UUID.randomUUID().toString() },
                vehicleId = expense.vehicleId,
                category = expense.category,
                occurredAt = expense.occurredAtMs,
                odometerKm = expense.odometerKm,
                amountMinor = expense.amountMinor,
                currency = expense.currency,
                includeInOperating = expense.includeInOperating,
                includeInGbo = expense.includeInGbo,
                notes = notes ?: expense.notes,
                createdAt = expense.createdAtMs.takeIf { it > 0 } ?: now,
                updatedAt = now,
            ),
        )
        refreshCache(expense.vehicleId)
    }

    override suspend fun deleteExpense(id: String) = withContext(Dispatchers.IO) {
        val row = dao.allExpenses().firstOrNull { it.id == id } ?: return@withContext
        dao.deleteExpense(id)
        refreshCache(row.vehicleId)
    }

    override suspend fun deleteService(id: String) = withContext(Dispatchers.IO) {
        val row = dao.allService().firstOrNull { it.id == id } ?: return@withContext
        dao.deleteService(id)
        refreshCache(row.vehicleId)
    }

    override suspend fun setPrefillFull(isFull: Boolean) = withContext(Dispatchers.IO) {
        val current = dao.prefs()
        dao.upsertPrefs(UserPrefsEntity(prefillFull = isFull, remindersEnabled = current?.remindersEnabled ?: false))
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = dao.prefs()
        dao.upsertPrefs(UserPrefsEntity(prefillFull = current?.prefillFull ?: false, remindersEnabled = enabled))
    }

    override suspend fun exportCsvZip(): ByteArray = withContext(Dispatchers.IO) {
        CsvExport.zip(loadBundle())
    }

    override suspend fun exportArchive(includeAttachments: Boolean): ByteArray = withContext(Dispatchers.IO) {
        ArchiveCodec.pack(loadBundle(), includeAttachments, Instant.now().toString())
    }

    override suspend fun previewArchive(bytes: ByteArray): ArchivePreview = withContext(Dispatchers.IO) {
        val payload = ArchiveCodec.read(bytes) ?: return@withContext ArchivePreview(0, "", false, emptyList(), 0)
        val matches = ArchiveCodec.checksumMatches(payload.manifest, payload.dataBytes)
        val current = loadBundle()
        val conflicts = conflicts(current, payload.data)
        val incoming = countRows(payload.data)
        ArchivePreview(
            schemaVersion = payload.manifest.optInt("schemaVersion"),
            exportedAt = payload.manifest.optString("exportedAt"),
            checksumMatches = matches,
            conflicts = conflicts,
            newRowCount = incoming,
        )
    }

    override suspend fun applyArchive(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val payload = ArchiveCodec.read(bytes) ?: error("archive")
        check(ArchiveCodec.checksumMatches(payload.manifest, payload.dataBytes))
        // OPEN-IMPORT, OPEN-BACKUP: перед заменой пишем резервную копию. Конфликт UUID не перезаписывается.
        val backupDir = context.filesDir.resolve("archives").apply { mkdirs() }
        backupDir.resolve("pre-restore-${System.currentTimeMillis()}.zip")
            .writeBytes(exportArchive(includeAttachments = true))
        val current = loadBundle()
        val conflictIds = conflicts(current, payload.data).map { it.id }.toSet()
        insertMissing(current, payload.data, conflictIds)
    }

    private suspend fun loadBundle(): ExportBundle = ExportBundle(
        vehicles = dao.allVehicles(),
        fuels = dao.allFuels(),
        fuel = dao.allFuel(),
        odometer = dao.allOdometer(),
        service = dao.allService(),
        schedules = dao.allSchedules(),
        expenses = dao.allExpenses(),
        settings = dao.allSettings(),
        attachments = dao.allAttachments(),
    )

    private suspend fun refreshCache(vehicleId: String) {
        val vehicle = dao.vehicle(vehicleId) ?: return
        val settings = dao.settings(vehicleId).toCalc()
        val input = app.duofuel.domain.model.CalculationInput(
            vehicleId = vehicleId,
            installedAtMs = vehicle.installedAt,
            installCostMinor = vehicle.installCostMinor,
            installCostCurrency = vehicle.installCostCurrency,
            configuredFuels = dao.fuels(vehicleId).map { app.duofuel.domain.model.FuelType.valueOf(it.fuelType) }.toSet(),
            fuelEntries = dao.fuel(vehicleId).map { it.toCalc() },
            odometerEvents = dao.odometer(vehicleId).map { it.toCalc() },
            serviceRecords = dao.service(vehicleId).map { it.toCalc() },
            schedules = dao.schedules(vehicleId).map { it.toCalc() },
            expenses = dao.expenses(vehicleId).map { it.toCalc() },
            settings = settings,
        )
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val report = DashboardCalculator().calculate(input, PeriodRequest.AllHistory, zone, today, System.currentTimeMillis())
        val rows = listOf(report.cashflow.total, report.fuelCostPerKm, report.saving.net, report.balance)
        rows.forEach { metric ->
            dao.upsertCache(
                AggregateCacheEntity(
                    id = "$vehicleId:${metric.methodId}:${metric.scope ?: "none"}",
                    vehicleId = vehicleId,
                    methodId = metric.methodId,
                    formulaVersion = FormulaVersion.CURRENT,
                    quality = metric.quality.name,
                    periodKey = metric.period.labelKey,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun conflicts(current: ExportBundle, incoming: JSONObject): List<ArchiveConflict> {
        val found = mutableListOf<ArchiveConflict>()
        fun check(entity: String, currentIds: Map<String, String>, rows: JSONArray?) {
            if (rows == null) return
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getString("id")
                val canonical = row.toString()
                val existing = currentIds[id] ?: continue
                if (existing != canonical) found += ArchiveConflict(id, entity)
            }
        }
        check("vehicle", current.vehicles.associate { it.id to vehicleJsonPublic(it) }, incoming.optJSONArray("vehicles"))
        check("fuel", current.fuel.associate { it.id to fuelJsonPublic(it) }, incoming.optJSONArray("fuelEntries"))
        return found
    }

    private suspend fun insertMissing(current: ExportBundle, data: JSONObject, conflictIds: Set<String>) {
        val vehicles = data.optJSONArray("vehicles")
        if (vehicles != null) {
            for (index in 0 until vehicles.length()) {
                val row = vehicles.getJSONObject(index)
                if (row.getString("id") in conflictIds) continue
                if (dao.vehicle(row.getString("id")) != null) continue
                dao.upsertVehicle(row.toVehicle())
            }
        }
        val fuels = data.optJSONArray("fuels")
        if (fuels != null) {
            val entities = (0 until fuels.length()).map { fuels.getJSONObject(it).toFuelConfig() }
                .filter { dao.vehicle(it.vehicleId) != null }
            if (entities.isNotEmpty()) dao.upsertFuels(entities)
        }
        val fuelEntries = data.optJSONArray("fuelEntries")
        if (fuelEntries != null) {
            for (index in 0 until fuelEntries.length()) {
                val row = fuelEntries.getJSONObject(index)
                if (row.getString("id") in conflictIds || dao.fuelById(row.getString("id")) != null) continue
                dao.upsertFuel(row.toFuel())
            }
        }
        val knownOdo = current.odometer.map { it.id }.toSet()
        val odometer = data.optJSONArray("odometer")
        if (odometer != null) {
            for (index in 0 until odometer.length()) {
                val row = odometer.getJSONObject(index)
                if (row.getString("id") in knownOdo || row.getString("id") in conflictIds) continue
                dao.upsertOdometer(row.toOdometer())
            }
        }
        val knownService = current.service.map { it.id }.toSet()
        val service = data.optJSONArray("service")
        if (service != null) {
            for (index in 0 until service.length()) {
                val row = service.getJSONObject(index)
                if (row.getString("id") in knownService) continue
                dao.upsertService(row.toService())
            }
        }
        val knownSchedules = current.schedules.map { it.id }.toSet()
        val schedules = data.optJSONArray("schedules")
        if (schedules != null) {
            for (index in 0 until schedules.length()) {
                val row = schedules.getJSONObject(index)
                if (row.getString("id") in knownSchedules) continue
                dao.upsertSchedule(row.toSchedule())
            }
        }
        val knownExpenses = current.expenses.map { it.id }.toSet()
        val expenses = data.optJSONArray("expenses")
        if (expenses != null) {
            for (index in 0 until expenses.length()) {
                val row = expenses.getJSONObject(index)
                if (row.getString("id") in knownExpenses) continue
                dao.upsertExpense(row.toExpense())
            }
        }
        val knownSettings = current.settings.map { it.id }.toSet()
        val settings = data.optJSONArray("settings")
        if (settings != null) {
            for (index in 0 until settings.length()) {
                val row = settings.getJSONObject(index)
                if (row.getString("id") in knownSettings) continue
                dao.upsertSettings(row.toSettings())
            }
        }
    }
}

private fun countRows(data: JSONObject): Int =
    listOf("vehicles", "fuels", "fuelEntries", "odometer", "service", "schedules", "expenses", "settings")
        .sumOf { data.optJSONArray(it)?.length() ?: 0 }

private fun vehicleJsonPublic(it: VehicleEntity): String = it.id + "|" + it.name + "|" + it.updatedAt
private fun fuelJsonPublic(it: FuelEntryEntity): String =
    it.id + "|" + it.quantity + "|" + it.odometerKm + "|" + it.totalCostMinor + "|" + it.updatedAt

private fun JSONObject.toVehicle() = VehicleEntity(
    id = getString("id"),
    name = getString("name"),
    make = optNullableString("make"),
    model = optNullableString("model"),
    year = optNullableInt("year"),
    engine = optNullableString("engine"),
    injectionType = optNullableString("injectionType"),
    gasFuelType = optNullableString("gasFuelType"),
    gboGeneration = optNullableString("gboGeneration"),
    gboManufacturer = optNullableString("gboManufacturer"),
    gboEquipmentModel = optNullableString("gboEquipmentModel"),
    installedAt = optNullableLong("installedAt"),
    installCostMinor = optNullableLong("installCostMinor"),
    installCostCurrency = optNullableString("installCostCurrency"),
    usefulVolumePercent = optNullableDouble("usefulVolumePercent"),
    switchTempC = optNullableDouble("switchTempC"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
    archivedAt = optNullableLong("archivedAt"),
    source = optString("source", "MANUAL"),
)

private fun JSONObject.toFuelConfig() = FuelTypeConfigEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    fuelType = getString("fuelType"),
    unit = getString("unit"),
    capacity = optNullableDouble("capacity"),
    currency = getString("currency"),
    defaultPriceMinor = optNullableLong("defaultPriceMinor"),
)

private fun JSONObject.toFuel() = FuelEntryEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    fuelType = getString("fuelType"),
    occurredAt = getLong("occurredAt"),
    odometerKm = getInt("odometerKm"),
    quantity = getDouble("quantity"),
    unit = getString("unit"),
    unitPriceMinor = optNullableLong("unitPriceMinor"),
    totalCostMinor = getLong("totalCostMinor"),
    currency = getString("currency"),
    isFull = getBoolean("isFull"),
    stationName = optNullableString("stationName"),
    receiptUri = optNullableString("receiptUri"),
    notes = optNullableString("notes"),
    source = optString("source", "MANUAL"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.toOdometer() = OdometerEventEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    occurredAt = getLong("occurredAt"),
    odometerKm = getInt("odometerKm"),
    inputMethod = getString("inputMethod"),
    kind = getString("kind"),
    source = optString("source", "MANUAL"),
    linkedFuelEntryId = optNullableString("linkedFuelEntryId"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.toService() = ServiceRecordEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    serviceType = getString("serviceType"),
    occurredAt = getLong("occurredAt"),
    odometerKm = getInt("odometerKm"),
    costMinor = getLong("costMinor"),
    currency = getString("currency"),
    provider = optNullableString("provider"),
    notes = optNullableString("notes"),
    isGbo = getBoolean("isGbo"),
    scheduleId = optNullableString("scheduleId"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.toSchedule() = ServiceScheduleEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    serviceType = getString("serviceType"),
    intervalKm = optNullableInt("intervalKm"),
    intervalDays = optNullableInt("intervalDays"),
    dueOdometer = optNullableInt("dueOdometer"),
    dueDateEpochDay = optNullableLong("dueDateEpochDay"),
    leadKm = optNullableInt("leadKm"),
    leadDays = optNullableInt("leadDays"),
    snoozeUntilEpochDay = optNullableLong("snoozeUntilEpochDay"),
    remindersEnabled = optBoolean("remindersEnabled", false),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.toExpense() = ExpenseEntryEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    category = getString("category"),
    occurredAt = getLong("occurredAt"),
    odometerKm = optNullableInt("odometerKm"),
    amountMinor = getLong("amountMinor"),
    currency = getString("currency"),
    includeInOperating = optBoolean("includeInOperating", false),
    includeInGbo = optBoolean("includeInGbo", false),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.toSettings() = CalculationSettingsEntity(
    id = getString("id"),
    vehicleId = getString("vehicleId"),
    baselinePetrolL100 = optNullableDouble("baselinePetrolL100"),
    petrolPriceMinor = optNullableLong("petrolPriceMinor"),
    petrolPriceCurrency = optNullableString("petrolPriceCurrency"),
    petrolPriceIsScenario = optBoolean("petrolPriceIsScenario", true),
    petrolModel = optNullableString("petrolModel"),
    manualEstimate = optNullableString("manualEstimate"),
    baselineFromFactualHistory = optBoolean("baselineFromFactualHistory", false),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
