package com.sputnik.helpdesk.ui.detail

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.PendingCommentEntity
import com.sputnik.helpdesk.data.model.Comment
import com.sputnik.helpdesk.data.model.Ticket
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityDetailBinding
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.ui.main.TicketAdapter
import com.sputnik.helpdesk.util.parseServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var attachmentAdapter: AttachmentAdapter
    private lateinit var commentPhotoAdapter: PhotoThumbnailAdapter
    private var ticketId: Int = -1

    private var pendingCameraUri: Uri? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Snackbar.make(binding.root, "Нет разрешения на камеру", Snackbar.LENGTH_SHORT).show()
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) pendingCameraUri?.let { addCommentPhoto(it) }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uris.forEach { addCommentPhoto(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ticketId = intent.getIntExtra(EXTRA_TICKET_ID, -1)
        if (ticketId == -1) { finish(); return }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        commentAdapter = CommentAdapter(Prefs(this).serverUrl)
        binding.recyclerComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerComments.adapter = commentAdapter

        attachmentAdapter = AttachmentAdapter(Prefs(this).serverUrl)
        binding.recyclerAttachments.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerAttachments.adapter = attachmentAdapter

        commentPhotoAdapter = PhotoThumbnailAdapter()
        binding.recyclerCommentPhotos.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerCommentPhotos.adapter = commentPhotoAdapter

        setupCopyOnLongPress()

        supportFragmentManager.setFragmentResultListener("ticket_updated", this) { _, bundle ->
            val json   = bundle.getString("ticket_json") ?: return@setFragmentResultListener
            val ticket = Gson().fromJson(json, Ticket::class.java)
            applyFreshTicket(ticket)
            updateCache(ticket)
        }

        loadTicket()
        binding.btnAttachPhoto.setOnClickListener { showPhotoSourceDialog() }
        binding.btnSendComment.setOnClickListener { sendComment() }
        binding.btnCloseTicket.setOnClickListener {
            CloseTicketSheet.newInstance(ticketId).show(supportFragmentManager, "close")
        }
        binding.btnReschedule.setOnClickListener {
            RescheduleSheet.newInstance(ticketId).show(supportFragmentManager, "reschedule")
        }
        binding.btnCancelTicket.setOnClickListener {
            CancelTicketSheet.newInstance(ticketId).show(supportFragmentManager, "cancel")
        }
        binding.btnReopenTicket.setOnClickListener { confirmReopen() }
    }

    private fun addCommentPhoto(uri: Uri) {
        commentPhotoAdapter.addPhotos(listOf(uri))
        binding.recyclerCommentPhotos.visibility = View.VISIBLE
    }

    private fun showPhotoSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Добавить фото")
            .setItems(arrayOf("Сделать фото", "Выбрать из галереи")) { _, which ->
                when (which) {
                    0 -> checkCameraAndLaunch()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun checkCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) launchCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val file = File(cacheDir, "photos").apply { mkdirs() }
            .let { File(it, "comment_${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    private fun setupCopyOnLongPress() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        binding.tvAddress.setOnLongClickListener {
            val text = binding.tvAddress.text.toString().takeIf { it.isNotEmpty() } ?: return@setOnLongClickListener false
            clipboard.setPrimaryClip(ClipData.newPlainText("address", text))
            Snackbar.make(binding.root, "Адрес скопирован", Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.tvPhone.setOnLongClickListener {
            val raw  = binding.tvPhone.text.toString()
            val text = raw.removePrefix("Тел: ").trim().takeIf { it.isNotEmpty() && it != "—" }
                ?: return@setOnLongClickListener false
            clipboard.setPrimaryClip(ClipData.newPlainText("phone", text))
            Snackbar.make(binding.root, "Телефон скопирован", Snackbar.LENGTH_SHORT).show()
            true
        }
    }

    private fun loadTicket() {
        lifecycleScope.launch {
            val entity = AppDatabase.get(this@DetailActivity).ticketDao().getById(ticketId)
                ?: return@launch

            supportActionBar?.title = entity.number
            with(binding) {
                tvAddress.text     = buildAddressText(entity.addressFull, entity.apartment)
                tvType.text        = "Тип: ${entity.typeName ?: "—"}"
                tvStatus.text      = "Статус: ${entity.statusName ?: "—"}"
                tvPhone.text       = "Тел: ${entity.phone ?: "—"}"
                tvTime.text        = "Время: ${formatTime(entity.scheduledAt)}"
                tvBrigade.text     = "Бригада: ${entity.brigade ?: "—"}"
                tvAssignee.text    = "Исполнитель: ${entity.assignee ?: "—"}"
                tvDescription.text = entity.description ?: ""
            }

            applyServiceTypeChip(entity.serviceTypeName, entity.serviceTypeColor)

            val cachedComments: List<Comment> = Gson().fromJson(
                entity.commentsJson,
                object : TypeToken<List<Comment>>() {}.type
            )
            commentAdapter.submitList(cachedComments)

            applyFinalState(
                isFinal    = entity.statusIsFinal,
                closedAt   = entity.closedAt,
                closedBy   = entity.closedBy,
                closeNotes = entity.closeNotes,
            )
            applyActBlock(entity.actId, entity.actNumber, promoName = null, promoPrice = null)
            applyCancelButtonVisibility(entity.statusIsFinal)
            // can.reopen приходит только со свежим объектом — до ответа сервера кнопку «Переоткрыть» прячем
            applyStatusActionsVisibility(entity.statusIsFinal, canReopen = false)

            try {
                val fresh = RetrofitClient.create(Prefs(this@DetailActivity)).getTicket(ticketId)
                applyFreshTicket(fresh)
            } catch (_: Exception) { }
        }
    }

    private fun applyFreshTicket(fresh: Ticket) {
        with(binding) {
            tvAddress.text  = buildAddressText(fresh.address?.full, fresh.apartment)
            tvTime.text     = "Время: ${formatTime(fresh.scheduled_at)}"
            tvStatus.text   = "Статус: ${fresh.status?.name ?: "—"}"
            tvBrigade.text  = "Бригада: ${fresh.brigade ?: "—"}"
            tvAssignee.text = "Исполнитель: ${fresh.assignee ?: "—"}"
        }

        applyServiceTypeChip(fresh.service_type?.name, fresh.service_type?.color)

        val isFinal = fresh.status?.is_final ?: false
        applyFinalState(isFinal, fresh.closed_at, fresh.closed_by, fresh.close_notes)
        applyActBlock(fresh.act?.id, fresh.act_number, fresh.act?.promotion_name, fresh.act?.promotion_price)
        applyCancelButtonVisibility(isFinal)
        // can отсутствует (старый бэкенд) -> показываем «Переоткрыть» на любой финальной заявке,
        // сервер всё равно проверит право и вернёт понятный 403. reopen == false -> прячем строго.
        applyStatusActionsVisibility(isFinal, canReopen = fresh.can?.reopen != false)

        if (fresh.comments.isNotEmpty()) commentAdapter.submitList(fresh.comments)

        if (fresh.attachments.isNotEmpty()) {
            attachmentAdapter.submitList(fresh.attachments)
            binding.blockAttachments.visibility = View.VISIBLE
        } else {
            binding.blockAttachments.visibility = View.GONE
        }
    }

    private fun updateCache(ticket: Ticket) {
        lifecycleScope.launch {
            val dao      = AppDatabase.get(this@DetailActivity).ticketDao()
            val existing = dao.getById(ticketId) ?: return@launch
            dao.insertAll(listOf(existing.copy(
                statusName    = ticket.status?.name,
                statusColor   = ticket.status?.color,
                statusIsFinal = ticket.status?.is_final ?: false,
                statusSlug    = ticket.status?.slug,
                closedAt      = ticket.closed_at,
                closedBy      = ticket.closed_by,
                closeNotes    = ticket.close_notes,
                actNumber     = ticket.act_number,
                actId         = ticket.act?.id,
                actStatus     = ticket.act?.status,
                actMaterialsChangedAt = ticket.act?.materials_changed_at,
                scheduledAt   = ticket.scheduled_at,
                commentsJson  = Gson().toJson(ticket.comments),
            )))
        }
    }

    private fun applyActBlock(actId: Int?, actNumber: String?, promoName: String?, promoPrice: Double?) {
        if (actId != null) {
            binding.blockAct.visibility = View.VISIBLE
            binding.tvActNumber.text    = "Акт: ${actNumber ?: "б/а"}"

            binding.btnOpenAct.visibility = View.VISIBLE
            binding.btnOpenAct.setOnClickListener {
                startActivity(Intent(this@DetailActivity, com.sputnik.helpdesk.ui.act.ActActivity::class.java).apply {
                    putExtra(com.sputnik.helpdesk.ui.act.ActActivity.EXTRA_ACT_ID, actId)
                })
            }

            if (promoName != null && promoPrice != null) {
                binding.tvPromotionBadge.text = "🎁 Акция «$promoName» — к оплате %.2f руб.".format(promoPrice)
                binding.tvPromotionBadge.visibility = View.VISIBLE
            } else {
                binding.tvPromotionBadge.visibility = View.GONE
            }
        } else {
            binding.blockAct.visibility = View.GONE
        }
    }

    private fun applyCancelButtonVisibility(isFinal: Boolean) {
        val isForeman = Prefs(this).userRole == "foreman"
        binding.btnCancelTicket.visibility =
            if (isForeman && !isFinal) View.VISIBLE else View.GONE
    }

    /**
     * Финальная заявка: «Закрыть»/«Перенести» скрываем (сервер их всё равно
     * отклонит), вместо них — «Переоткрыть». Рабочая заявка — наоборот.
     */
    private fun applyStatusActionsVisibility(isFinal: Boolean, canReopen: Boolean) {
        binding.btnCloseTicket.visibility = if (isFinal) View.GONE else View.VISIBLE
        binding.btnReschedule.visibility  = if (isFinal) View.GONE else View.VISIBLE
        binding.btnReopenTicket.visibility = if (isFinal && canReopen) View.VISIBLE else View.GONE
    }

    private fun confirmReopen() {
        AlertDialog.Builder(this)
            .setTitle("Переоткрыть заявку?")
            .setMessage("Заявка вернётся в статус «Новая». Её можно будет закрыть заново — например, чтобы указать материалы и тип акта.")
            .setPositiveButton("Переоткрыть") { _, _ -> reopenTicket() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun reopenTicket() {
        binding.btnReopenTicket.isEnabled = false
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(this@DetailActivity)).reopenTicket(ticketId)
                App.ticketUpdatedEvent.postValue(Unit)
                applyFreshTicket(updated)
                updateCache(updated)
                Snackbar.make(binding.root, "Заявка переоткрыта", Snackbar.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Log.e("DetailActivity", "reopen HTTP ${e.code()}: $body")
                val msg = when (e.code()) {
                    403  -> parseServerMessage(body, "Нет прав для переоткрытия заявки")
                    else -> "Ошибка сервера ${e.code()}"
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("DetailActivity", "reopen error", e)
                Snackbar.make(binding.root, "Ошибка: ${e.message ?: "нет связи с сервером"}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.btnReopenTicket.isEnabled = true
            }
        }
    }

    private fun applyFinalState(isFinal: Boolean, closedAt: String?, closedBy: String?, closeNotes: String?) {
        // Комментарии доступны независимо от статуса заявки — сервер их по статусу не ограничивает.
        binding.panelComment.visibility = View.VISIBLE

        if (isFinal) {
            binding.blockClosed.visibility  = View.VISIBLE
            binding.tvClosedAt.text         = "Закрыто: ${formatDateTime(closedAt) ?: "—"}"

            val hasCloseSection = !closeNotes.isNullOrEmpty() || closedBy != null
            binding.tvCloseNotesLabel.visibility = if (hasCloseSection) View.VISIBLE else View.GONE

            val byAtText = if (closedBy != null) buildClosedByAtText(closedBy, closedAt) else null
            if (byAtText != null) {
                binding.tvClosedByAt.text       = byAtText
                binding.tvClosedByAt.visibility = View.VISIBLE
            } else {
                binding.tvClosedByAt.visibility = View.GONE
            }

            if (!closeNotes.isNullOrEmpty()) {
                binding.tvCloseNotes.text       = closeNotes
                binding.tvCloseNotes.visibility = View.VISIBLE
            } else {
                binding.tvCloseNotes.visibility = View.GONE
            }
        } else {
            binding.blockClosed.visibility       = View.GONE
        }
    }

    private fun buildAddressText(addressFull: String?, apartment: String?): String {
        val parts = buildList {
            addressFull?.let { add(it) }
            apartment?.let { add("кв.$it") }
        }
        return parts.joinToString(", ").ifEmpty { "Адрес не указан" }
    }

    private fun buildClosedByAtText(closedBy: String, closedAt: String?): String {
        val date = formatDateTimeShort(closedAt)
        return if (date != null) "$closedBy · $date" else closedBy
    }

    private fun formatDateTimeShort(s: String?): String? {
        if (s == null) return null
        return try {
            TicketAdapter.parseIsoToLocal(s)
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } catch (_: Exception) { null }
    }

    private fun applyServiceTypeChip(name: String?, color: String?) {
        if (!name.isNullOrEmpty()) {
            binding.tvServiceTypeDetail.text = name
            try {
                binding.tvServiceTypeDetail.setBackgroundColor(Color.parseColor(color ?: "#6B7280"))
            } catch (_: Exception) {
                binding.tvServiceTypeDetail.setBackgroundColor(Color.parseColor("#6B7280"))
            }
            binding.tvServiceTypeDetail.visibility = View.VISIBLE
        } else {
            binding.tvServiceTypeDetail.visibility = View.GONE
        }
    }

    private fun formatTime(s: String?): String {
        if (s == null) return "—"
        return try {
            TicketAdapter.parseIsoToLocal(s).format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) { s.take(16).replace("T", " ") }
    }

    private fun formatDateTime(s: String?): String? {
        if (s == null) return null
        return try {
            TicketAdapter.parseIsoToLocal(s)
                .format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru")))
        } catch (_: Exception) { s.take(16).replace("T", " ") }
    }

    private fun sendComment() {
        val body   = binding.etComment.text.toString().trim()
        val photos = commentPhotoAdapter.getPhotos()
        if (body.isEmpty() && photos.isEmpty()) return

        binding.btnSendComment.isEnabled  = false
        binding.btnAttachPhoto.isEnabled  = false
        val plain = "text/plain".toMediaType()

        lifecycleScope.launch {
            try {
                val api    = RetrofitClient.create(Prefs(this@DetailActivity))
                val fields = mutableMapOf<String, RequestBody>()
                if (body.isNotEmpty()) fields["body"] = body.toRequestBody(plain)

                val attachmentParts = withContext(Dispatchers.IO) {
                    photos.mapNotNull { uri ->
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@mapNotNull null
                        MultipartBody.Part.createFormData(
                            "attachments[]", "photo_${System.currentTimeMillis()}.jpg",
                            bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        )
                    }
                }

                val result = api.addComment(ticketId, fields, attachmentParts)
                binding.etComment.text?.clear()
                commentPhotoAdapter.clearPhotos()
                binding.recyclerCommentPhotos.visibility = View.GONE

                val updated = commentAdapter.currentList.toMutableList()
                updated.add(0, Comment(result.id, result.body, result.author, result.created_at, result.attachments))
                commentAdapter.submitList(updated)
                binding.recyclerComments.scrollToPosition(0)

                Snackbar.make(binding.root, "Комментарий добавлен", Snackbar.LENGTH_SHORT).show()
            } catch (_: Exception) {
                if (photos.isEmpty()) {
                    AppDatabase.get(this@DetailActivity).pendingCommentDao()
                        .insert(PendingCommentEntity(ticketId = ticketId, body = body))
                    Snackbar.make(binding.root, "Нет связи — комментарий отправится при следующей синхронизации", Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(binding.root, "Ошибка отправки", Snackbar.LENGTH_LONG).show()
                }
            } finally {
                binding.btnSendComment.isEnabled = true
                binding.btnAttachPhoto.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_TICKET_ID = "ticket_id"
    }
}
