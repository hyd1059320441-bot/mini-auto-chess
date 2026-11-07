package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    private fun prepared(seed: Int = 43): GameEngine = GameEngine(seed).also { it.start() }
    private fun field(obj: Any, name: String) = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun player(game: GameEngine, id: Int = 0): Any = (field(game, "players").get(game) as List<*>)[id]!!
    @Suppress("UNCHECKED_CAST")
    private fun fixture(
        game: GameEngine, id: Int = 0, gold: Int = 100, level: Int = 3, xp: Int = 0,
        units: List<UnitSnapshot> = emptyList(), shop: List<String?> = List(5) { null },
        health: Int = 32,
    ) {
        val p = player(game, id)
        field(p, "gold").set(p, gold); field(p, "level").set(p, level)
        field(p, "xp").set(p, xp); field(p, "health").set(p, health)
        (field(p, "units").get(p) as MutableList<UnitSnapshot>).apply { clear(); addAll(units) }
        (field(p, "shop").get(p) as MutableList<String?>).apply { clear(); addAll(shop) }
        // Fixtures conserve the real shared pool; test setup is never an app-facing command.
        val pool = field(game, "pool").get(game) as MutableMap<String, Int>
        val snapshots = game.snapshot().players
        Catalog.heroList.forEach { def ->
            pool[def.id] = Catalog.poolSize(def.cost) -
                snapshots.sumOf { s -> s.units.filter { it.heroId == def.id }.sumOf { Catalog.copies(it.star) } + s.shop.count { it == def.id } }
        }
        game.assertInvariants()
    }
    private fun pawn(uid: Int, slot: Int, star: Int = 1, hero: String = "copper_guard") =
        UnitSnapshot(uid, hero, star, slot)

    @Test fun readyAndTimeoutFillOnlyGapsWithoutSpendingOrRearranging() {
        for (timeout in listOf(false, true)) for (onBoard in 0..3) {
            val game = prepared()
            val units = (0..4).map { i -> pawn(900 + i, if (i < onBoard) i * 3 else -i - 1,
                hero = Catalog.heroList[i].id) }
            fixture(game, gold = 37, units = units)
            val before = game.snapshot().human
            val pool = game.poolRemaining()
            if (timeout) game.advance(30.2f) else assertTrue(game.ready().ok)
            val after = game.snapshot().human
            assertEquals(3, after.deployed)
            units.filter { it.onBoard }.forEach { assertEquals(it, after.units.first { u -> u.uid == it.uid }) }
            assertEquals(before.gold, after.gold); assertEquals(before.shop, after.shop)
            assertEquals(pool, game.poolRemaining())
            assertEquals(units.map { it.copy(slot = 0) }, after.units.map { it.copy(slot = 0) })
            assertEquals(3, game.snapshot().battles.first { it.left.playerId == 0 }.left.units.size)
            game.assertInvariants()
        }
    }
    @Test fun lockWithEmptyBenchKeepsPartialArmyAndPopulationLimit() {
        val game = prepared()
        fixture(game, units = listOf(pawn(900, 19)))
        assertTrue(game.ready().ok)
        assertEquals(listOf(pawn(900, 19)), game.snapshot().human.units)
        game.assertInvariants()
    }
    @Test fun startResetsAnEntireMatchAndBothBoards() {
        val game = GameEngine(autoPlay = true)
        assertTrue(game.start().ok)
        val initialPlayers = game.snapshot().players
        assertFalse(game.start().ok)
        game.advance(700f)
        assertEquals(Phase.FINISHED, game.phase)
        assertTrue(game.start().ok)
        val s = game.snapshot()
        assertEquals(Phase.PREPARATION, s.phase); assertEquals(1, s.round)
        assertEquals(0f, s.elapsed, .001f)
        assertTrue(s.players.all { it.health == 32 && it.wins == 0 && it.eliminatedRound == null })
        assertEquals(initialPlayers, s.players)
        assertTrue(s.human.gold in 0..10)
        assertEquals(3, s.human.level)
        assertTrue(s.human.units.isNotEmpty()) // Initial autoplay immediately manages the new shop.
        assertTrue(s.battles.isEmpty())
        game.assertInvariants()
    }
    @Test fun insufficientGoldDoesNotConsumeShopOrPool() {
        val g = prepared()
        fixture(g, gold = 0, shop = listOf("copper_guard", null, null, null, null))
        val before = g.poolRemaining()
        assertFalse(g.buy(0, 0).ok); assertFalse(g.refresh(0).ok); assertFalse(g.buyXp(0).ok)
        assertEquals("copper_guard", g.snapshot().human.shop[0]); assertEquals(before, g.poolRemaining())
        g.assertInvariants()
    }
    @Test fun purchaseConsumesExactlyOneReservedCard() {
        val g = prepared()
        fixture(g, gold = 1, shop = listOf("copper_guard", null, null, null, null))
        val pool = g.poolRemaining()
        assertTrue(g.buy(0, 0).ok)
        assertEquals(0, g.snapshot().human.gold)
        assertEquals(pool, g.poolRemaining())
        assertNull(g.snapshot().human.shop[0]); assertFalse(g.buy(0, 0).ok)
        g.assertInvariants()
    }
    @Test fun threeCopiesMergeOnBoardWithoutLosingSlotOrPool() {
        val g = prepared()
        fixture(g, units = listOf(pawn(101, 2), pawn(102, -1)),
            shop = listOf("copper_guard", null, null, null, null))
        assertTrue(g.buy(0, 0).ok)
        val s = g.snapshot().human
        assertEquals(1, s.units.size); assertEquals(2, s.units[0].star)
        assertEquals(2, s.units[0].slot)
        assertEquals(3, s.units[0].value)
        g.assertInvariants()
    }
    @Test fun chainedMergeUsesNineCopiesAndStopsAtThreeStars() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 2, 2), pawn(101, -1, 2), pawn(102, -2), pawn(103, -3)),
            shop = listOf("copper_guard", null, null, null, null))
        assertTrue(g.buy(0, 0).ok)
        assertEquals(1, g.snapshot().human.units.size)
        val merged = g.snapshot().human.units.single()
        assertEquals(3, merged.star); assertEquals(9, merged.value)
        val remaining = g.poolRemaining().getValue("copper_guard")
        assertTrue(g.sell(0, merged.uid).ok)
        assertEquals(remaining + 9, g.poolRemaining().getValue("copper_guard"))
        assertEquals(108, g.snapshot().human.gold)
        g.assertInvariants()
    }
    @Test fun fullBenchBlocksPurchaseExceptAnImmediateMerge() {
        val g = prepared()
        val bench = List(8) { i -> pawn(100 + i, -1 - i, hero = Catalog.heroList[i].id) }
        fixture(g, units = bench, shop = listOf("copper_guard", null, null, null, null))
        assertFalse(g.buy(0, 0).ok)
        fixture(g, units = bench.dropLast(1) + pawn(200, -8), shop = listOf("copper_guard", null, null, null, null))
        assertTrue(g.buy(0, 0).ok)
        assertEquals(7, g.snapshot().human.units.size)
        assertTrue(g.snapshot().human.units.none { it.slot == -99 })
        g.assertInvariants()
    }
    @Test fun populationAllowsSwapButRejectsAnExtraDeployedHero() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 0), pawn(101, 1), pawn(102, 2), pawn(103, -1)))
        assertFalse(g.move(0, 103, 3).ok)
        assertTrue(g.move(0, 103, 0).ok)
        assertEquals(-1, g.snapshot().human.units.first { it.uid == 100 }.slot)
        assertEquals(3, g.snapshot().human.deployed)
        assertFalse(g.move(0, 103, Catalog.BOARD_SIZE).ok); assertFalse(g.move(0, 103, -9).ok)
        g.assertInvariants()
    }
    @Test fun occupiedBenchAndBoardSwapRetainsUniqueLocations() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, -1), pawn(101, -2), pawn(102, 0)))
        assertTrue(g.move(0, 100, -2).ok); assertTrue(g.move(0, 102, -2).ok)
        assertEquals(1, g.snapshot().human.deployed)
        g.assertInvariants()
    }
    @Test fun experienceRetainsOverflowAndFullLevelNeverDeductsGold() {
        val g = prepared()
        fixture(g, gold = 100, level = 3, xp = 5)
        assertTrue(g.buyXp(0).ok)
        assertEquals(4, g.snapshot().human.level); assertEquals(3, g.snapshot().human.xp)
        repeat(8) { g.buyXp(0) }
        assertEquals(7, g.snapshot().human.level)
        val gold = g.snapshot().human.gold
        assertFalse(g.buyXp(0).ok); assertEquals(gold, g.snapshot().human.gold)
        g.assertInvariants()
    }
    @Test fun refreshReturnsAllUnsoldReservationsAndChargesTwo() {
        val g = prepared()
        fixture(g, gold = 10, shop = List(5) { "copper_guard" })
        assertTrue(g.refresh(0).ok); assertEquals(8, g.snapshot().human.gold)
        g.assertInvariants()
    }
    @Test fun duplicateHeroesDoNotCountTwiceForTraits() {
        val t = traits(listOf(pawn(1, 0), pawn(2, 1), pawn(3, 2, hero = "dawn_priest")))
        assertEquals(2, t.first { it.label == "曙光" }.count)
        assertEquals(1, t.first { it.label == "曙光" }.tier)
        assertEquals(1, t.first { it.label == "卫士" }.count)
    }
    @Test fun interestIsOnePerTenWithNoUndocumentedCap() {
        assertEquals(0, Catalog.interest(9)); assertEquals(1, Catalog.interest(10))
        assertEquals(2, Catalog.interest(29)); assertEquals(3, Catalog.interest(30))
        assertEquals(4, Catalog.interest(40)); assertEquals(99, Catalog.interest(999))
    }
    @Test fun pauseFreezesTimersAndBothBattles() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 0)))
        g.ready(); g.advance(1f); g.setPaused(true)
        val before = g.snapshot()
        g.advance(50f)
        assertEquals(before.elapsed, g.elapsed, 0f)
        assertEquals(before.battles, g.snapshot().battles)
        g.setPaused(false); g.advance(.1f)
        assertTrue(g.elapsed > before.elapsed)
    }
    @Test fun readyCannotBypassPauseAndCreateBattles() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 0)))
        g.setPaused(true)
        val before = g.snapshot()
        assertFalse(g.ready().ok)
        assertEquals(Phase.PREPARATION, g.phase)
        assertEquals(before.elapsed, g.elapsed, 0f)
        assertEquals(before.players, g.snapshot().players)
        assertTrue(g.snapshot().battles.isEmpty())
        g.setPaused(false)
        assertTrue(g.ready().ok)
        assertEquals(Phase.BATTLE, g.phase)
    }
    @Test fun watchChangesOnlyTheViewAndNeverRestartsOrSettles() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 0)))
        g.ready(); g.advance(2f)
        val before = g.snapshot()
        repeat(10) { assertTrue(g.watch(it % 2).ok) }
        assertEquals(before.battles, g.snapshot().battles); assertEquals(before.players, g.snapshot().players)
        assertEquals(before.elapsed, g.elapsed, 0f)
        g.advance(.1f)
        assertTrue(g.snapshot().battles.all { it.elapsed >= before.battles[it.id].elapsed })
    }
    @Test fun fourPlayersRotateThroughAllThreeOpponents() {
        val g = prepared()
        (0..3).forEach { id -> fixture(g, id, units = emptyList()) }
        val opponents = mutableListOf<Int>()
        repeat(3) {
            g.ready()
            val battle = g.snapshot().battles.first { it.left.playerId == 0 }
            opponents.add(battle.right.playerId)
            g.advance(.1f)
            assertEquals(Phase.TRANSITION, g.phase)
            g.advance(5f)
            // Keep the pairings test independent of AI shopping in the next round.
            (0..3).forEach { id -> fixture(g, id, units = emptyList()) }
        }
        assertEquals(listOf(1, 2, 3), opponents)
    }
    @Test fun emptyBattlesDrawAndNoOneTakesDamageOrWinsGold() {
        val g = prepared()
        (0..3).forEach { fixture(g, it, gold = 20) }
        g.ready(); g.advance(.1f)
        val s = g.snapshot()
        assertTrue(s.players.all { it.health == 32 && it.wins == 0 && it.gold == 27 })
        assertTrue(s.results.all { it.result == "平局" && it.damage == 0 && it.interest == 2 && it.income == 7 })
        val gold = s.human.gold
        g.advance(1f)
        assertEquals(gold, g.snapshot().human.gold)
    }
    @Test fun eliminationReturnsPoolAndThreePlayersUsePreviousLockedGhost() {
        val g = prepared()
        fixture(g, 0, health = 1, units = emptyList())
        fixture(g, 1, units = listOf(pawn(101, 0, 2)))
        fixture(g, 2, units = listOf(pawn(102, 1, hero = "forest_ranger")))
        fixture(g, 3, units = listOf(pawn(103, 2, hero = "rune_apprentice")))
        g.ready(); g.advance(26f)
        assertNotNull(g.snapshot().human.eliminatedRound)
        if (g.phase == Phase.PREPARATION) g.ready()
        val s = g.snapshot()
        assertEquals(Phase.BATTLE, s.phase)
        assertEquals(2, s.battles.size)
        val ghost = s.battles.map { it.right }.single { it.ghost }
        val original = when (ghost.playerId) {
            1 -> pawn(101, 0, 2)
            2 -> pawn(102, 1, hero = "forest_ranger")
            else -> pawn(103, 2, hero = "rune_apprentice")
        }
        assertEquals(original.heroId, ghost.units.single().heroId)
        assertEquals(original.star, ghost.units.single().star)
        assertEquals(original.slot, ghost.units.single().slot)
        assertTrue(ghost.units.single().uid < 0)
        val ghostFighter = s.battles.single { it.right.ghost }.fighters.single { it.team == 1 }
        assertEquals(ghostFighter.maxHealth, ghostFighter.health, .001f)
        g.assertInvariants()
    }
    @Test fun completelyEqualFinalScoresShareRank() {
        val g = prepared()
        (0..3).forEach { fixture(g, it) }
        assertTrue(g.ranking().all { it.rank == 1 })
        fixture(g, 0, units = listOf(pawn(100, -1)))
        assertEquals(1, g.ranking().first().rank)
        assertTrue(g.ranking().drop(1).all { it.rank == 2 })
    }
    @Test fun combatTimesOutByCountThenHealthRatioThenDraw() {
        assertEquals(BattleOutcome.LEFT, Battle.compareAtTimeout(2, 1, .1, 1.0))
        assertEquals(BattleOutcome.RIGHT, Battle.compareAtTimeout(1, 1, .1, .2))
        assertEquals(BattleOutcome.DRAW, Battle.compareAtTimeout(1, 1, .5, .5))
        val b = Battle(0, Army(0, listOf(pawn(1, 17))), Army(1, listOf(pawn(2, 17))), limit = .2f)
        repeat(10) { b.step() }
        assertEquals(BattleOutcome.DRAW, b.outcome)
        assertEquals(.2f, b.elapsed, .001f)
    }
    @Test fun combatSnapshotIsDetachedAndPreservesPreparationArmy() {
        val units = listOf(pawn(1, 0))
        val b = Battle(0, Army(0, units), Army(1, listOf(pawn(2, 0))))
        val before = b.snapshot()
        repeat(50) { b.step() }
        assertEquals(before.left.units, b.snapshot().left.units)
        assertEquals(760f, before.fighters[0].health, .001f)
        assertTrue(b.snapshot().fighters.any { it.health < it.maxHealth })
        assertEquals(before, before.copy())
    }
    @Test fun allMutationsRejectBattleAndEliminatedPlayers() {
        val g = prepared(); g.ready()
        assertFalse(g.buy(0, 0).ok); assertFalse(g.refresh(0).ok); assertFalse(g.buyXp(0).ok)
        assertFalse(g.move(0, 1, 0).ok); assertFalse(g.sell(0, 1).ok)
        assertFalse(g.arrange(0).ok)
        assertFalse(g.ready().ok); assertFalse(g.watch(3).ok)
    }
    @Test fun fractionalAdvancesHaveTheSameFixedStepResult() {
        val a = GameEngine(222, true); val b = GameEngine(222, true)
        a.start(); b.start()
        repeat(500) { a.advance(.1f) }
        repeat(2000) { b.advance(.025f) }
        assertEquals(a.snapshot().players, b.snapshot().players)
        assertEquals(a.snapshot().battles, b.snapshot().battles)
        assertEquals(a.elapsed, b.elapsed, .001f)
    }
    @Test fun invalidTimeCannotPoisonTheClock() {
        val g = prepared()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach {
            try { g.advance(it); fail("Expected invalid-time rejection") } catch (_: IllegalArgumentException) {}
        }
        assertEquals(0f, g.elapsed, .001f)
    }
    @Test fun everyExpandedPreparationCellCanBeOccupiedAndPreservesEconomy() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, -1)))
        val before = g.snapshot().human
        val pool = g.poolRemaining()
        for (slot in 0 until Catalog.BOARD_SIZE) {
            assertTrue("Cell $slot", g.move(0, 100, slot).ok)
            assertEquals(slot, g.snapshot().human.units.single().slot)
            g.assertInvariants()
        }
        assertEquals(32, Catalog.BOARD_SIZE)
        assertEquals(before.gold, g.snapshot().human.gold)
        assertEquals(pool, g.poolRemaining())
    }
    @Test fun dragPreviewNeverChangesRulesAndReleaseCommitsOnlyOnce() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, -1)))
        val drag = DragPlacement(g)
        val before = g.snapshot()
        assertTrue(drag.begin(100))
        val dest = BoardLayout.position(31)
        val origin = drag.point!!
        drag.delta(dest.x - origin.x, dest.z - origin.z)
        assertEquals(before, g.snapshot())
        assertTrue(drag.release().ok)
        assertEquals(31, g.snapshot().human.units.single().slot)
        val released = g.snapshot()
        assertFalse(drag.release().ok)
        assertEquals(released, g.snapshot())
    }
    @Test fun cancelAndOutsideDropKeepSourceSlot() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 31)))
        val drag = DragPlacement(g)
        assertTrue(drag.begin(100)); drag.delta(.3f, .4f); drag.cancel()
        assertEquals(31, g.snapshot().human.units.single().slot)
        assertTrue(drag.begin(100)); drag.delta(3f, 0f)
        assertFalse(drag.release().ok)
        assertEquals(31, g.snapshot().human.units.single().slot)
        assertNull(drag.point)
    }
    @Test fun dragCannotBypassFullPopulationButCanSwapAtCapacity() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, 0), pawn(101, 1), pawn(102, 2), pawn(103, -1)))
        val drag = DragPlacement(g)
        fun drop(slot: Int): ActionResult {
            assertTrue(drag.begin(103))
            val dest = BoardLayout.position(slot); val start = drag.point!!
            drag.delta(dest.x - start.x, dest.z - start.z)
            return drag.release()
        }
        assertFalse(drop(31).ok)
        assertEquals(-1, g.snapshot().human.units.first { it.uid == 103 }.slot)
        assertTrue(drop(0).ok)
        assertEquals(-1, g.snapshot().human.units.first { it.uid == 100 }.slot)
        g.assertInvariants()
    }
    @Test fun phasePauseAutoPlayAndRemovedPawnInvalidateAnActiveDrag() {
        for (interruption in 0..3) {
            val g = prepared()
            fixture(g, units = listOf(pawn(100, -1)))
            val drag = DragPlacement(g)
            assertTrue(drag.begin(100))
            drag.delta(.23f, -.23f)
            when (interruption) {
                0 -> g.setPaused(true)
                1 -> g.ready()
                2 -> g.setAutoPlay(true)
                else -> g.sell(0, 100)
            }
            val before = g.snapshot()
            assertFalse(drag.release().ok)
            assertNull(drag.uid)
            assertEquals(before, g.snapshot())
            assertFalse(drag.begin(100))
        }
    }
    @Test fun changedAuthoritativeSlotInvalidatesPreview() {
        val g = prepared()
        fixture(g, units = listOf(pawn(100, -1)))
        val drag = DragPlacement(g)
        assertTrue(drag.begin(100))
        assertTrue(g.move(0, 100, 31).ok)
        drag.delta(.1f, .2f)
        assertNull(drag.uid)
        assertEquals(31, g.snapshot().human.units.single().slot)
    }

    @Test fun startMakesAllFiveShopCardsAvailableWithoutAdvancingTime() {
        val g = prepared()
        val s = g.snapshot()
        assertEquals(Phase.PREPARATION, s.phase)
        assertEquals(1, s.round); assertEquals(30f, s.secondsLeft, .001f)
        assertEquals(0f, s.elapsed, 0f)
        assertEquals(5, s.human.shop.filterNotNull().size)
        assertTrue(g.buy(0, 0).ok)
        g.assertInvariants()
    }
    private fun asymmetricRound(humanWins: Boolean, health: Int = 32, gold: Int = 19): GameEngine {
        val g = prepared()
        fixture(g, 0, gold = gold, health = health, units = if (humanWins) listOf(pawn(100, 0)) else emptyList())
        fixture(g, 1, units = if (humanWins) emptyList() else listOf(pawn(101, 0)))
        fixture(g, 2, units = listOf(pawn(102, 0)))
        fixture(g, 3, units = listOf(pawn(103, 0)))
        g.ready(); g.advance(.1f)
        assertEquals(Phase.BATTLE, g.phase)
        assertNull(g.snapshot().battles[1].outcome)
        return g
    }
    @Test fun winnerGetsOneCoinImmediatelyAndInterestIncludesThatCoinLater() {
        val g = asymmetricRound(humanWins = true)
        val immediate = g.snapshot()
        assertEquals(20, immediate.human.gold)
        assertEquals(1, immediate.human.wins); assertEquals(0, immediate.human.xp)
        val result = immediate.results.first { it.playerId == 0 }
        assertEquals(1, result.income); assertEquals(1, result.winGold); assertFalse(result.roundPaid)
        repeat(5) { g.watch(it % 2) }
        g.advance(1f)
        assertEquals(20, g.snapshot().human.gold); assertEquals(1, g.snapshot().human.wins)
        g.advance(24f)
        val end = g.snapshot()
        assertEquals(Phase.TRANSITION, end.phase)
        assertEquals(27, end.human.gold); assertEquals(2, end.human.xp)
        val paid = end.results.first { it.playerId == 0 }
        assertEquals(8, paid.income); assertEquals(2, paid.interest); assertEquals(20, paid.interestBalance)
        assertEquals(2, paid.xpGain); assertTrue(paid.roundPaid)
        g.advance(1f)
        assertEquals(end.players, g.snapshot().players)
        g.assertInvariants()
    }
    @Test fun loserTakesDamageAndMovesDownLiveRankingWhileOtherBattleContinues() {
        val g = asymmetricRound(humanWins = false)
        val s = g.snapshot()
        assertEquals(30, s.human.health)
        assertEquals(19, s.human.gold); assertEquals(0, s.human.wins)
        assertEquals(0, s.human.xp)
        assertEquals(2, s.results.first { it.playerId == 0 }.damage)
        assertEquals(0, s.ranking.last().playerId)
        g.setPaused(true); g.advance(30f)
        assertEquals(s.players, g.snapshot().players)
        g.setPaused(false); g.advance(1f)
        assertEquals(30, g.snapshot().human.health)
        g.assertInvariants()
    }
    @Test fun lethalDamageEliminatesAndReturnsPoolImmediatelyWithoutStoppingOtherBattle() {
        val g = asymmetricRound(humanWins = false, health = 1)
        val s = g.snapshot()
        assertEquals(0, s.human.health); assertEquals(1, s.human.eliminatedRound)
        assertTrue(s.human.shop.all { it == null }); assertTrue(s.human.units.isEmpty())
        assertTrue(s.message.contains("淘汰"))
        g.assertInvariants()
        val backgroundTime = s.battles[1].elapsed
        g.advance(1f)
        assertTrue(g.snapshot().battles[1].elapsed > backgroundTime)
        g.advance(24f)
        assertEquals(Phase.TRANSITION, g.phase)
        assertEquals(25, g.snapshot().human.gold)
        assertEquals(2, g.snapshot().human.xp)
        val paid = g.snapshot().results.first { it.playerId == 0 }
        assertTrue(paid.roundPaid); assertEquals(1, paid.interest)
        assertEquals(1, paid.damage); assertEquals(2, paid.nominalDamage)
        g.assertInvariants()
    }
    @Test fun roundXpPreservesOverflowCapsAtSevenAndIsAwardedOnceOnDraw() {
        val g = prepared()
        fixture(g, 0, gold = 9, level = 3, xp = 5)
        fixture(g, 1, gold = 30, level = 6, xp = 11)
        fixture(g, 2, gold = 40, level = 7)
        fixture(g, 3, gold = 10, level = 5, xp = 3)
        g.ready(); g.advance(.1f)
        val s = g.snapshot()
        assertEquals(4, s.human.level); assertEquals(1, s.human.xp)
        assertEquals(7, s.players[1].level); assertEquals(0, s.players[1].xp)
        assertEquals(7, s.players[2].level); assertEquals(0, s.players[2].xp)
        assertEquals(5, s.players[3].xp)
        assertEquals(listOf(14, 38, 49, 16), s.players.map { it.gold })
        assertEquals(listOf(2, 2, 0, 2), s.results.sortedBy { it.playerId }.map { it.xpGain })
        g.advance(1f)
        assertEquals(s.players, g.snapshot().players)
        g.assertInvariants()
    }
    @Test fun tenthRoundPaysRegularIncomeAndXpAndPublishesFinalPlace() {
        val g = prepared()
        (0..3).forEach { fixture(g, it, gold = 10) }
        field(g, "round").set(g, 10)
        g.ready(); g.advance(.1f)
        val s = g.snapshot()
        assertEquals(Phase.FINISHED, s.phase)
        assertTrue(s.players.all { it.gold == 16 && it.xp == 2 })
        assertTrue(s.message.contains("棋会结束") && s.message.contains("第 1 名"))
        g.advance(100f)
        assertEquals(s, g.snapshot())
        g.assertInvariants()
    }
    @Test fun ghostVictoryNeverPaysTheDonorOrMutatesItsOngoingRealBattle() {
        val g = prepared()
        fixture(g, 0, gold = 10, units = emptyList())
        fixture(g, 1, gold = 19, units = listOf(pawn(101, 0)))
        fixture(g, 2, gold = 19, units = listOf(pawn(102, 0)))
        fixture(g, 3, units = emptyList())
        field(player(g, 3), "eliminatedRound").set(player(g, 3), 1)
        g.ready(); g.advance(.1f)
        val s = g.snapshot()
        assertEquals(Phase.BATTLE, s.phase)
        assertEquals(1, s.battles.single { it.right.ghost }.right.playerId)
        assertEquals(BattleOutcome.RIGHT, s.battles.single { it.right.ghost }.outcome)
        assertNull(s.battles.single { !it.right.ghost }.outcome)
        assertEquals(19, s.players[1].gold); assertEquals(0, s.players[1].wins)
        assertEquals(32, s.players[1].health)
        assertEquals(listOf(0), s.results.map { it.playerId })
        g.assertInvariants()
        g.advance(25f)
        val end = g.snapshot()
        assertEquals(Phase.TRANSITION, end.phase)
        assertEquals(25, end.players[1].gold); assertEquals(0, end.players[1].wins)
        assertEquals(3, end.results.size)
        assertFalse(end.results.any { it.playerId == 3 })
        g.assertInvariants()
    }
    @Test fun victoryAndLossPayFiveBaseAndInterestAtEveryTenCoinBoundary() {
        for ((gold, winFinal, lossFinal) in listOf(
            Triple(0, 6, 5), Triple(9, 16, 14), Triple(10, 17, 16),
            Triple(19, 27, 25), Triple(29, 38, 36), Triple(39, 49, 47),
        )) for (won in listOf(false, true)) {
            val g = asymmetricRound(humanWins = won, gold = gold)
            g.advance(25f)
            val s = g.snapshot()
            val paid = s.results.first { it.playerId == 0 }
            assertEquals(if (won) winFinal else lossFinal, s.human.gold)
            assertEquals(gold + if (won) 1 else 0, paid.interestBalance)
            assertEquals(5, paid.income - paid.winGold - paid.interest)
            assertEquals(2, paid.xpGain)
            val before = s.players
            g.advance(1f)
            assertEquals(before, g.snapshot().players)
        }
    }
    @Test fun liveLossLedgerUsesActualSurvivorsNotLevelBenchOrStar() {
        val g = prepared()
        fixture(g, 0, health = 2)
        fixture(g, 2, level = 7, units = listOf(pawn(101, 0, 3), pawn(102, 1, 2)))
        fixture(g, 1); fixture(g, 3)
        field(g, "round").set(g, 8)
        g.ready(); g.advance(.1f)
        val paid = g.snapshot().results.first { it.playerId == 0 }
        assertEquals(4, paid.baseDamage); assertEquals(2, paid.survivorPopulation)
        assertEquals(6, paid.nominalDamage); assertEquals(2, paid.damage)
        assertEquals(0, g.snapshot().human.health)
        assertEquals(2, g.snapshot().human.xp)
        g.assertInvariants()
    }
    @Test fun xpGapChangesAfterPurchaseOverflowAndMaximumLevel() {
        val g = prepared()
        assertEquals(6, g.snapshot().human.xpRemaining)
        g.buyXp(0)
        assertEquals(4, g.snapshot().human.xp); assertEquals(2, g.snapshot().human.xpRemaining)
        g.buyXp(0)
        assertEquals(4, g.snapshot().human.level)
        assertEquals(2, g.snapshot().human.xp); assertEquals(6, g.snapshot().human.xpRemaining)
        fixture(g, level = 7)
        assertEquals(0, g.snapshot().human.xpRemaining); assertEquals(0, g.snapshot().human.xpRequired)
    }
}
