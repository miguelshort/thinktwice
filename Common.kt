package com.thinktwice.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thinktwice.app.AppEntry
import com.thinktwice.app.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Reloj que se actualiza cada `everyMs` mientras el composable está visible. */
@Composable
fun rememberNow(everyMs: Long = 1000L): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everyMs) {
        while (true) { now.longValue = System.currentTimeMillis(); delay(everyMs) }
    }
    return now
}

/** Se incrementa cada vez que la app vuelve al primer plano (p. ej. tras conceder un permiso en Ajustes). */
@Composable
fun rememberResumeTick(): State<Int> {
    val tick = remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick.intValue++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
fun rememberAppList(): State<List<AppEntry>> {
    val ctx = LocalContext.current
    return produceState(initialValue = emptyList(), ctx) {
        value = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
    }
}

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (_: Exception) { null }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(size.dp).clip(CircleShape).background(TT.Surface))
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton("Volver", onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = TT.Text) }
        Spacer(Modifier.width(14.dp))
        TText(title, TTType.Title, Modifier.weight(1f))
        trailing()
    }
}

/** Selector a pantalla completa de aplicaciones, con búsqueda. */
@Composable
fun AppPickerDialog(title: String, selected: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    val apps by rememberAppList()
    var sel by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(TT.Bg).systemBarsPadding().padding(20.dp)) {
            BackBar(title, onDismiss)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TT.Card).padding(14.dp)) {
                if (query.isEmpty()) TText("Buscar aplicación", TTType.Small)
                BasicTextField(query, { query = it }, textStyle = TTType.Body, cursorBrush = SolidColor(TT.Green), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            if (apps.isEmpty()) TText("Cargando aplicaciones…", TTType.Small, Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.pkg }) { a ->
                    val on = a.pkg in sel
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable { sel = if (on) sel - a.pkg else sel + a.pkg },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(a.pkg)
                        Spacer(Modifier.width(14.dp))
                        TText(a.label, TTType.Body, Modifier.weight(1f))
                        Checkbox(on, { sel = if (it) sel + a.pkg else sel - a.pkg },
                            colors = CheckboxDefaults.colors(checkedColor = TT.Green, checkmarkColor = TT.Bg, uncheckedColor = TT.Text2))
                    }
                }
            }
            WhiteButton("Listo (${sel.size})") { onDone(sel) }
        }
    }
}

fun Context.shareText(text: String) {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    startActivity(android.content.Intent.createChooser(i, "Compartir resumen").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

