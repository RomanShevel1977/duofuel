package app.duofuel.gasoline.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duofuel.gasoline.domain.model.CalculationSettingsCalc
import app.duofuel.gasoline.domain.model.ExpenseCalc
import app.duofuel.gasoline.domain.model.FuelType
import app.duofuel.gasoline.domain.model.InputMethod
import app.duofuel.gasoline.domain.model.OdometerKind
import app.duofuel.gasoline.domain.model.ServiceRecordCalc
import app.duofuel.gasoline.domain.repository.FuelConfigProfile
import app.duofuel.gasoline.domain.repository.FuelEntryDraft
import app.duofuel.gasoline.domain.repository.VehicleProfile
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
class GasoLinePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun roundTrip_reopens_andCsvKeepsUnitsCurrencyTimestampAndVehicle() = runBlocking {
        val name = "gasoline-roundtrip.db"
        context.deleteDatabase(name)
        val first = open(name)
        val repo = GasoLineRepositoryImpl(first.dao(), context)
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
            val state = GasoLineRepositoryImpl(second.dao(), context).observeApp().first()
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

    private fun open(name: String): GasoLineDatabase =
        Room.databaseBuilder(context, GasoLineDatabase::class.java, name)
            .addCallback(GasoLineDatabase.openCallback)
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
