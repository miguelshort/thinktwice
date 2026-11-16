package com.thinktwice.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

data class PauseSettings(
    val enabled: Boolean = true,
    val waitSec: Int = 15,
    val allowMin: Int = 5,
    val customMessages: List<String> = emptyList(),
)

data class Template(val id: String, val name: String, val apps: Set<String>, val minutes: Int)

data class Session(
    val id: String,
    val name: String,
    val apps: Set<String>,
    val startMs: Long,
    val endMs: Long,
    val endedAtMs: Long? = null,
    val emergency: Boolean = false,
) {
    fun effectiveEnd(): Long = endedAtMs ?: endMs
    fun isActive(now: Long): Boolean = now >= startMs && now < effectiveEnd()
    /** Terminó por sí sola (sin salida de emergencia). */
    fun completedNaturally(now: Long): Boolean = endedAtMs == null && now >= endMs
}

data class Routine(
    val id: String,
    val name: String,
    val apps: Set<String>,
    val startMin: Int,        // minutos desde las 00:00
    val endMin: Int,          // si <= startMin la rutina cruza la medianoche
    val days: Set<Int>,       // ISO: 1 = lunes ... 7 = domingo (día en que EMPIEZA)
    val enabled: Boolean,
)

/** rule: 0 = cumplir una rutina, 1 = completar una sesión diaria, 2 = respetar un objetivo de uso. */
data class Challenge(
    val id: String,
    val name: String,
    val hours: Int,
    val rule: Int,
    val ruleArg: String,      // id de rutina (rule 0) o minutos máximos (rule 2)
    val startMs: Long,
    val confirmed: Set<String> = emptySet(),   // fechas ISO confirmadas a mano
)

data class LogEvent(val ts: Long, val type: String, val pkg: String? = null, val detail: String? = null)

data class DaySnap(val totalMin: Int, val usefulMin: Int, val distractingMin: Int)

object Cat { const val USEFUL = 0; const val DISTRACTING = 1; const val NEUTRAL = 2 }

