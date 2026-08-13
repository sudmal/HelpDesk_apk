package com.sputnik.helpdesk.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.model.Promotion
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetCloseTicketBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File

class CloseTicketSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCloseTicketBinding? = null
    private val binding get() = _binding!!

    private lateinit var materialAdapter: MaterialPickerAdapter
    private lateinit var photoAdapter: PhotoThumbnailAdapter
    private var ticketId: Int = -1
    private var pendingCameraUri: Uri? = null
    private var promotions: List<Promotion> = emptyList()
    private var typeAllowsPromotion: Boolean = false

    // --- Activity result launchers (must be registered before onStart) ---

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Snackbar.make(binding.root, "Нет разрешения на камеру", Snackbar.LENGTH_SHORT).show()
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) pendingCameraUri?.let { photoAdapter.addPhotos(listOf(it)) }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) photoAdapter.addPhotos(uris)
        }

    // ---

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCloseTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ticketId = arguments?.getInt(ARG_TICKET_ID, -1) ?: -1

        materialAdapter = MaterialPickerAdapter(emptyList()) {
            updateActTypeVisibility()
            updateTotal()
            updatePromotionVisibility()
        }
        binding.rvMaterials.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMaterials.adapter = materialAdapter
        binding.rvMaterials.isNestedScrollingEnabled = false

        photoAdapter = PhotoThumbnailAdapter()
        binding.rvPhotos.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPhotos.adapter = photoAdapter

        lifecycleScope.launch {
            val materials = AppDatabase.get(requireContext()).materialDao().getAll()
            materialAdapter.updateCatalog(materials)
        }

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(Prefs(requireContext()))
                val ticket = api.getTicket(ticketId)
                if (ticket.type_allows_promotion) {
                    typeAllowsPromotion = true
                    promotions = api.getPromotions()
                    setupPromotionSpinner()
                    updatePromotionVisibility()
                }
            } catch (_: Exception) { /* акции необязательны — селектор просто не показываем */ }
        }

        binding.rgActType.setOnCheckedChangeListener { _, _ -> updatePromotionVisibility() }

        binding.spinnerPromotion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePromotionPay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnAddMaterial.setOnClickListener { materialAdapter.addRow() }

        binding.btnAddPhoto.setOnClickListener { showPhotoSourceDialog() }

        binding.btnSubmitClose.setOnClickListener { submitClose() }
    }

    private fun updateActTypeVisibility() {
        binding.layoutActType.visibility =
            if (materialAdapter.itemCount > 0) View.VISIBLE else View.GONE
    }

    private fun selectedActType(): String? = when (binding.rgActType.checkedRadioButtonId) {
        binding.rbActRegular.id -> "regular"
        binding.rbActRepair.id  -> "repair"
        else                    -> null
    }

    private fun setupPromotionSpinner() {
        val names = listOf("Без акции") + promotions.map { "${it.name} — %.2f руб.".format(it.price) }
        binding.spinnerPromotion.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, names
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun selectedPromotion(): Promotion? {
        val pos = binding.spinnerPromotion.selectedItemPosition
        return promotions.getOrNull(pos - 1)
    }

    private fun updatePromotionVisibility() {
        val showPromotion = typeAllowsPromotion &&
            promotions.isNotEmpty() &&
            materialAdapter.itemCount > 0 &&
            selectedActType() == "regular"
        binding.tvPromotionLabel.visibility = if (showPromotion) View.VISIBLE else View.GONE
        binding.spinnerPromotion.visibility = if (showPromotion) View.VISIBLE else View.GONE
        if (!showPromotion) {
            binding.spinnerPromotion.setSelection(0)
        }
        updatePromotionPay()
    }

    private fun updatePromotionPay() {
        val promo = selectedPromotion()
        if (binding.spinnerPromotion.visibility == View.VISIBLE && promo != null) {
            binding.tvPromotionPay.text = "Абонент платит: %.2f руб.".format(promo.price)
            binding.tvPromotionPay.visibility = View.VISIBLE
        } else {
            binding.tvPromotionPay.visibility = View.GONE
        }
    }

    private fun updateTotal() {
        val total = materialAdapter.getTotal()
        if (materialAdapter.itemCount > 0 && total > 0.0) {
            binding.tvTotal.text = "Итого: %.2f руб.".format(total)
            binding.tvTotal.visibility = View.VISIBLE
        } else {
            binding.tvTotal.visibility = View.GONE
        }
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Добавить фото")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoDir = File(requireContext().cacheDir, "photos").apply { mkdirs() }
        val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    private fun submitClose() {
        val items = materialAdapter.getItems()
        if (items == null) {
            Snackbar.make(binding.root, "Заполните все поля материалов корректно", Snackbar.LENGTH_SHORT).show()
            return
        }

        val actType: String? = selectedActType()
        if (items.isNotEmpty() && actType == null) {
            Snackbar.make(binding.root, "Выберите тип акта", Snackbar.LENGTH_SHORT).show()
            return
        }

        val notes  = binding.etCloseNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val photos = photoAdapter.getPhotos()
        val promotionId = if (binding.spinnerPromotion.visibility == View.VISIBLE) selectedPromotion()?.id else null

        setLoading(true)
        val ctx = requireContext()

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(Prefs(ctx))

                val plain = "text/plain".toMediaType()

                val fields = mutableMapOf<String, RequestBody>()
                notes?.let       { fields["close_notes"]  = it.toRequestBody(plain) }
                actType?.let     { fields["act_type"]     = it.toRequestBody(plain) }
                promotionId?.let { fields["promotion_id"] = it.toString().toRequestBody(plain) }
                items.forEachIndexed { i, item ->
                    fields["materials[$i][material_id]"] = item.material_id.toString().toRequestBody(plain)
                    fields["materials[$i][quantity]"]    = item.quantity.toString().toRequestBody(plain)
                }

                val attachmentParts = withContext(Dispatchers.IO) {
                    photos.mapNotNull { uri ->
                        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@mapNotNull null
                        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        MultipartBody.Part.createFormData(
                            "attachments[]", "photo_${System.currentTimeMillis()}.jpg", body
                        )
                    }
                }

                val updated = api.closeTicket(ticketId, fields, attachmentParts)
                App.ticketUpdatedEvent.postValue(Unit)
                val bundle = Bundle().apply { putString("ticket_json", Gson().toJson(updated)) }
                parentFragmentManager.setFragmentResult("ticket_updated", bundle)
                updated.act?.let { act ->
                    Toast.makeText(ctx, "Акт № ${act.number} создан, отправлен бригадиру на утверждение", Toast.LENGTH_LONG).show()
                }
                dismiss()
            } catch (e: HttpException) {
                Log.e("CloseTicket", "HTTP ${e.code()}: ${e.response()?.errorBody()?.string()}")
                val serverDetail = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val msg = when (e.code()) {
                    403  -> "Нет прав для закрытия заявки"
                    422  -> serverDetail?.let { extractFieldError(it, "promotion_id") }
                        ?: "Ошибка: ${serverDetail?.take(120) ?: "проверьте заполненные поля"}"
                    else -> "Ошибка сервера ${e.code()}"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                setLoading(false)
            } catch (e: Exception) {
                Log.e("CloseTicket", "Unexpected error", e)
                Toast.makeText(ctx, "Ошибка: ${e.message ?: "нет связи с сервером"}", Toast.LENGTH_LONG).show()
                setLoading(false)
            }
        }
    }

    private fun extractFieldError(errorBody: String, field: String): String? = runCatching {
        val json = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
        val errors = json.getAsJsonObject("errors") ?: return null
        val fieldErrors = errors.getAsJsonArray(field) ?: return null
        fieldErrors.firstOrNull()?.asString
    }.getOrNull()

    private fun setLoading(loading: Boolean) {
        binding.btnSubmitClose.isEnabled = !loading
        binding.btnSubmitClose.text = if (loading) "Отправка..." else "Закрыть заявку"
        binding.progressSubmit.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TICKET_ID = "ticket_id"
        fun newInstance(ticketId: Int) = CloseTicketSheet().apply {
            arguments = Bundle().apply { putInt(ARG_TICKET_ID, ticketId) }
        }
    }
}
