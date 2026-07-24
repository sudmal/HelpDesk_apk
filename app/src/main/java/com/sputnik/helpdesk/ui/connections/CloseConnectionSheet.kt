package com.sputnik.helpdesk.ui.connections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.toEntity
import com.sputnik.helpdesk.data.model.CloseConnectionReq
import com.sputnik.helpdesk.data.model.MaterialItem
import com.sputnik.helpdesk.data.model.Promotion
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetCloseConnectionBinding
import com.sputnik.helpdesk.ui.detail.MaterialPickerAdapter
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CloseConnectionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCloseConnectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var materialAdapter: MaterialPickerAdapter
    private var connectionId: Int = -1
    private var promotions: List<Promotion> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCloseConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        connectionId = arguments?.getInt(ARG_ID, -1) ?: -1

        materialAdapter = MaterialPickerAdapter(emptyList()) { updateTotal() }
        binding.rvMaterials.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMaterials.adapter = materialAdapter
        binding.rvMaterials.isNestedScrollingEnabled = false

        lifecycleScope.launch {
            val materials = AppDatabase.get(requireContext()).materialDao().getAll()
            materialAdapter.updateCatalog(materials)
        }

        lifecycleScope.launch {
            try {
                promotions = RetrofitClient.create(Prefs(requireContext())).getPromotions()
                setupPromotionSpinner()
                updateTotal()
            } catch (_: Exception) { /* акции необязательны — селектор просто не показываем */ }
        }

        binding.spinnerPromotion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePromotionPay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnAddMaterial.setOnClickListener {
            materialAdapter.addRow()
            updateTotal()
        }

        binding.btnSubmitClose.setOnClickListener { submit() }
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

    private fun updateTotal() {
        val total = materialAdapter.getTotal()
        if (materialAdapter.itemCount > 0 && total > 0.0) {
            binding.tvTotal.text = "Материалы: %.2f руб.".format(total)
            binding.tvTotal.visibility = View.VISIBLE
        } else {
            binding.tvTotal.visibility = View.GONE
        }

        val showPromotion = materialAdapter.itemCount > 0 && promotions.isNotEmpty()
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

    private fun submit() {
        val items: List<MaterialItem> = materialAdapter.getItems() ?: run {
            Toast.makeText(requireContext(), "Заполните все поля материалов", Toast.LENGTH_SHORT).show()
            return
        }
        val notes = binding.etNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val promotionId = if (binding.spinnerPromotion.visibility == View.VISIBLE) selectedPromotion()?.id else null

        binding.tvError.visibility = View.GONE
        setLoading(true)
        val ctx = requireContext()
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(ctx))
                    .closeConnectionRequest(connectionId, CloseConnectionReq(notes, items, promotionId))
                AppDatabase.get(ctx).connectionRequestDao().upsert(updated.toEntity())
                App.connectionUpdatedEvent.postValue(Unit)
                parentFragmentManager.setFragmentResult("connection_closed", Bundle())
                dismiss()
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val serviceTypeError = body?.let { extractServiceTypeError(it) }
                if (serviceTypeError != null) {
                    binding.tvError.text = serviceTypeError
                    binding.tvError.visibility = View.VISIBLE
                } else {
                    val msg = when (e.code()) {
                        422  -> "Ошибка: ${body?.take(120) ?: "проверьте поля"}"
                        else -> "Ошибка сервера ${e.code()}"
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }
                setLoading(false)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Нет соединения", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    private fun extractServiceTypeError(errorBody: String): String? = runCatching {
        val json = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
        val errors = json.getAsJsonObject("errors") ?: return null
        val fieldErrors = errors.getAsJsonArray("service_type_id") ?: return null
        fieldErrors.firstOrNull()?.asString
    }.getOrNull()

    private fun setLoading(loading: Boolean) {
        binding.btnSubmitClose.isEnabled = !loading
        binding.btnSubmitClose.text = if (loading) "Отправка..." else "Завершить"
        binding.progressSubmit.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "connection_id"
        fun newInstance(id: Int) = CloseConnectionSheet().apply {
            arguments = Bundle().apply { putInt(ARG_ID, id) }
        }
    }
}
