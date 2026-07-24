package com.sputnik.helpdesk.ui.connections

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.toEntity
import com.sputnik.helpdesk.data.model.FeasibilityReq
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.SheetFeasibilityBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class FeasibilitySheet : BottomSheetDialogFragment() {

    private var _binding: SheetFeasibilityBinding? = null
    private val binding get() = _binding!!
    private var connectionId: Int = -1
    private var selectedAnswer: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetFeasibilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        connectionId = arguments?.getInt(ARG_ID, -1) ?: -1

        binding.btnPossible.setOnClickListener { selectAnswer(ANSWER_POSSIBLE) }
        binding.btnImpossible.setOnClickListener { selectAnswer(ANSWER_IMPOSSIBLE) }
        binding.btnSubmitFeasibility.setOnClickListener { submit() }
    }

    private fun selectAnswer(answer: String) {
        selectedAnswer = answer
        applySelectionStyle(binding.btnPossible, answer == ANSWER_POSSIBLE, "#4ADE80")
        applySelectionStyle(binding.btnImpossible, answer == ANSWER_IMPOSSIBLE, "#EF4444")
        binding.btnSubmitFeasibility.isEnabled = true
        binding.btnSubmitFeasibility.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(if (answer == ANSWER_POSSIBLE) "#10B981" else "#EF4444")
        )
    }

    private fun applySelectionStyle(button: MaterialButton, selected: Boolean, color: String) {
        if (selected) {
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
            button.setTextColor(Color.WHITE)
        } else {
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            button.setTextColor(Color.parseColor(color))
        }
    }

    private fun submit() {
        val answer = selectedAnswer ?: return
        val comment = binding.etComment.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        binding.tvError.visibility = View.GONE
        setLoading(true)
        val ctx = requireContext()
        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(Prefs(ctx))
                    .answerFeasibility(connectionId, FeasibilityReq(answer, comment))
                AppDatabase.get(ctx).connectionRequestDao().upsert(updated.toEntity())
                App.connectionUpdatedEvent.postValue(Unit)
                parentFragmentManager.setFragmentResult(
                    "connection_feasibility_answered",
                    Bundle().apply { putInt("connection_id", updated.id) }
                )
                dismiss()
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    403 -> "Ответить может только монтажник."
                    else -> {
                        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                        "Ошибка: ${body?.take(120) ?: "код ${e.code()}"}"
                    }
                }
                binding.tvError.text = msg
                binding.tvError.visibility = View.VISIBLE
                setLoading(false)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Нет соединения", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSubmitFeasibility.isEnabled = !loading && selectedAnswer != null
        binding.btnSubmitFeasibility.text = if (loading) "Отправка..." else "Отправить"
        binding.progressSubmit.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnPossible.isEnabled = !loading
        binding.btnImpossible.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "connection_id"
        const val ANSWER_POSSIBLE = "possible"
        const val ANSWER_IMPOSSIBLE = "impossible"

        fun newInstance(id: Int) = FeasibilitySheet().apply {
            arguments = Bundle().apply { putInt(ARG_ID, id) }
        }
    }
}
