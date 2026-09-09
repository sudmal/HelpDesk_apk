package com.sputnik.helpdesk.ui.main

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sputnik.helpdesk.data.db.ConnectionRequestEntity
import com.sputnik.helpdesk.data.db.TicketEntity
import com.sputnik.helpdesk.databinding.ItemConnectionBinding
import com.sputnik.helpdesk.databinding.ItemTicketBinding
import com.sputnik.helpdesk.ui.connections.bindConnectionRow
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Объединённая лента дня: обычные заявки + назначенные заявки на подключение
 * (см. [DayListItem]). Тикеты кликаются в [com.sputnik.helpdesk.ui.detail.DetailActivity]
 * и поддерживают свайпы, строки подключений — только переход в карточку.
 */
class TicketAdapter(
    private val onTicketClick: (TicketEntity) -> Unit,
    private val onConnectionClick: (ConnectionRequestEntity) -> Unit = {},
) : ListAdapter<DayListItem, RecyclerView.ViewHolder>(DIFF) {

    class TicketVH(val binding: ItemTicketBinding) : RecyclerView.ViewHolder(binding.root)
    class ConnectionVH(val binding: ItemConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is DayListItem.TicketItem -> TYPE_TICKET
        is DayListItem.ConnectionItem -> TYPE_CONNECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CONNECTION) {
            ConnectionVH(ItemConnectionBinding.inflate(inflater, parent, false))
        } else {
            TicketVH(ItemTicketBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DayListItem.TicketItem -> bindTicket((holder as TicketVH).binding, item.ticket)
            is DayListItem.ConnectionItem ->
                (holder as ConnectionVH).binding.bindConnectionRow(item.connection, onConnectionClick)
        }
    }

    private fun bindTicket(binding: ItemTicketBinding, t: TicketEntity) {
        with(binding) {
            tvNumber.text = t.number

            val addrParts = buildList {
                t.addressFull?.let { add(it) }
                t.apartment?.let { add("кв.$it") }
            }
            tvAddress.text = addrParts.joinToString(", ").ifEmpty { "Адрес не указан" }

            tvType.text   = t.typeName ?: "—"
            tvStatus.text = t.statusName ?: "—"
            tvTime.text   = formatScheduledAt(t.scheduledAt, t.group)

            try {
                tvStatus.setBackgroundColor(Color.parseColor(t.statusColor ?: "#6B7280"))
            } catch (_: Exception) {
                tvStatus.setBackgroundColor(Color.parseColor("#6B7280"))
            }

            // Service type chip
            if (!t.serviceTypeName.isNullOrEmpty()) {
                tvServiceType.text = t.serviceTypeName
                try {
                    tvServiceType.setBackgroundColor(Color.parseColor(t.serviceTypeColor ?: "#6B7280"))
                } catch (_: Exception) {
                    tvServiceType.setBackgroundColor(Color.parseColor("#6B7280"))
                }
                tvServiceType.visibility = View.VISIBLE
            } else {
                tvServiceType.visibility = View.GONE
            }

            // Left colored strip — same color as service type
            val stripColor = t.serviceTypeColor?.let { hex ->
                runCatching { Color.parseColor(hex) }.getOrNull()
            } ?: Color.parseColor("#6B7280")
            viewColorStrip.setBackgroundColor(stripColor)

            // "Добавлена в HH:mm" badge — время создания из БД, fallback на время получения
            if (t.isNew) {
                val hm = t.createdAt?.let { iso ->
                    runCatching { parseIsoToLocal(iso).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
                } ?: Instant.ofEpochMilli(t.cachedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                tvNew.text = "добавлена в $hm"
                tvNew.visibility = View.VISIBLE
            } else {
                tvNew.visibility = View.GONE
            }

            tvActBadge.visibility = if (t.actMaterialsChangedAt != null) View.VISIBLE else View.GONE

            // Call button
            val phone = t.phone?.trim()?.takeIf { it.isNotEmpty() }
            if (phone != null) {
                btnCall.visibility = View.VISIBLE
                btnCall.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    it.context.startActivity(intent)
                }
            } else {
                btnCall.visibility = View.GONE
            }

            // Description preview
            val firstDescLine = t.description?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            if (!firstDescLine.isNullOrEmpty()) {
                tvDescPreview.text = firstDescLine
                tvDescPreview.visibility = View.VISIBLE
            } else {
                tvDescPreview.visibility = View.GONE
            }

            // Card background: 20% service type color blended into dark base
            val darkBase = 28
            val cardBg = t.serviceTypeColor?.let { hex ->
                runCatching {
                    val c = Color.parseColor(hex)
                    val r = (Color.red(c) * 0.20 + darkBase * 0.80).toInt()
                    val g = (Color.green(c) * 0.20 + darkBase * 0.80).toInt()
                    val b = (Color.blue(c) * 0.20 + darkBase * 0.80).toInt()
                    Color.rgb(r, g, b)
                }.getOrNull()
            } ?: Color.parseColor("#2A2A2A")
            cardRoot.setCardBackgroundColor(cardBg)

            root.setOnClickListener { onTicketClick(t) }
        }
    }

    private fun formatScheduledAt(scheduledAt: String?, group: String): String {
        if (scheduledAt == null) return "—"
        return try {
            val ldt = parseIsoToLocal(scheduledAt)
            if (group == "today") {
                ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                ldt.format(DateTimeFormatter.ofPattern("d.MM HH:mm", Locale("ru")))
            }
        } catch (_: Exception) {
            scheduledAt.take(16).replace("T", " ")
        }
    }

    companion object {
        private const val TYPE_TICKET = 0
        private const val TYPE_CONNECTION = 1

        fun parseIsoToLocal(s: String): LocalDateTime = try {
            OffsetDateTime.parse(s).toLocalDateTime()
        } catch (_: Exception) {
            LocalDateTime.parse(s)
        }

        val DIFF = object : DiffUtil.ItemCallback<DayListItem>() {
            override fun areItemsTheSame(a: DayListItem, b: DayListItem): Boolean = when {
                a is DayListItem.TicketItem && b is DayListItem.TicketItem ->
                    a.ticket.id == b.ticket.id
                a is DayListItem.ConnectionItem && b is DayListItem.ConnectionItem ->
                    a.connection.id == b.connection.id
                else -> false
            }

            override fun areContentsTheSame(a: DayListItem, b: DayListItem) = a == b
        }
    }
}
