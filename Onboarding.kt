package com.thinktwice.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thinktwice.app.*

@Composable
fun OnboardingScreen(state: AppState) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(state.userName) }
    var apps by remember { mutableStateOf(state.controlled) }
    var pick by remember { mutableStateOf(false) }
    val resume by rememberResumeTick()
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val last = 4
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onb") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoMark(Modifier.size(if (s == 0) 200.dp else 110.dp))
                when (s) {
                    0 -> {
                        TText("thinktwice", TTType.Hero, Modifier.padding(top = 8.dp))
                        TText("Detén la apertura automática de apps y decide con calma si quieres entrar.", TTType.Body, Modifier.padding(top = 8.dp, bottom = 20.dp), color = TT.Text2, align = TextAlign.Center)
                        IntroCard("Pausas", "Una breve espera antes de abrir las apps que elijas, para decidir si continuar.")
                        IntroCard("Bloqueos", "Sesiones con duración para proteger momentos de concentración.")
                        IntroCard("Rutinas", "Horarios que se repiten: estudio, trabajo, noche…")
                        TText("No promete curar ninguna adicción ni mejoras concretas: es una ayuda para tomar decisiones más conscientes.", TTType.Label, Modifier.padding(top = 12.dp), align = TextAlign.Center)
                    }
                    1 -> {
                        TText("¿Cómo te llamamos?", TTType.Title, Modifier.padding(top = 16.dp, bottom = 12.dp))
                        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TText("Se guarda solo en este teléfono.", TTType.Small, Modifier.padding(top = 8.dp))
                    }
                    2 -> {
                        TText("¿Qué apps quieres controlar?", TTType.Title, Modifier.padding(top = 16.dp, bottom = 12.dp), align = TextAlign.Center)
                        TText("Elegirás apps que suelen abrirse por impulso. Podrás cambiarlas cuando quieras.", TTType.Body, color = TT.Text2, align = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        TCard(Modifier.fillMaxWidth(), onClick = { pick = true }) {
                            TText(if (apps.isEmpty()) "Elegir aplicaciones" else "${apps.size} aplicaciones elegidas", TTType.Section)
                        }
                    }
                    3 -> {
                        TText("Tu primera pausa", TTType.Title, Modifier.padding(top = 16.dp, bottom = 12.dp))
                        TText("Al abrir una app controlada verás un mensaje, una espera y podrás continuar o salir sin entrar.", TTType.Body, color = TT.Text2, align = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        OnbStepper("Espera inicial", "${state.pause.waitSec} s",
                            { Store.update { it.copy(pause = it.pause.copy(waitSec = (it.pause.waitSec - 5).coerceAtLeast(0))) } },
                            { Store.update { it.copy(pause = it.pause.copy(waitSec = (it.pause.waitSec + 5).coerceAtMost(120))) } })
                        OnbStepper("Acceso permitido", "${state.pause.allowMin} min",
                            { Store.update { it.copy(pause = it.pause.copy(allowMin = (it.pause.allowMin - 1).coerceAtLeast(1))) } },
                            { Store.update { it.copy(pause = it.pause.copy(allowMin = (it.pause.allowMin + 1).coerceAtMost(120))) } })
                        TText("Valores iniciales: 15 s de espera y 5 min de acceso.", TTType.Label)
                    }
                    else -> {
                        TText("Permisos", TTType.Title, Modifier.padding(top = 16.dp, bottom = 8.dp))
                        TText("Te explicamos cada uno antes de pedirlo. Si prefieres no concederlos, podrás seguir con lo que quede disponible.", TTType.Body, color = TT.Text2, align = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        @Suppress("UNUSED_VARIABLE") val t = resume
                        PermCard(
                            "Acceso a estadísticas de uso",
                            "Permite ver cuánto tiempo pasas en cada app. No bloquea nada. Solo lee el tiempo en primer plano.",
                            "Si no lo concedes: no habrá estadísticas ni anillo de uso.",
                            Perms.usageAccess(ctx),
                        ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        PermCard(
                            "Servicio de accesibilidad (intervención)",
                            "Permite saber qué app se abre para mostrar la pausa o el bloqueo encima. No lee mensajes ni contenido de otras apps y no es una herramienta de accesibilidad para discapacidad. " +
                                "En Android 13+ si instalaste el APK a mano, antes puede hacer falta «Permitir ajustes restringidos» en Información de la app.",
                            "Si no lo concedes: no habrá pausas ni bloqueos (solo estadísticas y organización).",
                            Perms.accessibilityEnabled(ctx),
                        ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        PermCard(
                            "Notificaciones",
                            "Muestran la cuenta atrás de las sesiones activas y los recordatorios opcionales.",
                            "Si no las concedes: no verás la notificación de sesión.",
                            Perms.notifications(ctx),
                        ) { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                    }
                }
            }
        }
        WhiteButton(if (step == last) "Empezar" else if (step == 0) "Comenzar" else "Continuar") {
            if (step == 1) Store.update { it.copy(userName = name.trim()) }
            if (step == last) Store.update { it.copy(onboardingDone = true, userName = name.trim()) } else step++
        }
        if (step in 1..last) GhostButton(if (step == 2 || step == last) "Ahora no" else "Omitir", Modifier.fillMaxWidth()) {
            if (step == last) Store.update { it.copy(onboardingDone = true, userName = name.trim()) } else step++
        }
    }
    if (pick) AppPickerDialog("Aplicaciones controladas", apps, { pick = false }) {
        apps = it; pick = false; Store.update { s -> s.copy(controlled = it) }
    }
}

@Composable
private fun IntroCard(title: String, text: String) {
    TCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        TText(title, TTType.Section)
        TText(text, TTType.Small)
    }
}

@Composable
private fun OnbStepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TText(label, TTType.Body, Modifier.weight(1f))
        GhostButton("−", Modifier.size(48.dp), minus)
        TText(value, TTType.BodyMed, Modifier.widthIn(min = 56.dp), align = TextAlign.Center)
        GhostButton("+", Modifier.size(48.dp), plus)
    }
}

@Composable
private fun PermCard(title: String, what: String, ifNot: String, granted: Boolean, onGrant: () -> Unit) {
    TCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        TText(title, TTType.Section)
        TText(what, TTType.Small, Modifier.padding(top = 4.dp))
        TText(ifNot, TTType.Label, Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        if (granted) TText("✓ Concedido", TTType.BodyMed, color = TT.Green)
        else WhiteButton("Conceder…", onClick = onGrant)
    }
}
