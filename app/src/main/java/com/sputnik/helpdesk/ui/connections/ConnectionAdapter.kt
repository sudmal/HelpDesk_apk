package com.sputnik.helpdesk.ui.connections

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sputnik.helpdesk.data.db.ConnectionRequestEntity
import com.sputnik.helpdesk.databinding.ItemConnectionBinding

class ConnectionAdapter(private val onClick: (ConnectionRequestEntity) -> Unit) :
    ListAdapter<ConnectionRequestEntity, ConnectionAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        with(holder.binding) {
            tvName.text    = c.name
            tvAddress.text = c.addressString ?: "Адрес не указан"
            tvTerritory.text = c.territoryName ?: ""
            tvDate.text    = c.createdAt?.take(10) ?: ""

            val (statusText, statusColor) = statusDisplay(c.status)
            tvStatus.text = statusText
            tvStatus.setBackgroundColor(Color.parseColor(statusColor))
            viewStatusStrip.setBackgroundColor(Color.parseColor(statusColor))

            if (c.isNew) {
                tvNew.visibility = View.VISIBLE
            } else {
                tvNew.visibility = View.GONE
            }

            tvActBadge.visibility = if (c.actMaterialsChangedAt != null) View.VISIBLE else View.GONE

            when {
                c.status != "pending" -> tvFeasibilityBadge.visibility = View.GONE
                c.feasibility == "possible" -> {
                    tvFeasibilityBadge.text = "Возможно · на согласовании"
                    tvFeasibilityBadge.setBackgroundColor(Color.parseColor("#10B981"))
                    tvFeasibilityBadge.visibility = View.VISIBLE
                }
                c.feasibility == null -> {
                    tvFeasibilityBadge.text = "нужен ответ монтажника"
                    tvFeasibilityBadge.setBackgroundColor(Color.parseColor("#8B5CF6"))
                    tvFeasibilityBadge.visibility = View.VISIBLE
                }
                else -> tvFeasibilityBadge.visibility = View.GONE
            }

            val desc = c.description?.trim()?.takeIf { it.isNotEmpty() }
            if (desc != null) {
                tvDescription.text = desc
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }

            val phone = c.phone?.trim()?.takeIf { it.isNotEmpty() }
            if (phone != null) {
                btnCall.visibility = View.VISIBLE
                btnCall.setOnClickListener {
                    it.context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }
            } else {
                btnCall.visibility = View.GONE
            }

            root.setOnClickListener { onClick(c) }
        }
    }

    companion object {
        fun statusDisplay(status: String): Pair<String, String> = when (status) {
            "pending"   -> "Ожидает"      to "#F59E0B"
            "scheduled" -> "Запланировано" to "#3B82F6"
            "rejected"  -> "Отклонено"    to "#EF4444"
            "closed"    -> "Выполнено"    to "#10B981"
            else        -> status          to "#6B7280"
        }

        val DIFF = object : DiffUtil.ItemCallback<ConnectionRequestEntity>() {
            override fun areItemsTheSame(a: ConnectionRequestEntity, b: ConnectionRequestEntity) = a.id == b.id
            override fun areContentsTheSame(a: ConnectionRequestEntity, b: ConnectionRequestEntity) = a == b
        }
    }
}
