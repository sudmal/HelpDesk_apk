package com.sputnik.helpdesk.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.R
import com.sputnik.helpdesk.update.UpdateChecker
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.ui.connections.ConnectionsFragment
import com.sputnik.helpdesk.ui.schedule.ScheduleActivity
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.MaterialEntity
import com.sputnik.helpdesk.data.model.ServiceType
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityMainBinding
import com.sputnik.helpdesk.ui.login.LoginActivity
import com.sputnik.helpdesk.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var filterViewModel: FilterViewModel
    private lateinit var prefs: Prefs
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    private val serviceTypeNames = mutableListOf<String?>()

    private var lastKnownDate = java.time.LocalDate.now()
    private var lastUpdateCheckAt = 0L

    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateDateDisplay()
            filterViewModel.requestSync()
        }
    }

    private var spinnerTouched = false

    private val tabs = listOf(
        "Просрочено"  to "overdue",
        "Сегодня"     to "today",
        "Завтра"      to "tomorrow",
        "Подключения" to "connections",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        filterViewModel = ViewModelProvider(this)[FilterViewModel::class.java]
        filterViewModel.selectedServiceTypeName.value = prefs.selectedServiceTypeName
        filterViewModel.sortOrder.value = prefs.sortOrder

        App.sessionExpiredEvent.observe(this) { event ->
            event ?: return@observe
            App.sessionExpiredEvent.value = null
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        App.ticketUpdatedEvent.observe(this) { event ->
            event ?: return@observe
            App.ticketUpdatedEvent.value = null
            filterViewModel.requestSync()
        }

        App.connectionUpdatedEvent.observe(this) { event ->
            event ?: return@observe
            App.connectionUpdatedEvent.value = null
            // ConnectionsFragment observes DB via Flow, sync happens inside fragment
        }

        filterViewModel.lastSyncTime.observe(this) { time ->
            binding.tvLastSync.text = time ?: ""
        }

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = tabs.size
            override fun createFragment(position: Int): Fragment =
                if (tabs[position].second == "connections") ConnectionsFragment.newInstance()
                else TicketsFragment.newInstance(tabs[position].second)
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabs[pos].first
        }.attach()

        binding.viewPager.offscreenPageLimit = tabs.size
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.setCurrentItem(1, false)

        updateDateDisplay()

        binding.btnMenu.setOnClickListener { view ->
            PopupMenu(this, view).apply {
                menuInflater.inflate(R.menu.menu_main, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_schedule -> {
                            startActivity(Intent(this@MainActivity, ScheduleActivity::class.java))
                            true
                        }
                        R.id.action_settings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            true
                        }
                        R.id.action_about -> {
                            showAboutDialog()
                            true
                        }
                        R.id.action_logout -> {
                            prefs.clear()
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        setupSpinner()
        loadFromServer()
        checkBatteryOptimization()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(dateChangeReceiver, IntentFilter(Intent.ACTION_DATE_CHANGED))
    }

    override fun onResume() {
        super.onResume()

        filterViewModel.sortOrder.value = prefs.sortOrder

        val today = LocalDate.now()
        if (today != lastKnownDate) {
            lastKnownDate = today
            updateDateDisplay()
            filterViewModel.requestSync()
        }

        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckAt > 60 * 60_000L) {
            lastUpdateCheckAt = now
            UpdateChecker.check(this)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(dateChangeReceiver)
    }

    private fun updateDateDisplay() {
        binding.tvDate.text = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
    }

    private fun checkBatteryOptimization() {
        if (prefs.batteryOptimizationAsked) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        prefs.batteryOptimizationAsked = true
        AlertDialog.Builder(this)
            .setTitle("Оптимизация батареи")
            .setMessage("Для надёжной доставки уведомлений рекомендуется отключить оптимизацию батареи для этого приложения.")
            .setPositiveButton("Отключить") { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton("Пропустить", null)
            .show()
    }

    private fun setupSpinner() {
        serviceTypeNames.clear()
        serviceTypeNames.add(null)

        spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item_filter, mutableListOf("Все участки"))
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_filter)
        binding.spinnerServiceType.adapter = spinnerAdapter

        binding.spinnerServiceType.setOnTouchListener { _, _ ->
            spinnerTouched = true
            false
        }

        binding.spinnerServiceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnerTouched) return
                spinnerTouched = false
                val selected = serviceTypeNames.getOrNull(position)
                prefs.selectedServiceTypeName = selected
                filterViewModel.selectedServiceTypeName.value = selected
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                spinnerTouched = false
            }
        }
    }

    private fun loadFromServer() {
        lifecycleScope.launch {
            val api = RetrofitClient.create(prefs)

            var apiTypes: List<ServiceType> = emptyList()
            try {
                apiTypes = api.getServiceTypes()
            } catch (_: Exception) { }

            if (apiTypes.isNotEmpty()) {
                populateSpinnerFromNames(apiTypes.map { it.name })
            } else {
                val cached = AppDatabase.get(this@MainActivity).ticketDao().getDistinctServiceTypeNames()
                populateSpinnerFromNames(cached)
            }

            try {
                val materials = api.getMaterials()
                val db = AppDatabase.get(this@MainActivity)
                db.materialDao().deleteAll()
                db.materialDao().insertAll(materials.map { m ->
                    MaterialEntity(m.id, m.code, m.name, m.unit, m.price)
                })
            } catch (_: Exception) { }
        }
    }

    fun refreshSpinnerFromCache() {
        if (spinnerAdapter.count > 1) return
        lifecycleScope.launch {
            val names = AppDatabase.get(this@MainActivity).ticketDao().getDistinctServiceTypeNames()
            if (names.isNotEmpty()) populateSpinnerFromNames(names)
        }
    }

    private fun populateSpinnerFromNames(names: List<String>) {
        serviceTypeNames.clear()
        serviceTypeNames.add(null)
        serviceTypeNames.addAll(names)

        spinnerAdapter.clear()
        spinnerAdapter.add("Все участки")
        names.forEach { spinnerAdapter.add(it) }
        spinnerAdapter.notifyDataSetChanged()

        val saved = prefs.selectedServiceTypeName
        if (saved != null) {
            val idx = serviceTypeNames.indexOf(saved)
            if (idx >= 0) binding.spinnerServiceType.setSelection(idx, false)
        }
    }

    private fun showAboutDialog() {
        val pi = packageManager.getPackageInfo(packageName, 0)
        AlertDialog.Builder(this)
            .setTitle("HelpDesk")
            .setMessage("Версия ${pi.versionName} (${pi.versionCode})\n\nСервер: ${prefs.serverUrl}")
            .setPositiveButton("OK", null)
            .show()
    }

    fun setViewPagerSwipeEnabled(enabled: Boolean) {
        binding.viewPager.isUserInputEnabled = enabled
    }

    fun goToConnectionsTab() {
        val index = tabs.indexOfFirst { it.second == "connections" }
        if (index >= 0) binding.viewPager.setCurrentItem(index, false)
    }

    fun updateTabCount(group: String, count: Int) {
        val index = tabs.indexOfFirst { it.second == group }
        if (index < 0) return
        val label = tabs[index].first
        val tab = binding.tabLayout.getTabAt(index) ?: return
        if (count > 0) {
            val countPart = " ($count)"
            val full = label + countPart
            val span = SpannableString(full)
            if (group == "overdue" || group == "today") {
                span.setSpan(
                    ForegroundColorSpan(0xFFF97316.toInt()),  // orange
                    label.length, full.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            tab.text = span
        } else {
            tab.text = label
        }
    }
}
