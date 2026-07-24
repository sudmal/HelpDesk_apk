package com.sputnik.helpdesk.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalDate

class Prefs(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "helpdesk_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(v) = prefs.edit().putString(KEY_TOKEN, v).apply()

    var userName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(v) = prefs.edit().putString(KEY_NAME, v).apply()

    var userRole: String?
        get() = prefs.getString(KEY_ROLE, null)
        set(v) = prefs.edit().putString(KEY_ROLE, v).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(v) = prefs.edit().putString(KEY_SERVER_URL, v).apply()

    var selectedServiceTypeName: String?
        get() = prefs.getString(KEY_SERVICE_TYPE_FILTER, null)
        set(v) = prefs.edit().putString(KEY_SERVICE_TYPE_FILTER, v).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL, 15)
        set(v) = prefs.edit().putInt(KEY_SYNC_INTERVAL, v).apply()

    var sortOrder: String
        get() = prefs.getString(KEY_SORT_ORDER, SORT_BY_TIME) ?: SORT_BY_TIME
        set(v) = prefs.edit().putString(KEY_SORT_ORDER, v).apply()

    var batteryOptimizationAsked: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_OPT_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_BATTERY_OPT_ASKED, v).apply()

    /** How many hours a closed/rejected connection request stays visible in the list. */
    var closedConnectionsRetentionHours: Int
        get() = prefs.getInt(KEY_CLOSED_CONN_RETENTION_HOURS, DEFAULT_CLOSED_CONN_RETENTION_HOURS)
        set(v) = prefs.edit().putInt(KEY_CLOSED_CONN_RETENTION_HOURS, v).apply()

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()

    /** Returns ticket IDs already notified today; empty set if date has rolled over. */
    fun getNotifiedTicketIds(): Set<Int> {
        val storedDate = prefs.getString(KEY_NOTIFIED_DATE, null)
        if (storedDate != LocalDate.now().toString()) return emptySet()
        val raw = prefs.getString(KEY_NOTIFIED_IDS, "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    /** Merges the given IDs into the today-notified set and persists it. */
    fun addNotifiedTicketIds(ids: Set<Int>) {
        val all = getNotifiedTicketIds() + ids
        prefs.edit()
            .putString(KEY_NOTIFIED_DATE, LocalDate.now().toString())
            .putString(KEY_NOTIFIED_IDS, all.joinToString(","))
            .apply()
    }

    var connectionTerritoriesJson: String
        get() = prefs.getString(KEY_CONN_TERRITORIES, "") ?: ""
        set(v) = prefs.edit().putString(KEY_CONN_TERRITORIES, v).apply()

    fun getNotifiedConnectionIds(): Set<Int> {
        val storedDate = prefs.getString(KEY_CONN_NOTIFIED_DATE, null)
        if (storedDate != LocalDate.now().toString()) return emptySet()
        val raw = prefs.getString(KEY_CONN_NOTIFIED_IDS, "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun addNotifiedConnectionIds(ids: Set<Int>) {
        val all = getNotifiedConnectionIds() + ids
        prefs.edit()
            .putString(KEY_CONN_NOTIFIED_DATE, LocalDate.now().toString())
            .putString(KEY_CONN_NOTIFIED_IDS, all.joinToString(","))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_TOKEN               = "token"
        private const val KEY_NAME                = "name"
        private const val KEY_ROLE                = "role"
        private const val KEY_SERVER_URL          = "server_url"
        private const val KEY_SERVICE_TYPE_FILTER = "service_type_filter"
        private const val KEY_NOTIFIED_IDS        = "notified_ticket_ids"
        private const val KEY_NOTIFIED_DATE       = "notified_ticket_date"
        private const val KEY_SYNC_INTERVAL       = "sync_interval"
        private const val KEY_SORT_ORDER          = "sort_order"
        private const val KEY_BATTERY_OPT_ASKED    = "battery_opt_asked"
        private const val KEY_CONN_TERRITORIES     = "conn_territories"
        private const val KEY_CONN_NOTIFIED_IDS   = "conn_notified_ids"
        private const val KEY_CONN_NOTIFIED_DATE  = "conn_notified_date"
        private const val KEY_CLOSED_CONN_RETENTION_HOURS = "closed_conn_retention_hours"
        const val DEFAULT_SERVER_URL              = "https://vega8.ru"
        const val SORT_BY_TIME                    = "time"
        const val SORT_BY_ADDRESS                 = "address"
        const val SORT_BY_SERVICE                 = "service"
        const val DEFAULT_CLOSED_CONN_RETENTION_HOURS = 24
    }
}
