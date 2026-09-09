package com.sputnik.helpdesk.ui.main

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.ConnectionRequestEntity
import com.sputnik.helpdesk.data.db.TicketEntity
import com.sputnik.helpdesk.data.db.dateGroup
import com.sputnik.helpdesk.data.model.CRTerritory
import com.sputnik.helpdesk.data.model.Ticket
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.FragmentTicketsBinding
import com.sputnik.helpdesk.ui.connections.ConnectionDetailActivity
import com.sputnik.helpdesk.ui.detail.CloseTicketSheet
import com.sputnik.helpdesk.ui.detail.DetailActivity
import com.sputnik.helpdesk.ui.detail.RescheduleSheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.content.Intent

@OptIn(ExperimentalCoroutinesApi::class)
class TicketsFragment : Fragment() {

    private var _binding: FragmentTicketsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TicketAdapter
    private lateinit var group: String
    private val filterViewModel: FilterViewModel by activityViewModels()
    private var cachedTerritories = listOf<CRTerritory>()
    private var hasLoadedOnce = false

    // Последние данные из Room — держим их, чтобы пересобрать ленту при смене
    // режима сортировки без ожидания нового эмита БД.
    private var latestTickets: List<TicketEntity> = emptyList()
    private var latestConnections: List<ConnectionRequestEntity> = emptyList()
    private var latestServiceFilter: String? = null
    private var latestTerritoryFilter: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTicketsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        group = arguments?.getString(ARG_GROUP) ?: "today"

