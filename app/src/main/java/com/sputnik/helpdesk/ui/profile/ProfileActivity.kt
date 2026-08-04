package com.sputnik.helpdesk.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.Profile
import com.sputnik.helpdesk.data.model.UpdateProfileReq
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "Профиль"
            setDisplayHomeAsUpEnabled(true)
        }

        prefs = Prefs(this)

        App.sessionExpiredEvent.observe(this) { event ->
            event ?: return@observe
            App.sessionExpiredEvent.value = null
            startActivity(Intent(this, com.sputnik.helpdesk.ui.login.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.btnSaveProfile.setOnClickListener { save() }

        load()
    }

    private fun load() {
        binding.progressProfile.visibility = View.VISIBLE
        binding.scrollProfile.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val profile = RetrofitClient.create(prefs).getProfile()
                applyData(profile)
                binding.scrollProfile.visibility = View.VISIBLE
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Ошибка загрузки профиля", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressProfile.visibility = View.GONE
            }
        }
    }

    private fun applyData(profile: Profile) {
        binding.etName.setText(profile.name)
        binding.etPhone.setText(profile.phone)
        binding.etEmail.setText(profile.email)
        binding.etTelegramChatId.setText(profile.telegram_chat_id)
        binding.etMaxChatId.setText(profile.max_chat_id)
        binding.switchNotifyDaysOff.isChecked = profile.notify_on_days_off
    }

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.etName.error = "Обязательное поле"
            return
        }

        binding.tvProfileError.visibility = View.GONE
        binding.btnSaveProfile.isEnabled = false

        val request = UpdateProfileReq(
            name = name,
            phone = binding.etPhone.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            email = binding.etEmail.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            telegram_chat_id = binding.etTelegramChatId.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            max_chat_id = binding.etMaxChatId.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            notify_on_days_off = binding.switchNotifyDaysOff.isChecked,
        )

        lifecycleScope.launch {
            try {
                val updated = RetrofitClient.create(prefs).updateProfile(request)
                prefs.userName = updated.name
                applyData(updated)
                Snackbar.make(binding.root, "Профиль сохранён", Snackbar.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                showApiError(e)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.btnSaveProfile.isEnabled = true
            }
        }
    }

    private fun showApiError(e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val msg = when (e.code()) {
            422 -> extractErrorMessage(body) ?: "Проверьте заполненные поля"
            else -> "Ошибка сервера ${e.code()}"
        }
        binding.tvProfileError.text = msg
        binding.tvProfileError.visibility = View.VISIBLE
    }

    private fun extractErrorMessage(errorBody: String?): String? = runCatching {
        errorBody ?: return null
        val json = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
        val errors = json.getAsJsonObject("errors")
        val firstFieldError = errors?.entrySet()?.firstOrNull()?.value?.asJsonArray?.firstOrNull()?.asString
        firstFieldError ?: json.get("message")?.asString
    }.getOrNull()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
