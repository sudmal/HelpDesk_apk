package com.sputnik.helpdesk.util

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CrashLogger {

    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_BYTES = 512 * 1024  // 512 KB

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(appContext.filesDir, LOG_FILE)
                if (file.exists() && file.length() > MAX_BYTES) file.delete()
                val ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                file.appendText("--- $ts ---\nThread: ${thread.name}\n${throwable.stackTraceToString()}\n\n")
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLog(context: Context): String =
        File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText() ?: ""

    fun clearLog(context: Context) {
        File(context.filesDir, LOG_FILE).delete()
    }
}
