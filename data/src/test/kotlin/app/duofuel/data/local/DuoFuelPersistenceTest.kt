package app.duofuel.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.RecordSource
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.repository.FuelConfigProfile
import app.duofuel.domain.repository.FuelEntryDraft
import app.duofuel.domain.repository.VehicleProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DuoFuelPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun roundTrip_reopens_andCsvKeepsUnitsCurrencyTimestampAndVehicle() = runBlocking {
        val name = "duofuel-roundtrip.db"
        context.deleteDatabase(name)
        val first = open(name)
        val repo = DuoFuelRepositoryImpl(first.dao(), context)
        val vehicleId = "veh-1"
        val occurredAt = 1_700_000_000_000L
        repo.saveVehicle(
            profile(vehicleId, occurredAt),
            listOf(
                FuelConfigProfile("fuel-petrol", FuelType.PETROL, "L", 50.0, "UAH", 5_500),
                FuelConfigProfile("fuel-lpg", FuelType.LPG, "L", 42.0, "UAH", 2_700),
            ),
            CalculationSettingsCalc(8.5, 5_500, "UAH", petrolPriceIsScenario = true, baselineFromFactualHistory = false),
        )
        repo.saveFuel(
            FuelEntryDraft(
                id = "fill-1",
                vehicleId = vehicleId,
                fuelType = FuelType.LPG,
                occurredAtMs = occurredAt,
                odometerKm = 1_000,
                inputMethod = InputMethod.ABSOLUTE,
                odometerKind = OdometerKind.READING,
                quantity = 20.5,
                unit = "L",
                unitPriceMinor = 2_700,
                totalCostMinor = 55_350,
                currency = "UAH",
                isFull = true,
                station = "OKKO",
                notes = null,
                receiptUri = null,
                extraAttachmentUris = emptyList(),
            ),
        )
        repo.saveService(
            ServiceRecordCalc(
                id = "svc-1",
                vehicleId = vehicleId,
                serviceType = "liquid-filter",
                occurredAtMs = occurredAt,
                odometerKm = 1_000,
                costMinor = 80_000,
                currency = "UAH",
                isGbo = true,
                scheduleId = null,
                createdAtMs = occurredAt,
            ),
            provider = "Master",
            notes = null,
        )
        repo.saveExpense(
            ExpenseCalc(
                id = "exp-1",
                vehicleId = vehicleId,
                category = "parking",
                occurredAtMs = occurredAt,
                odometerKm = 1_000,
                amountMinor = 5_000,
                currency = "UAH",
                includeInOperating = false,
                includeInGbo = false,
                createdAtMs = occurredAt,
            ),
            notes = null,
        )

        val files = unzip(repo.exportCsvZip())
        val fuelCsv = files.getValue("fuel_entries.csv")
        assertTrue(fuelCsv.startsWith("id;vehicle_id;fuel_type;occurred_at;"))
        assertTrue(fuelCsv.contains(vehicleId))
        assertTrue(fuelCsv.contains(";20.5;L;"))
        assertTrue(fuelCsv.contains(";UAH;"))
        assertTrue(fuelCsv.contains(Instant.ofEpochMilli(occurredAt).toString()))
        assertTrue(files.getValue("profile.csv").contains("STAG"))
        assertTrue(files.getValue("profile.csv").contains(";4;"))
        first.close()

        val second = open(name)
        try {
            val state = DuoFuelRepositoryImpl(second.dao(), context).observeApp().first()
            val active = state.active!!
            assertEquals("Octavia", active.vehicle.name)
            assertEquals(FuelType.LPG, active.vehicle.gasFuelType)
            assertEquals("4", active.vehicle.gboGeneration)
            assertEquals("STAG", active.vehicle.gboManufacturer)
            assertEquals("QBOX", active.vehicle.gboEquipmentModel)
            assertEquals(12_000_00L, active.vehicle.installCostMinor)
            assertEquals("UAH", active.vehicle.installCostCurrency)
            assertEquals(20.5, active.entries.single().quantity, 0.0)
            assertEquals("L", active.entries.single().unit)
            assertEquals(1_000, active.odometer.single().odometerKm)
            assertEquals(true, active.serviceRecords.single().isGbo)
            assertEquals(false, active.expenses.single().includeInOperating)
            assertEquals(false, active.expenses.single().includeInGbo)
        } finally {
            second.close()
        }
    }

    @Test
    fun editFuel_keepsOneLinkedReading_andStandaloneOdometerIsSeparate() = runBlocking {
        val name = "duofuel-edit.db"
        context.deleteDatabase(name)
        val db = open(name)
        try {
            val repo = DuoFuelRepositoryImpl(db.dao(), context)
            val at = 1_700_000_000_000L
            repo.saveVehicle(
                profile("veh-edit", at),
                listOf(FuelConfigProfile("fuel-lpg", FuelType.LPG, "L", null, "UAH", null)),
                CalculationSettingsCalc(null, null, "UAH", true, false),
            )
            repo.saveFuel(
                FuelEntryDraft(
                    id = "fill-edit",
                    vehicleId = "veh-edit",
                    fuelType = FuelType.LPG,
                    occurredAtMs = at,
                    odometerKm = 100,
                    inputMethod = InputMethod.ABSOLUTE,
                    odometerKind = OdometerKind.READING,
                    quantity = 10.0,
                    unit = "L",
                    unitPriceMinor = 100,
                    totalCostMinor = 1_000,
                    currency = "UAH",
                    isFull = true,
                    station = null,
                    notes = null,
                    receiptUri = null,
                    extraAttachmentUris = emptyList(),
                ),
            )
            repo.saveFuel(
                FuelEntryDraft(
                    id = "fill-edit",
                    vehicleId = "veh-edit",
                    fuelType = FuelType.LPG,
                    occurredAtMs = at,
                    odometerKm = 100,
                    inputMethod = InputMethod.ABSOLUTE,
                    odometerKind = OdometerKind.READING,
                    quantity = 12.5,
                    unit = "L",
                    unitPriceMinor = 100,
                    totalCostMinor = 1_250,
                    currency = "UAH",
                    isFull = false,
                    station = null,
                    notes = null,
                    receiptUri = null,
                    extraAttachmentUris = emptyList(),
                ),
            )
            repo.saveOdometer(
                OdometerEventCalc(
                    id = "odo-reset",
                    vehicleId = "veh-edit",
                    occurredAtMs = at + 1,
                    odometerKm = 5,
                    kind = OdometerKind.RESET,
                    inputMethod = InputMethod.ABSOLUTE,
                    source = RecordSource.MANUAL,
                    linkedFuelEntryId = null,
                    createdAtMs = at,
                ),
            )
            val state = repo.observeApp().first()
            val active = state.active!!
            assertEquals(12.5, active.entries.single().quantity, 0.0)
            assertEquals(false, active.entries.single().isFull)
            assertEquals(1, active.odometer.count { it.linkedFuelEntryId == "fill-edit" })
            assertEquals(OdometerKind.RESET, active.odometer.single { it.id == "odo-reset" }.kind)
        } finally {
            db.close()
        }
    }

    @Test
    fun resave_keepsFuelConfig_andBackdatedService() = runBlocking {
        val name = "duofuel-profile.db"
        context.deleteDatabase(name)
        val db = open(name)
        try {
            val repo = DuoFuelRepositoryImpl(db.dao(), context)
            val at = 1_700_000_000_000L
            val fuels = listOf(
                FuelConfigProfile("fuel-petrol", FuelType.PETROL, "L", 50.0, "UAH", 5_500),
                FuelConfigProfile("fuel-lpg", FuelType.LPG, "L", 42.0, "UAH", 2_700),
            )
            val settings = CalculationSettingsCalc(null, null, "UAH", true, false)
            repo.saveVehicle(profile("veh-profile", at), fuels, settings)
            repo.saveVehicle(
                profile("veh-profile", at),
                listOf(
                    FuelConfigProfile("fuel-petrol", FuelType.PETROL, "L", 55.0, "UAH", 5_600),
                    FuelConfigProfile("fuel-lpg", FuelType.LPG, "L", 42.0, "EUR", 2_800),
                ),
                settings,
            )
            val past = at - 86_400_000L
            repo.saveService(
                ServiceRecordCalc(
                    id = "svc-back",
                    vehicleId = "veh-profile",
                    serviceType = "liquid-filter",
                    occurredAtMs = past,
                    odometerKm = 900,
                    costMinor = 10_000,
                    currency = "UAH",
                    isGbo = true,
                    scheduleId = "sch-1",
                    createdAtMs = at,
                    provider = "Master",
                    notes = "backdated",
                ),
                provider = "Master",
                notes = "backdated",
            )
            val schedule = ServiceScheduleCalc(
                id = "sch-1",
                vehicleId = "veh-profile",
                serviceType = "liquid-filter",
                intervalKm = 10_000,
                intervalDays = 180,
                dueOdometerKm = 10_900,
                dueDateEpochDay = 20_000,
                leadKm = 500,
                leadDays = 14,
                snoozeUntilEpochDay = 20_030,
                remindersEnabled = true,
            )
            repo.saveSchedule(schedule)
            val inserted = db.dao().schedules("veh-profile").single()
            db.dao().upsertSchedule(inserted.copy(createdAt = 111L, updatedAt = 111L))
            repo.saveSchedule(schedule.copy(leadDays = 21))
            val active = repo.observeApp().first().active!!
            val petrol = active.fuels.single { it.fuelType == FuelType.PETROL }
            val lpg = active.fuels.single { it.fuelType == FuelType.LPG }
            assertEquals("fuel-petrol", petrol.id)
            assertEquals(55.0, petrol.capacity!!, 0.0)
            assertEquals(5_600L, petrol.defaultPriceMinor)
            assertEquals("fuel-lpg", lpg.id)
            assertEquals("EUR", lpg.currency)
            assertEquals(2_800L, lpg.defaultPriceMinor)
            val work = active.serviceRecords.single()
            assertEquals(past, work.occurredAtMs)
            assertEquals("Master", work.provider)
            assertEquals("backdated", work.notes)
            val stored = active.schedules.single()
            assertEquals(21, stored.leadDays)
            assertEquals(20_030L, stored.snoozeUntilEpochDay)
            assertEquals(20_000L, stored.dueDateEpochDay)
            val row = db.dao().schedules("veh-profile").single()
            assertEquals(111L, row.createdAt)
            assertEquals(21, row.leadDays)
        } finally {
            db.close()
        }
    }

    private fun open(name: String): DuoFuelDatabase =
        Room.databaseBuilder(context, DuoFuelDatabase::class.java, name)
            .addCallback(DuoFuelDatabase.openCallback)
            .build()

    private fun profile(id: String, at: Long) = VehicleProfile(
        id = id,
        name = "Octavia",
        make = "Skoda",
        model = "A7",
        year = 2018,
        engine = "1.6",
        injectionType = "MPI",
        gasFuelType = FuelType.LPG,
        gboGeneration = "4",
        gboManufacturer = "STAG",
        gboEquipmentModel = "QBOX",
        installedAtMs = at,
        installCostMinor = 12_000_00,
        installCostCurrency = "UAH",
        usefulVolumePercent = 80.0,
        switchTempC = 30.0,
        archivedAtMs = null,
        createdAtMs = at,
        updatedAtMs = at,
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                out[entry.name] = zip.readBytes().decodeToString()
                entry = zip.nextEntry
            }
        }
        return out
    }
}