        adapter = TicketAdapter(
            onTicketClick = { ticket ->
                startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
                    putExtra(DetailActivity.EXTRA_TICKET_ID, ticket.id)
                })
            },
            onConnectionClick = { conn ->
                startActivity(Intent(requireContext(), ConnectionDetailActivity::class.java).apply {
                    putExtra(ConnectionDetailActivity.EXTRA_ID, conn.id)
                })
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        setupSwipeGestures()

        // Pull-to-refresh syncs all tabs
        binding.swipeRefresh.setOnRefreshListener { filterViewModel.requestSync() }

        if (filterViewModel.selectedTerritoryId.value == null) {
            filterViewModel.selectedTerritoryId.value = Prefs(requireContext()).selectedTicketTerritoryId
        }
        populateTerritoryChips(parseTerritories(Prefs(requireContext()).connectionTerritoriesJson))

        // Другая вкладка могла сменить фильтр — держим подсветку чипов в этом инстансе в синхроне.
        filterViewModel.selectedTerritoryId.observe(viewLifecycleOwner) {
            populateTerritoryChips(cachedTerritories)
        }

        // Room Flow: авто-обновление ленты при изменении БД или фильтров.
        // Заявки и назначенные подключения одного дня сливаются в одну ленту.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    filterViewModel.selectedServiceTypeName.asFlow(),
                    filterViewModel.selectedTerritoryId.asFlow(),
                ) { filterName, territoryId -> filterName to territoryId }
                    .flatMapLatest { (filterName, territoryId) ->
                        combine(
                            AppDatabase.get(requireContext()).ticketDao()
                                .getByGroupFilteredFlow(group, filterName, territoryId),
                            AppDatabase.get(requireContext()).connectionRequestDao().getAllFlow(),
                        ) { tickets, connections ->
                            Triple(tickets, connections, filterName to territoryId)
                        }
                    }
                    .collect { (tickets, connections, filter) ->
                        latestTickets = tickets
                        latestConnections = connections
                        latestServiceFilter = filter.first
                        latestTerritoryFilter = filter.second
                        rebuildList()
                        if (!hasLoadedOnce) {
                            hasLoadedOnce = true
                            binding.progressInitial.visibility = View.GONE
                        }
                    }
            }
        }

        // Re-sort when sort order changes without DB change
        filterViewModel.sortOrder.observe(viewLifecycleOwner) { rebuildList() }

        // Trigger network sync
        filterViewModel.syncTrigger.observe(viewLifecycleOwner) {
            syncFromServer()
        }

        // Show loading indicator on first open
        binding.progressInitial.visibility = View.VISIBLE
        syncFromServer()
    }

    /** Собирает объединённую ленту дня из последних данных Room и текущего режима сортировки. */
    private fun rebuildList() {
        if (_binding == null) return
        showItems(buildDayList())
    }

    private fun buildDayList(): List<DayListItem> {
        val ticketItems = sortTickets(latestTickets).map { DayListItem.TicketItem(it) }

        // У подключения есть участок (service_type) — при активном фильтре по услуге
        // показываем только совпадающие; заявки без участка при фильтре скрываем.
        val connItems = latestConnections
            .filter { it.dateGroup() == group }
            .filter { latestTerritoryFilter == null || it.territoryId == latestTerritoryFilter }
            .filter { latestServiceFilter == null || it.serviceTypeName == latestServiceFilter }
            .map { DayListItem.ConnectionItem(it) }

        if (connItems.isEmpty()) return ticketItems

        return when (filterViewModel.sortOrder.value) {
            // «По адресу» — общий ключ есть у обоих типов, мешаем в одну ленту.
            Prefs.SORT_BY_ADDRESS -> (ticketItems + connItems).sortedBy { it.address }
            // «По типу участка» / «По статусу» — у подключения аналога нет, кладём их
            // после заявок, между собой по времени выезда.
            Prefs.SORT_BY_SERVICE,
            Prefs.SORT_BY_STATUS  -> ticketItems + connItems.sortedBy { it.scheduledAt }
            // «По времени» (по умолчанию) — одна лента по времени выезда, как на вебе и в PWA.
            else                  -> (ticketItems + connItems).sortedBy { it.scheduledAt }
        }
    }

    private fun sortTickets(tickets: List<TicketEntity>): List<TicketEntity> {
        return when (filterViewModel.sortOrder.value) {
            Prefs.SORT_BY_ADDRESS -> tickets.sortedBy { it.addressFull ?: "" }
            Prefs.SORT_BY_SERVICE -> tickets.sortedBy { it.serviceTypeName ?: "" }
            // Группами по числу status.sort_order (открытые/закрытые/отменённые), внутри группы — по времени.
            Prefs.SORT_BY_STATUS  -> tickets.sortedWith(compareBy({ it.statusSortOrder }, { it.scheduledAt ?: "" }))
            else                  -> tickets  // already sorted by scheduledAt from SQL
        }
    }

    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            // Свайпы доступны только строкам заявок — у подключения из объединённой ленты
            // (как на вебе/PWA) действий по свайпу нет, только переход в карточку.
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val item = adapter.currentList.getOrNull(vh.adapterPosition)
                if (item !is DayListItem.TicketItem) return 0
                return super.getMovementFlags(rv, vh)
            }

            // While the user is actively dragging — disable ViewPager2 so it doesn't steal the gesture
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                val swiping = actionState == ItemTouchHelper.ACTION_STATE_SWIPE
                (activity as? MainActivity)?.setViewPagerSwipeEnabled(!swiping)
            }

            // Swipe finished (card snapped back or fully swiped) — restore ViewPager2
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                (activity as? MainActivity)?.setViewPagerSwipeEnabled(true)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList.getOrNull(position) as? DayListItem.TicketItem ?: return
                val ticket = item.ticket
                adapter.notifyItemChanged(position)
                when (direction) {
                    ItemTouchHelper.RIGHT ->
                        CloseTicketSheet.newInstance(ticket.id)
                            .show(parentFragmentManager, "close_swipe")
                    ItemTouchHelper.LEFT ->
                        RescheduleSheet.newInstance(ticket.id)
                            .show(parentFragmentManager, "reschedule_swipe")
                }
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = viewHolder.itemView
                val bg = ColorDrawable()
                when {
                    dX > 0 -> {
                        bg.color = Color.parseColor("#10B981")
                        bg.setBounds(item.left, item.top, item.left + dX.toInt(), item.bottom)
                    }
                    dX < 0 -> {
                        bg.color = Color.parseColor("#3B82F6")
                        bg.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
                    }
                }
                bg.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recycler)
    }

    private fun syncFromServer() {
        val prefs = Prefs(requireContext())
        if (!prefs.isLoggedIn) { binding.swipeRefresh.isRefreshing = false; return }

        lifecycleScope.launch {
            var failed = false
            for (attempt in 1..3) {
                try {
                    val response = RetrofitClient.create(prefs).getTickets()
                    val newIds = if (group == "today") response.new_today.map { it.id }.toSet() else emptySet()
                    val tickets = when (group) {
                        "overdue"  -> response.overdue
                        "today"    -> (response.today + response.new_today).distinctBy { it.id }
                        "tomorrow" -> response.tomorrow
                        else       -> emptyList()
                    }
                    saveToCache(tickets, newIds)
                    // Room Flow will auto-emit with new data — no manual showItems needed
                    (activity as? MainActivity)?.refreshSpinnerFromCache()
                    // Territories are only returned by GET /connection-requests — pick up whatever
                    // ConnectionsFragment has cached to prefs by now (per API_MOBILE.md, the id set
                    // is shared between tickets and connection requests).
                    populateTerritoryChips(parseTerritories(prefs.connectionTerritoriesJson))
                    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    filterViewModel.lastSyncTime.value = "Обновлено в $time"
                    failed = false
                    break
                } catch (e: Exception) {
                    if (attempt < 3) delay(3000L) else failed = true
                }
            }
            if (failed) {
                _binding?.let {
                    Snackbar.make(it.root, "Нет соединения, показаны кешированные данные", Snackbar.LENGTH_SHORT).show()
                }
            }
            _binding?.swipeRefresh?.isRefreshing = false
            _binding?.progressInitial?.visibility = View.GONE
        }
    }

    private fun showItems(list: List<DayListItem>) {
        adapter.submitList(list)
        binding.layoutEmpty.visibility = if (list.isEmpty() && hasLoadedOnce) View.VISIBLE else View.GONE
        (activity as? MainActivity)?.updateTabCount(group, list.size)
    }

    private suspend fun saveToCache(tickets: List<Ticket>, newIds: Set<Int> = emptySet()) {
        val gson = Gson()
        val db   = AppDatabase.get(requireContext())
        val existingTimes = db.ticketDao().getByGroup(group).associate { it.id to it.cachedAt }
        val entities = tickets.map { t ->
            TicketEntity(
                id               = t.id,
                number           = t.number,
                createdAt        = t.created_at,
                scheduledAt      = t.scheduled_at,
                closedAt         = t.closed_at,
                closedBy         = t.closed_by,
                description      = t.description,
                phone            = t.phone,
                apartment        = t.apartment,
                closeNotes       = t.close_notes,
                actNumber        = t.act_number,
                actId            = t.act?.id,
                actStatus        = t.act?.status,
                actMaterialsChangedAt = t.act?.materials_changed_at,
                addressFull      = t.address?.full,
                territoryId      = t.territory?.id,
                territoryName    = t.territory?.name,
                typeName         = t.type,
                serviceTypeName  = t.service_type?.name,
                serviceTypeColor = t.service_type?.color,
                statusName       = t.status?.name,
                statusColor      = t.status?.color,
                statusIsFinal    = t.status?.is_final ?: false,
                statusSlug       = t.status?.slug,
                statusSortOrder  = t.status?.sort_order ?: 0,
                brigade          = t.brigade,
                assignee         = t.assignee,
                commentsJson     = gson.toJson(t.comments),
                group            = group,
                isNew            = t.id in newIds,
                cachedAt         = existingTimes[t.id] ?: System.currentTimeMillis(),
            )
        }
        db.ticketDao().deleteByGroup(group)
        db.ticketDao().insertAll(entities)
    }

    private fun populateTerritoryChips(territories: List<CRTerritory>) {
        if (_binding == null) return
        cachedTerritories = territories
        val selected = filterViewModel.selectedTerritoryId.value
        binding.chipGroupTicketTerritories.removeAllViews()

        val chipAll = Chip(requireContext()).apply {
            text = "Все территории"
            isCheckable = true
            isChecked = selected == null
            setOnClickListener { setTerritoryFilter(null) }
        }
        binding.chipGroupTicketTerritories.addView(chipAll)

        territories.forEach { t ->
            val chip = Chip(requireContext()).apply {
                text = t.name
                isCheckable = true
                isChecked = selected == t.id
                setOnClickListener { setTerritoryFilter(t.id) }
            }
            binding.chipGroupTicketTerritories.addView(chip)
        }
    }

    private fun setTerritoryFilter(territoryId: Int?) {
        Prefs(requireContext()).selectedTicketTerritoryId = territoryId
        filterViewModel.selectedTerritoryId.value = territoryId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_GROUP = "group"
        fun newInstance(group: String) = TicketsFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP, group) }
        }
    }
}

private fun parseTerritories(json: String): List<CRTerritory> =
    if (json.isBlank()) emptyList()
    else runCatching {
        Gson().fromJson<List<CRTerritory>>(json, object : TypeToken<List<CRTerritory>>() {}.type)
    }.getOrDefault(emptyList())
