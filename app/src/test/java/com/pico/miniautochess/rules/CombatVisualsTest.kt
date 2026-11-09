package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class CombatVisualsTest {
    @Test fun skillsKeepFullBrightnessForReadableTimeBeforeFading() {
        Skill.entries.forEach { skill ->
            val life = CombatVisuals.skillSeconds(skill)
            assertTrue(life >= 1.2f && life <= 1.8f)
            assertEquals(1f, CombatVisuals.brightness(.6f), .0001f)
            assertTrue(life * .6f >= .72f)
        }
        assertEquals(.5f, CombatVisuals.brightness(.8f), .0001f)
        assertEquals(0f, CombatVisuals.brightness(1f), 0f)
    }
    @Test fun arrowsAndOrbsReachCapturedEndpointsAcrossDirectionsAndZeroDistance() {
        for (arrow in listOf(false, true)) for ((dx, dz) in listOf(0f to 0f, 1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
            val start = CombatVisuals.flight(0f, dx, .03f, dz, arrow)
            val end = CombatVisuals.flight(1f, dx, .03f, dz, arrow)
            assertEquals(0f, start.x, 0f); assertEquals(0f, start.y, 0f); assertEquals(0f, start.z, 0f)
            assertEquals(dx, end.x, 0f); assertEquals(.03f, end.y, .00001f); assertEquals(dz, end.z, 0f)
            val mid = CombatVisuals.flight(.5f, dx, .03f, dz, arrow)
            assertEquals(dx * .5f, mid.x, .00001f)
            if (arrow) assertTrue(mid.y > .05f) else assertEquals(.015f, mid.y, .00001f)
        }
        assertEquals(.30f, CombatVisuals.projectileSeconds(0f), .00001f)
        assertEquals(.55f, CombatVisuals.projectileSeconds(100f), .00001f)
    }
    @Test fun liveCombatEventsCaptureFiniteEndpointsBeforeTargetsFallAndNeverChangeAfterward() {
        val battle = Battle(1, Army(0, listOf(UnitSnapshot(1, "dawn_archer", 3, 0))),
            Army(1, listOf(UnitSnapshot(2, "rune_apprentice", 1, 0))))
        val events = linkedMapOf<Int, CombatEvent>()
        repeat(400) {
            battle.step()
            battle.snapshot().events.forEach { e ->
                assertTrue(listOf(e.fromX, e.fromY, e.toX, e.toY, e.time).all { it.isFinite() })
                events[e.sequence]?.let { assertEquals(it, e) }
                events[e.sequence] = e
            }
        }
        assertTrue(events.values.any { it.kind == CombatEventKind.SHOT })
        assertTrue(events.values.any { it.kind == CombatEventKind.FALL })
        assertNotNull(battle.outcome)
        assertTrue(events.values.all { it.time <= battle.elapsed })
    }
}
