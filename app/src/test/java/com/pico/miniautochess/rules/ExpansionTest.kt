package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

/** Regression for the V1.5 user-reported population, pool, drag and casting boundaries. */
class ExpansionTest {
    private fun field(obj: Any, name: String) = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    private fun setup(level: Int, units: List<UnitSnapshot>, xp: Int = 0, seed: Int = 71): GameEngine {
        val game = GameEngine(seed).also { it.start() }
        val players = field(game, "players").get(game) as List<Any>
        players.forEach { p ->
            (field(p, "units").get(p) as MutableList<UnitSnapshot>).clear()
            (field(p, "shop").get(p) as MutableList<String?>).fill(null)
        }
        val p = players[0]
        field(p, "level").set(p, level); field(p, "gold").set(p, 500); field(p, "xp").set(p, xp)
        (field(p, "units").get(p) as MutableList<UnitSnapshot>).addAll(units)
        val pool = field(game, "pool").get(game) as MutableMap<String, Int>
        Catalog.heroList.forEach { hero ->
            pool[hero.id] = Catalog.poolSize(hero.cost) - units.filter { it.heroId == hero.id }.sumOf { Catalog.copies(it.star) }
        }
        game.assertInvariants()
        return game
    }
    private fun units(board: Int, bench: Int) = List(board + bench) { i ->
        UnitSnapshot(1000 + i, Catalog.heroList[i % Catalog.heroList.size].id, 1, if (i < board) i else -(i - board + 1))
    }

