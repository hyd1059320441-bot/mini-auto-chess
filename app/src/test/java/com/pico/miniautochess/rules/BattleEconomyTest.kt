package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class BattleEconomyTest {
    private fun field(obj: Any, name: String) = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun pawn(uid: Int, hero: String = "copper_guard", star: Int = 1, slot: Int = -1) =
        UnitSnapshot(uid, hero, star, slot)
    private val board = listOf(pawn(1000, slot = 3), pawn(1001, "rune_apprentice", slot = 27),
        pawn(1002, "forest_ranger", slot = 29))
    @Suppress("UNCHECKED_CAST")
    private fun battle(bench: List<UnitSnapshot> = listOf(pawn(1003, slot = -1), pawn(1004, slot = -2)),
                       deployed: List<UnitSnapshot> = board, offer: String = "copper_guard"): GameEngine {
        val game = GameEngine(812, aiStyles = List(4) { AiStyle.FLEX }).also { it.start() }
        field(game, "nextUid").set(game, 2000)
        val players = field(game, "players").get(game) as List<Any>
        players.forEachIndexed { id, p ->
            (field(p, "shop").get(p) as MutableList<String?>).fill(null)
            (field(p, "units").get(p) as MutableList<UnitSnapshot>).apply {
                clear()
                addAll(if (id == 0) deployed + bench else listOf(pawn(900 + id,
                    listOf("moss_guard", "rune_sentinel", "flame_fighter")[id - 1], 3, 3)))
            }
        }
        val human = players[0]
        field(human, "gold").set(human, 100); field(human, "xp").set(human, 5)
        (field(human, "shop").get(human) as MutableList<String?>)[0] = offer
        val snapshots = game.snapshot().players
        val pool = field(game, "pool").get(game) as MutableMap<String, Int>
        Catalog.heroList.forEach { h ->
            pool[h.id] = Catalog.poolSize(h.cost) - snapshots.sumOf { p ->
                p.units.filter { it.heroId == h.id }.sumOf { Catalog.copies(it.star) } + p.shop.count { it == h.id }
            }
        }
        assertTrue(game.ready().ok)
        assertEquals(Phase.BATTLE, game.phase)
        game.assertInvariants()
        return game
    }
    @Test fun benchTradingAndLevelUpLeaveEveryLockedArmyAndFighterUnchanged() {
        val game = battle()
        val locked = game.snapshot().battles
        assertTrue(game.buy(0, 0).ok)
        assertEquals(2, game.snapshot().human.units.first { it.uid == 1003 }.star)
        assertEquals(board, game.snapshot().human.units.filter { it.onBoard })
        assertTrue(game.move(0, 1003, -5).ok)
        assertTrue(game.buyXp(0).ok)
        assertEquals(4, game.snapshot().human.level)
        assertEquals(3, game.snapshot().human.deployed)
        assertTrue(game.sell(0, 1003).ok)
        assertTrue(game.refresh(0).ok)
        assertEquals(locked, game.snapshot().battles)
        assertEquals(96, game.snapshot().human.gold) // -1 -4 +3 -2.
        game.assertInvariants()
    }
    @Test fun boardMovesSalesAndBoardBenchSwapsRemainLockedEvenAfterLevelUp() {
        val game = battle()
        assertFalse(game.canEditUnit(0, 1000))
        assertTrue(game.canEditUnit(0, 1003))
        val before = game.snapshot().human.units
        assertFalse(game.sell(0, 1000).ok)
        assertFalse(game.move(0, 1000, -3).ok)
        assertFalse(game.move(0, 1003, 3).ok)
        assertFalse(game.move(0, 1003, 1).ok)
        assertFalse(game.arrange(0).ok)
        assertTrue(game.buyXp(0).ok)
        assertFalse(game.move(0, 1003, 1).ok)
        assertEquals(before, game.snapshot().human.units)
    }
    @Test fun fullBenchCannotUseLockedBoardCopiesToMakeRoomForPurchase() {
        val bench = listOf(pawn(1003, slot = -1)) + Catalog.heroList.filter { it.id != "copper_guard" }
            .take(7).mapIndexed { i, h -> pawn(1004 + i, h.id, slot = -i - 2) }
        val game = battle(bench)
        val before = game.snapshot()
        assertFalse(game.buy(0, 0).ok)
        assertEquals(before.players, game.snapshot().players)
        assertEquals(before.pool, game.poolRemaining())
    }
    @Test fun threeBenchCopiesCanCascadeToThreeStarsWithoutConsumingBoardCopy() {
        val deployed = board.map { if (it.uid == 1000) it.copy(star = 2) else it }
        val bench = listOf(pawn(1010, star = 2, slot = -1), pawn(1011, star = 2, slot = -2),
            pawn(1012, slot = -3), pawn(1013, slot = -4))
        val game = battle(bench, deployed)
        val locked = game.snapshot().battles
        assertTrue(game.buy(0, 0).ok)
        assertEquals(deployed, game.snapshot().human.units.filter { it.onBoard })
        assertEquals(3, game.snapshot().human.units.single { !it.onBoard }.star)
        assertEquals(locked, game.snapshot().battles)
        game.assertInvariants()
    }
    @Test fun lockedCopiesJoinDeferredMergeOnlyAtNextPreparation() {
        val deployed = board.map { if (it.uid == 1000) it.copy(star = 2) else it }
        val game = battle(listOf(pawn(1010, star = 2, slot = -1), pawn(1011, star = 2, slot = -2)),
            deployed, "ember_acolyte")
        val locked = game.snapshot().battles
        assertTrue(game.buy(0, 0).ok)
        assertEquals(3, game.snapshot().human.units.count { it.heroId == "copper_guard" && it.star == 2 })
        assertEquals(locked, game.snapshot().battles)
        while (game.phase == Phase.BATTLE) game.advance(Catalog.STEP)
        assertEquals(Phase.TRANSITION, game.phase)
        game.advance(Catalog.TRANSITION_SECONDS + .1f)
        assertEquals(Phase.PREPARATION, game.phase)
        assertEquals(3, game.snapshot().human.units.first { it.uid == 1000 }.star)
        game.assertInvariants()
    }
    @Test fun dragDuringBattleAcceptsOnlyBenchAndRejectsBoardDrop() {
        val game = battle()
        val drag = DragPlacement(game)
        assertFalse(drag.begin(1000))
        assertTrue(drag.begin(1003))
        val target = BoardLayout.position(-2); val origin = drag.point!!
        drag.delta(target.x - origin.x, target.z - origin.z)
        assertTrue(drag.release().ok)
        assertEquals(-2, game.snapshot().human.units.first { it.uid == 1003 }.slot)
        assertTrue(drag.begin(1003))
        val boardTarget = BoardLayout.position(3); val from = drag.point!!
        drag.delta(boardTarget.x - from.x, boardTarget.z - from.z)
        assertFalse(drag.release().ok)
        assertEquals(-2, game.snapshot().human.units.first { it.uid == 1003 }.slot)
        assertTrue(drag.begin(1003)); assertTrue(drag.release(sell = true).ok)
        assertFalse(drag.release(sell = true).ok)
        game.assertInvariants()
    }
    @Test fun pauseStillBlocksAllTradingAndCancelsBenchDrag() {
        val game = battle()
        val drag = DragPlacement(game)
        assertTrue(drag.begin(1003))
        game.setPaused(true)
        assertFalse(drag.validate())
        assertFalse(game.buy(0, 0).ok); assertFalse(game.sell(0, 1003).ok)
        assertFalse(game.refresh(0).ok); assertFalse(game.buyXp(0).ok)
        assertNull(drag.uid)
    }
}
