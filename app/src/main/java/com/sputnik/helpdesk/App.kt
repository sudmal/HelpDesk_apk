package com.sputnik.helpdesk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.sputnik.helpdesk.util.CrashLogger
import com.sputnik.helpdesk.worker.SyncWorker
import java.util.concurrent.TimeUnit

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        try {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            createNotificationChannel()
            scheduleSyncWorker()
        } catch (t: Throwable) {
            // Never let startup wiring (notification channel / WorkManager) crash-loop the app.
            CrashLogger.log(this, "App.onCreate init failed", t)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Новые заявки",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых заявках на сегодня"
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ticket_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val CHANNEL_ID = "helpdesk_tickets"

        // Emits Unit when the server returns 401; null means no pending event.
        val sessionExpiredEvent = MutableLiveData<Unit?>()

        // Emits Unit after a ticket is closed or rescheduled — triggers list sync in MainActivity.
        val ticketUpdatedEvent = MutableLiveData<Unit?>()

        // Emits Unit after a connection request is updated — triggers list sync.
        val connectionUpdatedEvent = MutableLiveData<Unit?>()

        // Emits Unit after an act is approved/acknowledged/edited — refreshes the acts cache + badge.
        val actUpdatedEvent = MutableLiveData<Unit?>()
    }
}
