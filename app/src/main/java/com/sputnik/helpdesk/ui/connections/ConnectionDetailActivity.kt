package com.sputnik.helpdesk.ui.connections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.ConnectionMaterialLine
import com.sputnik.helpdesk.data.model.ConnectionRequest
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityConnectionDetailBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ConnectionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionDetailBinding
    private var connectionId: Int = -1
    private var currentConnection: ConnectionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectionId = intent.getIntExtra(EXTRA_ID, -1)
        if (connectionId == -1) { finish(); return }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Подключение"

        App.sessionExpiredEvent.observe(this) { event ->
            event ?: return@observe
            App.sessionExpiredEvent.value = null
            startActivity(Intent(this, com.sputnik.helpdesk.ui.login.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        supportFragmentManager.setFragmentResultListener("connection_closed", this) { _, _ ->
            loadFromServer()
        }
        supportFragmentManager.setFragmentResultListener("connection_feasibility_answered", this) { _, _ ->
            loadFromServer()
        }
        supportFragmentManager.setFragmentResultListener("connection_cancelled", this) { _, _ ->
            loadFromServer()
        }

        setupCopyPhone()
        loadFromServer()
    }

    private fun setupCopyPhone() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        binding.tvPhone.setOnLongClickListener {
            val text = binding.tvPhone.text.toString().removePrefix("Тел: ").trim()
                .takeIf { it.isNotEmpty() && it != "—" } ?: return@setOnLongClickListener false
            clipboard.setPrimaryClip(ClipData.newPlainText("phone", text))
            Snackbar.make(binding.root, "Телефон скопирован", Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.tvPhone.setOnClickListener {
            val text = binding.tvPhone.text.toString().removePrefix("Тел: ").trim()
                .takeIf { it.isNotEmpty() && it != "—" } ?: return@setOnClickListener
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$text")))
        }
    }

    private fun loadFromServer() {
        binding.progressDetail.visibility = View.VISIBLE
        binding.layoutContent.visibility = View.GONE
        binding.panelActions.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val cr = RetrofitClient.create(Prefs(this@ConnectionDetailActivity))
                    .getConnectionRequest(connectionId)
                currentConnection = cr
                applyData(cr)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Ошибка загрузки данных", Snackbar.LENGTH_LONG).show()
                binding.progressDetail.visibility = View.GONE
            }
        }
    }

    private fun applyData(cr: ConnectionRequest) {
        binding.progressDetail.visibility = View.GONE
        binding.layoutContent.visibility = View.VISIBLE

        supportActionBar?.title = cr.name

        binding.tvName.text    = cr.name
        binding.tvAddress.text = "Адрес: ${cr.address_string ?: "—"}"
        binding.tvPhone.text   = "Тел: ${cr.phone ?: "—"}"
        binding.tvTerritory.text = "Территория: ${cr.territory?.name ?: "—"}"
        binding.tvCreator.text = "Добавил: ${cr.creator ?: "—"}"

        val (statusText, statusColor) = ConnectionAdapter.statusDisplay(cr.status)
        binding.tvStatusBadge.text = statusText
        binding.tvStatusBadge.setBackgroundColor(android.graphics.Color.parseColor(statusColor))

        if (cr.scheduled_at != null) {
            binding.tvScheduledAt.text = "Дата выезда: ${formatDate(cr.scheduled_at)}"
            binding.tvScheduledAt.visibility = View.VISIBLE
        } else {
            binding.tvScheduledAt.visibility = View.GONE
        }

        if (!cr.description.isNullOrEmpty()) {
            binding.tvDescription.text = cr.description
            binding.tvDescription.visibility = View.VISIBLE
        } else {
            binding.tvDescription.visibility = View.GONE
        }

        if (!cr.notes.isNullOrEmpty()) {
            binding.tvNotes.text = cr.notes
            binding.tvNotes.visibility = View.VISIBLE
        } else {
            binding.tvNotes.visibility = View.GONE
        }

        applyFeasibilityBlock(cr)

        if (cr.status == "closed") {
            binding.blockClosed.visibility = View.VISIBLE
            binding.tvActNumber.text = "Акт: ${cr.act_number ?: "б/а"}"
            applyMaterials(cr.materials)

            val promoName = cr.act?.promotion_name
            val promoPrice = cr.act?.promotion_price
            if (promoName != null && promoPrice != null) {
                binding.tvPromotionBadge.text = "🎁 Акция «$promoName» — к оплате %.2f руб.".format(promoPrice)
                binding.tvPromotionBadge.visibility = View.VISIBLE
            } else {
                binding.tvPromotionBadge.visibility = View.GONE
            }

            if (cr.act != null) {
                binding.btnOpenAct.visibility = View.VISIBLE
                binding.btnOpenAct.setOnClickListener {
                    startActivity(Intent(this, com.sputnik.helpdesk.ui.act.ActActivity::class.java).apply {
                        putExtra(com.sputnik.helpdesk.ui.act.ActActivity.EXTRA_ACT_ID, cr.act.id)
                    })
                }
            } else {
                binding.btnOpenAct.visibility = View.GONE
            }
        } else {
            binding.blockClosed.visibility = View.GONE
        }

        applyActionPanel(cr)
    }

    private fun applyFeasibilityBlock(cr: ConnectionRequest) {
        when (cr.feasibility) {
            "possible" -> {
                binding.blockFeasibility.visibility = View.VISIBLE
                binding.blockFeasibility.setBackgroundColor(android.graphics.Color.parseColor("#0D2B1D"))
                binding.tvFeasibilityStatus.text = "✓ Подключение возможно"
                binding.tvFeasibilityStatus.setTextColor(android.graphics.Color.parseColor("#4ADE80"))
            }
            "impossible" -> {
                binding.blockFeasibility.visibility = View.VISIBLE
                binding.blockFeasibility.setBackgroundColor(android.graphics.Color.parseColor("#2D1414"))
                binding.tvFeasibilityStatus.text = "✕ Подключение невозможно"
                binding.tvFeasibilityStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"))
            }
            else -> {
                binding.blockFeasibility.visibility = View.GONE
            }
        }
        val comment = cr.feasibility_comment?.trim()?.takeIf { it.isNotEmpty() }
        if (comment != null && cr.feasibility != null) {
            binding.tvFeasibilityComment.text = comment
            binding.tvFeasibilityComment.visibility = View.VISIBLE
        } else {
            binding.tvFeasibilityComment.visibility = View.GONE
        }
    }

    private fun applyMaterials(materials: List<ConnectionMaterialLine>) {
        binding.layoutMaterials.removeAllViews()
        if (materials.isEmpty()) {
            binding.tvMaterialsHeader.visibility = View.GONE
            binding.tvTotal.visibility = View.GONE
            return
        }
        binding.tvMaterialsHeader.visibility = View.VISIBLE
        var total = 0.0
        materials.forEach { m ->
            val tv = TextView(this).apply {
                text = "${m.name} — ${m.quantity} ${m.unit} × ${m.price_at_time} = ${m.total} руб."
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#D1D5DB"))
            }
            binding.layoutMaterials.addView(tv)
            total += m.total
        }
        binding.tvTotal.text = "Итого: ${"%.2f".format(total)} руб."
        binding.tvTotal.visibility = View.VISIBLE
    }

    private fun applyActionPanel(cr: ConnectionRequest) {
        val status = cr.status
        val isFinal = status == "closed" || status == "rejected" || status == "cancelled"
        val isForeman = Prefs(this).userRole == "foreman"

        val needsFeasibility = status == "pending" && cr.feasibility == null
        val showCancel = isForeman && !isFinal
        binding.btnFeasibility.visibility = if (needsFeasibility) View.VISIBLE else View.GONE
        binding.btnClose.visibility    = if (status == "scheduled") View.VISIBLE else View.GONE
        binding.btnCancelConnection.visibility = if (showCancel) View.VISIBLE else View.GONE

        binding.panelActions.visibility =
            if (needsFeasibility || status == "scheduled" || showCancel) View.VISIBLE else View.GONE

        binding.btnFeasibility.setOnClickListener {
            FeasibilitySheet.newInstance(connectionId)
                .show(supportFragmentManager, "feasibility")
        }
        binding.btnClose.setOnClickListener    {
            CloseConnectionSheet.newInstance(connectionId)
                .show(supportFragmentManager, "close_conn")
        }
        binding.btnCancelConnection.setOnClickListener {
            CancelConnectionSheet.newInstance(connectionId)
                .show(supportFragmentManager, "cancel_conn")
        }
    }

    private fun formatDate(s: String): String {
        val norm = s.replace('T', ' ')
        runCatching {
            return LocalDateTime.parse(norm.take(16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru")))
        }
        return runCatching {
            LocalDate.parse(norm.take(10)).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
        }.getOrDefault(s)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_ID = "connection_id"
    }
}
