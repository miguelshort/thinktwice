package com.thinktwice.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thinktwice.app.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayStats(
    val totalMs: Long, val perApp: List<Pair<String, Long>>,
    val usefulMs: Long, val distractingMs: Long, val access: Boolean,
)

fun loadDayStats(ctx: android.content.Context, date: LocalDate, state: AppState): DayStats {
    if (!Perms.usageAccess(ctx)) return DayStats(0, emptyList(), 0, 0, false)
    val pm = ctx.packageManager
    val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
    val raw = UsageRepo.dayUsageMs(ctx, date)
    val per = raw.filter { (pkg, _) -> pkg != home && pm.getLaunchIntentForPackage(pkg) != null }
        .entries.sortedByDescending { it.value }.map { it.key to it.value }
    val total = per.sumOf { it.second }
    val useful = per.filter { state.category(it.first) == Cat.USEFUL }.sumOf { it.second }
    val distracting = per.filter { state.category(it.first) == Cat.DISTRACTING }.sumOf { it.second }
    // Los días ya cerrados se guardan para poder resumir el mes aunque Android olvide los eventos antiguos.
    if (date < LocalDate.now() && total > 0) {
        val snap = DaySnap((total / 60000).toInt(), (useful / 60000).toInt(), (distracting / 60000).toInt())
        if (Store.state.value.daySnaps[date.toString()] != snap) Store.update { it.copy(daySnaps = it.daySnaps + (date.toString() to snap)) }
    }
    return DayStats(total, per, useful, distracting, true)
}

@Composable
fun rememberDayStats(date: LocalDate, state: AppState, tick: Int): State<DayStats?> {
    val ctx = LocalContext.current
    return produceState<DayStats?>(null, date, state.categories, tick) {
        value = withContext(Dispatchers.IO) { loadDayStats(ctx, date, state) }
    }
}

fun eventsOn(s: AppState, d: LocalDate, type: String, zone: ZoneId = ZoneId.systemDefault()) =
    s.events.count { it.type == type && Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() == d }

fun catLabel(c: Int) = when (c) { Cat.USEFUL -> "Útil"; Cat.DISTRACTING -> "Distractora"; else -> "Neutra" }

@Composable
fun UsageRing(stats: DayStats?, modifier: Modifier = Modifier) {
    val total = stats?.totalMs ?: 0L
    val useful = (stats?.usefulMs ?: 0L).toFloat()
    val distracting = (stats?.distractingMs ?: 0L).toFloat()
    val centerText = when {
        stats == null -> "…"
        !stats.access -> "—"
        else -> fmtMs(total)
    }
    val desc = when {
        stats == null -> "Cargando uso"
        !stats.access -> "Sin acceso a estadísticas de uso"
        else -> "Tiempo de uso ${fmtMs(total)}. Útil ${fmtMs(stats.usefulMs)}, distractora ${fmtMs(stats.distractingMs)}"
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        ConcentricBackdrop(Modifier.fillMaxSize())
        Ring(
            segments = listOf(useful to TT.Green, distracting to TT.Pink),
            max = total.toFloat().coerceAtLeast(1f), size = 230.dp, stroke = 18.dp, description = desc,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TText(centerText, TTType.Big)
                TText("de uso", TTType.Label)
            }
        }
    }
}

@Composable
fun RingLegend(stats: DayStats?) {
    if (stats == null || !stats.access) return
    val neutral = (stats.totalMs - stats.usefulMs - stats.distractingMs).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendItem("▲ Útil", fmtMs(stats.usefulMs), TT.Green)
        LegendItem("▼ Distractora", fmtMs(stats.distractingMs), TT.Pink)
        LegendItem("● Neutra", fmtMs(neutral), TT.Text2)
    }
}

@Composable
private fun LegendItem(label: String, value: String, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TText(label, TTType.Label, color = c)
        TText(value, TTType.BodyMed)
    }
}

