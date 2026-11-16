package com.thinktwice.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Lógica pura de horarios, restricciones solapadas y retos. No necesita Android. */
class LogicTest {
    private val mad = ZoneId.of("Europe/Madrid")
    private val A = "app.a"
    private val B = "app.b"

    // 2026-11-16 es lunes.
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, z: ZoneId = mad) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()

    private fun routine(id: String = "r1", apps: Set<String> = setOf(A), s: Int, e: Int,
                        days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), enabled: Boolean = true) =
        Routine(id, "R-$id", apps, s, e, days, enabled)

    private fun state(vararg r: Routine, sessions: List<Session> = emptyList(), suspended: Map<String, Long> = emptyMap()) =
        AppState(routines = r.toList(), sessions = sessions, suspended = suspended)

    private fun restricted(s: AppState, now: Long, pkg: String = A, zone: ZoneId = mad) = Logic.activeRestrictions(s, now, zone)[pkg]

    // ---------- horarios en el mismo día
    @Test fun sameDayWindowIsHalfOpen() {
        val s = state(routine(s = 9 * 60, e = 14 * 60, days = setOf(1, 2, 3, 4, 5)))
        assertNotNull(restricted(s, at(2026, 11, 16, 10)))
        assertNull("el fin es exclusivo", restricted(s, at(2026, 11, 16, 14)))
        assertNull(restricted(s, at(2026, 11, 16, 8, 59)))
        assertNotNull("inicio inclusivo", restricted(s, at(2026, 11, 16, 9)))
        assertNull("sábado no está en los días", restricted(s, at(2026, 11, 21, 10)))
    }

    // ---------- medianoche
    @Test fun nightRoutineCrossesMidnightFromTheDayItStarts() {
        val s = state(routine(s = 22 * 60 + 30, e = 7 * 60, days = setOf(1)))   // empieza los lunes
        assertNotNull("lunes 23:00", restricted(s, at(2026, 11, 16, 23)))
        assertNotNull("martes 02:00 (empezó el lunes)", restricted(s, at(2026, 11, 17, 2)))
        assertNull("martes 07:00 ya terminó", restricted(s, at(2026, 11, 17, 7)))
        assertNull("lunes 02:00: el domingo no está en los días", restricted(s, at(2026, 11, 16, 2)))
        assertNull("martes 23:00: el martes no está en los días", restricted(s, at(2026, 11, 17, 23)))
    }

    @Test fun nightRoutineReportsExactEndInstant() {
        val s = state(routine(s = 22 * 60, e = 7 * 60))
        assertEquals(at(2026, 11, 17, 7), restricted(s, at(2026, 11, 16, 23))!!.endsAtMs)
        assertEquals(at(2026, 11, 17, 7), restricted(s, at(2026, 11, 17, 1))!!.endsAtMs)
    }

    @Test fun equalStartAndEndMeansTwentyFourHours() {
        val s = state(routine(s = 8 * 60, e = 8 * 60))
        assertNotNull(restricted(s, at(2026, 11, 16, 20)))
        assertNotNull(restricted(s, at(2026, 11, 17, 7, 59)))
        assertEquals(at(2026, 11, 17, 8), restricted(s, at(2026, 11, 16, 20))!!.endsAtMs)
    }

    // ---------- solapes
    @Test fun overlappingRoutinesKeepAppRestrictedUntilTheLastEnds() {
        val night = routine("n", s = 22 * 60, e = 7 * 60)
        val morning = routine("m", s = 6 * 60, e = 9 * 60)
        val s = state(night, morning)
        val r1 = restricted(s, at(2026, 11, 17, 6, 30))!!
        assertEquals("manda la que termina más tarde", at(2026, 11, 17, 9), r1.endsAtMs)
        assertEquals("R-m", r1.name)
        assertNotNull("a las 07:30 la de noche acabó pero la de mañana sigue", restricted(s, at(2026, 11, 17, 7, 30)))
        assertNull(restricted(s, at(2026, 11, 17, 9)))
    }

    @Test fun endingASessionDoesNotReleaseAppsAnotherRoutineStillRestricts() {
        val session = Session("s1", "Estudiar", setOf(A), at(2026, 11, 16, 7), at(2026, 11, 16, 8))
        val s = state(routine(s = 6 * 60, e = 9 * 60), sessions = listOf(session))
        assertEquals(at(2026, 11, 16, 9), restricted(s, at(2026, 11, 16, 7, 30))!!.endsAtMs)
        val after = restricted(s, at(2026, 11, 16, 8, 30))
        assertNotNull("la sesión terminó pero la rutina sigue", after)
        assertEquals("routine", after!!.source)
    }

    @Test fun sessionAloneIsReleasedAtItsEndAndOnlyAffectsItsApps() {
        val session = Session("s1", "Concentración", setOf(A), at(2026, 11, 16, 10), at(2026, 11, 16, 11))
        val s = state(sessions = listOf(session))
        assertNotNull(restricted(s, at(2026, 11, 16, 10, 30)))
        assertNull(restricted(s, at(2026, 11, 16, 10, 30), B))
        assertNull("fin exclusivo", restricted(s, at(2026, 11, 16, 11)))
        assertNull("antes del inicio", restricted(s, at(2026, 11, 16, 9, 59)))
    }

    // ---------- salida de emergencia
    @Test fun emergencyExitEndsSessionImmediately() {
        val ended = Session("s1", "Trabajo", setOf(A), at(2026, 11, 16, 10), at(2026, 11, 16, 12),
            endedAtMs = at(2026, 11, 16, 10, 20), emergency = true)
        val s = state(sessions = listOf(ended))
        assertNull(restricted(s, at(2026, 11, 16, 10, 21)))
        assertFalse("no cuenta como completada", ended.completedNaturally(at(2026, 11, 16, 13)))
    }

    @Test fun emergencyExitSuspendsOnlyThatOccurrenceOfARoutine() {
        val r = routine(s = 22 * 60, e = 7 * 60)
        val windowEnd = at(2026, 11, 17, 7)
        val s = state(r, suspended = mapOf("r1" to windowEnd))
        assertNull("noche suspendida", restricted(s, at(2026, 11, 17, 1)))
        assertNotNull("la noche siguiente vuelve a aplicarse", restricted(s, at(2026, 11, 17, 23)))
    }

    // ---------- zonas horarias y horario de verano
    @Test fun windowsFollowTheCurrentTimeZone() {
        val s = state(routine(s = 9 * 60, e = 10 * 60))
        val now = at(2026, 11, 16, 9, 30)                            // 09:30 en Madrid
        assertNotNull(restricted(s, now, zone = mad))
        assertNull("en Nueva York son las 03:30", restricted(s, now, zone = ZoneId.of("America/New_York")))
        assertNull("en Londres son las 08:30", restricted(s, now, zone = ZoneId.of("Europe/London")))
    }

    @Test fun nightRoutineAcrossAutumnDstChangeEndsAtLocalSeven() {
        val s = state(routine(s = 22 * 60, e = 7 * 60, days = setOf(6)))   // empieza el sábado 24-oct-2026
        val r = restricted(s, at(2026, 10, 25, 6, 30))                    // domingo, ya con la hora de invierno
        assertNotNull(r)
        assertEquals(at(2026, 10, 25, 7), r!!.endsAtMs)
        val start = at(2026, 10, 24, 22)
        assertEquals("esa noche dura 10 h por la hora repetida", 10L * 3_600_000, r.endsAtMs - start)
    }

    // ---------- otros
    @Test fun disabledOrDaylessRoutinesNeverRestrict() {
        assertNull(restricted(state(routine(s = 0, e = 1439, enabled = false)), at(2026, 11, 16, 10)))
        assertNull(restricted(state(routine(s = 0, e = 1439, days = emptySet())), at(2026, 11, 16, 10)))
    }

    @Test fun activeRoutinesListsOnlyRunningOnes() {
        val s = state(routine("a", s = 9 * 60, e = 10 * 60), routine("b", s = 20 * 60, e = 21 * 60))
        assertEquals(listOf("a"), Logic.activeRoutines(s, at(2026, 11, 16, 9, 30), mad).map { it.id })
    }

    @Test fun nextStartLooksAheadAndHandlesEmptyDays() {
        val r = routine(s = 9 * 60, e = 10 * 60, days = setOf(1))                    // lunes
        assertEquals(at(2026, 11, 23, 9), Logic.nextStart(r, at(2026, 11, 16, 10), mad))
        assertEquals(at(2026, 11, 16, 9), Logic.nextStart(r, at(2026, 11, 15, 12), mad))
        assertNull(Logic.nextStart(r.copy(days = emptySet()), at(2026, 11, 16, 10), mad))
    }

    @Test fun grantsExpire() {
        val s = AppState(grants = mapOf(A to 1000L))
        assertTrue(Logic.isGranted(s, A, 999)); assertFalse(Logic.isGranted(s, A, 1000)); assertFalse(Logic.isGranted(s, B, 1))
    }

    @Test fun productivityPercentNeedsUsefulOrDistractingTime() {
        assertNull(Logic.productivityPercent(0, 0))
        assertEquals(75, Logic.productivityPercent(30, 10))
        assertEquals(0, Logic.productivityPercent(0, 10))
        assertEquals(100, Logic.productivityPercent(10, 0))
    }

    @Test fun challengeLengths() {
        assertEquals(listOf(1, 2, 7, 21, 30), listOf(24, 48, 168, 504, 720).map { Logic.challengeLengthDays(it) })
    }

    // ---------- retos: medido vs. manual vs. sin datos
    private val today = LocalDate.of(2026, 11, 16)
    private val nowMs get() = at(2026, 11, 16, 12)

    @Test fun dailySessionChallengeDistinguishesMeasuredManualMissedPending() {
        val c = Challenge("c", "Reto", 168, 1, "", at(2026, 11, 13, 9), confirmed = setOf("2026-11-13"))
        val done = Session("s1", "x", setOf(A), at(2026, 11, 14, 9), at(2026, 11, 14, 10))
        val emergency = Session("s2", "x", setOf(A), at(2026, 11, 15, 9), at(2026, 11, 15, 10), endedAtMs = at(2026, 11, 15, 9, 5), emergency = true)
        val days = Logic.challengeDays(c, state(sessions = listOf(done, emergency)), nowMs, mad, emptyMap(), true)
        assertEquals(7, days.size)
        assertEquals("manual", DayStatus.MET_MANUAL, days[0].status)          // 13: sin sesión pero confirmado a mano
        assertEquals(DayStatus.MET_MEASURED, days[1].status)                   // 14
        assertEquals("la salida de emergencia no cuenta", DayStatus.MISSED_MEASURED, days[2].status)  // 15
        assertEquals("hoy aún puede cumplirse", DayStatus.PENDING, days[3].status)                    // 16
        assertEquals(DayStatus.PENDING, days[6].status)
    }

    @Test fun usageGoalChallengeNeedsUsageDataElseNoData() {
        val c = Challenge("c", "Reto", 168, 2, "120", at(2026, 11, 12, 9))
        val usage = mapOf(LocalDate.of(2026, 11, 12) to 100, LocalDate.of(2026, 11, 14) to 200)
        val days = Logic.challengeDays(c, state(), nowMs, mad, usage, false)
        assertEquals(DayStatus.MET_MEASURED, days[0].status)     // 12: 100 <= 120
        assertEquals("sin datos de ese día: no se inventa", DayStatus.NO_DATA, days[1].status)  // 13
        assertEquals(DayStatus.MISSED_MEASURED, days[2].status)  // 14: 200 > 120
        assertEquals(DayStatus.NO_DATA, days[3].status)          // 15
        assertEquals(DayStatus.PENDING, days[4].status)          // 16 = hoy
    }

    @Test fun routineChallengeIsNotMeasuredWithoutTheService() {
        val r = routine("r1", s = 9 * 60, e = 10 * 60)
        val c = Challenge("c", "Reto", 48, 0, "r1", at(2026, 11, 14, 9))
        val off = Logic.challengeDays(c, state(r), nowMs, mad, emptyMap(), serviceActive = false)
        assertEquals("sin servicio no hay medición", DayStatus.NO_DATA, off[0].status)
        val on = Logic.challengeDays(c, state(r), nowMs, mad, emptyMap(), serviceActive = true)
        assertEquals(DayStatus.MET_MEASURED, on[0].status)
        val withExit = state(r).copy(events = listOf(LogEvent(at(2026, 11, 14, 9, 30), "ROUTINE_EMERGENCY", A, "r1")))
        assertEquals(DayStatus.MISSED_MEASURED, Logic.challengeDays(c, withExit, nowMs, mad, emptyMap(), true)[0].status)
    }
}
