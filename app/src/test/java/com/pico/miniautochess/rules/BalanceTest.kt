package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class BalanceTest {
    private fun fighters(b: Battle) =
        b.javaClass.getDeclaredField("fighters").apply { isAccessible = true }.get(b) as List<*>
    private fun set(f: Any, key: String, value: Float) {
        f.javaClass.getDeclaredField(key).apply { isAccessible = true }.set(f, value)
    }
    private fun pawn(id: Int, hero: String, star: Int = 2, slot: Int = id % 8) =
        UnitSnapshot(id, hero, star, slot)
    private fun finish(b: Battle): BattleOutcome {
        repeat(401) { b.step() }
        return b.outcome!!
    }
    @Test fun everyHeroUpgradePreservesSoloWinDirectionAcrossFourLayouts() {
        val layouts = listOf(3 to 4, 27 to 28, 0 to 0, 3 to 3)
        for (h in Catalog.heroList) for (star in 2..3) for ((left, right) in layouts) {
            val battle = Battle(0, Army(0, listOf(pawn(1, h.id, star, left))),
                Army(1, listOf(pawn(100, h.id, star - 1, right))))
            assertNotEquals("${h.name} $star at $left/$right", BattleOutcome.RIGHT, finish(battle))
        }
    }
    @Test fun guardAndSupportProvideMagicDefenseOnlyForDeployedTraits() {
        val guard = pawn(1, "copper_guard")
        val others = listOf(pawn(2, "moss_guard"), pawn(3, "flame_fighter"))
        val supports = listOf(pawn(4, "ember_acolyte"), pawn(5, "dawn_priest"), pawn(6, "forest_blessing"))
        assertEquals(28f, CombatMath.stats(guard, listOf(guard)).magicResist, .001f)
        assertEquals(65f, CombatMath.stats(guard, listOf(guard) + others + supports).magicResist, .001f)
        val reserve = supports.map { it.copy(slot = -it.uid) }
        assertEquals(40f, CombatMath.stats(guard, listOf(guard) + others + reserve).magicResist, .001f)
        assertEquals(50f, CombatMath.magicDamage(100f, 100f), .001f)
        assertEquals(100f, CombatMath.magicDamage(100f, 0f), .001f)
    }
    @Test fun novaUsesFullPrimaryDamageAndReducedSplashInActualBattle() {
        val battle = Battle(0, Army(0, listOf(pawn(1, "rune_sage", 2, 3))),
            Army(1, listOf(pawn(100, "copper_guard", 2, 3), pawn(101, "copper_guard", 2, 4))))
        set(fighters(battle).first()!!, "mana", 100f)
        battle.step()
        val hits = battle.snapshot().events.filter { it.actor == 1 && it.kind == CombatEventKind.CAST }.sortedBy { it.amount }
        assertEquals(2, hits.size)
        assertEquals(1, hits.map { it.actionId }.distinct().size)
        assertEquals(301, hits[0].amount)
        assertEquals(464, hits[1].amount)
    }
    @Test fun healingIgnoresResistanceAndIsHalvedOnlyAtOvertime() {
        for (overtime in listOf(false, true)) {
            val battle = Battle(0, Army(0, listOf(pawn(1, "ember_acolyte"), pawn(2, "copper_guard"))),
                Army(1, listOf(pawn(100, "copper_guard", 3, 31))))
            set(fighters(battle).first()!!, "mana", 100f)
            set(fighters(battle)[1]!!, "health", 50f)
            if (overtime) battle.javaClass.getDeclaredField("wallElapsed").apply { isAccessible = true }.set(battle, 25f)
            battle.step()
            val heal = battle.snapshot().events.first { it.kind == CombatEventKind.HEAL }
            assertEquals(if (overtime) 258 else 517, heal.amount)
            assertEquals(50f + 517.5f * if (overtime) .5f else 1f,
                battle.snapshot().fighters[1].health, .01f)
        }
    }
    @Test fun evaluatorRetainsStrongLowCostThreeStarAndLocksManualPlacements() {
        val low = pawn(1, "rune_apprentice", 3)
        val high = pawn(2, "rune_sage", 2)
        assertEquals(listOf(low), ArmyEvaluator.select(listOf(low, high), 1))
        val tank = pawn(3, "copper_guard", 1)
        val selected = ArmyEvaluator.select(listOf(low, high, tank), 2, listOf(tank))
        assertTrue(selected.contains(tank))
        assertTrue(selected.any { it.uid == low.uid || it.uid == high.uid })
        assertEquals(2, selected.map { it.uid }.distinct().size)
    }
    @Test fun armySelectionUsesWholeDeployedTeamAndDoesNotCountReserveTraits() {
        val all = Catalog.heroList.take(10).mapIndexed { i, h -> pawn(i + 1, h.id, if (i == 0) 3 else 2, -i - 1) }
        val selected = ArmyEvaluator.select(all, 7)
        assertEquals(7, selected.size)
        assertEquals(7, selected.map { it.uid }.distinct().size)
        val before = all.toList()
        assertEquals(selected, ArmyEvaluator.select(all.reversed(), 7))
        assertEquals(before, all)
        assertTrue(ArmyEvaluator.score(selected) > 0f)
    }
}
