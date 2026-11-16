package com.thinktwice.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.thinktwice.app.ui.*
import kotlinx.coroutines.delay

/**
 * Pantalla que se muestra sobre otra app: modo PAUSA (espera + decidir) o modo BLOQUEO (motivo y hora de fin).
 * Es una actividad normal: no usa superposición del sistema (SYSTEM_ALERT_WINDOW).
 */
class InterventionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_END = "end"
        @Volatile var showingPkg: String? = null

        fun pause(ctx: Context, pkg: String) = Intent(ctx, InterventionActivity::class.java)
            .putExtra(EXTRA_MODE, "pause").putExtra(EXTRA_PKG, pkg)

        fun block(ctx: Context, pkg: String, r: Restriction) = Intent(ctx, InterventionActivity::class.java)
            .putExtra(EXTRA_MODE, "block").putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_NAME, r.name).putExtra(EXTRA_END, r.endsAtMs)
    }

    private var pkg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        showingPkg = pkg
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "pause"
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val end = intent.getLongExtra(EXTRA_END, 0L)
        val appLabel = Apps.label(this, pkg)
        val state = Store.state.value
        val message = Messages.pick(state)
        val shownAt = System.currentTimeMillis()
        if (mode == "pause") Store.log("PAUSE_SHOWN", pkg) else Store.log("BLOCK_SHOWN", pkg, name)

        setContent {
            ThinkTwiceTheme {
                Box(Modifier.fillMaxSize().background(TT.Bg)) {
                    if (mode == "pause") PauseScreen(appLabel, message, shownAt, state.pause.waitSec, state.pause.allowMin)
                    else BlockScreen(appLabel, name, end)
                }
            }
        }
    }

    // ---------- PAUSA ----------
    @Composable
    private fun PauseScreen(appLabel: String, message: String, shownAt: Long, waitSec: Int, allowMin: Int) {
        var remaining by remember { mutableLongStateOf(waitSec * 1000L) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                remaining = (waitSec * 1000L - (System.currentTimeMillis() - shownAt)).coerceAtLeast(0)
                delay(100)
            }
        }
        val progress by animateFloatAsState(
            if (waitSec == 0) 1f else 1f - remaining / (waitSec * 1000f), label = "wait")
        val ready = remaining <= 0
        BackHandler { leave("PAUSE_EXIT") }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            LogoMark(Modifier.size(150.dp))
            Spacer(Modifier.weight(1f))
            TText(appLabel, TTType.Label, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TText(message, TTType.Big, align = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Ring(
                segments = listOf(progress to TT.Green), max = 1f, size = 120.dp, stroke = 8.dp,
                description = if (ready) "Espera terminada" else "Quedan ${(remaining + 999) / 1000} segundos de espera",
            ) {
                TText(if (ready) "✓" else "${(remaining + 999) / 1000}", TTType.Big)
            }
            Spacer(Modifier.weight(1f))
            WhiteButton(
                if (ready) "Continuar $allowMin min" else "Espera ${(remaining + 999) / 1000} s",
                enabled = ready,
            ) {
                val until = System.currentTimeMillis() + allowMin * 60_000L
                Store.update { it.copy(grants = it.grants + (pkg to until)) }
                Store.log("PAUSE_CONTINUE", pkg)
                close()
            }
            GhostButton("Salir sin entrar") { leave("PAUSE_EXIT") }
        }
    }

    // ---------- BLOQUEO ----------
    @Composable
    private fun BlockScreen(appLabel: String, name: String, endMs: Long) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var confirm by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (System.currentTimeMillis() < endMs) { now = System.currentTimeMillis(); delay(1000) }
            close()   // la restricción terminó: se libera la app
        }
        BackHandler { leave("BLOCK_EXIT") }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            LogoMark(Modifier.size(150.dp))
            Spacer(Modifier.weight(1f))
            TText("$appLabel está en pausa", TTType.Big, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TText("Motivo: $name", TTType.Body, align = TextAlign.Center)
            TText("Termina a las ${hhmmFromMs(endMs)} · faltan ${fmtCountdown(endMs - now)}", TTType.Small, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            TText(
                "Es una restricción de thinktwice, no un bloqueo del sistema: puedes saltártela con la salida de " +
                    "emergencia o desactivando el servicio en los ajustes de Android.",
                TTType.Label, align = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            WhiteButton("Volver al inicio") { leave("BLOCK_EXIT") }
            GhostButton("Salida de emergencia") { confirm = true }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                containerColor = TT.Surface,
                title = { TText("¿Terminar la restricción ahora?", TTType.Section) },
                text = { TText("Se terminará antes de tiempo la sesión o rutina que restringe $appLabel (y con ella el resto de sus apps). Quedará registrado como salida de emergencia.", TTType.Body) },
                confirmButton = { TextButton(onClick = { emergencyExit(); }) { TText("Sí, terminar", TTType.BodyMed, color = TT.Pink) } },
                dismissButton = { TextButton(onClick = { confirm = false }) { TText("Mantener", TTType.BodyMed) } },
            )
        }
    }

    /** Termina las restricciones que afectan a esta app. Otras apps siguen restringidas por sus propias reglas. */
    private fun emergencyExit() {
        val now = System.currentTimeMillis()
        val s = Store.state.value
        val zone = java.time.ZoneId.systemDefault()
        val endedSessions = s.sessions.filter { it.isActive(now) && pkg in it.apps }
        val suspendedRoutines = s.routines.filter { r ->
            r.enabled && pkg in r.apps && Logic.routineWindows(r, now, zone).any { now >= it.first && now < it.second }
        }
        Store.update { st ->
            st.copy(
                sessions = st.sessions.map { if (it.id in endedSessions.map { e -> e.id }) it.copy(endedAtMs = now, emergency = true) else it },
                suspended = st.suspended + suspendedRoutines.associate { r ->
                    r.id to Logic.routineWindows(r, now, zone).first { now >= it.first && now < it.second }.second
                },
            )
        }
        endedSessions.forEach { Notifier.cancelSession(this, it.id); Store.log("SESSION_EMERGENCY", pkg, it.id) }
        suspendedRoutines.forEach { Store.log("ROUTINE_EMERGENCY", pkg, it.id) }
        close()
    }

    private fun leave(type: String) {
        Store.log(type, pkg)
        GuardService.instance?.goHome() ?: startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        close()
    }

    private fun close() { showingPkg = null; finish() }

    override fun onDestroy() {
        if (showingPkg == pkg) showingPkg = null
        super.onDestroy()
    }
}
