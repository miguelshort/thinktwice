package com.thinktwice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thinktwice.app.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ------------------------------------------------------------------ Pantalla Rutinas
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutinesScreen(state: AppState, push: (String) -> Unit) {
    val now by rememberNow(30_000)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        TText("Rutinas", TTType.Title, Modifier.padding(top = 16.dp, bottom = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TCard(Modifier.weight(1f), onClick = { push("routine:new") }) {
                Icon(Icons.Outlined.Add, null, tint = TT.Green)
                TText("Crear rutina", TTType.Section, Modifier.padding(top = 8.dp))
            }
            TCard(Modifier.weight(1f), onClick = { push("scheduled") }) {
                Icon(Icons.Outlined.DateRange, null, tint = TT.Green)
                TText("Programadas", TTType.Section, Modifier.padding(top = 8.dp))
            }
        }

        SectionTitle("Rutinas activas")
        val enabled = state.routines.filter { it.enabled }
        if (enabled.isEmpty()) DashedEmpty("Aún no tienes rutinas activas. Crea una o activa alguna programada.")
        else enabled.forEach { RoutineCard(it, state, now, push) ; Spacer(Modifier.height(8.dp)) }

        SectionTitle("Retos")
        TText("Periodos de seguimiento de un compromiso. No bloquean todo el móvil.", TTType.Small, Modifier.padding(bottom = 10.dp))
        val activeChallenges = state.challenges
        activeChallenges.forEach { c ->
            val end = c.startMs + c.hours * 3_600_000L
            TCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { push("challenge:${c.id}") }) {
                TText(c.name, TTType.Section)
                TText(ruleText(c, state), TTType.Small)
                TText(if (now < end) "En curso · termina ${hhmmFromMs(end)}" else "Terminado", TTType.Label, Modifier.padding(top = 4.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(24 to "24 horas", 48 to "48 horas", 168 to "7 días", 504 to "21 días", 720 to "30 días").forEach { (h, label) ->
                TCard(Modifier.width(150.dp), onClick = { push("newchallenge:$h") }) {
                    TText(label, TTType.Section)
                    TText("Empezar reto", TTType.Small)
                }
            }
        }
    }
}

fun ruleText(c: Challenge, s: AppState): String = when (c.rule) {
    0 -> "Regla: cumplir la rutina «${s.routines.firstOrNull { it.id == c.ruleArg }?.name ?: "eliminada"}» sin salidas de emergencia"
    1 -> "Regla: completar una sesión de bloqueo cada día"
    else -> "Regla: usar tus apps controladas menos de ${fmtMinutes(c.ruleArg.toIntOrNull() ?: 0)} al día"
}

@Composable
private fun RoutineCard(r: Routine, state: AppState, now: Long, push: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val running = Logic.activeRoutines(state, now).any { it.id == r.id }
    TCard(Modifier.fillMaxWidth(), onClick = { push("routine:${r.id}") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TText(r.name, TTType.Section)
                TText("${hhmm(r.startMin)} – ${hhmm(r.endMin)}${if (r.crossesMidnight()) " (+1 día)" else ""}", TTType.Body)
                TText("${daysSummary(r.days)} · ${r.apps.size} apps", TTType.Small)
                if (running) TText("● En curso ahora", TTType.Label, color = TT.Pink)
            }
            Switch(
                checked = r.enabled, onCheckedChange = { on -> Store.update { st -> st.copy(routines = st.routines.map { if (it.id == r.id) it.copy(enabled = on) else it }) } },
                colors = SwitchDefaults.colors(checkedTrackColor = TT.Green, checkedThumbColor = TT.Bg),
                modifier = Modifier.semantics { contentDescription = "Rutina ${r.name} ${if (r.enabled) "activada" else "desactivada"}" },
            )
            Box {
                TextButton(onClick = { menu = true }, Modifier.size(48.dp)) { TText("⋯", TTType.Section) }
                DropdownMenu(menu, { menu = false }, containerColor = TT.Surface) {
                    DropdownMenuItem(text = { TText("Editar", TTType.Body) }, onClick = { menu = false; push("routine:${r.id}") })
                    DropdownMenuItem(text = { TText("Duplicar", TTType.Body) }, onClick = { menu = false; duplicate(r) })
                    DropdownMenuItem(text = { TText("Eliminar", TTType.Body, color = TT.Pink) }, onClick = { menu = false; delete(r.id) })
                }
            }
        }
    }
}

private fun duplicate(r: Routine) = Store.update { it.copy(routines = it.routines + r.copy(id = newId(), name = r.name + " (copia)", enabled = false)) }
private fun delete(id: String) = Store.update { it.copy(routines = it.routines.filterNot { r -> r.id == id }, suspended = it.suspended - id) }

// ------------------------------------------------------------------ Programadas
@Composable
fun ScheduledScreen(state: AppState, onBack: () -> Unit, push: (String) -> Unit) {
    val now by rememberNow(30_000)
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BackBar("Programadas", onBack)
        if (state.routines.isEmpty()) DashedEmpty("No hay rutinas programadas. Crea una desde la pantalla Rutinas.")
        state.routines.forEach { r ->
            val next = Logic.nextStart(r, now, zone)
            val running = Logic.activeRoutines(state, now).any { it.id == r.id }
            TCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { push("routine:${r.id}") }) {
                TText(r.name, TTType.Section)
                TText(
                    when {
                        !r.enabled -> "Desactivada"
                        running -> "En curso ahora · termina a las ${hhmm(r.endMin)}"
                        next != null -> "Próximo inicio: ${dateLabel(Instant.ofEpochMilli(next).atZone(zone).toLocalDate(), LocalDate.now())} a las ${hhmmFromMs(next)}"
                        else -> "Sin próximos días"
                    }, TTType.Body)
                TText("${hhmm(r.startMin)} – ${hhmm(r.endMin)} · ${daysSummary(r.days)}", TTType.Small)
            }
        }
        TText(
            "Limitación: Android o el fabricante pueden retrasar tareas en segundo plano. Las rutinas se comprueban " +
                "cuando abres una app vigilada, así que no dependen de alarmas exactas.",
            TTType.Label, Modifier.padding(top = 12.dp),
        )
    }
}

