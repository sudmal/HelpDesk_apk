package com.sputnik.helpdesk.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.sputnik.helpdesk.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val VERSION_URL = "https://vega8.ru/apk/version.json"

object UpdateChecker {

    fun check(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val info = fetchUpdateInfo() ?: return@launch
            val installedCode = activity.packageManager
                .getPackageInfo(activity.packageName, 0).versionCode
            if (info.version_code <= installedCode) return@launch
            showUpdateDialog(activity, info)
        }
    }

    private suspend fun fetchUpdateInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(VERSION_URL).build()
            val body = client.newCall(request).execute().use { it.body?.string() } ?: return@withContext null
            Gson().fromJson(body, UpdateInfo::class.java)
        } catch (_: Exception) { null }
    }

    private fun showUpdateDialog(activity: AppCompatActivity, info: UpdateInfo) {
        val message = buildString {
            append("Доступна версия ${info.version_name}")
            if (!info.changelog.isNullOrBlank()) append("\n\n${info.changelog}")
        }
        AlertDialog.Builder(activity)
            .setTitle("Обновление приложения")
            .setMessage(message)
            .setPositiveButton("Обновить") { _, _ -> downloadAndInstall(activity, info) }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun downloadAndInstall(activity: AppCompatActivity, info: UpdateInfo) {
        val apkName = "helpdesk-${info.version_name}.apk"

        val dmRequest = DownloadManager.Request(Uri.parse(info.apk_url))
            .setTitle("HelpDesk ${info.version_name}")
            .setDescription("Загрузка обновления...")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = activity.getSystemService(DownloadManager::class.java)
        val downloadId = dm.enqueue(dmRequest)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                activity.unregisterReceiver(this)
                installApk(activity, dm, downloadId)
            }
        }
        activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun installApk(activity: AppCompatActivity, dm: DownloadManager, downloadId: Long) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Требуется разрешение")
                .setMessage("Разрешите установку из неизвестных источников для этого приложения, затем повторите.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    activity.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        val uri = dm.getUriForDownloadedFile(downloadId) ?: return
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
