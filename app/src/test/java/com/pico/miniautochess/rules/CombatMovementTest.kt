package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class CombatMovementTest {
    private fun guard(uid: Int, slot: Int) = UnitSnapshot(uid, "moss_guard", 3, slot)
    private fun finish(b: Battle) {
        repeat(401) {
            if (b.outcome == null) b.step()
            assertTrue(b.snapshot().fighters.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..7f && it.y in 0f..7f })
        }
        assertNotNull(b.outcome)
    }
    @Test fun oppositeCornersReachMeleeRangeAndKeepAttacking() {
        val b = Battle(0, Army(0, listOf(guard(1, 24))), Army(1, listOf(guard(2, 24))))
        finish(b)
        b.snapshot().fighters.forEach {
            assertTrue("corner fighter $it", it.attacks >= 4)
            assertTrue(it.firstAttackTime!! < 10f)
        }
    }
    @Test fun crowdedSevenAgainstSevenIncludesRearAndEdgeFighters() {
        for (slots in listOf(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 8, 16, 24, 1, 9, 17),
            listOf(2, 3, 4, 10, 11, 12, 19))) {
            val b = Battle(0, Army(0, slots.mapIndexed { i, slot -> guard(i + 1, slot) }),
                Army(1, slots.mapIndexed { i, slot -> guard(i + 101, slot) }))
            finish(b)
            b.snapshot().fighters.forEach {
                assertTrue("never attacked, slots=$slots fighter=$it", it.attacks > 0)
                assertTrue("persistent obstruction $it", it.maxBlockedSeconds < 6f)
            }
        }
    }
    @Test fun rearMeleeDetoursAroundRangedAllyRatherThanAttackingThroughIt() {
        val rear = guard(1, 11)
        val blocker = UnitSnapshot(2, "dawn_archer", 3, 3)
        val b = Battle(0, Army(0, listOf(rear, blocker)), Army(1, listOf(guard(101, 4))))
        var sidestepped = false
        repeat(80) {
            if (b.outcome == null) b.step()
            val a = b.snapshot().fighters.first { it.uid == 1 }
            if (kotlin.math.abs(a.x - 3f) > .3f) sidestepped = true
        }
        assertTrue("rear fighter must take a lateral route", sidestepped)
        assertTrue(b.snapshot().fighters.first { it.uid == 1 }.attacks > 0)
    }
    @Test fun livingBodySeparationAndDeathRetargetAreDeterministic() {
        val left = Army(0, listOf(guard(1, 3), guard(2, 11), guard(3, 4)))
        val right = Army(1, listOf(UnitSnapshot(101, "rune_apprentice", 1, 4), guard(102, 5)))
        val a = Battle(0, left, right); val b = Battle(0, left, right)
        repeat(200) {
            a.step(); b.step()
            assertEquals(a.snapshot(), b.snapshot())
            val alive = a.snapshot().fighters.filter { it.health > 0 }
            for (i in alive.indices) for (j in i + 1 until alive.size)
                assertTrue(hypot(alive[i].x - alive[j].x, alive[i].y - alive[j].y) > .56f)
        }
        assertTrue(a.snapshot().fighters.filter { it.team == 0 }.all { it.attacks > 1 })
        assertTrue(a.snapshot().events.any { it.target == 102 })
    }
}
