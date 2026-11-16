package com.thinktwice.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thinktwice.app.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit, push: (String) -> Unit) {
    val ctx = LocalContext.current
    val tick by rememberResumeTick()
    var name by remember { mutableStateOf(state.userName) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<AppState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var hourDialog by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        message = try {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.state.value.toJson().toString(2).toByteArray()) }
            "Copia exportada. Contiene tus ajustes, reglas y registros; guárdala en un sitio de confianza."
        } catch (e: Exception) { "No se pudo exportar: ${e.message}" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            if (text.length > 5_000_000) error("Archivo demasiado grande")
            pendingImport = AppState.fromJson(JSONObject(text))
        } catch (e: Exception) { message = "Archivo no válido: ${e.message}" }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackBar("Perfil y ajustes", onBack)

        SectionTitle("Perfil")
        OutlinedTextField(name, { name = it.take(40); Store.update { s -> s.copy(userName = name.trim()) } },
            label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionTitle("Aplicaciones controladas")
        TCard(Modifier.fillMaxWidth(), onClick = { push("apps") }) {
            TText("${state.controlled.size} aplicaciones con pausa consciente", TTType.Body)
            TText("Toca para cambiarlas", TTType.Small)
        }

        SectionTitle("Pausas")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TText("Pausa antes de abrir las apps controladas", TTType.Body, Modifier.weight(1f))
            Switch(state.pause.enabled, { on -> Store.update { it.copy(pause = it.pause.copy(enabled = on)) } },
                colors = SwitchDefaults.colors(checkedTrackColor = TT.Green, checkedThumbColor = TT.Bg))
        }
        Stepper("Espera inicial", "${state.pause.waitSec} s", { Store.update { it.copy(pause = it.pause.copy(waitSec = (it.pause.waitSec - 5).coerceAtLeast(0))) } },
            { Store.update { it.copy(pause = it.pause.copy(waitSec = (it.pause.waitSec + 5).coerceAtMost(120))) } })
        Stepper("Acceso permitido", "${state.pause.allowMin} min", { Store.update { it.copy(pause = it.pause.copy(allowMin = (it.pause.allowMin - 1).coerceAtLeast(1))) } },
            { Store.update { it.copy(pause = it.pause.copy(allowMin = (it.pause.allowMin + 1).coerceAtMost(120))) } })
        TText("La pausa vuelve a aparecer cuando termina el acceso permitido. Si sales y vuelves a entrar antes, no se repite.", TTType.Label)
        TCard(Modifier.fillMaxWidth().padding(top = 10.dp), onClick = { push("messages") }) {
            TText("Mensajes de la pausa", TTType.Section)
            TText("${Messages.defaults.size} incluidos · ${state.pause.customMessages.size} tuyos", TTType.Small)
        }

        SectionTitle("Notificaciones")
        TText("Las sesiones activas se muestran en una notificación con cuenta atrás cuando el permiso está concedido.", TTType.Small)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TText("Recordatorio diario de retos", TTType.Body, Modifier.weight(1f))
            Switch(state.remindersOn, { on ->
                if (on && !Perms.notifications(ctx) && Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                Store.update { it.copy(remindersOn = on) }
                if (on) Notifier.scheduleReminder(ctx, state.reminderHour) else Notifier.cancelReminder(ctx)
            }, colors = SwitchDefaults.colors(checkedTrackColor = TT.Green, checkedThumbColor = TT.Bg))
        }
        if (state.remindersOn) {
            TextButton(onClick = { hourDialog = true }, Modifier.heightIn(min = 48.dp)) { TText("Hora: ${hhmm(state.reminderHour * 60)} (aproximada)", TTType.BodyMed) }
        }

        SectionTitle("Permisos y diagnóstico")
        val usage = Perms.usageAccess(ctx); val enabled = Perms.accessibilityEnabled(ctx)
        val running = Perms.serviceRunning(); val notif = Perms.notifications(ctx)
        @Suppress("UNUSED_VARIABLE") val t = tick
        DiagRow("Acceso a estadísticas de uso", usage, "Sin él no hay datos de uso ni anillo.") {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        DiagRow("Servicio de intervención (habilitado)", enabled, "Sin él no hay pausas ni bloqueos.") {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        DiagRow("Servicio de intervención (en ejecución)", running,
            if (enabled && !running) "Está habilitado pero no conectado: reinicia el servicio o el móvil." else "Se conecta al habilitarlo.", null)
        DiagRow("Notificaciones", notif, "Sin ellas no se muestra la cuenta atrás de las sesiones.") {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        DiagRow("Mostrar sobre otras apps (opcional, refuerzo)", Perms.overlay(ctx),
            "Solo hace falta si la pausa no aparece encima de otras apps en tu móvil: Android o el fabricante pueden impedir abrirla desde segundo plano.") {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        DiagRow("Sin restricción de batería", Perms.ignoringBatteryOptimizations(ctx),
            "Opcional. Algunos fabricantes cierran servicios en segundo plano; si pasa, excluye thinktwice del ahorro de batería.") {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        TCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            TText("Accesibilidad: qué hace y qué no", TTType.Section)
            TText(
                "thinktwice usa el servicio de accesibilidad de Android únicamente para saber qué app se abre y mostrar la pausa o el bloqueo. " +
                    "No lee mensajes, contenido ni texto de otras apps. No es una herramienta de accesibilidad para personas con discapacidad. " +
                    "Puedes desactivarlo cuando quieras en los ajustes de Android; los controles para revocar permisos nunca se restringen.",
                TTType.Small, Modifier.padding(top = 4.dp),
            )
        }

        SectionTitle("Datos")
        TText("Todo se guarda solo en este teléfono. Sin cuentas, anuncios ni subida de datos.", TTType.Small)
        Spacer(Modifier.height(8.dp))
        WhiteButton("Exportar mis datos") { export.launch("thinktwice-copia.json") }
        GhostButton("Importar copia…", Modifier.fillMaxWidth()) { import.launch(arrayOf("application/json", "text/plain", "*/*")) }
        GhostButton("Eliminar todos los datos", Modifier.fillMaxWidth()) { confirmDelete = true }
        message?.let { TText(it, TTType.Small, Modifier.padding(top = 6.dp), color = TT.Green) }
        TText("thinktwice 0.1.0 · sin IA, sin conexión necesaria", TTType.Label, Modifier.padding(top = 18.dp).fillMaxWidth(), align = TextAlign.Center)
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = TT.Surface,
        title = { TText("¿Eliminar todos los datos?", TTType.Section) },
        text = { TText("Se borrarán ajustes, reglas, rutinas, retos y registros de este teléfono. No se puede deshacer. Los permisos del sistema no cambian.", TTType.Body) },
        confirmButton = { TextButton(onClick = { Store.reset(); Notifier.cancelAll(ctx); Notifier.cancelReminder(ctx); confirmDelete = false; onBack() }) { TText("Eliminar", TTType.BodyMed, color = TT.Pink) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { TText("Cancelar", TTType.BodyMed) } },
    )
    pendingImport?.let { imp ->
        AlertDialog(
            onDismissRequest = { pendingImport = null }, containerColor = TT.Surface,
            title = { TText("¿Reemplazar tus datos?", TTType.Section) },
            text = {
                TText("La copia contiene ${imp.routines.size} rutinas, ${imp.challenges.size} retos, ${imp.sessions.size} sesiones y " +
                    "${imp.controlled.size} apps controladas. Se sustituirán tus datos actuales. Las apps se identifican por su paquete: " +
                    "las que no estén instaladas aquí no tendrán efecto.", TTType.Body)
            },
            confirmButton = { TextButton(onClick = { Store.replaceAll(imp.copy(grants = emptyMap())); pendingImport = null; message = "Datos importados." }) { TText("Reemplazar", TTType.BodyMed, color = TT.Pink) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { TText("Cancelar", TTType.BodyMed) } },
        )
    }
    if (hourDialog) TimeDialog(state.reminderHour * 60, { hourDialog = false }) { m ->
        Store.update { it.copy(reminderHour = m / 60) }; Notifier.scheduleReminder(ctx, m / 60); hourDialog = false
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TText(label, TTType.Body, Modifier.weight(1f))
        TextButton(onClick = minus, Modifier.size(48.dp)) { TText("−", TTType.Section) }
        TText(value, TTType.BodyMed, Modifier.widthIn(min = 56.dp), align = TextAlign.Center)
        TextButton(onClick = plus, Modifier.size(48.dp)) { TText("+", TTType.Section) }
    }
}

/** Estado en texto + símbolo (no solo color). */
@Composable
private fun DiagRow(title: String, ok: Boolean, hint: String, action: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TText(title, TTType.BodyMed)
            TText(if (ok) "✓ Activo" else "✗ Inactivo · $hint", TTType.Small, color = if (ok) TT.Green else TT.Pink)
        }
        if (!ok && action != null) TextButton(onClick = action, Modifier.heightIn(min = 48.dp)) { TText("Abrir", TTType.BodyMed) }
    }
}

@Composable
fun ControlledAppsScreen(state: AppState, onBack: () -> Unit) {
    AppPickerDialog("Aplicaciones controladas", state.controlled, onBack) { sel ->
        Store.update { it.copy(controlled = sel) }; onBack()
    }
}

@Composable
fun MessagesScreen(state: AppState, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BackBar("Mensajes de la pausa", onBack)
        TText("Se elige uno al azar en cada pausa. Escribe frases amables: nada que culpabilice.", TTType.Small)
        SectionTitle("Tus frases")
        if (state.pause.customMessages.isEmpty()) DashedEmpty("Aún no has añadido ninguna.")
        state.pause.customMessages.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TText(m, TTType.Body, Modifier.weight(1f))
                TextButton(onClick = { Store.update { s -> s.copy(pause = s.pause.copy(customMessages = s.pause.customMessages.filterIndexed { j, _ -> j != i })) } },
                    Modifier.heightIn(min = 48.dp)) { TText("Quitar", TTType.BodyMed, color = TT.Pink) }
            }
        }
        OutlinedTextField(text, { text = it.take(120) }, label = { Text("Nueva frase") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        Spacer(Modifier.height(8.dp))
        WhiteButton("Añadir", enabled = text.isNotBlank()) {
            Store.update { s -> s.copy(pause = s.pause.copy(customMessages = s.pause.customMessages + text.trim())) }; text = ""
        }
        SectionTitle("Incluidas")
        Messages.defaults.forEach { TText("• $it", TTType.Body, Modifier.padding(bottom = 6.dp)) }
    }
}
