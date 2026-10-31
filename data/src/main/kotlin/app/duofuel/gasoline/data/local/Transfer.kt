package app.duofuel.gasoline.data.local

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object CsvExport {
    fun zip(snapshot: ExportBundle): ByteArray = zipOf(
        mapOf(
            "profile.csv" to profile(snapshot),
            "fuel_type_configs.csv" to fuels(snapshot),
            "fuel_entries.csv" to fuelEntries(snapshot),
            "odometer_events.csv" to odometer(snapshot),
            "service_records.csv" to service(snapshot),
            "service_schedules.csv" to schedules(snapshot),
            "expense_entries.csv" to expenses(snapshot),
            "calculation_settings.csv" to settings(snapshot),
        ),
    )

    private fun profile(bundle: ExportBundle): String = table(
        listOf(
            "id", "name", "make", "model", "year", "engine", "injection_type",
            "gas_fuel_type", "gbo_generation", "gbo_manufacturer", "gbo_model",
            "installed_at", "install_cost_minor", "install_cost_currency",
            "useful_volume_percent", "switch_temp_c", "created_at", "updated_at", "archived_at",
        ),
        bundle.vehicles.map { vehicle ->
            listOf(
                vehicle.id, vehicle.name, vehicle.make, vehicle.model, vehicle.year, vehicle.engine,
                vehicle.injectionType, vehicle.gasFuelType, vehicle.gboGeneration, vehicle.gboManufacturer,
                vehicle.gboEquipmentModel, iso(vehicle.installedAt), vehicle.installCostMinor,
                vehicle.installCostCurrency, plain(vehicle.usefulVolumePercent), plain(vehicle.switchTempC),
                iso(vehicle.createdAt), iso(vehicle.updatedAt), iso(vehicle.archivedAt),
            )
        },
    )

    private fun fuels(bundle: ExportBundle): String = table(
        listOf("id", "vehicle_id", "fuel_type", "unit", "capacity", "currency", "default_price_minor"),
        bundle.fuels.map {
            listOf(it.id, it.vehicleId, it.fuelType, it.unit, plain(it.capacity), it.currency, it.defaultPriceMinor)
        },
    )

    private fun fuelEntries(bundle: ExportBundle): String = table(
        listOf(
            "id", "vehicle_id", "fuel_type", "occurred_at", "odometer_km", "quantity", "unit",
            "unit_price_minor", "total_cost_minor", "currency", "is_full", "station_name", "notes",
            "source", "created_at", "updated_at",
        ),
        bundle.fuel.map {
            listOf(
                it.id, it.vehicleId, it.fuelType, iso(it.occurredAt), it.odometerKm, plain(it.quantity), it.unit,
                it.unitPriceMinor, it.totalCostMinor, it.currency, if (it.isFull) 1 else 0, it.stationName, it.notes,
                it.source, iso(it.createdAt), iso(it.updatedAt),
            )
        },
    )

    private fun odometer(bundle: ExportBundle): String = table(
        listOf("id", "vehicle_id", "occurred_at", "odometer_km", "input_method", "kind", "source", "linked_fuel_entry_id", "created_at"),
        bundle.odometer.map {
            listOf(it.id, it.vehicleId, iso(it.occurredAt), it.odometerKm, it.inputMethod, it.kind, it.source, it.linkedFuelEntryId, iso(it.createdAt))
        },
    )

    private fun service(bundle: ExportBundle): String = table(
        listOf("id", "vehicle_id", "service_type", "occurred_at", "odometer_km", "cost_minor", "currency", "provider", "notes", "is_gbo", "schedule_id", "created_at"),
        bundle.service.map {
            listOf(it.id, it.vehicleId, it.serviceType, iso(it.occurredAt), it.odometerKm, it.costMinor, it.currency, it.provider, it.notes, if (it.isGbo) 1 else 0, it.scheduleId, iso(it.createdAt))
        },
    )

    private fun schedules(bundle: ExportBundle): String = table(
        listOf("id", "vehicle_id", "service_type", "interval_km", "interval_days", "due_odometer", "due_date_epoch_day", "lead_km", "lead_days", "snooze_until_epoch_day", "reminders_enabled"),
        bundle.schedules.map {
            listOf(it.id, it.vehicleId, it.serviceType, it.intervalKm, it.intervalDays, it.dueOdometer, it.dueDateEpochDay, it.leadKm, it.leadDays, it.snoozeUntilEpochDay, if (it.remindersEnabled) 1 else 0)
        },
    )

    private fun expenses(bundle: ExportBundle): String = table(
        listOf("id", "vehicle_id", "category", "occurred_at", "odometer_km", "amount_minor", "currency", "include_in_operating", "include_in_gbo", "notes", "created_at"),
        bundle.expenses.map {
            listOf(it.id, it.vehicleId, it.category, iso(it.occurredAt), it.odometerKm, it.amountMinor, it.currency, if (it.includeInOperating) 1 else 0, if (it.includeInGbo) 1 else 0, it.notes, iso(it.createdAt))
        },
    )

    private fun settings(bundle: ExportBundle): String = table(
        listOf("id", "vehicle_id", "baseline_petrol_l100", "petrol_price_minor", "petrol_price_currency", "petrol_price_is_scenario", "baseline_from_factual_history"),
        bundle.settings.map {
            listOf(it.id, it.vehicleId, plain(it.baselinePetrolL100), it.petrolPriceMinor, it.petrolPriceCurrency, if (it.petrolPriceIsScenario) 1 else 0, if (it.baselineFromFactualHistory) 1 else 0)
        },
    )
}

