package app.duofuel.gasoline.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.duofuel.gasoline.domain.repository.GasoLineRepository

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
abstract class GasoLineDatabase : RoomDatabase() {
    abstract fun dao(): GasoLineDao

    companion object {
        const val NAME = "gasoline.db"

        // NFR-05: destructive migration is intentionally not enabled.
        val openCallback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}

fun createGasoLineRepository(context: Context): GasoLineRepository {
    val database = Room.databaseBuilder(context, GasoLineDatabase::class.java, GasoLineDatabase.NAME)
        .addCallback(GasoLineDatabase.openCallback)
        .build()
    return GasoLineRepositoryImpl(database.dao(), context)
}
