package com.sputnik.helpdesk.ui.act

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.ActEntity
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.MaterialEntity
import com.sputnik.helpdesk.data.model.Act
import com.sputnik.helpdesk.data.model.ActHistoryEntry
import com.sputnik.helpdesk.data.model.ActMaterial
import com.sputnik.helpdesk.data.model.MaterialItem
import com.sputnik.helpdesk.data.model.Promotion
import com.sputnik.helpdesk.data.model.UpdateActMaterialReq
import com.sputnik.helpdesk.data.model.UpdateActPromotionReq
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
    private var currentAct: Act? = null
    private var promotions: List<Promotion> = emptyList()
    private var promotionSelectorSuppressed = false

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

        binding.spinnerActPromotion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (promotionSelectorSuppressed) return
                val newId = promotions.getOrNull(position - 1)?.id
                if (newId == currentAct?.promotion_id) return
                updatePromotion(newId)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

    private suspend fun applyData(act: Act) {
        currentAct = act
        binding.layoutContent.visibility = View.VISIBLE
        supportActionBar?.title = "Акт № ${act.number}"

        binding.tvNumber.text = "№ ${act.number}"
        binding.tvType.text = "Тип: ${typeLabel(act.type)}"
        binding.tvCreator.text = "Создал: ${act.creator ?: "—"}"

        if (act.materials_corrected_at != null) {
            binding.tvCorrectedBadge.text = "✏️ Исправлено после утверждения ${formatDateTime(act.materials_corrected_at)}"
            binding.tvCorrectedBadge.visibility = View.VISIBLE
        } else {
            binding.tvCorrectedBadge.visibility = View.GONE
        }

        val (statusText, statusColor) = statusDisplay(act.status)
        binding.tvStatus.text = statusText
        binding.tvStatus.setBackgroundColor(Color.parseColor(statusColor))

        binding.bannerAcknowledge.visibility = if (act.can.acknowledge) View.VISIBLE else View.GONE
        binding.btnApprove.visibility = if (act.can.foreman_review) View.VISIBLE else View.GONE
        binding.btnAddMaterial.visibility = if (act.can.edit_materials) View.VISIBLE else View.GONE
        binding.panelActions.visibility = if (act.can.foreman_review) View.VISIBLE else View.GONE

        applyMaterials(act.materials, act.can.edit_materials)
        applyPromotionUi(act)
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

        if (act.status == "completed") {
            db.actDao().deleteById(act.id)
        } else {
            val existingAct = db.actDao().getById(act.id)
            db.actDao().upsert(
                ActEntity(
                    id = act.id,
                    number = act.number,
                    type = act.type,
                    status = act.status,
                    ticketId = act.ticket_id,
                    ticketNumber = existingAct?.ticketNumber,
                    connectionRequestId = act.connection_request_id,
                    connectionRequestName = existingAct?.connectionRequestName,
                    address = existingAct?.address,
                    creator = act.creator,
                    createdAt = act.created_at,
                    foremanReviewedAt = act.foreman_reviewed_at,
                    materialsChangedAt = act.materials_changed_at,
                    materialsCorrectedAt = act.materials_corrected_at,
                    canForemanReview = act.can.foreman_review,
                    canEditMaterials = act.can.edit_materials,
                    canAcknowledge = act.can.acknowledge,
                )
            )
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

        binding.layoutMaterials.addView(materialsHeaderRow(editable))

        var total = 0.0
        materials.forEach { m ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            row.addView(tableCell(m.code, dp(COL_CODE_DP), "#9CA3AF", gravityStart = true))
            row.addView(tableCell(m.name, dp(COL_NAME_DP), "#E5E7EB", gravityStart = true))
            row.addView(tableCell(m.unit, dp(COL_UNIT_DP), "#9CA3AF"))
            row.addView(tableCell(fmt(m.quantity), dp(COL_QTY_DP), "#D1D5DB"))
            row.addView(tableCell(fmt(m.price_at_time), dp(COL_PRICE_DP), "#D1D5DB"))
            row.addView(tableCell(fmt(m.total), dp(COL_SUM_DP), "#D1D5DB", bold = true))

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

    private fun materialsHeaderRow(editable: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(headerCell("Код", dp(COL_CODE_DP), gravityStart = true))
        addView(headerCell("Наименование", dp(COL_NAME_DP), gravityStart = true))
        addView(headerCell("Ед.", dp(COL_UNIT_DP)))
        addView(headerCell("Кол-во", dp(COL_QTY_DP)))
        addView(headerCell("Цена", dp(COL_PRICE_DP)))
        addView(headerCell("Сумма", dp(COL_SUM_DP)))
        if (editable) addView(headerCell("", dp(64)))
    }

    private fun headerCell(text: String, widthPx: Int, gravityStart: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.parseColor("#6B7280"))
        gravity = if (gravityStart) android.view.Gravity.START else android.view.Gravity.END
        setPadding(if (gravityStart) 0 else dp(8), 0, dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun tableCell(
        text: String, widthPx: Int, colorHex: String,
        gravityStart: Boolean = false, bold: Boolean = false,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor(colorHex))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = if (gravityStart) android.view.Gravity.START else android.view.Gravity.END
        setPadding(if (gravityStart) 0 else dp(8), 0, dp(8), 0)
        maxLines = if (gravityStart) 2 else 1
        layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Read-only summary badge — shown when the promotion selector isn't editable. */
    private fun applyPromotionBadge(name: String?, price: Double?) {
        if (name != null && price != null) {
            binding.tvPromotionBadge.text = "🎁 Акция «$name» — к оплате ${fmt(price)} руб."
            binding.tvPromotionBadge.visibility = View.VISIBLE
        } else {
            binding.tvPromotionBadge.visibility = View.GONE
        }
    }

    /**
     * Selector visible only when materials are editable AND the promotion is applicable —
     * connection-request acts always allow it, ticket acts only when the underlying
     * ticket has `type_allows_promotion == true` (see PUT /acts/{id}/promotion in API_MOBILE.md).
     */
    private suspend fun applyPromotionUi(act: Act) {
        val eligible = act.can.edit_materials && isPromotionEligible(act)
        if (!eligible) {
            binding.tvActPromotionLabel.visibility = View.GONE
            binding.spinnerActPromotion.visibility = View.GONE
            applyPromotionBadge(act.promotion_name, act.promotion_price)
            return
        }

        if (promotions.isEmpty()) {
            promotions = try {
                RetrofitClient.create(Prefs(this@ActActivity)).getPromotions()
            } catch (_: Exception) { emptyList() }
        }
        if (promotions.isEmpty()) {
            binding.tvActPromotionLabel.visibility = View.GONE
            binding.spinnerActPromotion.visibility = View.GONE
            applyPromotionBadge(act.promotion_name, act.promotion_price)
            return
        }

        binding.tvPromotionBadge.visibility = View.GONE

        val names = listOf("Без акции") + promotions.map { "${it.name} — ${fmt(it.price)} руб." }
        promotionSelectorSuppressed = true
        binding.spinnerActPromotion.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val selectedIndex = promotions.indexOfFirst { it.id == act.promotion_id }
        binding.spinnerActPromotion.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
        promotionSelectorSuppressed = false

        binding.tvActPromotionLabel.visibility = View.VISIBLE
        binding.spinnerActPromotion.visibility = View.VISIBLE
    }

    private suspend fun isPromotionEligible(act: Act): Boolean {
        if (act.connection_request_id != null) return true
        val ticketId = act.ticket_id ?: return false
        return try {
            RetrofitClient.create(Prefs(this@ActActivity)).getTicket(ticketId).type_allows_promotion
        } catch (_: Exception) { false }
    }

    private fun updatePromotion(promotionId: Int?) {
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@ActActivity))
                    .updateActPromotion(actId, UpdateActPromotionReq(promotionId))
                applyData(updated)
                Snackbar.make(
                    binding.root,
                    if (promotionId != null) "Акция назначена" else "Акция снята",
                    Snackbar.LENGTH_SHORT
                ).show()
            } catch (e: HttpException) {
                showApiError(e)
                currentAct?.let { applyPromotionUi(it) }
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
                currentAct?.let { applyPromotionUi(it) }
            }
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
                App.actUpdatedEvent.postValue(Unit)
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
                App.actUpdatedEvent.postValue(Unit)
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

        private const val COL_CODE_DP = 64
        private const val COL_NAME_DP = 170
        private const val COL_UNIT_DP = 48
        private const val COL_QTY_DP = 64
        private const val COL_PRICE_DP = 72
        private const val COL_SUM_DP = 84
    }
}