    @Test fun sixthAndSeventhPopulationPlaceExactlyAtTheDeclaredLimit() {
        for (level in 3..7) {
            val game = setup(level, units(level - 1, 2))
            assertTrue(game.move(0, 1000 + level - 1, 25).ok)
            assertEquals(level, game.snapshot().human.deployed)
            val before = game.snapshot().human.units
            assertFalse(game.move(0, 1000 + level, 26).ok)
            assertEquals(before, game.snapshot().human.units)
            game.assertInvariants()
        }
    }
    @Test fun fullPopulationDragSwapsBothDirectionsAndWithinBoardAndBench() {
        val game = setup(6, units(6, 2))
        val drag = DragPlacement(game)
        fun move(uid: Int, slot: Int) {
            assertTrue(drag.begin(uid))
            val target = BoardLayout.position(slot)
            val source = drag.point!!
            drag.delta(target.x - source.x, target.z - source.z)
            assertTrue(drag.release().ok)
            assertEquals(slot, game.snapshot().human.units.first { it.uid == uid }.slot)
            assertEquals(6, game.snapshot().human.deployed)
            game.assertInvariants()
        }
        move(1006, 0)  // Bench onto an occupied board slot.
        assertEquals(-1, game.snapshot().human.units.first { it.uid == 1000 }.slot)
        move(1006, -1) // Board onto an occupied bench slot.
        move(1000, 1)  // Occupied board slot.
        move(1006, -2) // Occupied bench slot.
        assertTrue(drag.begin(1007))
        drag.delta(10f, 10f)
        val before = game.snapshot().human.units
        assertFalse(drag.release().ok)
        assertEquals(before, game.snapshot().human.units)
    }
    @Test fun levelUpFillsNewPopulationAndMergeRefillsConsumedCopies() {
        val game = setup(5, units(5, 2), xp = 9)
        assertTrue(game.buyXp(0).ok)
        assertEquals(6, game.snapshot().human.level)
        assertEquals(6, game.snapshot().human.deployed)
        game.assertInvariants()

        val mergeUnits = units(6, 3).mapIndexed { i, u ->
            if (i in 0..1) u.copy(heroId = "copper_guard") else u
        }
        val merged = setup(6, mergeUnits)
        @Suppress("UNCHECKED_CAST")
        val p = (field(merged, "players").get(merged) as List<Any>)[0]
        @Suppress("UNCHECKED_CAST")
        (field(p, "shop").get(p) as MutableList<String?>)[0] = "copper_guard"
        @Suppress("UNCHECKED_CAST")
        val pool = field(merged, "pool").get(merged) as MutableMap<String, Int>
        pool["copper_guard"] = pool.getValue("copper_guard") - 1
        assertTrue(merged.buy(0, 0).ok)
        assertEquals(6, merged.snapshot().human.deployed)
        assertTrue(merged.snapshot().human.units.any { it.heroId == "copper_guard" && it.star == 2 })
        merged.assertInvariants()
    }
    @Test fun probabilityRowsSumToOneHundredAndShiftFromLowToHighCostWithLevel() {
        for (level in 1..Catalog.MAX_LEVEL) {
            val odds = Catalog.odds(level)
            assertEquals(100, odds.sum()); assertTrue(odds.all { it in 0..100 })
        }
        assertEquals(listOf(100, 0, 0), Catalog.odds(1))
        assertEquals(listOf(100, 0, 0), Catalog.odds(2))
        assertEquals(listOf(75, 25, 0), Catalog.odds(3))
        assertEquals(listOf(55, 30, 15), Catalog.odds(4))
        assertEquals(listOf(45, 33, 22), Catalog.odds(5))
        assertEquals(listOf(30, 40, 30), Catalog.odds(6))
        assertEquals(listOf(16, 30, 54), Catalog.odds(7))
        for (level in 2..Catalog.MAX_LEVEL) {
            assertTrue(Catalog.odds(level)[0] <= Catalog.odds(level - 1)[0])
            assertTrue(Catalog.odds(level)[2] >= Catalog.odds(level - 1)[2])
        }
    }
    @Test fun identicalPopulationPoolAndRandomSeedProduceTheSameShopsInEveryRound() {
        for (level in 3..Catalog.MAX_LEVEL) {
            val games = listOf(1, 2, 5, 10, 11, 20, 50).map { round ->
                setup(level, emptyList()).also { field(it, "round").set(it, round) }
            }
            repeat(20) {
                games.forEach { assertTrue(it.refresh(0).ok); it.assertInvariants() }
                val expected = games.first().snapshot()
                games.drop(1).forEach { game ->
                    assertEquals(expected.human.shop, game.snapshot().human.shop)
                    assertEquals(expected.pool, game.snapshot().pool)
                }
            }
        }
    }
    @Test fun buyingExperienceUsesTheNewPopulationWeightsOnTheNextRefresh() {
        val game = setup(6, emptyList(), xp = 11)
        assertTrue(game.buyXp(0).ok)
        assertEquals(7, game.snapshot().human.level)
        assertEquals(1, game.round)
        val counts = IntArray(3)
        repeat(200) {
            assertTrue(game.refresh(0).ok)
            game.snapshot().human.shop.filterNotNull().forEach { counts[Catalog.hero(it).cost - 1]++ }
            game.assertInvariants()
        }
        assertEquals(1000, counts.sum())
        assertTrue(counts[0] in 110..210)
        assertTrue(counts[1] in 250..350)
        assertTrue(counts[2] in 490..590)
    }
    @Test fun exhaustedFeeRenormalizesOnlySupportedRemainingWeights() {
        val game = setup(7, emptyList())
        @Suppress("UNCHECKED_CAST")
        val pool = field(game, "pool").get(game) as MutableMap<String, Int>
        Catalog.heroList.filter { it.cost == 3 }.forEach { pool[it.id] = 0 }
        assertEquals(listOf(16, 30, 0), Catalog.availableWeights(7, pool))
        val counts = IntArray(3)
        repeat(200) {
            assertTrue(game.refresh(0).ok)
            game.snapshot().human.shop.filterNotNull().forEach { counts[Catalog.hero(it).cost - 1]++ }
        }
        // Deliberately exhausted hostile fixture; 16/(16+30) ~= 34.8%.
        assertEquals(1000, counts.sum())
        assertTrue(counts[0] in 295..400)
        assertEquals(0, counts[2])
    }
    @Test fun exhaustedPoolsNeverUnlockAZeroProbabilityFee() {
        val game = setup(3, emptyList())
        @Suppress("UNCHECKED_CAST")
        val pool = field(game, "pool").get(game) as MutableMap<String, Int>
        Catalog.heroList.filter { it.cost < 3 }.forEach { pool[it.id] = 0 }
        // Targeted hostile pool fixture tests the draw; conservation covered separately.
        assertTrue(game.refresh(0).ok)
        assertTrue(game.snapshot().human.shop.all { it == null })
        assertTrue(pool.filterKeys { Catalog.hero(it).cost == 3 }.values.all { it == 14 })
        pool["copper_guard"] = 1
        assertTrue(game.refresh(0).ok)
        assertEquals(listOf("copper_guard", null, null, null, null), game.snapshot().human.shop)
        assertEquals(0, pool.getValue("copper_guard"))
    }
    @Test fun drawsFollowFeeWeightsAndNeverCreateCopiesOverManyRefreshes() {
        for (level in 3..Catalog.MAX_LEVEL) {
            val counts = IntArray(3)
            repeat(8) { seed ->
                val game = setup(level, emptyList(), seed = seed + 1)
                repeat(250) {
                    assertTrue(game.refresh(0).ok)
                    game.snapshot().human.shop.filterNotNull().forEach { counts[Catalog.hero(it).cost - 1]++ }
                    game.assertInvariants()
                }
            }
            assertEquals(10000, counts.sum())
            Catalog.odds(level).forEachIndexed { index, percent ->
                if (percent == 0) assertEquals(0, counts[index])
                else assertTrue("Level $level fee ${index + 1}: ${counts[index]}/10000, expected $percent%",
                    counts[index] in (percent * 100 - 200)..(percent * 100 + 200))
            }
            println("SHOP_DISTRIBUTION level=$level samples=10000 counts=${counts.joinToString("/")} target=${Catalog.odds(level).joinToString("/")}")
        }
    }
    @Test fun oneHeroHasExactlyTwentySixCopiesUntilAPlayerReturnsOne() {
        val owned = listOf(
            UnitSnapshot(1000, "copper_guard", 3, 0), UnitSnapshot(1001, "copper_guard", 3, 1),
            UnitSnapshot(1002, "copper_guard", 2, -1), UnitSnapshot(1003, "copper_guard", 2, -2),
            UnitSnapshot(1004, "copper_guard", 1, -3), UnitSnapshot(1005, "copper_guard", 1, -4))
        val game = setup(3, owned)
        assertEquals(26, owned.sumOf { Catalog.copies(it.star) })
        assertEquals(0, game.poolRemaining().getValue("copper_guard"))
        repeat(60) {
            assertTrue(game.refresh(0).ok)
            assertFalse(game.snapshot().human.shop.contains("copper_guard"))
            game.assertInvariants()
        }
        assertTrue(game.sell(0, 1004).ok)
        assertEquals(1, game.poolRemaining().getValue("copper_guard"))
        assertTrue(game.refresh(0).ok)
        assertEquals(1, game.poolRemaining().getValue("copper_guard") + game.snapshot().human.shop.count { it == "copper_guard" })
        game.assertInvariants()
    }
    @Test fun mirrorRandomlyUsesEitherOtherCurrentArmyWithoutDoublePaying() {
        val donors = mutableSetOf<Int>()
        repeat(40) { index ->
            val game = setup(3, emptyList(), seed = index + 1)
            @Suppress("UNCHECKED_CAST")
            val players = field(game, "players").get(game) as List<Any>
            (1..2).forEach { id ->
                @Suppress("UNCHECKED_CAST")
                (field(players[id], "units").get(players[id]) as MutableList<UnitSnapshot>)
                    .add(UnitSnapshot(1000 + id, "copper_guard", 1, 3))
            }
            field(players[3], "eliminatedRound").set(players[3], 1)
            @Suppress("UNCHECKED_CAST")
            val pool = field(game, "pool").get(game) as MutableMap<String, Int>
            pool["copper_guard"] = 24
            game.assertInvariants()
            game.ready()
            val initial = game.snapshot()
            val mirror = initial.battles.single { it.right.ghost }.right
            donors.add(mirror.playerId)
            assertEquals(initial.players[mirror.playerId].units.map { it.copy(uid = -it.uid) }, mirror.units)
            val before = initial.players[mirror.playerId]
            game.advance(.1f) // Solo empty army loses instantly; donor's real battle is ongoing.
            assertEquals(before, game.snapshot().players[mirror.playerId])
            assertEquals(listOf(0), game.snapshot().results.map { it.playerId })
            game.assertInvariants()
        }
        assertEquals(setOf(1, 2), donors)
    }
    @Test fun everyAdvertisedTraitTierHasEnoughDistinctAvailableHeroes() {
        Catalog.traitDefinitions.forEach { def ->
            val candidates = Catalog.heroList.filter { it.faction.label == def.label || it.job.label == def.label }
            assertTrue("${def.label} unreachable", candidates.size >= def.thresholds.last())
            val army = candidates.mapIndexed { i, hero -> UnitSnapshot(i + 1, hero.id, 1, i) }
            for (n in 0..army.size) {
                val trait = traits(army.take(n)).first { it.label == def.label }
                assertEquals(def.thresholds.count { n >= it }, trait.tier)
                assertTrue(trait.progress.isNotBlank())
            }
        }
    }
    @Test fun fullManaCastsDuringBasicAttackCooldownAndDeadFightersCannotCast() {
        val b = Battle(0, Army(0, listOf(UnitSnapshot(1, "rune_apprentice", 1, 3))),
            Army(1, listOf(UnitSnapshot(2, "copper_guard", 3, 3))))
        val fighters = field(b, "fighters").get(b) as List<*>
        val caster = fighters.first()!!
        field(caster, "mana").set(caster, 100f)
        field(caster, "cooldown").set(caster, 5f)
        b.step()
        val f = b.snapshot().fighters.first()
        assertEquals(1, f.skills); assertEquals(0, f.attacks)
        assertTrue(f.mana in 0f..100f)
        assertTrue(b.snapshot().events.any { it.actor == 1 && it.kind == CombatEventKind.CAST })
        field(caster, "health").set(caster, 0f); field(caster, "mana").set(caster, 100f)
        b.step()
        assertEquals(1, b.snapshot().fighters.first().skills)
    }
    @Test fun longBattlesKeepManaHealthAndShieldBoundedForEveryHero() {
        Catalog.heroList.forEach { hero ->
            val b = Battle(0, Army(0, listOf(UnitSnapshot(1, hero.id, 3, 3))),
                Army(1, listOf(UnitSnapshot(2, "rune_sentinel", 3, 3))))
            repeat(401) {
                b.step()
                b.snapshot().fighters.forEach { f ->
                    assertTrue(f.mana in 0f..Catalog.MAX_MANA)
                    assertTrue(f.health in 0f..f.maxHealth)
                    assertTrue(f.shield in 0f..f.maxHealth)
                }
            }
        }
    }
}
