package com.sputnik.helpdesk.ui.detail

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sputnik.helpdesk.data.db.MaterialEntity
import com.sputnik.helpdesk.data.model.MaterialItem
import com.sputnik.helpdesk.databinding.ItemMaterialRowBinding

class MaterialPickerAdapter(
    private var catalog: List<MaterialEntity>,
    private val onRowsChanged: (() -> Unit)? = null,
) : RecyclerView.Adapter<MaterialPickerAdapter.VH>() {

    data class Row(var material: MaterialEntity? = null, var qty: String = "")

    private val rows = mutableListOf<Row>()

    fun updateCatalog(newCatalog: List<MaterialEntity>) {
        catalog = newCatalog
        notifyDataSetChanged()
    }

    fun addRow() {
        rows.add(Row())
        notifyItemInserted(rows.lastIndex)
        onRowsChanged?.invoke()
    }

    private fun removeRow(pos: Int) {
        if (pos < 0 || pos >= rows.size) return
        rows.removeAt(pos)
        notifyItemRemoved(pos)
        onRowsChanged?.invoke()
    }

    fun getItems(): List<MaterialItem>? {
        if (rows.isEmpty()) return emptyList()
        return rows.map { row ->
            val m = row.material ?: return null
            val q = row.qty.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
            MaterialItem(m.id, q)
        }
    }

    /** Sum of price × quantity across all rows that currently have both a material and a valid quantity. */
    fun getTotal(): Double = rows.sumOf { rowTotal(it) }

    private fun rowTotal(row: Row): Double {
        val price = row.material?.price ?: return 0.0
        val qty = row.qty.replace(',', '.').toDoubleOrNull() ?: return 0.0
        return price * qty
    }

    override fun getItemCount(): Int = rows.size

    inner class VH(val binding: ItemMaterialRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemMaterialRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        with(holder.binding) {
            // Remove old watcher before setting text to avoid feedback loop
            (etQuantity.tag as? TextWatcher)?.let { etQuantity.removeTextChangedListener(it) }

            btnSelectMaterial.text = row.material?.let { shortLabel(it) } ?: "Выберите материал"
            tilQuantity.suffixText = row.material?.unit ?: ""
            etQuantity.setText(row.qty)
            updateLineTotal(tvLineTotal, row)

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        rows[pos].qty = s.toString()
                        updateLineTotal(tvLineTotal, rows[pos])
                        onRowsChanged?.invoke()
                    }
                }
            }
            etQuantity.addTextChangedListener(watcher)
            etQuantity.tag = watcher

            btnSelectMaterial.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION || catalog.isEmpty()) return@setOnClickListener
                MaterialSelectDialog.show(holder.itemView.context, catalog) { material ->
                    rows[pos].material = material
                    tilQuantity.suffixText = material.unit
                    btnSelectMaterial.text = shortLabel(material)
                    updateLineTotal(tvLineTotal, rows[pos])
                    onRowsChanged?.invoke()
                }
            }

            btnRemoveMaterial.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) removeRow(pos)
            }
        }
    }

    /** Compact label shown on the row button once a material is selected. */
    private fun shortLabel(m: MaterialEntity): String = "${m.name} (${m.unit})"

    private fun updateLineTotal(tv: android.widget.TextView, row: Row) {
        val amount = rowTotal(row)
        if (row.material != null && amount > 0.0) {
            tv.text = "= %.2f руб.".format(amount)
            tv.visibility = android.view.View.VISIBLE
        } else {
            tv.visibility = android.view.View.GONE
        }
    }
}
