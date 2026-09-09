package com.sputnik.helpdesk.ui.detail

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sputnik.helpdesk.data.db.MaterialEntity
import com.sputnik.helpdesk.databinding.ItemMaterialPickerBinding

/** Replaces the plain AlertDialog.setItems() list for material selection with rows that visually
 * separate name / code / price so long catalog entries don't read as one run-on string. */
object MaterialSelectDialog {

    fun show(context: Context, catalog: List<MaterialEntity>, onSelected: (MaterialEntity) -> Unit) {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Выберите материал")
            .setView(recyclerView)
            .setNegativeButton("Отмена", null)
            .create()
        recyclerView.adapter = Adapter(catalog) { material ->
            dialog.dismiss()
            onSelected(material)
        }
        dialog.show()
    }

    private class Adapter(
        private val catalog: List<MaterialEntity>,
        private val onClick: (MaterialEntity) -> Unit,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        inner class VH(val binding: ItemMaterialPickerBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemMaterialPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount(): Int = catalog.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val material = catalog[position]
            with(holder.binding) {
                tvMaterialName.text = material.name
                tvMaterialCode.text = material.code
                tvMaterialPrice.text = "%.2f руб./%s".format(material.price, material.unit)
                viewDivider.visibility = if (position == catalog.lastIndex) android.view.View.GONE else android.view.View.VISIBLE
                root.setOnClickListener { onClick(material) }
            }
        }
    }
}
