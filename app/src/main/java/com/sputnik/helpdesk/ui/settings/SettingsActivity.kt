package com.sputnik.helpdesk.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivitySettingsBinding
import com.sputnik.helpdesk.util.CrashLogger
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private val syncIntervalOptions = listOf(15, 30, 60)
    private val syncIntervalLabels  = listOf("15 минут", "30 минут", "1 час")

    private val sortOptions = listOf(Prefs.SORT_BY_TIME, Prefs.SORT_BY_ADDRESS, Prefs.SORT_BY_SERVICE)
    private val sortLabels  = listOf("По времени", "По адресу", "По типу участка")

    private val closedConnRetentionOptions = listOf(6, 12, 24, 72, 168)
    private val closedConnRetentionLabels  = listOf("6 часов", "12 часов", "1 сутки", "3 суток", "7 суток")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "Настройки"
            setDisplayHomeAsUpEnabled(true)
        }

        prefs = Prefs(this)

        setupSyncIntervalSpinner()
        setupSortOrderSpinner()
        setupClosedConnRetentionSpinner()
        loadCurrentValues()

        binding.btnSaveSettings.setOnClickListener { saveSettings() }

        binding.btnClearCache.setOnClickListener {
            lifecycleScope.launch {
                val db = AppDatabase.get(this@SettingsActivity)
                listOf("overdue", "today", "tomorrow").forEach {
                    db.ticketDao().deleteByGroup(it)
                }
                Toast.makeText(this@SettingsActivity, "Кеш очищен", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyCrashLog.setOnClickListener {
            val log = CrashLogger.getLog(this)
            if (log.isEmpty()) {
                Toast.makeText(this, "Журнал пуст", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", log))
            Toast.makeText(this, "Журнал скопирован", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearCrashLog.setOnClickListener {
            CrashLogger.clearLog(this)
            binding.tvCrashLog.text = "Ошибок нет"
            Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show()
        }

        loadCrashLog()
        showVersion()
    }

    private fun showVersion() {
        val pi = packageManager.getPackageInfo(packageName, 0)
        binding.tvVersion.text = "Версия ${pi.versionName} (сборка ${pi.versionCode})"
    }

    private fun setupSyncIntervalSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, syncIntervalLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSyncInterval.adapter = adapter
    }

    private fun setupSortOrderSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSortOrder.adapter = adapter
    }

    private fun setupClosedConnRetentionSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, closedConnRetentionLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerClosedConnRetention.adapter = adapter
    }

    private fun loadCurrentValues() {
        val intervalIdx = syncIntervalOptions.indexOf(prefs.syncIntervalMinutes).coerceAtLeast(0)
        binding.spinnerSyncInterval.setSelection(intervalIdx)

        val sortIdx = sortOptions.indexOf(prefs.sortOrder).coerceAtLeast(0)
        binding.spinnerSortOrder.setSelection(sortIdx)

        val retentionIdx = closedConnRetentionOptions.indexOf(prefs.closedConnectionsRetentionHours).coerceAtLeast(0)
        binding.spinnerClosedConnRetention.setSelection(retentionIdx)

        binding.etServerUrl.setText(prefs.serverUrl)
    }

    private fun saveSettings() {
        val intervalIdx = binding.spinnerSyncInterval.selectedItemPosition
        prefs.syncIntervalMinutes = syncIntervalOptions.getOrElse(intervalIdx) { 15 }

        val sortIdx = binding.spinnerSortOrder.selectedItemPosition
        prefs.sortOrder = sortOptions.getOrElse(sortIdx) { Prefs.SORT_BY_TIME }

        val retentionIdx = binding.spinnerClosedConnRetention.selectedItemPosition
        prefs.closedConnectionsRetentionHours = closedConnRetentionOptions.getOrElse(retentionIdx) { Prefs.DEFAULT_CLOSED_CONN_RETENTION_HOURS }

        val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
        if (url.isNotEmpty()) prefs.serverUrl = url

        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
    }

    private fun loadCrashLog() {
        val log = CrashLogger.getLog(this)
        binding.tvCrashLog.text = log.ifEmpty { "Ошибок нет" }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
