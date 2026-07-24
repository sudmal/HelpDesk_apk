package com.sputnik.helpdesk.ui.connections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.CRTerritory
import com.sputnik.helpdesk.data.model.CreateConnectionReq
import com.sputnik.helpdesk.data.model.ServiceType
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetCreateConnectionBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CreateConnectionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCreateConnectionBinding? = null
    private val binding get() = _binding!!

    private var territories: List<CRTerritory> = emptyList()
    private var serviceTypes: List<ServiceType> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCreateConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        @Suppress("UNCHECKED_CAST")
        territories = arguments?.getSerializable(ARG_TERRITORIES) as? List<CRTerritory> ?: emptyList()

        val names = territories.map { it.name }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerTerritory.adapter = spinnerAdapter

        lifecycleScope.launch {
            try {
                serviceTypes = RetrofitClient.create(Prefs(requireContext())).getServiceTypes()
                val stAdapter = ArrayAdapter(
                    requireContext(), android.R.layout.simple_spinner_item, serviceTypes.map { it.name }
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                binding.spinnerServiceType.adapter = stAdapter
            } catch (_: Exception) { /* участок необязателен при создании — оставляем пустым */ }
        }

        binding.btnCreate.setOnClickListener { submit() }
    }

    private fun submit() {
        val name    = binding.etName.text?.toString()?.trim() ?: ""
        val phone   = binding.etPhone.text?.toString()?.trim() ?: ""
        val address = binding.etAddress.text?.toString()?.trim() ?: ""
        val desc    = binding.etDescription.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val selectedIdx = binding.spinnerTerritory.selectedItemPosition

        if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
            return
        }
        if (territories.isEmpty() || selectedIdx < 0) {
            Toast.makeText(requireContext(), "Нет доступных территорий", Toast.LENGTH_SHORT).show()
            return
        }

        val territoryId = territories[selectedIdx].id
        val serviceTypeId = binding.spinnerServiceType.selectedItemPosition
            .takeIf { it >= 0 && it < serviceTypes.size }
            ?.let { serviceTypes[it].id }

        setLoading(true)
        val ctx = requireContext()
        lifecycleScope.launch {
            try {
                RetrofitClient.create(Prefs(ctx)).createConnectionRequest(
                    CreateConnectionReq(name, phone, address, territoryId, desc, serviceTypeId)
                )
                parentFragmentManager.setFragmentResult("connection_created", Bundle())
                dismiss()
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Toast.makeText(ctx, "Ошибка ${e.code()}: ${body?.take(100)}", Toast.LENGTH_LONG).show()
                setLoading(false)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Нет соединения", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnCreate.isEnabled = !loading
        binding.btnCreate.text = if (loading) "Создание..." else "Создать заявку"
        binding.progressCreate.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TERRITORIES = "territories"

        fun newInstance(territories: List<CRTerritory>) = CreateConnectionSheet().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_TERRITORIES, ArrayList(territories))
            }
        }
    }
}
