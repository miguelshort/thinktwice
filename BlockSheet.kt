package com.thinktwice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.thinktwice.app.*

/** Panel inferior "Crear bloqueo": plantillas, aplicaciones, duración e inicio. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockSheet(state: AppState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var templateId by remember { mutableStateOf(state.templates.firstOrNull()?.id) }
    val template = state.templates.firstOrNull { it.id == templateId }
    var apps by remember { mutableStateOf(template?.apps ?: emptySet()) }
    var minutes by remember { mutableIntStateOf(template?.minutes ?: 45) }
    var custom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var pickApps by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = TT.Surface, contentColor = TT.Text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(TT.Text2))
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            TText("Crear bloqueo", TTType.Title)
            TText("Plantillas", TTType.Label, Modifier.padding(top = 16.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.templates.forEach { t ->
                    Chip(t.name, templateId == t.id, { templateId = t.id; apps = t.apps; minutes = t.minutes; custom = false })
                }
            }
            if (template != null) GhostButton("Guardar cambios en «${template.name}»") {
                Store.update { st -> st.copy(templates = st.templates.map { if (it.id == template.id) it.copy(apps = apps, minutes = minutes) else it }) }
            }

            TText("Aplicaciones", TTType.Label, Modifier.padding(top = 8.dp, bottom = 8.dp))
            TCard(Modifier.fillMaxWidth(), onClick = { pickApps = true }) {
                TText(
                    if (apps.isEmpty()) "Elegir aplicaciones"
                    else "${apps.size} aplicaciones: " + apps.take(3).joinToString { Apps.label(ctx, it) } + if (apps.size > 3) "…" else "",
                    TTType.Body,
                )
            }

            TText("Duración", TTType.Label, Modifier.padding(top = 16.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120, 240).forEach { m -> Chip(fmtMinutes(m), !custom && minutes == m, { custom = false; minutes = m }) }
                Chip("Personalizada", custom, { custom = true })
            }
            if (custom) {
                OutlinedTextField(
                    customText, { v -> customText = v.filter { it.isDigit() }.take(4); customText.toIntOrNull()?.let { minutes = it } },
                    label = { Text("Minutos") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            val running = Perms.serviceRunning()
            if (!running) {
                TText(
                    "El servicio de intervención está desactivado: si inicias la sesión, solo verás el contador y no se bloqueará ninguna app. Actívalo en Ajustes > Permisos.",
                    TTType.Small, Modifier.padding(top = 14.dp), color = TT.Pink,
                )
            }
            Spacer(Modifier.height(18.dp))
            val ok = apps.isNotEmpty() && minutes > 0
            WhiteButton(if (ok) "Iniciar bloqueo · ${fmtMinutes(minutes)}" else "Elige apps y duración", enabled = ok) {
                val now = System.currentTimeMillis()
                val s = Session(newId(), template?.name ?: "Bloqueo", apps, now, now + minutes * 60_000L)
                Store.update { it.copy(sessions = (it.sessions + s).takeLast(300)) }
                Store.log("SESSION_START", null, s.id)
                Notifier.showSession(ctx, s)
                onDismiss()
            }
        }
    }
    if (pickApps) AppPickerDialog("Aplicaciones del bloqueo", apps, { pickApps = false }) { apps = it; pickApps = false }
}
