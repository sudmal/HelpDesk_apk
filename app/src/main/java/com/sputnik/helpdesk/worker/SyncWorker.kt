package com.sputnik.helpdesk.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.R
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.MaterialEntity
import com.sputnik.helpdesk.data.db.TicketEntity
import com.sputnik.helpdesk.data.db.toEntity
import com.sputnik.helpdesk.data.model.ConnectionRequest
import com.sputnik.helpdesk.data.model.Ticket
import com.sputnik.helpdesk.ui.connections.ConnectionDetailActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.ui.detail.DetailActivity
import com.sputnik.helpdesk.ui.main.MainActivity

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.isLoggedIn) return Result.success()

        return try {
            val api      = RetrofitClient.create(prefs)
            val response = api.getTickets()
            val db       = AppDatabase.get(applicationContext)
            val gson     = Gson()

            // Flush pending comments before syncing
            val pending = db.pendingCommentDao().getAll()
            for (pc in pending) {
                try {
                    val plain = "text/plain".toMediaType()
                    val fields = mapOf("body" to pc.body.toRequestBody(plain))
                    api.addComment(pc.ticketId, fields, emptyList())
                    db.pendingCommentDao().deleteById(pc.id)
                } catch (_: Exception) { /* keep for next sync */ }
            }

            val newTickets = response.new_today
            val newIds     = newTickets.map { it.id }.toSet()
            val allToday   = (response.today + newTickets).distinctBy { it.id }

            val groups = mapOf(
                "overdue"  to response.overdue,
                "today"    to allToday,
                "tomorrow" to response.tomorrow,
            )
            groups.forEach { (group, tickets) ->
                val existingTimes = db.ticketDao().getByGroup(group).associate { it.id to it.cachedAt }
                db.ticketDao().deleteByGroup(group)
                db.ticketDao().insertAll(tickets.map {
                    it.toEntity(group, gson, it.id in newIds, existingTimes[it.id])
                })
            }
            db.ticketDao().deleteByGroup("new_today")

            val threshold = System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000
            db.ticketDao().deleteOlderThan(threshold)

            // Sync materials catalog
            try {
                val materials = api.getMaterials()
                db.materialDao().deleteAll()
                db.materialDao().insertAll(materials.map { m ->
                    MaterialEntity(m.id, m.code, m.name, m.unit, m.price)
                })
            } catch (_: Exception) { }

            // Badge count = total overdue + today tickets
            val badgeCount = response.overdue.size + allToday.size

            // Notify only once per ticket per day
            val alreadyNotified = prefs.getNotifiedTicketIds()
            val trulyNew = newTickets.filter { it.id !in alreadyNotified }
            if (trulyNew.isNotEmpty()) {
                prefs.addNotifiedTicketIds(trulyNew.map { it.id }.toSet())
                showNotification(trulyNew, badgeCount)
            }

            // Sync connection requests
            try {
                syncConnectionRequests(api.getConnectionRequests(perPage = 100).data)
            } catch (_: Exception) { }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncConnectionRequests(fetched: List<ConnectionRequest>) {
        val db = AppDatabase.get(applicationContext)
        val prefs = Prefs(applicationContext)

        val existingIds = db.connectionRequestDao().getAllIds().toSet()
        val alreadyNotified = prefs.getNotifiedConnectionIds()

        val trulyNew = fetched.filter { it.id !in existingIds && it.id !in alreadyNotified && it.status == "pending" }

        val entities = fetched.map { cr ->
            cr.toEntity(isNew = cr.id !in existingIds && cr.id !in alreadyNotified)
        }
        db.connectionRequestDao().deleteAll()
        db.connectionRequestDao().insertAll(entities)

        if (trulyNew.isNotEmpty()) {
            prefs.addNotifiedConnectionIds(trulyNew.map { it.id }.toSet())
            showConnectionNotification(trulyNew)
        }
    }

    private fun showConnectionNotification(items: List<ConnectionRequest>) {
        val count = items.size
        val intent = if (count == 1) {
            Intent(applicationContext, ConnectionDetailActivity::class.java).apply {
                putExtra(ConnectionDetailActivity.EXTRA_ID, items.first().id)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count == 1) "Новая заявка на подключение: ${items.first().name}"
                   else "Новых заявок на подключение: $count"
        val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Подключения")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(2, notification)
    }

    private fun showNotification(trulyNew: List<Ticket>, badgeCount: Int) {
        val count = trulyNew.size
        val intent = if (count == 1) {
            Intent(applicationContext, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_TICKET_ID, trulyNew.first().id)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count == 1) "Добавлена 1 новая заявка" else "Добавлено новых заявок: $count"
        val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Новые заявки на сегодня")
            .setContentText(text)
            .setNumber(badgeCount)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(1, notification)
    }
}

private fun Ticket.toEntity(
    group: String,
    gson: Gson,
    isNew: Boolean = false,
    existingCachedAt: Long? = null,
) = TicketEntity(
    id               = id,
    number           = number,
    createdAt        = created_at,
    scheduledAt      = scheduled_at,
    closedAt         = closed_at,
    closedBy         = closed_by,
    description      = description,
    phone            = phone,
    apartment        = apartment,
    closeNotes       = close_notes,
    actNumber        = act_number,
    actId            = act?.id,
    actStatus        = act?.status,
    actMaterialsChangedAt = act?.materials_changed_at,
    addressFull      = address?.full,
    typeName         = type,
    serviceTypeName  = service_type?.name,
    serviceTypeColor = service_type?.color,
    statusName       = status?.name,
    statusColor      = status?.color,
    statusIsFinal    = status?.is_final ?: false,
    statusSlug       = status?.slug,
    brigade          = brigade,
    assignee         = assignee,
    commentsJson     = gson.toJson(comments),
    group            = group,
    isNew            = isNew,
    cachedAt         = existingCachedAt ?: System.currentTimeMillis(),
)
