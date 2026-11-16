package com.thinktwice.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinktwice.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        setContent { ThinkTwiceTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val state by Store.state.collectAsState()
    Box(Modifier.fillMaxSize().background(TT.Bg)) {
        if (!state.onboardingDone) OnboardingScreen(state)
        else MainShell(state)
    }
}

/** Pila de rutas serializada como texto ("stats|routine:ab12") para que sobreviva a rotaciones. */
@Composable
private fun MainShell(state: AppState) {
    var stack by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("home") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val routes = if (stack.isEmpty()) emptyList() else stack.split("|")
    fun push(r: String) { stack = if (stack.isEmpty()) r else "$stack|$r" }
    fun pop() { stack = routes.dropLast(1).joinToString("|") }
    // "tabroutines" cambia de pestaña en lugar de apilar una pantalla (se usa desde las tarjetas de Inicio).
    fun go(r: String) { if (r == "tabroutines") { stack = ""; tab = "routines" } else push(r) }
    BackHandler(enabled = routes.isNotEmpty() && !sheet) { pop() }

    val blur by animateDpAsState(if (sheet) 14.dp else 0.dp, label = "blur")

    Column(Modifier.fillMaxSize().systemBarsPadding().blur(blur)) {
        Box(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            AnimatedContent(
                targetState = routes.lastOrNull() ?: "tab:$tab",
                transitionSpec = {
                    val deeper = targetState != "tab:home" && targetState != "tab:routines"
                    if (deeper) (slideInHorizontally { it / 6 } + fadeIn()) togetherWith (fadeOut())
                    else fadeIn() togetherWith fadeOut()
                },
                label = "screen",
            ) { route ->
                when {
                    route == "tab:home" -> HomeScreen(state, ::go)
                    route == "tab:routines" -> RoutinesScreen(state, ::go)
                    route == "stats" -> StatsScreen(state, ::pop)
                    route == "settings" -> SettingsScreen(state, ::pop, ::go)
                    route == "apps" -> ControlledAppsScreen(state, ::pop)
                    route == "messages" -> MessagesScreen(state, ::pop)
                    route == "scheduled" -> ScheduledScreen(state, ::pop, ::go)
                    route.startsWith("routine:") -> RoutineEditorScreen(state, route.removePrefix("routine:"), ::pop)
                    route.startsWith("newchallenge:") -> NewChallengeScreen(state, route.removePrefix("newchallenge:").toIntOrNull() ?: 24, ::pop)
                    route.startsWith("challenge:") -> ChallengeDetailScreen(state, route.removePrefix("challenge:"), ::pop)
                    else -> HomeScreen(state, ::go)
                }
            }
        }
        if (routes.isEmpty()) BottomBar(tab, onTab = { tab = it }, onAdd = { sheet = true })
    }
    if (sheet) BlockSheet(state, onDismiss = { sheet = false })
}

@Composable
private fun BottomBar(tab: String, onTab: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(TT.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem("Rutinas", Icons.Outlined.DateRange, tab == "routines") { onTab("routines") }
        NavItem("Inicio", Icons.Outlined.Home, tab == "home") { onTab("home") }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.White).clickable(onClick = onAdd)
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Add, "Crear bloqueo", tint = TT.Bg) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c by animateColorAsState(if (selected) TT.Text else TT.Text2, label = "nav")
    Column(
        Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).semantics { role = Role.Tab }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c)
        // El estado activo no depende solo del color: la etiqueta pasa a seminegrita.
        TText(label, if (selected) TTType.Section.copy(fontSize = 12.sp) else TTType.Label, color = c)
    }
}
