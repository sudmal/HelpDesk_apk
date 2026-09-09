package com.sputnik.helpdesk.ui.detail

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.CancelRequest
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetCancelTicketBinding
import com.sputnik.helpdesk.util.parseServerMessage
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CancelTicketSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCancelTicketBinding? = null
    private val binding get() = _binding!!
    private var ticketId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCancelTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ticketId = arguments?.getInt(ARG_TICKET_ID, -1) ?: -1
        binding.btnSubmitCancel.setOnClickListener { submitCancel() }
    }

    private fun submitCancel() {
        val comment = binding.etCancelComment.text?.toString()?.trim()
        if (comment.isNullOrEmpty()) {
            binding.etCancelComment.error = "Укажите причину отмены"
            return
        }

        val ctx = requireContext()
        setLoading(true)
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(ctx)).cancelTicket(ticketId, CancelRequest(comment))
                App.ticketUpdatedEvent.postValue(Unit)
                val bundle = Bundle().apply { putString("ticket_json", Gson().toJson(updated)) }
                parentFragmentManager.setFragmentResult("ticket_updated", bundle)
                dismiss()
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Log.e("CancelTicket", "HTTP ${e.code()}: $body")
                val msg = when (e.code()) {
                    403  -> parseServerMessage(body, "Нет прав для отмены заявки, либо заявка уже в финальном статусе")
                    422  -> "Укажите причину отмены"
                    else -> "Ошибка сервера ${e.code()}"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                setLoading(false)
            } catch (e: Exception) {
                Log.e("CancelTicket", "Unexpected error", e)
                Toast.makeText(ctx, "Ошибка: ${e.message ?: "нет связи с сервером"}", Toast.LENGTH_LONG).show()
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
        private const val ARG_TICKET_ID = "ticket_id"
        fun newInstance(ticketId: Int) = CancelTicketSheet().apply {
            arguments = Bundle().apply { putInt(ARG_TICKET_ID, ticketId) }
        }
    }
}