@Composable
fun StatsScreen(state: AppState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    val d = LocalDate.parse(date)
    val resume by rememberResumeTick()
    val stats by rememberDayStats(d, state, resume)
    val week by produceState<List<Pair<LocalDate, DayStats>>?>(null, state.categories, resume) {
        value = withContext(Dispatchers.IO) { (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to loadDayStats(ctx, it, state) } }
    }
    val zone = ZoneId.systemDefault()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackBar("Estadísticas", onBack) {
            GhostButton("Compartir") { ctx.shareText(buildSummary(state, week, today)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("‹ Día anterior") { date = d.minusDays(1).toString() }
            Spacer(Modifier.weight(1f))
            TText(dateLabel(d, today), TTType.Section)
            Spacer(Modifier.weight(1f))
            if (d < today) GhostButton("Siguiente ›") { date = d.plusDays(1).toString() } else Spacer(Modifier.width(100.dp))
        }
        UsageRing(stats, Modifier.fillMaxWidth().height(280.dp))
        RingLegend(stats)
        if (stats?.access == false) {
            Spacer(Modifier.height(12.dp))
            PermissionCard("Para ver tu tiempo de uso, thinktwice necesita el acceso a estadísticas de uso. Solo lee cuánto tiempo pasa cada app en primer plano.") {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } else if (stats != null && stats!!.totalMs == 0L) {
            Spacer(Modifier.height(12.dp))
            DashedEmpty("No hay datos de uso para este día. Android solo conserva los eventos detallados unos días.")
        }

        // Productividad diaria
        SectionTitle("Productividad del día")
        val prod = stats?.let { Logic.productivityPercent((it.usefulMs / 60000).toInt(), (it.distractingMs / 60000).toInt()) }
        TCard(Modifier.fillMaxWidth()) {
            TText(if (prod != null) "$prod %" else "Sin datos suficientes", TTType.Big)
            TText("Cálculo: tiempo en apps útiles ÷ (útil + distractora). Las neutras no cuentan. " +
                "Depende de cómo clasifiques tus apps; no es una medida universal.", TTType.Small, Modifier.padding(top = 6.dp))
        }

        // Desglose por app
        SectionTitle("Desglose por aplicación")
        val per = stats?.perApp.orEmpty()
        if (per.isEmpty()) DashedEmpty("Sin uso registrado para mostrar.")
        else per.take(12).forEach { (pkg, ms) ->
            val cat = state.category(pkg)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    TText(Apps.label(ctx, pkg), TTType.BodyMed, maxLines = 1)
                    TText(fmtMs(ms), TTType.Small)
                }
                Chip(catLabel(cat), selected = false, onClick = {
                    val next = (cat + 1) % 3
                    Store.update { it.copy(categories = it.categories + (pkg to next)) }
                })
            }
        }
        if (per.isNotEmpty()) TText("Toca la etiqueta para cambiar entre útil, distractora y neutra.", TTType.Label, Modifier.padding(top = 4.dp))

        // Evolución semanal
        SectionTitle("Evolución semanal")
        val w = week
        if (w == null) TText("Cargando…", TTType.Small)
        else if (w.all { it.second.totalMs == 0L }) DashedEmpty("Aún no hay datos de los últimos 7 días.")
        else {
            WeekBars(w, today)
            val wu = w.sumOf { it.second.usefulMs / 60000 }.toInt(); val wd = w.sumOf { it.second.distractingMs / 60000 }.toInt()
            val daysWithData = w.count { it.second.totalMs > 0 }
            val wp = Logic.productivityPercent(wu, wd)
            TCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                TText("Resumen semanal: ${if (wp != null) "$wp % productivo" else "productividad sin datos suficientes"}", TTType.BodyMed)
                TText("Últimos 7 días, $daysWithData con datos. Mismo cálculo que el diario, sumando los 7 días.", TTType.Small)
            }
        }

        // Pausas y decisiones
        SectionTitle("Pausas y decisiones")
        val last7 = (0..6).map { today.minusDays(it.toLong()) }
        val shown = last7.sumOf { eventsOn(state, it, "PAUSE_SHOWN", zone) }
        val cont = last7.sumOf { eventsOn(state, it, "PAUSE_CONTINUE", zone) }
        val exits = last7.sumOf { eventsOn(state, it, "PAUSE_EXIT", zone) }
        TCard(Modifier.fillMaxWidth()) {
            TText("Últimos 7 días", TTType.Label)
            TText("$shown pausas realizadas · $cont veces continuaste · $exits veces saliste sin entrar", TTType.Body)
            TText("Solo cuentan las pausas registradas por thinktwice con el servicio de intervención activo.", TTType.Small, Modifier.padding(top = 4.dp))
        }

        // Historial de sesiones
        SectionTitle("Historial de sesiones")
        val done = state.sessions.sortedByDescending { it.startMs }.take(10)
        if (done.isEmpty()) DashedEmpty("Todavía no has iniciado ninguna sesión.")
        else done.forEach { s ->
            val real = s.effectiveEnd().coerceAtMost(System.currentTimeMillis()) - s.startMs
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TText(s.name, TTType.BodyMed)
                    TText(Instant.ofEpochMilli(s.startMs).atZone(zone).toLocalDate().let { dateLabel(it, today) } + " · " + hhmmFromMs(s.startMs), TTType.Small)
                }
                TText(
                    when { s.emergency -> "Salida de emergencia · ${fmtMs(real)}"; s.isActive(System.currentTimeMillis()) -> "En curso"; else -> "Completada · ${fmtMs(real)}" },
                    TTType.Label,
                )
            }
        }

        // Resumen mensual con periodos equivalentes
        SectionTitle("Resumen mensual")
        MonthlySummary(state, today)
    }
}

