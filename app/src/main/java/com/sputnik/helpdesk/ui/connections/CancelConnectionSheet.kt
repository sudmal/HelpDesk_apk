package com.sputnik.helpdesk.ui.connections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.toEntity
import com.sputnik.helpdesk.data.model.CancelRequest
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetCancelConnectionBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CancelConnectionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCancelConnectionBinding? = null
    private val binding get() = _binding!!
    private var connectionId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCancelConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        connectionId = arguments?.getInt(ARG_ID, -1) ?: -1
        binding.btnSubmitCancel.setOnClickListener { submitCancel() }
    }

    private fun submitCancel() {
        val comment = binding.etCancelComment.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val ctx = requireContext()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(ctx))
                    .cancelConnectionRequest(connectionId, CancelRequest(comment))
                AppDatabase.get(ctx).connectionRequestDao().upsert(updated.toEntity())
                App.connectionUpdatedEvent.postValue(Unit)
                parentFragmentManager.setFragmentResult("connection_cancelled", Bundle())
                dismiss()
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    422  -> "Заявка уже в финальном статусе"
                    else -> "Ошибка сервера ${e.code()}"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                setLoading(false)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Нет соединения", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSubmitCancel.isEnabled = !loading
        binding.btnSubmitCancel.text = if (loading) "Отправка..." else "Отменить заявку"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "connection_id"
        fun newInstance(id: Int) = CancelConnectionSheet().apply {
            arguments = Bundle().apply { putInt(ARG_ID, id) }
        }
    }
}
