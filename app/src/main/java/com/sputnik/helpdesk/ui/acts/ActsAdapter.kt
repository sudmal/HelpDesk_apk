package com.sputnik.helpdesk.ui.acts

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sputnik.helpdesk.data.db.ActEntity
import com.sputnik.helpdesk.databinding.ItemActBinding
import com.sputnik.helpdesk.ui.main.TicketAdapter
import java.time.format.DateTimeFormatter
import java.util.Locale

class ActsAdapter(private val onClick: (ActEntity) -> Unit) :
    ListAdapter<ActEntity, ActsAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemActBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemActBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = getItem(position)
        with(holder.binding) {
            tvNumber.text = "№ ${a.number}"

            val (statusText, statusColor) = statusDisplay(a.status)
            tvStatus.text = statusText
            tvStatus.setBackgroundColor(Color.parseColor(statusColor))
            viewStatusStrip.setBackgroundColor(Color.parseColor(statusColor))

            tvContext.text = when {
                a.ticketId != null -> "Заявка ${a.ticketNumber ?: "№${a.ticketId}"}"
                a.connectionRequestId != null -> "Подключение: ${a.connectionRequestName ?: "№${a.connectionRequestId}"}"
                else -> "—"
            }

            if (!a.address.isNullOrEmpty()) {
                tvAddress.text = a.address
                tvAddress.visibility = View.VISIBLE
            } else {
                tvAddress.visibility = View.GONE
            }

            tvCreator.text = a.creator?.let { "Создал: $it" } ?: ""
            tvDate.text = formatDate(a.createdAt)

            tvActionBadge.visibility =
                if (a.status == "pending_foreman" && a.canForemanReview) View.VISIBLE else View.GONE
            tvMaterialsBadge.visibility = if (a.materialsChangedAt != null) View.VISIBLE else View.GONE
            tvCorrectedBadge.visibility = if (a.materialsCorrectedAt != null) View.VISIBLE else View.GONE

            root.setOnClickListener { onClick(a) }
        }
    }

    private fun formatDate(s: String): String = try {
        TicketAdapter.parseIsoToLocal(s).format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru")))
    } catch (_: Exception) {
        s.take(16).replace("T", " ")
    }

    companion object {
        fun statusDisplay(status: String): Pair<String, String> = when (status) {
            "pending_foreman"         -> "Ожидает утверждения бригадиром" to "#F59E0B"
            "approved"                -> "Утверждён бригадиром" to "#3B82F6"
            "processing"              -> "В обработке в офисе" to "#6B7280"
            "pending_subscriber_dept" -> "В обработке в офисе" to "#6B7280"
            "completed"               -> "Завершён" to "#10B981"
            else                      -> status to "#6B7280"
        }

        val DIFF = object : DiffUtil.ItemCallback<ActEntity>() {
            override fun areItemsTheSame(a: ActEntity, b: ActEntity) = a.id == b.id
            override fun areContentsTheSame(a: ActEntity, b: ActEntity) = a == b
        }
    }
}
