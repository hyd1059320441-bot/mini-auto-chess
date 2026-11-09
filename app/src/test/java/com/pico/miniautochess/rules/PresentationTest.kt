package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    private fun fighter(attack: Float = -10f, cast: Float = -10f) = FighterSnapshot(
        1, "rune_sage", 1, 0, 0f, 0f, 500f, 500f, 0f, 0f, 2, 0f,
        lastAttackTime = attack, lastCastTime = cast)

    @Test fun skillPoseTakesPrecedenceAndDoesNotRepeatAnOldBasicAttack() {
        assertEquals(HeroAction.CAST, HeroPresentation.action(fighter(1f, 1.1f), false, false, false, 1.2f))
        assertEquals(HeroAction.READY, HeroPresentation.action(fighter(1f, 1.1f), false, false, false, 2f))
        assertEquals(HeroAction.WALK, HeroPresentation.action(fighter(), true, false, false, 2f))
        assertEquals(HeroAction.VICTORY, HeroPresentation.action(fighter(1f, 1f), true, true, false, 1.1f))
        assertEquals(HeroAction.BENCH, HeroPresentation.action(null, false, false, true, 0f))
        assertEquals(HeroAction.READY, HeroPresentation.action(null, false, false, false, 0f))
    }
    @Test fun everyHeroHasDistinctSixFinitePosesAcrossAnimationBoundaries() {
        assertEquals(Catalog.heroes.keys, HeroPresentation.looks.keys)
        assertEquals(16, HeroPresentation.looks.values.map { it.motif }.toSet().size)
        Catalog.heroList.forEach { hero ->
            val poses = HeroAction.entries.map { HeroPresentation.pose(hero, it, 2.3f, .17f, 17) }
            assertEquals("${hero.id} needs six distinct actions", 6, poses.toSet().size)
            HeroAction.entries.forEach { action ->
                listOf(0f, .0001f, .34f, .72f, 10000f).forEach { t ->
                    val p = HeroPresentation.pose(hero, action, t, t, -17)
                    assertTrue(listOf(p.lift, p.lean, p.turn, p.roll, p.leftArm, p.rightArm,
                        p.leftLeg, p.rightLeg).all { it.isFinite() })
                    assertTrue(p.lift in 0f.. .04f)
                }
            }
        }
    }
    @Test fun absoluteTicksRetainRemainderAndFullManaIsTwentySegments() {
        assertEquals(listOf(.25f, .5f, .75f), StatusBarScale.marks(400f, 100))
        assertEquals(7, StatusBarScale.marks(760f, 100).size)
        assertEquals(100f / 760, StatusBarScale.marks(760f, 100).first(), .00001f)
        assertEquals(19, StatusBarScale.marks(100f, 5).size)
        assertTrue(StatusBarScale.marks(4380.48f, 100).all { it > 0f && it < 1f })
        assertEquals("0", StatusBarScale.digits(-1f, 100f))
        assertEquals("1", StatusBarScale.digits(.01f, 100f))
        assertEquals("100", StatusBarScale.digits(100.01f, 100f))
    }
    @Test fun cueGroupingHandlesInterleavedTargetsAndResetsBetweenBattles() {
        val tracker = CombatCueTracker()
        val shield = CombatEvent(1, CombatEventKind.BLOCK, 1, 1, 300, 12)
        assertTrue(tracker.first(shield))
        assertTrue(tracker.first(CombatEvent(2, CombatEventKind.CAST, 2, 3, 100, 13)))
        assertFalse(tracker.first(CombatEvent(3, CombatEventKind.CAST, 1, 3, 50, 12)))
        assertFalse(tracker.first(shield))
        tracker.clear()
        assertTrue(tracker.first(shield))
    }
    @Test fun liveShieldAndAreaSkillEventsShareActionIdsButKeepDifferentCastTimes() {
        val units = listOf("rune_sentinel", "rune_sage", "rune_apprentice")
            .mapIndexed { i, h -> UnitSnapshot(i + 1, h, 3, i) }
        val enemies = listOf("moss_guard", "copper_guard", "ember_breaker")
            .mapIndexed { i, h -> UnitSnapshot(i + 11, h, 3, i) }
        val battle = Battle(1, Army(0, units), Army(1, enemies))
        val collected = linkedMapOf<Int, CombatEvent>()
        repeat(250) { battle.step(); battle.snapshot().events.forEach { collected[it.sequence] = it } }
        val groups = collected.values.filter { it.kind in listOf(CombatEventKind.BLOCK, CombatEventKind.CAST, CombatEventKind.HEAL) }
            .groupBy { it.actionId }
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.values.any { it.size > 1 })
        assertTrue(groups.values.any { group -> group.any { it.kind == CombatEventKind.BLOCK } && group.any { it.kind == CombatEventKind.CAST } })
        groups.values.forEach { group -> assertEquals(1, group.map { it.actor }.toSet().size) }
        battle.snapshot().fighters.filter { it.skills > 0 }.forEach {
            assertTrue(it.lastCastTime > 0f)
            assertTrue(it.lastAttackTime != it.lastCastTime)
        }
    }
}
