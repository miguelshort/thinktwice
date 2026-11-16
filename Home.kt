package com.thinktwice.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thinktwice.app.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState, push: (String) -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    var dateStr by rememberSaveable { mutableStateOf(today.toString()) }
    val d = LocalDate.parse(dateStr)
    var picking by remember { mutableStateOf(false) }
    val resume by rememberResumeTick()
    val stats by rememberDayStats(d, state, resume)
    val now by rememberNow(1000)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // Cabecera: logo, nombre, ajustes y perfil
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(52.dp))
            Spacer(Modifier.width(8.dp))
            TText("thinktwice", TTType.Title, Modifier.weight(1f))
            CircleIconButton("Perfil", { push("settings") }) { Icon(Icons.Outlined.Person, null, tint = TT.Text) }
            Spacer(Modifier.width(8.dp))
            CircleIconButton("Ajustes", { push("settings") }) { Icon(Icons.Outlined.Settings, null, tint = TT.Text) }
        }
        if (state.userName.isNotBlank()) TText("Hola, ${state.userName}", TTType.Small, Modifier.padding(start = 4.dp))

        // Fecha seleccionable
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("‹") { dateStr = d.minusDays(1).toString() }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { picking = true }, Modifier.heightIn(min = 48.dp)) { TText(dateLabel(d, today), TTType.Section) }
            Spacer(Modifier.weight(1f))
            if (d < today) GhostButton("›") { dateStr = d.plusDays(1).toString() } else Spacer(Modifier.width(48.dp))
        }

        UsageRing(stats, Modifier.fillMaxWidth().height(290.dp))
        RingLegend(stats)
        TText(
            "El anillo reparte tu tiempo de uso según cómo hayas clasificado cada app (útil, distractora o neutra). " +
                "Es una referencia personal, no un diagnóstico.",
            TTType.Label, Modifier.padding(top = 10.dp),
        )

        // Sesiones activas
        val active = state.sessions.filter { it.isActive(now) }
        if (active.isNotEmpty()) {
            SectionTitle("Sesión activa")
            val guarded = Perms.serviceRunning()
            active.forEach { s ->
                TCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TText(s.name, TTType.Section)
                            TText(
                                if (guarded) "Protección activa · ${s.apps.size} apps" else "Solo recordatorio: el servicio de intervención está desactivado",
                                TTType.Small, color = if (guarded) TT.Green else TT.Pink,
                            )
                        }
                        TText(fmtCountdown(s.effectiveEnd() - now), TTType.Big)
                    }
                }
            }
        }

        // Tarjetas
        SectionTitle("Resumen")
        val activeRoutines = Logic.activeRoutines(state, now.coerceAtLeast(0), zone).size
        val scheduledToday = state.routines.count { it.enabled && today.dayOfWeek.value in it.days }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TCard(Modifier.weight(1f), onClick = { push("stats") }) {
                TText("Tiempo de uso", TTType.Label)
                TText(if (stats?.access == true) fmtMs(stats!!.totalMs) else "—", TTType.Big)
                TText("Ver estadísticas", TTType.Small)
            }
            TCard(Modifier.weight(1f), onClick = { push("tabroutines") }) {
                TText("Rutinas", TTType.Label)
                TText("$activeRoutines activas", TTType.Big)
                TText("$scheduledToday programadas hoy", TTType.Small)
            }
        }
        Spacer(Modifier.height(10.dp))
        val paused = eventsOn(state, d, "PAUSE_SHOWN", zone)
        val exits = eventsOn(state, d, "PAUSE_EXIT", zone)
        val blocks = state.sessions.count { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == d }
        TCard(Modifier.fillMaxWidth(), onClick = { push("stats") }) {
            TText("Actividad", TTType.Label)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("$paused", "pausas")
                Metric("$exits", "no continuó")
                Metric("$blocks", "sesiones")
            }
        }

        // Estado de las funciones
        if (!Perms.serviceRunning() && state.controlled.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PermissionCard("Las pausas y los bloqueos no se están aplicando porque el servicio de intervención está desactivado.") {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    if (picking) {
        val startMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val ps = rememberDatePickerState(
            initialSelectedDateMillis = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= startMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    ps.selectedDateMillis?.let { dateStr = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { TText("Aceptar", TTType.BodyMed) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { TText("Cancelar", TTType.BodyMed) } },
        ) { DatePicker(ps) }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        TText(value, TTType.Big)
        TText(label, TTType.Label)
    }
}
