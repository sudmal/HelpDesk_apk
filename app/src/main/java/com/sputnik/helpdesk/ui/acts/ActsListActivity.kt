package com.sputnik.helpdesk.ui.acts

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.db.AppDatabase
import com.sputnik.helpdesk.data.db.toEntity
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityActsListBinding
import com.sputnik.helpdesk.ui.act.ActActivity
import kotlinx.coroutines.launch

class ActsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActsListBinding
    private lateinit var adapter: ActsAdapter
    private var hasLoadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Акты"

        App.sessionExpiredEvent.observe(this) { event ->
            event ?: return@observe
            App.sessionExpiredEvent.value = null
            startActivity(Intent(this, com.sputnik.helpdesk.ui.login.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        App.actUpdatedEvent.observe(this) { event ->
            event ?: return@observe
            App.actUpdatedEvent.value = null
            syncFromServer()
        }

        adapter = ActsAdapter { act ->
            startActivity(Intent(this, ActActivity::class.java).apply {
                putExtra(ActActivity.EXTRA_ACT_ID, act.id)
            })
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { syncFromServer() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.get(this@ActsListActivity).actDao().getAllFlow()
                    .collect { items ->
                        val sorted = items.sortedByDescending {
                            it.status == "pending_foreman" && it.canForemanReview
                        }
                        adapter.submitList(sorted)
                        if (!hasLoadedOnce) {
                            hasLoadedOnce = true
                            binding.progressInitial.visibility = View.GONE
                        }
                        binding.layoutEmpty.visibility =
                            if (sorted.isEmpty() && hasLoadedOnce) View.VISIBLE else View.GONE
                    }
            }
        }

        binding.progressInitial.visibility = View.VISIBLE
        syncFromServer()
    }

    private fun syncFromServer() {
        val prefs = Prefs(this)
        if (!prefs.isLoggedIn) { binding.swipeRefresh.isRefreshing = false; return }

        lifecycleScope.launch {
            try {
                val acts = RetrofitClient.create(prefs).getActs().data
                val db = AppDatabase.get(this@ActsListActivity)
                db.actDao().deleteAll()
                db.actDao().insertAll(acts.map { it.toEntity() })
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Нет соединения, показаны кешированные данные", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
                binding.progressInitial.visibility = View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
