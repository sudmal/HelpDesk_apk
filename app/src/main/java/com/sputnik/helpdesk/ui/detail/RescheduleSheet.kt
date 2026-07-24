package com.sputnik.helpdesk.ui.detail

import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.gson.Gson
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.RescheduleRequest
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetRescheduleBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RescheduleSheet : BottomSheetDialogFragment() {

    private var _binding: SheetRescheduleBinding? = null
    private val binding get() = _binding!!
    private var ticketId: Int = -1
    private var selectedDate: LocalDate? = null
    private var selectedHour: Int = 9
    private var selectedMinute: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetRescheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ticketId = arguments?.getInt(ARG_TICKET_ID, -1) ?: -1
        binding.etScheduledAt.setOnClickListener { showDatePicker() }
        binding.btnSubmitReschedule.setOnClickListener { submitReschedule() }
    }

    private fun showDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Новая дата")
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { epochMs ->
            selectedDate = Instant.ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            showTimePicker()
        }
        picker.show(parentFragmentManager, "date_picker")
    }

    private fun showTimePicker() {
        TimePickerDialog(requireContext(), { _, hour, minute ->
            selectedHour = hour
            selectedMinute = minute
            updateDateDisplay()
        }, selectedHour, selectedMinute, true).show()
    }

    private fun updateDateDisplay() {
        val date = selectedDate ?: return
        val ldt = LocalDateTime.of(date, LocalTime.of(selectedHour, selectedMinute))
        binding.etScheduledAt.setText(
            ldt.format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru")))
        )
    }

    private fun submitReschedule() {
        val date = selectedDate
        if (date == null) {
            Toast.makeText(requireContext(), "Выберите дату и время", Toast.LENGTH_SHORT).show()
            return
        }

        val ldt = LocalDateTime.of(date, LocalTime.of(selectedHour, selectedMinute))
        val isoString = ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val comment   = binding.etRescheduleComment.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val request   = RescheduleRequest(scheduled_at = isoString, comment = comment)
        val ctx       = requireContext()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(ctx)).rescheduleTicket(ticketId, request)
                App.ticketUpdatedEvent.postValue(Unit)
                val bundle = Bundle().apply { putString("ticket_json", Gson().toJson(updated)) }
                parentFragmentManager.setFragmentResult("ticket_updated", bundle)
                dismiss()
            } catch (e: HttpException) {
                Log.e("RescheduleSheet", "HTTP ${e.code()}: ${e.response()?.errorBody()?.string()}")
                val msg = when (e.code()) {
                    403  -> "Нет прав для переноса заявки"
                    422  -> "Неверная дата — должна быть позже текущего момента"
                    else -> "Ошибка сервера ${e.code()}"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                setLoading(false)
            } catch (e: Exception) {
                Log.e("RescheduleSheet", "Unexpected error", e)
                Toast.makeText(ctx, "Ошибка: ${e.message ?: "нет связи с сервером"}", Toast.LENGTH_LONG).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSubmitReschedule.isEnabled = !loading
        binding.btnSubmitReschedule.text = if (loading) "Отправка..." else "Перенести"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TICKET_ID = "ticket_id"
        fun newInstance(ticketId: Int) = RescheduleSheet().apply {
            arguments = Bundle().apply { putInt(ARG_TICKET_ID, ticketId) }
        }
    }
}
