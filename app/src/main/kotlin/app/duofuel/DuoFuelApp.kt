package app.duofuel

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.duofuel.reminder.ServiceReminderWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class DuoFuelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<ServiceReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "duofuel-service",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