@Composable
private fun WeekBars(week: List<Pair<LocalDate, DayStats>>, today: LocalDate) {
    val max = week.maxOf { it.second.totalMs }.coerceAtLeast(1L)
    val desc = week.joinToString(". ") { "${dateLabel(it.first, today)}: ${if (it.second.totalMs == 0L) "sin datos" else fmtMs(it.second.totalMs)}" }
    TCard(Modifier.fillMaxWidth().semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 7
            week.forEachIndexed { i, (d, st) ->
                val h = size.height * (st.totalMs.toFloat() / max)
                val x = i * slot + slot * 0.2f
                if (st.totalMs == 0L) {
                    drawRoundRect(TT.Track, Offset(x, size.height - 4f), Size(slot * 0.6f, 4f), CornerRadius(2f))
                } else {
                    drawRoundRect(if (d == today) Color.White else TT.Text2, Offset(x, size.height - h), Size(slot * 0.6f, h), CornerRadius(10f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            week.forEach { (d, _) -> TText(DAY_LETTERS[d.dayOfWeek.value - 1], TTType.Label, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        TText("Los días sin barra no tienen datos (no es lo mismo que 0 minutos).", TTType.Label, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MonthlySummary(state: AppState, today: LocalDate) {
    val n = today.dayOfMonth - 1  // días ya cerrados del mes en curso
    val prevStart = today.withDayOfMonth(1).minusMonths(1)
    fun agg(start: LocalDate, days: Int): Pair<Int, Int> {
        var total = 0; var withData = 0
        for (i in 0 until days) {
            val sn = state.daySnaps[start.plusDays(i.toLong()).toString()] ?: continue
            total += sn.totalMin; withData++
        }
        return total to withData
    }
    if (n < 1) { DashedEmpty("El resumen mensual aparece a partir del día 2 del mes, cuando hay un día cerrado que comparar."); return }
    val cur = agg(today.withDayOfMonth(1), n)
    val prev = agg(prevStart, minOf(n, prevStart.lengthOfMonth()))
    if (cur.second == 0) { DashedEmpty("Aún no hay días cerrados guardados este mes."); return }
    TCard(Modifier.fillMaxWidth()) {
        TText("Del 1 al $n de ${monthName(today)}", TTType.Label)
        TText("${fmtMinutes(cur.first)} en ${cur.second} días con datos", TTType.BodyMed)
        if (prev.second > 0) {
            TText("Mismo periodo de ${monthName(prevStart)}: ${fmtMinutes(prev.first)} en ${prev.second} días con datos", TTType.Body, Modifier.padding(top = 6.dp))
            val a = cur.first / cur.second; val b = prev.first / prev.second
            TText("Media por día con datos: ${fmtMinutes(a)} frente a ${fmtMinutes(b)}.", TTType.Small, Modifier.padding(top = 4.dp))
        } else TText("No hay datos guardados del mismo periodo del mes anterior para comparar.", TTType.Small, Modifier.padding(top = 6.dp))
        TText("Se basa en los días que thinktwice ha podido leer y guardar; no incluye días anteriores a la instalación.", TTType.Label, Modifier.padding(top = 6.dp))
    }
}

fun buildSummary(state: AppState, week: List<Pair<LocalDate, DayStats>>?, today: LocalDate): String {
    val sb = StringBuilder("Resumen de thinktwice (últimos 7 días)\n")
    week?.forEach { (d, s) -> sb.append("• ${dateLabel(d, today)}: ${if (s.totalMs == 0L) "sin datos" else fmtMs(s.totalMs)}\n") }
    val last7 = (0..6).map { today.minusDays(it.toLong()) }
    sb.append("Pausas realizadas: ${last7.sumOf { eventsOn(state, it, "PAUSE_SHOWN") }}\n")
    sb.append("Veces que decidí no continuar: ${last7.sumOf { eventsOn(state, it, "PAUSE_EXIT") }}\n")
    return sb.toString()
}

@Composable
fun PermissionCard(text: String, onGrant: () -> Unit) {
    TCard(Modifier.fillMaxWidth()) {
        TText(text, TTType.Body)
        Spacer(Modifier.height(12.dp))
        WhiteButton("Abrir ajustes", onClick = onGrant)
    }
}