internal data class ExportBundle(
    val vehicles: List<VehicleEntity>,
    val fuels: List<FuelTypeConfigEntity>,
    val fuel: List<FuelEntryEntity>,
    val odometer: List<OdometerEventEntity>,
    val service: List<ServiceRecordEntity>,
    val schedules: List<ServiceScheduleEntity>,
    val expenses: List<ExpenseEntryEntity>,
    val settings: List<CalculationSettingsEntity>,
    val attachments: List<AttachmentEntity>,
)

internal object ArchiveCodec {
    const val SCHEMA_VERSION = 1

    fun pack(bundle: ExportBundle, includeAttachments: Boolean, exportedAt: String): ByteArray {
        val data = dataJson(bundle).toString()
        val checksum = sha256(data.toByteArray(Charsets.UTF_8))
        val manifest = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", exportedAt)
            .put("app", "GasoLine")
            .put("checksumSha256", checksum)
            .put("includesAttachments", includeAttachments)
        val files = mutableMapOf(
            "manifest.json" to manifest.toString().toByteArray(),
            "data.json" to data.toByteArray(),
        )
        if (includeAttachments) {
            bundle.attachments.forEach { attachment ->
                files["attachments/${attachment.id}.uri"] = attachment.localUri.toByteArray()
            }
        }
        return zipOfBytes(files)
    }

