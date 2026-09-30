package com.sputnik.helpdesk.ui.connections

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.ConnectionRequestEntity
import com.sputnik.helpdesk.data.db.toEntity
import com.sputnik.helpdesk.data.model.CRTerritory
import com.sputnik.helpdesk.data.model.ConnectionRequest
import com.sputnik.helpdesk.data.model.ConnectionRequestsResponse
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.FragmentConnectionsBinding
import com.sputnik.helpdesk.ui.main.MainActivity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ConnectionsFragment : Fragment() {

    private var _binding: FragmentConnectionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConnectionAdapter
    private var allItems = listOf<ConnectionRequestEntity>()
    private var selectedTerritoryId: Int? = null
    private var selectedSubTab: String = SUB_TAB_APPROVED
    private var hasLoadedOnce = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ConnectionAdapter { entity ->
            startActivity(Intent(requireContext(), ConnectionDetailActivity::class.java).apply {
                putExtra(ConnectionDetailActivity.EXTRA_ID, entity.id)
            })
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.tabLayoutConnStatus.addTab(binding.tabLayoutConnStatus.newTab().setText(LABEL_APPROVED))
        binding.tabLayoutConnStatus.addTab(binding.tabLayoutConnStatus.newTab().setText(LABEL_WAITING))
        binding.tabLayoutConnStatus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedSubTab = if (tab.position == 0) SUB_TAB_APPROVED else SUB_TAB_WAITING
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.swipeRefresh.setOnRefreshListener { syncFromServer() }

        // Populate territory chips from cache
        val prefs = Prefs(requireContext())
        populateChips(parseTerritories(prefs.connectionTerritoriesJson))

        // Observe DB
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.get(requireContext()).connectionRequestDao().getAllFlow()
                    .collect { items ->
                        allItems = items
                        applyFilter()
                        if (!hasLoadedOnce) {
                            hasLoadedOnce = true
                            binding.progressInitial.visibility = View.GONE
                        }
                    }
            }
        }

        binding.progressInitial.visibility = View.VISIBLE
        syncFromServer()
    }

    private fun populateChips(territories: List<CRTerritory>) {
        binding.chipGroupTerritories.removeAllViews()

        val chipAll = Chip(requireContext()).apply {
            text = "Все"
            isCheckable = true
            isChecked = selectedTerritoryId == null
            setOnClickListener { selectedTerritoryId = null; applyFilter() }
        }
        binding.chipGroupTerritories.addView(chipAll)

        territories.forEach { t ->
            val chip = Chip(requireContext()).apply {
                text = t.name
                isCheckable = true
                isChecked = selectedTerritoryId == t.id
                setOnClickListener { selectedTerritoryId = t.id; applyFilter() }
            }
            binding.chipGroupTerritories.addView(chip)
        }
    }

    private fun applyFilter() {
        val prefs = Prefs(requireContext())
        val retentionHours = prefs.closedConnectionsRetentionHours
        val cutoff = Instant.now().minus(retentionHours.toLong(), ChronoUnit.HOURS)

        // Items visible in the app at all: rejected and "impossible" feasibility answers
        // disappear immediately (that's operator/portal territory now), closed items stick
        // around for the configured retention window, everything else (pending/scheduled) stays.
        val visible = allItems.filter { item ->
            when {
                item.status == "rejected" -> false
                item.feasibility == "impossible" -> false
                item.status == "closed" -> {
                    val updatedInstant = parseToInstant(item.updatedAt)
                    updatedInstant == null || !updatedInstant.isBefore(cutoff)
                }
                else -> true
            }
        }

        val byTerritory = if (selectedTerritoryId != null) {
            visible.filter { it.territoryId == selectedTerritoryId }
        } else visible

        val waiting  = byTerritory.filter { it.status == "pending" }
            .sortedBy { it.feasibility != "possible" } // «на согласовании» — выше просто ожидающих
        val approved = byTerritory.filter { it.status == "scheduled" || it.status == "closed" }
            .sortedBy { it.status == "closed" } // выполненные — в самый низ, остальной порядок стабилен
        updateSubTabLabels(waiting.size, approved.size)

        val shown = if (selectedSubTab == SUB_TAB_WAITING) waiting else approved
        adapter.submitList(shown)
        binding.layoutEmpty.visibility = if (shown.isEmpty() && hasLoadedOnce) View.VISIBLE else View.GONE
        (activity as? MainActivity)?.updateTabCount("connections", waiting.size + approved.size)
    }

    private fun updateSubTabLabels(waitingCount: Int, approvedCount: Int) {
        binding.tabLayoutConnStatus.getTabAt(0)?.text =
            LABEL_APPROVED + (if (approvedCount > 0) " ($approvedCount)" else "")
        binding.tabLayoutConnStatus.getTabAt(1)?.text =
            LABEL_WAITING + (if (waitingCount > 0) " ($waitingCount)" else "")
    }

    private fun parseToInstant(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant() }
            .getOrNull()
    }

    private fun syncFromServer() {
        val prefs = Prefs(requireContext())
        if (!prefs.isLoggedIn) { binding.swipeRefresh.isRefreshing = false; return }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(prefs)

                // Default filter (active + recently closed) can span more than one page across
                // the whole company — a single page (см. «⚠️ Баг на Android» в API_MOBILE.md)
                // silently dropped older scheduled requests from the cache. Pull every page.
                val allItems = mutableListOf<ConnectionRequest>()
                var page = 1
                lateinit var response: ConnectionRequestsResponse
                do {
                    response = api.getConnectionRequests(perPage = 100, page = page)
                    allItems += response.data
                    page++
                } while (page <= response.last_page)

                // Save territories to prefs for CreateSheet and chip population
                prefs.connectionTerritoriesJson = Gson().toJson(response.territories)
                populateChips(response.territories)

                saveToCache(allItems)
            } catch (_: Exception) {
                _binding?.let {
                    Snackbar.make(it.root, "Нет соединения, показаны кешированные данные", Snackbar.LENGTH_SHORT).show()
                }
            } finally {
                _binding?.swipeRefresh?.isRefreshing = false
                _binding?.progressInitial?.visibility = View.GONE
            }
        }
    }

    private suspend fun saveToCache(items: List<ConnectionRequest>) {
        val db = AppDatabase.get(requireContext())
        val existingIds = db.connectionRequestDao().getAllIds().toSet()
        val prefs = Prefs(requireContext())
        val alreadyNotified = prefs.getNotifiedConnectionIds()

        val entities = items.map { cr ->
            cr.toEntity(isNew = cr.id !in existingIds && cr.id !in alreadyNotified)
        }
        db.connectionRequestDao().deleteAll()
        db.connectionRequestDao().insertAll(entities)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SUB_TAB_WAITING  = "waiting"
        private const val SUB_TAB_APPROVED = "approved"
        private const val LABEL_WAITING    = "Ожидающие"
        private const val LABEL_APPROVED   = "Утверждённые"

        fun newInstance() = ConnectionsFragment()
    }
}

private fun parseTerritories(json: String): List<CRTerritory> =
    if (json.isBlank()) emptyList()
    else runCatching {
        Gson().fromJson<List<CRTerritory>>(json, object : TypeToken<List<CRTerritory>>() {}.type)
    }.getOrDefault(emptyList())