// ------------------------------------------------------------------ Editor de rutina
private data class Example(val name: String, val start: Int, val end: Int, val days: Set<Int>)
private val EXAMPLES = listOf(
    Example("Mañana", 7 * 60, 9 * 60, setOf(1, 2, 3, 4, 5)),
    Example("Estudio", 17 * 60, 19 * 60, setOf(1, 2, 3, 4, 5)),
    Example("Trabajo", 9 * 60, 14 * 60, setOf(1, 2, 3, 4, 5)),
    Example("Noche", 22 * 60 + 30, 7 * 60, setOf(1, 2, 3, 4, 5, 6, 7)),
)

private fun overlaps(a: Routine, b: Routine): Boolean {
    if (a.apps.intersect(b.apps).isEmpty()) return false
    val week = 7 * 1440
    fun spans(r: Routine) = r.days.map { d ->
        val s = (d - 1) * 1440 + r.startMin
        val len = if (r.endMin > r.startMin) r.endMin - r.startMin else 1440 - r.startMin + r.endMin
        s to len
    }
    for ((s1, l1) in spans(a)) for ((s2, l2) in spans(b)) {
        for (shift in listOf(-week, 0, week)) if (s1 < s2 + shift + l2 && s2 + shift < s1 + l1) return true
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(state: AppState, idOrNew: String, onBack: () -> Unit) {
    val existing = state.routines.firstOrNull { it.id == idOrNew }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var start by rememberSaveable { mutableIntStateOf(existing?.startMin ?: 9 * 60) }
    var end by rememberSaveable { mutableIntStateOf(existing?.endMin ?: 18 * 60) }
    var days by rememberSaveable { mutableStateOf((existing?.days ?: setOf(1, 2, 3, 4, 5)).joinToString(",")) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var apps by remember { mutableStateOf(existing?.apps ?: emptySet()) }
    var pickApps by remember { mutableStateOf(false) }
    var timeFor by remember { mutableStateOf<String?>(null) }
    val daySet = days.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val ctx = LocalContext.current

    val draft = Routine(existing?.id ?: newId(), name.trim(), apps, start, end, daySet, enabled)
    val clash = state.routines.filter { it.id != draft.id && it.enabled && draft.enabled && overlaps(draft, it) }
    val valid = name.isNotBlank() && daySet.isNotEmpty() && apps.isNotEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BackBar(if (existing == null) "Nueva rutina" else "Editar rutina", onBack)
        if (existing == null) {
            TText("Empezar desde un ejemplo (queda desactivado hasta que lo actives)", TTType.Label, Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EXAMPLES.forEach { ex ->
                    Chip(ex.name, false, { name = ex.name; start = ex.start; end = ex.end; days = ex.days.joinToString(","); enabled = false })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionTitle("Horario")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TCard(Modifier.weight(1f), onClick = { timeFor = "start" }) { TText("Inicio", TTType.Label); TText(hhmm(start), TTType.Big) }
            TCard(Modifier.weight(1f), onClick = { timeFor = "end" }) { TText("Fin", TTType.Label); TText(hhmm(end), TTType.Big) }
        }
        TText(
            if (draft.crossesMidnight()) "Cruza la medianoche: empieza el día elegido y termina al día siguiente a las ${hhmm(end)}." else "Termina el mismo día.",
            TTType.Small, Modifier.padding(top = 6.dp),
        )
        SectionTitle("Días en que empieza")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_LETTERS.forEachIndexed { i, l ->
                val d = i + 1
                Chip(l, d in daySet, { days = (if (d in daySet) daySet - d else daySet + d).sorted().joinToString(",") }, Modifier.weight(1f))
            }
        }
        SectionTitle("Aplicaciones")
        TCard(Modifier.fillMaxWidth(), onClick = { pickApps = true }) {
            TText(if (apps.isEmpty()) "Elegir aplicaciones" else "${apps.size} aplicaciones: " + apps.take(3).joinToString { Apps.label(ctx, it) } + if (apps.size > 3) "…" else "", TTType.Body)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TText("Rutina activada", TTType.BodyMed, Modifier.weight(1f))
            Switch(enabled, { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = TT.Green, checkedThumbColor = TT.Bg))
        }
        if (clash.isNotEmpty()) {
            TCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                TText("Se solapa con: ${clash.joinToString { it.name }}. Las apps compartidas seguirán restringidas hasta que termine la última.", TTType.Small)
            }
        }
        if (!valid) TText("Falta: " + listOfNotNull(
            "un nombre".takeIf { name.isBlank() }, "algún día".takeIf { daySet.isEmpty() }, "alguna aplicación".takeIf { apps.isEmpty() }).joinToString(", "),
            TTType.Small, Modifier.padding(top = 10.dp), color = TT.Pink)
        Spacer(Modifier.height(16.dp))
        WhiteButton("Guardar", enabled = valid) {
            Store.update { st ->
                if (existing == null) st.copy(routines = st.routines + draft)
                else st.copy(routines = st.routines.map { if (it.id == draft.id) draft else it })
            }
            onBack()
        }
        if (existing != null) {
            GhostButton("Duplicar", Modifier.fillMaxWidth()) { duplicate(existing); onBack() }
            GhostButton("Eliminar rutina", Modifier.fillMaxWidth()) { delete(existing.id); onBack() }
        }
    }
    if (pickApps) AppPickerDialog("Aplicaciones de la rutina", apps, { pickApps = false }) { apps = it; pickApps = false }
    timeFor?.let { which ->
        TimeDialog(if (which == "start") start else end, { timeFor = null }) { m -> if (which == "start") start = m else end = m; timeFor = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    val st = rememberTimePickerState(initial / 60, initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = TT.Surface,
        confirmButton = { TextButton(onClick = { onOk(st.hour * 60 + st.minute) }) { TText("Aceptar", TTType.BodyMed) } },
        dismissButton = { TextButton(onClick = onDismiss) { TText("Cancelar", TTType.BodyMed) } },
        text = { TimePicker(st) },
    )
}

// ------------------------------------------------------------------ Retos
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewChallengeScreen(state: AppState, hours: Int, onBack: () -> Unit) {
    var rule by rememberSaveable { mutableIntStateOf(1) }
    var routineId by rememberSaveable { mutableStateOf(state.routines.firstOrNull()?.id ?: "") }
    var minutes by rememberSaveable { mutableIntStateOf(120) }
    val label = when (hours) { 24 -> "24 horas"; 48 -> "48 horas"; 168 -> "7 días"; 504 -> "21 días"; else -> "30 días" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BackBar("Reto de $label", onBack)
        TText("Elige la regla que vas a seguir. thinktwice la medirá cuando pueda y, si no, podrás confirmarla a mano.", TTType.Small)
        SectionTitle("Regla")
        Chip("Cumplir una rutina", rule == 0, { rule = 0 }); Spacer(Modifier.height(8.dp))
        Chip("Completar una sesión diaria", rule == 1, { rule = 1 }); Spacer(Modifier.height(8.dp))
        Chip("Respetar un objetivo de uso", rule == 2, { rule = 2 })
        if (rule == 0) {
            SectionTitle("Rutina")
            if (state.routines.isEmpty()) TText("Primero crea una rutina en la pantalla Rutinas.", TTType.Small, color = TT.Pink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.routines.forEach { Chip(it.name, routineId == it.id, { routineId = it.id }) }
            }
        }
        if (rule == 2) {
            SectionTitle("Máximo diario con tus apps controladas")
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton("−15") { minutes = (minutes - 15).coerceAtLeast(15) }
                TText(fmtMinutes(minutes), TTType.Section, Modifier.weight(1f), align = TextAlign.Center)
                GhostButton("+15") { minutes = (minutes + 15).coerceAtMost(720) }
            }
            if (state.controlled.isEmpty()) TText("Aún no has elegido apps controladas (Ajustes).", TTType.Small, color = TT.Pink)
        }
        TText("Es un periodo de seguimiento, no un bloqueo continuo del móvil. No hay un número de días que garantice formar un hábito.", TTType.Label, Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(16.dp))
        val ok = rule != 0 || routineId.isNotEmpty()
        WhiteButton("Empezar reto", enabled = ok) {
            val c = Challenge(newId(), "Reto de $label", hours, rule, if (rule == 0) routineId else minutes.toString(), System.currentTimeMillis())
            Store.update { it.copy(challenges = it.challenges + c) }
            onBack()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeDetailScreen(state: AppState, id: String, onBack: () -> Unit) {
    val c = state.challenges.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val resume by rememberResumeTick()
    if (c == null) { Column { BackBar("Reto", onBack); DashedEmpty("Este reto ya no existe.") }; return }
    val start = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalDate()
    val usage by produceState<Map<LocalDate, Int>>(emptyMap(), c, resume) {
        if (c.rule != 2) return@produceState
        value = withContext(Dispatchers.IO) {
            (0 until Logic.challengeLengthDays(c.hours)).map { start.plusDays(it.toLong()) }.filter { it <= today }
                .mapNotNull { d ->
                    val u = UsageRepo.dayUsageMs(ctx, d)
                    if (u.isEmpty()) null else d to (u.filterKeys { it in state.controlled }.values.sum() / 60000).toInt()
                }.toMap()
        }
    }
    val days = Logic.challengeDays(c, state, System.currentTimeMillis(), zone, usage, Perms.serviceRunning())
    val met = days.count { it.status == DayStatus.MET_MEASURED || it.status == DayStatus.MET_MANUAL }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BackBar(c.name, onBack)
        TCard(Modifier.fillMaxWidth()) {
            TText(ruleText(c, state), TTType.Body)
            TText("$met de ${days.size} días cumplidos", TTType.Big, Modifier.padding(top = 8.dp))
        }
        SectionTitle("Calendario")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { cd ->
                val (sym, txt) = when (cd.status) {
                    DayStatus.MET_MEASURED -> "✓" to "cumplido (medido)"
                    DayStatus.MET_MANUAL -> "✎" to "cumplido (confirmado a mano)"
                    DayStatus.MISSED_MEASURED -> "✗" to "no cumplido (medido)"
                    DayStatus.PENDING -> "·" to "pendiente"
                    DayStatus.NO_DATA -> "?" to "sin datos suficientes"
                }
                val bg = when (cd.status) { DayStatus.MET_MEASURED, DayStatus.MET_MANUAL -> TT.Green; DayStatus.MISSED_MEASURED -> TT.Pink; else -> TT.Card }
                val fg = if (bg == TT.Card) TT.Text else TT.Bg
                Column(
                    Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                        .semantics { contentDescription = "${cd.date.dayOfMonth} de ${monthName(cd.date)}: $txt" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    TText("${cd.date.dayOfMonth}", TTType.Label, color = fg)
                    TText(sym, TTType.Section, color = fg)
                }
            }
        }
        Column(Modifier.padding(top = 12.dp)) {
            TText("✓ cumplido (medido) · ✎ confirmado a mano · ✗ no cumplido · · pendiente · ? sin datos suficientes", TTType.Label)
            TText("Medido: lo comprueba thinktwice (sesiones registradas, servicio activo o estadísticas de uso). Manual: lo confirmas tú.", TTType.Label, Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        val todayStatus = days.firstOrNull { it.date == today }
        if (todayStatus != null && todayStatus.status != DayStatus.MET_MEASURED && todayStatus.status != DayStatus.MET_MANUAL) {
            WhiteButton("Confirmar hoy a mano") {
                Store.update { st -> st.copy(challenges = st.challenges.map { if (it.id == id) it.copy(confirmed = it.confirmed + today.toString()) else it }) }
            }
        }
        GhostButton("Abandonar reto", Modifier.fillMaxWidth()) {
            Store.update { st -> st.copy(challenges = st.challenges.filterNot { it.id == id }) }
            onBack()
        }
    }
}