    fun read(bytes: ByteArray): ArchivePayload? {
        val files = unzip(bytes)
        val manifestBytes = files["manifest.json"] ?: return null
        val dataBytes = files["data.json"] ?: return null
        return ArchivePayload(
            manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8)),
            data = JSONObject(dataBytes.toString(Charsets.UTF_8)),
            dataBytes = dataBytes,
        )
    }

    fun checksumMatches(manifest: JSONObject, dataBytes: ByteArray): Boolean {
        val expected = manifest.optString("checksumSha256")
        return expected == sha256(dataBytes)
    }

    private fun dataJson(bundle: ExportBundle): JSONObject = obj(
        "vehicles" to JSONArray(bundle.vehicles.map { vehicleJson(it) }),
        "fuels" to JSONArray(bundle.fuels.map { fuelConfigJson(it) }),
        "fuelEntries" to JSONArray(bundle.fuel.map { fuelJson(it) }),
        "odometer" to JSONArray(bundle.odometer.map { odoJson(it) }),
        "service" to JSONArray(bundle.service.map { serviceJson(it) }),
        "schedules" to JSONArray(bundle.schedules.map { scheduleJson(it) }),
        "expenses" to JSONArray(bundle.expenses.map { expenseJson(it) }),
        "settings" to JSONArray(bundle.settings.map { settingsJson(it) }),
        "attachments" to JSONArray(bundle.attachments.map { attachmentJson(it) }),
    )

    private fun vehicleJson(it: VehicleEntity) = obj(
        "id" to it.id, "name" to it.name, "make" to it.make, "model" to it.model,
        "year" to it.year, "engine" to it.engine, "injectionType" to it.injectionType,
        "gasFuelType" to it.gasFuelType, "gboGeneration" to it.gboGeneration,
        "gboManufacturer" to it.gboManufacturer, "gboEquipmentModel" to it.gboEquipmentModel,
        "installedAt" to it.installedAt, "installCostMinor" to it.installCostMinor,
        "installCostCurrency" to it.installCostCurrency, "usefulVolumePercent" to it.usefulVolumePercent,
        "switchTempC" to it.switchTempC, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
        "archivedAt" to it.archivedAt, "source" to it.source,
    )

    private fun fuelConfigJson(it: FuelTypeConfigEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "fuelType" to it.fuelType,
        "unit" to it.unit, "capacity" to it.capacity, "currency" to it.currency,
        "defaultPriceMinor" to it.defaultPriceMinor,
    )

    private fun fuelJson(it: FuelEntryEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "fuelType" to it.fuelType,
        "occurredAt" to it.occurredAt, "odometerKm" to it.odometerKm, "quantity" to it.quantity,
        "unit" to it.unit, "unitPriceMinor" to it.unitPriceMinor, "totalCostMinor" to it.totalCostMinor,
        "currency" to it.currency, "isFull" to it.isFull, "stationName" to it.stationName,
        "receiptUri" to it.receiptUri, "notes" to it.notes, "source" to it.source,
        "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
    )

    private fun odoJson(it: OdometerEventEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "occurredAt" to it.occurredAt,
        "odometerKm" to it.odometerKm, "inputMethod" to it.inputMethod, "kind" to it.kind,
        "source" to it.source, "linkedFuelEntryId" to it.linkedFuelEntryId,
        "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
    )

    private fun serviceJson(it: ServiceRecordEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "serviceType" to it.serviceType,
        "occurredAt" to it.occurredAt, "odometerKm" to it.odometerKm, "costMinor" to it.costMinor,
        "currency" to it.currency, "provider" to it.provider, "notes" to it.notes,
        "isGbo" to it.isGbo, "scheduleId" to it.scheduleId, "createdAt" to it.createdAt,
        "updatedAt" to it.updatedAt,
    )

    private fun scheduleJson(it: ServiceScheduleEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "serviceType" to it.serviceType,
        "intervalKm" to it.intervalKm, "intervalDays" to it.intervalDays,
        "dueOdometer" to it.dueOdometer, "dueDateEpochDay" to it.dueDateEpochDay,
        "leadKm" to it.leadKm, "leadDays" to it.leadDays,
        "snoozeUntilEpochDay" to it.snoozeUntilEpochDay, "remindersEnabled" to it.remindersEnabled,
        "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
    )

    private fun expenseJson(it: ExpenseEntryEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "category" to it.category,
        "occurredAt" to it.occurredAt, "odometerKm" to it.odometerKm, "amountMinor" to it.amountMinor,
        "currency" to it.currency, "includeInOperating" to it.includeInOperating,
        "includeInGbo" to it.includeInGbo, "notes" to it.notes,
        "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
    )

    private fun settingsJson(it: CalculationSettingsEntity) = obj(
        "id" to it.id, "vehicleId" to it.vehicleId, "baselinePetrolL100" to it.baselinePetrolL100,
        "petrolPriceMinor" to it.petrolPriceMinor, "petrolPriceCurrency" to it.petrolPriceCurrency,
        "petrolPriceIsScenario" to it.petrolPriceIsScenario, "petrolModel" to it.petrolModel,
        "manualEstimate" to it.manualEstimate, "baselineFromFactualHistory" to it.baselineFromFactualHistory,
        "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
    )

    private fun attachmentJson(it: AttachmentEntity) = obj(
        "id" to it.id, "ownerType" to it.ownerType, "ownerId" to it.ownerId,
        "localUri" to it.localUri, "mimeType" to it.mimeType, "createdAt" to it.createdAt,
    )
}

internal data class ArchivePayload(
    val manifest: JSONObject,
    val data: JSONObject,
    val dataBytes: ByteArray,
)

private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
    val json = JSONObject()
    pairs.forEach { (key, value) ->
        if (value == null) json.put(key, JSONObject.NULL) else json.put(key, value)
    }
    return json
}

internal fun iso(millis: Long?): String = millis?.let { Instant.ofEpochMilli(it).toString() } ?: ""

internal fun plain(value: Double?): String = value?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: ""

internal fun csvCell(value: Any?): String {
    if (value == null) return ""
    val text = value.toString()
    val escaped = text.replace("\"", "\"\"")
    return if (escaped.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

internal fun table(header: List<String>, rows: List<List<Any?>>): String = buildString {
    append(header.joinToString(";") { csvCell(it) })
    append('\n')
    rows.forEach { row ->
        append(row.joinToString(";") { csvCell(it) })
        append('\n')
    }
}

internal fun zipOf(files: Map<String, String>): ByteArray = zipOfBytes(files.mapValues { it.value.toByteArray(Charsets.UTF_8) })

internal fun zipOfBytes(files: Map<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

internal fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) result[entry.name] = zip.readBytes()
            entry = zip.nextEntry
        }
    }
    return result
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
