package com.sputnik.helpdesk.ui.act

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.MaterialEntity
import com.sputnik.helpdesk.data.model.Act
import com.sputnik.helpdesk.data.model.ActHistoryEntry
import com.sputnik.helpdesk.data.model.ActMaterial
import com.sputnik.helpdesk.data.model.MaterialItem
import com.sputnik.helpdesk.data.model.UpdateActMaterialReq
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityActBinding
import com.sputnik.helpdesk.ui.main.TicketAdapter
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.format.DateTimeFormatter
import java.util.Locale

class ActActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActBinding
    private var actId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActBinding.inflate(layoutInflater)
        setContentView(binding.root)

        actId = intent.getIntExtra(EXTRA_ACT_ID, -1)
        if (actId == -1) { finish(); return }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Акт"

        App.sessionExpiredEvent.observe(this) { event ->
            event ?: return@observe
            App.sessionExpiredEvent.value = null
            startActivity(Intent(this, com.sputnik.helpdesk.ui.login.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.btnApprove.setOnClickListener { approve() }
        binding.btnAcknowledge.setOnClickListener { acknowledge() }
        binding.btnAddMaterial.setOnClickListener { showAddMaterialDialog() }

        load()
    }

    private fun load() {
        binding.progressAct.visibility = View.VISIBLE
        binding.layoutContent.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val act = RetrofitClient.create(Prefs(this@ActActivity)).getAct(actId)
                applyData(act)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Ошибка загрузки акта", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressAct.visibility = View.GONE
            }
        }
    }

    private fun applyData(act: Act) {
        binding.layoutContent.visibility = View.VISIBLE
        supportActionBar?.title = "Акт № ${act.number}"

        binding.tvNumber.text = "№ ${act.number}"
        binding.tvType.text = "Тип: ${typeLabel(act.type)}"
        binding.tvCreator.text = "Создал: ${act.creator ?: "—"}"

        val (statusText, statusColor) = statusDisplay(act.status)
        binding.tvStatus.text = statusText
        binding.tvStatus.setBackgroundColor(Color.parseColor(statusColor))

        binding.bannerAcknowledge.visibility = if (act.can.acknowledge) View.VISIBLE else View.GONE
        binding.btnApprove.visibility = if (act.can.foreman_review) View.VISIBLE else View.GONE
        binding.btnAddMaterial.visibility = if (act.can.edit_materials) View.VISIBLE else View.GONE
        binding.panelActions.visibility = if (act.can.foreman_review) View.VISIBLE else View.GONE

        applyMaterials(act.materials, act.can.edit_materials)
        applyPromotion(act.promotion_name, act.promotion_price)
        applyHistory(act.history)

        lifecycleScope.launch { syncActToCache(act) }
    }

    /** Keeps the list-screen "unread act changes" badge in sync without waiting for the next background sync. */
    private suspend fun syncActToCache(act: Act) {
        val db = AppDatabase.get(this@ActActivity)
        act.ticket_id?.let { tid ->
            val existing = db.ticketDao().getById(tid) ?: return@let
            db.ticketDao().insertAll(listOf(existing.copy(
                actStatus = act.status,
                actMaterialsChangedAt = act.materials_changed_at,
            )))
        }
        act.connection_request_id?.let { cid ->
            val existing = db.connectionRequestDao().getById(cid) ?: return@let
            db.connectionRequestDao().insertAll(listOf(existing.copy(
                actStatus = act.status,
                actMaterialsChangedAt = act.materials_changed_at,
            )))
        }
    }

    private fun applyMaterials(materials: List<ActMaterial>, editable: Boolean) {
        binding.layoutMaterials.removeAllViews()
        if (materials.isEmpty()) {
            binding.tvMaterialsEmpty.visibility = View.VISIBLE
            binding.tvTotal.visibility = View.GONE
            return
        }
        binding.tvMaterialsEmpty.visibility = View.GONE

        var total = 0.0
        materials.forEach { m ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6 }
            }
            val label = TextView(this).apply {
                text = "${m.name} — ${fmt(m.quantity)} ${m.unit} × ${fmt(m.price_at_time)} = ${fmt(m.total)} руб."
                textSize = 13f
                setTextColor(Color.parseColor("#D1D5DB"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)

            if (editable) {
                val editBtn = TextView(this).apply {
                    text = "✎"
                    textSize = 16f
                    setTextColor(Color.parseColor("#42A5F5"))
                    setPadding(20, 8, 20, 8)
                    setOnClickListener { showEditQuantityDialog(m) }
                }
                row.addView(editBtn)

                val deleteBtn = TextView(this).apply {
                    text = "✕"
                    textSize = 16f
                    setTextColor(Color.parseColor("#EF4444"))
                    setPadding(12, 8, 8, 8)
                    setOnClickListener { confirmDeleteMaterial(m) }
                }
                row.addView(deleteBtn)
            }

            binding.layoutMaterials.addView(row)
            total += m.total
        }
        binding.tvTotal.text = "Итого: ${fmt(total)} руб."
        binding.tvTotal.visibility = View.VISIBLE
    }

    private fun applyPromotion(name: String?, price: Double?) {
        if (name != null && price != null) {
            binding.tvPromotionBadge.text = "🎁 Акция «$name» — к оплате ${fmt(price)} руб."
            binding.tvPromotionBadge.visibility = View.VISIBLE
        } else {
            binding.tvPromotionBadge.visibility = View.GONE
        }
    }

    private fun applyHistory(history: List<ActHistoryEntry>) {
        binding.layoutHistory.removeAllViews()
        if (history.isEmpty()) return
        history.forEach { h ->
            val row = TextView(this).apply {
                text = historyLine(h)
                textSize = 12f
                setTextColor(Color.parseColor("#9CA3AF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            }
            binding.layoutHistory.addView(row)
        }
    }

    private fun historyLine(h: ActHistoryEntry): String {
        val action = actionLabel(h.action)
        val who = h.user?.let { " · $it" } ?: ""
        val date = formatDateTime(h.created_at)
        val unread = if (isMaterialAction(h.action) && h.acknowledged_at == null) " · не подтверждено" else ""
        return "$action$who · $date$unread"
    }

    private fun isMaterialAction(action: String) =
        action == "material_added" || action == "material_changed" || action == "material_removed"

    private fun actionLabel(action: String): String = when (action) {
        "created"             -> "Акт создан"
        "approved"            -> "Акт утверждён бригадиром"
        "material_added"      -> "Добавлен материал"
        "material_changed"    -> "Изменено количество материала"
        "material_removed"    -> "Удалён материал"
        "acknowledged"        -> "Правки подтверждены монтажником"
        "peo_processed"       -> "Обработано ПЭО"
        "logistics_processed" -> "Обработано логистикой"
        "completed"           -> "Акт завершён"
        else                  -> action
    }

    private fun typeLabel(type: String): String = when (type) {
        "regular" -> "Обычный"
        "repair"  -> "Ремонт"
        else      -> type
    }

    private fun statusDisplay(status: String): Pair<String, String> = when (status) {
        "pending_foreman"         -> "Ожидает утверждения бригадиром" to "#F59E0B"
        "approved"                -> "Утверждён бригадиром" to "#3B82F6"
        "processing"              -> "В обработке в офисе" to "#6B7280"
        "pending_subscriber_dept" -> "В обработке в офисе" to "#6B7280"
        "completed"               -> "Завершён" to "#10B981"
        else                      -> status to "#6B7280"
    }

    private fun approve() {
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@ActActivity)).approveAct(actId)
                applyData(updated)
                Snackbar.make(binding.root, "Акт утверждён", Snackbar.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                showApiError(e)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun acknowledge() {
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@ActActivity)).acknowledgeAct(actId)
                applyData(updated)
                Snackbar.make(binding.root, "Подтверждено", Snackbar.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                showApiError(e)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddMaterialDialog() {
        lifecycleScope.launch {
            val catalog = AppDatabase.get(this@ActActivity).materialDao().getAll()
            if (catalog.isEmpty()) {
                Snackbar.make(binding.root, "Справочник материалов пуст", Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val names = catalog.map { "${it.name} [${it.code}] — %.2f руб./%s".format(it.price, it.unit) }.toTypedArray()
            AlertDialog.Builder(this@ActActivity)
                .setTitle("Выберите материал")
                .setItems(names) { _, idx -> promptQuantityForAdd(catalog[idx]) }
                .show()
        }
    }

    private fun promptQuantityForAdd(material: MaterialEntity) {
        val input = EditText(this).apply {
            hint = "Количество, ${material.unit}"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle(material.name)
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val qty = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (qty == null || qty <= 0) {
                    Snackbar.make(binding.root, "Некорректное количество", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                addMaterial(material.id, qty)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addMaterial(materialId: Int, qty: Double) {
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@ActActivity))
                    .addActMaterial(actId, MaterialItem(materialId, qty))
                applyData(updated)
            } catch (e: HttpException) {
                showApiError(e)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditQuantityDialog(m: ActMaterial) {
        val input = EditText(this).apply {
            hint = "Количество, ${m.unit}"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(fmt(m.quantity))
        }
        AlertDialog.Builder(this)
            .setTitle(m.name)
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val qty = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (qty == null || qty <= 0) {
                    Snackbar.make(binding.root, "Некорректное количество", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateQuantity(m.id, qty)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateQuantity(materialLineId: Int, qty: Double) {
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@ActActivity))
                    .updateActMaterial(actId, materialLineId, UpdateActMaterialReq(qty))
                applyData(updated)
            } catch (e: HttpException) {
                showApiError(e)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteMaterial(m: ActMaterial) {
        AlertDialog.Builder(this)
            .setTitle("Удалить материал?")
            .setMessage(m.name)
            .setPositiveButton("Удалить") { _, _ -> deleteMaterial(m.id) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteMaterial(materialLineId: Int) {
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@ActActivity))
                    .deleteActMaterial(actId, materialLineId)
                applyData(updated)
            } catch (e: HttpException) {
                showApiError(e)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showApiError(e: HttpException) {
        val msg = when (e.code()) {
            403  -> "Нет прав для этого действия"
            422  -> "Ошибка: ${runCatching { e.response()?.errorBody()?.string() }.getOrNull()?.take(120) ?: "проверьте данные"}"
            else -> "Ошибка сервера ${e.code()}"
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

    private fun formatDateTime(s: String): String = try {
        TicketAdapter.parseIsoToLocal(s).format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru")))
    } catch (_: Exception) {
        s.take(16).replace("T", " ")
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_ACT_ID = "act_id"
    }
}
