package app.duofuel.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.duofuel.domain.repository.DuoFuelRepository

@Database(
    entities = [
        VehicleEntity::class,
        FuelTypeConfigEntity::class,
        FuelEntryEntity::class,
        FuelLevelMeasurementEntity::class,
        OdometerEventEntity::class,
        ServiceRecordEntity::class,
        ServiceScheduleEntity::class,
        ExpenseEntryEntity::class,
        CalculationSettingsEntity::class,
        AttachmentEntity::class,
        AggregateCacheEntity::class,
        UserPrefsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DuoFuelDatabase : RoomDatabase() {
    abstract fun dao(): DuoFuelDao

    companion object {
        const val NAME = "duofuel.db"

        // NFR-05: destructive migration is intentionally not enabled.
        val openCallback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}

fun createDuoFuelRepository(context: Context): DuoFuelRepository {
    val database = Room.databaseBuilder(context, DuoFuelDatabase::class.java, DuoFuelDatabase.NAME)
        .addCallback(DuoFuelDatabase.openCallback)
        .build()
    return DuoFuelRepositoryImpl(database.dao(), context)
}
