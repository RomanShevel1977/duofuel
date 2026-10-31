package app.duofuel.gasoline.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.duofuel.gasoline.R
import app.duofuel.gasoline.domain.calculator.MaintenanceCostCalculator
import app.duofuel.gasoline.domain.model.ServiceDueStatus
import app.duofuel.gasoline.domain.repository.GasoLineRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class ServiceReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            ReminderEntryPoint::class.java,
        ).repository()
        val state = repository.observeApp().first()
        if (!state.remindersEnabled) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val active = state.active ?: return Result.success()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val statuses = MaintenanceCostCalculator().statuses(
            schedules = active.schedules,
            records = active.serviceRecords,
            odometer = active.odometer,
            today = today,
            zone = zone,
            nowMs = System.currentTimeMillis(),
        )
        val enabled = active.schedules.filter { it.remindersEnabled }.associateBy { it.id }
        val due = statuses.firstOrNull { status ->
            val schedule = enabled[status.scheduleId] ?: return@firstOrNull false
            val snooze = schedule.snoozeUntilEpochDay
            val snoozed = snooze != null && today.toEpochDay() < snooze
            !snoozed && status.status in setOf(ServiceDueStatus.DUE, ServiceDueStatus.OVERDUE, ServiceDueStatus.SOON)
        } ?: return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return Result.success()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    applicationContext.getString(R.string.settings_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        manager.notify(
            due.scheduleId.hashCode(),
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(due.serviceType)
                .setContentText(due.status.name)
                .build(),
        )
        return Result.success()
    }

    private companion object {
        const val CHANNEL = "gasoline-service"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun repository(): GasoLineRepository
}
