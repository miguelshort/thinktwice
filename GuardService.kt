package com.thinktwice.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de intervención. Mecanismo:
 *  - Escucha SOLO TYPE_WINDOW_STATE_CHANGED para saber qué aplicación pasa a primer plano.
 *  - Declara canRetrieveWindowContent="false": no lee texto, mensajes ni contenido de otras apps.
 *  - Si la app está restringida (sesión/rutina) o tiene pausa activa, lanza InterventionActivity encima.
 *  - "Salir sin entrar" usa la acción global HOME.
 * Es una barrera de software que el usuario puede desactivar desde Ajustes de Android: no es un bloqueo del sistema.
 */
class GuardService : AccessibilityService() {

    companion object {
        @Volatile var instance: GuardService? = null
        private const val RECHECK_MS = 30_000L
        private const val DEBOUNCE_MS = 1_500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var ignored: Set<String> = emptySet()
    private val lastShown = HashMap<String, Long>()
    private val recheck = Runnable { currentPkg?.let { evaluate(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(applicationContext)
        instance = this
        ignored = Apps.imePackages(this) + "com.android.systemui"
        Store.log("SERVICE_ON")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return
        currentPkg = pkg
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        handler.removeCallbacks(recheck)
        if (Apps.isEssential(this, pkg)) return
        val s = Store.state.value
        val now = System.currentTimeMillis()

        val restriction = Logic.activeRestrictions(s, now)[pkg]
        if (restriction != null) {
            launch(InterventionActivity.block(this, pkg, restriction))
            return
        }
        val watched = pkg in s.controlled || s.routines.any { pkg in it.apps } || s.sessions.any { pkg in it.apps }
        if (s.pause.enabled && pkg in s.controlled) {
            val until = s.grants[pkg] ?: 0L
            if (until > now) {
                // Acceso permitido tras una pausa: se vuelve a comprobar justo al terminar.
                handler.postDelayed(recheck, (until - now + 300).coerceAtLeast(500))
                return
            }
            launch(InterventionActivity.pause(this, pkg))
            return
        }
        // Comprobación periódica barata, solo mientras una app vigilada está delante (p. ej. empieza una rutina).
        if (watched) handler.postDelayed(recheck, RECHECK_MS)
    }

    private fun launch(i: Intent) {
        val pkg = i.getStringExtra(InterventionActivity.EXTRA_PKG) ?: return
        if (InterventionActivity.showingPkg != null) return            // ya hay una intervención visible: sin duplicados
        val now = System.currentTimeMillis()
        if (now - (lastShown[pkg] ?: 0L) < DEBOUNCE_MS) return          // evita bucles por eventos repetidos
        lastShown[pkg] = now
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try { startActivity(i) } catch (_: Exception) { Store.log("LAUNCH_FAILED", pkg) }
    }

    fun goHome() { performGlobalAction(GLOBAL_ACTION_HOME) }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacksAndMessages(null)
        Store.log("SERVICE_OFF")
        return super.onUnbind(intent)
    }
}
