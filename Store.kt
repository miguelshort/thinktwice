package com.thinktwice.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

class ThinkTwiceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

/**
 * Almacenamiento local único (un JSON en el directorio privado de la app).
 * Lo comparten la interfaz y el servicio porque viven en el mismo proceso.
 * Se escribe de forma atómica (archivo temporal + renombrado).
 */
object Store {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    private var file: File? = null

    @Volatile var loadedFromCorrupt = false
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "thinktwice_state.json")
        file = f
        if (f.exists()) {
            try {
                _state.value = AppState.fromJson(JSONObject(f.readText()))
            } catch (e: Exception) {
                // Archivo dañado: se conserva una copia y se empieza limpio.
                loadedFromCorrupt = true
                runCatching { f.copyTo(File(f.parentFile, "thinktwice_state.corrupt.json"), overwrite = true) }
                _state.value = AppState()
            }
        }
    }

    @Synchronized
    fun update(block: (AppState) -> AppState) {
        val next = block(_state.value)
        _state.value = next
        persist(next)
    }

    @Synchronized
    fun replaceAll(newState: AppState) {
        _state.value = newState
        persist(newState)
    }

    @Synchronized
    fun reset() = replaceAll(AppState())

    fun log(type: String, pkg: String? = null, detail: String? = null) = update { s ->
        val ev = (s.events + LogEvent(System.currentTimeMillis(), type, pkg, detail)).takeLast(3000)
        s.copy(events = ev)
    }

    private fun persist(s: AppState) {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(s.toJson(includeTransient = true).toString())
            if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        } catch (_: Exception) { /* sin espacio o error de E/S: el estado en memoria sigue vigente */ }
    }
}