data class AppState(
    val userName: String = "",
    val onboardingDone: Boolean = false,
    val controlled: Set<String> = emptySet(),
    val pause: PauseSettings = PauseSettings(),
    val templates: List<Template> = defaultTemplates(),
    val sessions: List<Session> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    val categories: Map<String, Int> = emptyMap(),
    val events: List<LogEvent> = emptyList(),
    val grants: Map<String, Long> = emptyMap(),        // paquete -> fin del acceso permitido tras una pausa
    val suspended: Map<String, Long> = emptyMap(),     // rutina -> fin de la ocurrencia suspendida (salida de emergencia)
    val daySnaps: Map<String, DaySnap> = emptyMap(),   // fecha ISO -> resumen guardado de días ya cerrados
    val remindersOn: Boolean = false,
    val reminderHour: Int = 20,
) {
    fun category(pkg: String) = categories[pkg] ?: Cat.NEUTRAL

    fun toJson(includeTransient: Boolean = false): JSONObject = JSONObject().apply {
        put("format", "thinktwice-backup")
        put("version", 1)
        put("userName", userName)
        put("onboardingDone", onboardingDone)
        put("controlled", JSONArray(controlled.toList()))
        put("pause", JSONObject().apply {
            put("enabled", pause.enabled); put("waitSec", pause.waitSec); put("allowMin", pause.allowMin)
            put("customMessages", JSONArray(pause.customMessages))
        })
        put("templates", JSONArray().also { a -> templates.forEach { t ->
            a.put(JSONObject().put("id", t.id).put("name", t.name).put("apps", JSONArray(t.apps.toList())).put("minutes", t.minutes)) } })
        put("sessions", JSONArray().also { a -> sessions.forEach { s ->
            a.put(JSONObject().put("id", s.id).put("name", s.name).put("apps", JSONArray(s.apps.toList()))
                .put("startMs", s.startMs).put("endMs", s.endMs)
                .put("endedAtMs", s.endedAtMs ?: JSONObject.NULL).put("emergency", s.emergency)) } })
        put("routines", JSONArray().also { a -> routines.forEach { r ->
            a.put(JSONObject().put("id", r.id).put("name", r.name).put("apps", JSONArray(r.apps.toList()))
                .put("startMin", r.startMin).put("endMin", r.endMin).put("days", JSONArray(r.days.toList()))
                .put("enabled", r.enabled)) } })
        put("challenges", JSONArray().also { a -> challenges.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("name", c.name).put("hours", c.hours).put("rule", c.rule)
                .put("ruleArg", c.ruleArg).put("startMs", c.startMs).put("confirmed", JSONArray(c.confirmed.toList()))) } })
        put("categories", JSONObject().also { o -> categories.forEach { (k, v) -> o.put(k, v) } })
        put("events", JSONArray().also { a -> events.forEach { e ->
            a.put(JSONObject().put("ts", e.ts).put("type", e.type)
                .put("pkg", e.pkg ?: JSONObject.NULL).put("detail", e.detail ?: JSONObject.NULL)) } })
        put("daySnaps", JSONObject().also { o -> daySnaps.forEach { (k, v) ->
            o.put(k, JSONObject().put("t", v.totalMin).put("u", v.usefulMin).put("d", v.distractingMin)) } })
        put("remindersOn", remindersOn)
        put("reminderHour", reminderHour)
        if (includeTransient) {
            put("grants", JSONObject().also { o -> grants.forEach { (k, v) -> o.put(k, v) } })
            put("suspended", JSONObject().also { o -> suspended.forEach { (k, v) -> o.put(k, v) } })
        }
    }

    companion object {
        fun defaultTemplates() = listOf(
            Template("t_focus", "Concentración", emptySet(), 45),
            Template("t_study", "Estudiar", emptySet(), 90),
            Template("t_work", "Trabajo", emptySet(), 120),
            Template("t_sleep", "Dormir", emptySet(), 480),
        )

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
        private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }
        private fun JSONObject.optStr(k: String): String? = if (isNull(k)) null else optString(k)

        /** Lanza una excepción si el JSON no es una copia válida de thinktwice. */
        fun fromJson(o: JSONObject): AppState {
            require(o.optString("format") == "thinktwice-backup") { "No es una copia de thinktwice" }
            require(o.optInt("version", -1) == 1) { "Versión de copia no compatible" }
            val p = o.getJSONObject("pause")
            val pause = PauseSettings(
                enabled = p.getBoolean("enabled"),
                waitSec = p.getInt("waitSec").coerceIn(0, 120),
                allowMin = p.getInt("allowMin").coerceIn(1, 120),
                customMessages = p.getJSONArray("customMessages").strings().map { it.take(140) },
            )
            val templates = o.getJSONArray("templates").let { a -> (0 until a.length()).map {
                val t = a.getJSONObject(it)
                Template(t.getString("id"), t.getString("name"), t.getJSONArray("apps").strings().toSet(), t.getInt("minutes"))
            } }
            val sessions = o.getJSONArray("sessions").let { a -> (0 until a.length()).map {
                val s = a.getJSONObject(it)
                Session(s.getString("id"), s.getString("name"), s.getJSONArray("apps").strings().toSet(),
                    s.getLong("startMs"), s.getLong("endMs"),
                    if (s.isNull("endedAtMs")) null else s.getLong("endedAtMs"), s.optBoolean("emergency"))
            } }
            val routines = o.getJSONArray("routines").let { a -> (0 until a.length()).map {
                val r = a.getJSONObject(it)
                Routine(r.getString("id"), r.getString("name"), r.getJSONArray("apps").strings().toSet(),
                    r.getInt("startMin").coerceIn(0, 1439), r.getInt("endMin").coerceIn(0, 1439),
                    r.getJSONArray("days").ints().filter { d -> d in 1..7 }.toSet(), r.getBoolean("enabled"))
            } }
            val challenges = o.getJSONArray("challenges").let { a -> (0 until a.length()).map {
                val c = a.getJSONObject(it)
                Challenge(c.getString("id"), c.getString("name"), c.getInt("hours"), c.getInt("rule"),
                    c.getString("ruleArg"), c.getLong("startMs"), c.getJSONArray("confirmed").strings().toSet())
            } }
            val cats = o.getJSONObject("categories").let { c -> c.keys().asSequence().associateWith { k -> c.getInt(k) } }
            val events = o.getJSONArray("events").let { a -> (0 until a.length()).map {
                val e = a.getJSONObject(it)
                LogEvent(e.getLong("ts"), e.getString("type"), e.optStr("pkg"), e.optStr("detail"))
            } }
            val snaps = o.getJSONObject("daySnaps").let { c -> c.keys().asSequence().associateWith { k ->
                val v = c.getJSONObject(k); DaySnap(v.getInt("t"), v.getInt("u"), v.getInt("d")) } }
            val grants = o.optJSONObject("grants")?.let { c -> c.keys().asSequence().associateWith { k -> c.getLong(k) } } ?: emptyMap()
            val susp = o.optJSONObject("suspended")?.let { c -> c.keys().asSequence().associateWith { k -> c.getLong(k) } } ?: emptyMap()
            return AppState(
                userName = o.optString("userName").take(40),
                onboardingDone = o.optBoolean("onboardingDone", true),
                controlled = o.getJSONArray("controlled").strings().toSet(),
                pause = pause, templates = templates.ifEmpty { defaultTemplates() }, sessions = sessions,
                routines = routines, challenges = challenges, categories = cats, events = events,
                grants = grants, suspended = susp, daySnaps = snaps,
                remindersOn = o.optBoolean("remindersOn"), reminderHour = o.optInt("reminderHour", 20).coerceIn(0, 23),
            )
        }
    }
}
