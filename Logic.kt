package com.thinktwice.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class Restriction(val name: String, val endsAtMs: Long, val source: String)

enum class DayStatus { MET_MEASURED, MET_MANUAL, MISSED_MEASURED, PENDING, NO_DATA }
data class ChallengeDay(val date: LocalDate, val status: DayStatus)

/** Lógica pura (sin Android) para poder probarla con tests unitarios. */
object Logic {

    /**
     * Ventanas [inicio, fin) de una rutina que pueden contener `nowMs`: las que empiezan hoy y las que
     * empezaron ayer (para horarios que cruzan la medianoche). Se calculan con hora local de pared, así que
     * un cambio de zona horaria o de huso se refleja en la siguiente comprobación sin reprogramar nada.
     */
    fun routineWindows(r: Routine, nowMs: Long, zone: ZoneId): List<Pair<Long, Long>> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val out = ArrayList<Pair<Long, Long>>(2)
        for (offset in -1L..0L) {
            val d = today.plusDays(offset)
            if (d.dayOfWeek.value !in r.days) continue
            val start = d.atTime(r.startMin / 60, r.startMin % 60).atZone(zone)
            val endDate = if (r.endMin > r.startMin) d else d.plusDays(1)
            val end = endDate.atTime(r.endMin / 60, r.endMin % 60).atZone(zone)
            out += start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
        }
        return out
    }

    /**
     * Restricciones vigentes por aplicación. Si varias se solapan, manda la que termina más tarde, de modo
     * que al acabar una sesión no se libera una app que otra rutina sigue restringiendo.
     */
    fun activeRestrictions(s: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<String, Restriction> {
        val m = HashMap<String, Restriction>()
        fun add(pkg: String, r: Restriction) {
            val cur = m[pkg]
            if (cur == null || r.endsAtMs > cur.endsAtMs) m[pkg] = r
        }
        for (ss in s.sessions) if (ss.isActive(now)) ss.apps.forEach { add(it, Restriction(ss.name, ss.effectiveEnd(), "session")) }
        for (r in s.routines) {
            if (!r.enabled) continue
            val suspendedUntil = s.suspended[r.id] ?: 0L
            for (w in routineWindows(r, now, zone)) {
                if (now >= w.first && now < w.second && w.second > suspendedUntil) {
                    r.apps.forEach { add(it, Restriction(r.name, w.second, "routine")) }
                }
            }
        }
        return m
    }

    /** Rutinas con ventana en curso ahora (para el estado en Inicio). */
    fun activeRoutines(s: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Routine> =
        s.routines.filter { r ->
            r.enabled && routineWindows(r, now, zone).any { now >= it.first && now < it.second && it.second > (s.suspended[r.id] ?: 0L) }
        }

    /** Próximo inicio (epoch ms) de la rutina a partir de `now`, buscando hasta 8 días. null si no tiene días. */
    fun nextStart(r: Routine, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (r.days.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (o in 0L..8L) {
            val d = today.plusDays(o)
            if (d.dayOfWeek.value !in r.days) continue
            val start = d.atTime(r.startMin / 60, r.startMin % 60).atZone(zone).toInstant().toEpochMilli()
            if (start > now) return start
        }
        return null
    }

    fun isGranted(s: AppState, pkg: String, now: Long): Boolean = (s.grants[pkg] ?: 0L) > now

    fun productivityPercent(usefulMin: Int, distractingMin: Int): Int? {
        val base = usefulMin + distractingMin
        return if (base <= 0) null else (usefulMin * 100.0 / base).roundToInt()
    }

    fun challengeLengthDays(hours: Int): Int = when (hours) { 24 -> 1; 48 -> 2; else -> hours / 24 }

    /**
     * Estado de cada día de un reto. Distingue resultados medidos de confirmaciones manuales:
     *  - regla 0 (rutina): medido solo si el servicio de intervención está activo; sin él, "sin datos".
     *  - regla 1 (sesión diaria): medido con las sesiones registradas por la propia app.
     *  - regla 2 (objetivo de uso): medido con estadísticas de uso; si no hay datos del día, "sin datos".
     */
    fun challengeDays(
        c: Challenge, s: AppState, now: Long, zone: ZoneId,
        usageMinByDate: Map<LocalDate, Int>, serviceActive: Boolean,
    ): List<ChallengeDay> {
        val startDate = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (0 until challengeLengthDays(c.hours)).map { i ->
            val d = startDate.plusDays(i.toLong())
            val manual = d.toString() in c.confirmed
            val measured: Boolean? = when (c.rule) {
                1 -> if (s.sessions.any { it.completedNaturally(now) && !it.emergency &&
                        Instant.ofEpochMilli(it.endMs).atZone(zone).toLocalDate() == d }) true
                     else if (d < today) false else null
                0 -> {
                    val routine = s.routines.firstOrNull { it.id == c.ruleArg }
                    val scheduled = routine != null && d.dayOfWeek.value in routine.days
                    val emergency = s.events.any { it.type == "ROUTINE_EMERGENCY" && it.detail == c.ruleArg &&
                        Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() == d }
                    when {
                        !serviceActive || routine == null -> null
                        emergency -> false
                        d < today && scheduled -> true
                        else -> null
                    }
                }
                2 -> usageMinByDate[d]?.let { it <= (c.ruleArg.toIntOrNull() ?: 0) }
                else -> null
            }
            val status = when {
                measured == true -> DayStatus.MET_MEASURED
                manual -> DayStatus.MET_MANUAL
                measured == false -> DayStatus.MISSED_MEASURED
                d >= today -> DayStatus.PENDING
                else -> DayStatus.NO_DATA
            }
            ChallengeDay(d, status)
        }
    }
}
