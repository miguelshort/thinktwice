package com.thinktwice.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exportar/importar con validación. Necesita org.json real (dependencia de test org.json:json). */
class BackupTest {
    private fun sample() = AppState(
        userName = "Ana", onboardingDone = true, controlled = setOf("a.b", "c.d"),
        pause = PauseSettings(true, 20, 7, listOf("Tómatelo con calma.")),
        sessions = listOf(Session("s1", "Trabajo", setOf("a.b"), 1000, 2000, endedAtMs = 1500, emergency = true)),
        routines = listOf(Routine("r1", "Noche", setOf("a.b"), 22 * 60, 7 * 60, setOf(1, 2), true)),
        challenges = listOf(Challenge("c1", "Reto", 168, 2, "90", 5, setOf("2026-11-16"))),
        categories = mapOf("a.b" to Cat.DISTRACTING),
        events = listOf(LogEvent(1, "PAUSE_SHOWN", "a.b", null)),
        daySnaps = mapOf("2026-11-15" to DaySnap(120, 30, 60)),
        grants = mapOf("a.b" to 99L),
    )

    @Test fun roundTripKeepsUserData() {
        val back = AppState.fromJson(JSONObject(sample().toJson().toString()))
        val s = sample()
        assertEquals(s.userName, back.userName); assertEquals(s.controlled, back.controlled)
        assertEquals(s.pause, back.pause); assertEquals(s.sessions, back.sessions)
        assertEquals(s.routines, back.routines); assertEquals(s.challenges, back.challenges)
        assertEquals(s.categories, back.categories); assertEquals(s.events, back.events)
        assertEquals(s.daySnaps, back.daySnaps)
    }

    @Test fun exportOmitsTransientAccessGrants() {
        assertFalse(sample().toJson().has("grants"))
        assertTrue(sample().toJson(includeTransient = true).has("grants"))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsForeignFiles() {
        AppState.fromJson(JSONObject("""{"format":"otra-app","version":1}"""))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownVersion() {
        AppState.fromJson(JSONObject(sample().toJson().put("version", 2).toString()))
    }

    @Test(expected = Exception::class) fun rejectsTruncatedFile() {
        AppState.fromJson(JSONObject(sample().toJson().toString().dropLast(40)))
    }

    @Test fun clampsOutOfRangeValues() {
        val j = sample().toJson()
        j.getJSONObject("pause").put("waitSec", 99999).put("allowMin", -5)
        j.put("reminderHour", 77)
        val s = AppState.fromJson(JSONObject(j.toString()))
        assertEquals(120, s.pause.waitSec); assertEquals(1, s.pause.allowMin); assertEquals(23, s.reminderHour)
    }
}
