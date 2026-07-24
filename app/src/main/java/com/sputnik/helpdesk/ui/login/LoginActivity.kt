package com.sputnik.helpdesk.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.LoginRequest
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityLoginBinding
import com.sputnik.helpdesk.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (prefs.isLoggedIn) { startMain(); return }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etServerUrl.setText(prefs.serverUrl)
        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        val login     = binding.etLogin.text.toString().trim()
        val password  = binding.etPassword.text.toString()

        if (serverUrl.isEmpty() || login.isEmpty() || password.isEmpty()) {
            Snackbar.make(binding.root, "Заполните все поля", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (!serverUrl.startsWith("https://")) {
            Snackbar.make(binding.root, "Адрес сервера должен начинаться с https://", Snackbar.LENGTH_LONG).show()
            return
        }

        prefs.serverUrl = serverUrl
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(prefs).login(LoginRequest(login, password))
                prefs.token    = response.token
                prefs.userName = response.user.name
                prefs.userRole = response.user.role
                startMain()
            } catch (e: Exception) {
                setLoading(false)
                val msg = when {
                    e.message?.contains("401") == true                       -> "Неверный логин или пароль"
                    e.message?.contains("Unable to resolve host") == true    -> "Нет подключения к серверу"
                    else -> "Ошибка подключения"
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled       = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
