package com.thinktwice.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val ES = Locale("es", "ES")

object Messages {
    /** Mensajes originales, guardados en la propia app. Sin lenguaje culpabilizador. */
    val defaults = listOf(
        "¿Quieres entrar o lo estás haciendo por costumbre?",
        "Piensa dos veces. Tu tiempo también cuenta.",
        "¿Qué venías a hacer aquí?",
        "Puedes decidir volver a lo que estabas haciendo.",
        "Respira una vez antes de entrar.",
        "Entrar está bien si es una decisión tuya.",
    )

    fun pick(s: AppState): String {
        val all = defaults + s.pause.customMessages
        return all.random()
    }
}

fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

fun hhmmFromMs(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

/** 125 -> "2 h 5 min", 45 -> "45 min", 0 -> "0 min". */
fun fmtMinutes(m: Int): String = when {
    m >= 60 && m % 60 == 0 -> "${m / 60} h"
    m >= 60 -> "${m / 60} h ${m % 60} min"
    else -> "$m min"
}

fun fmtMs(ms: Long): String = fmtMinutes((ms / 60_000).toInt())

/** Cuenta atrás mm:ss o h:mm:ss. */
fun fmtCountdown(ms: Long): String {
    val t = (ms.coerceAtLeast(0) + 999) / 1000
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")
val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

fun daysSummary(days: Set<Int>): String = when {
    days.size == 7 -> "Todos los días"
    days == setOf(1, 2, 3, 4, 5) -> "De lunes a viernes"
    days == setOf(6, 7) -> "Fines de semana"
    days.isEmpty() -> "Sin días"
    else -> days.sorted().joinToString(" ") { DAY_LETTERS[it - 1] }
}

fun dateLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)).replaceFirstChar { it.uppercase() }
}

fun monthName(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL, ES)

fun Routine.crossesMidnight() = endMin <= startMin
