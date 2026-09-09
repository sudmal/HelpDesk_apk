package com.sputnik.helpdesk.util

import com.google.gson.JsonParser

/**
 * Достаёт человекочитаемую причину из тела ответа-ошибки Laravel.
 *
 * И 403 (`Response::deny('текст')` в политиках), и 422 приходят в одном
 * формате — `{"message": "<причина>", ...}`. Раньше на 403 клиент показывал
 * захардкоженную фразу, игнорируя тело; теперь берём `message`, а жёсткий
 * текст оставляем только как [fallback] на случай пустого/битого тела.
 */
fun parseServerMessage(errorBody: String?, fallback: String): String {
    if (errorBody.isNullOrBlank()) return fallback
    return runCatching {
        val message = JsonParser.parseString(errorBody).asJsonObject
            .get("message")?.asString?.trim()
        message?.takeIf { it.isNotEmpty() } ?: fallback
    }.getOrDefault(fallback)
}
