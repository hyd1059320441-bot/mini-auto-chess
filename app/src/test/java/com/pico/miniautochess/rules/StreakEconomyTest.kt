package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class StreakEconomyTest {
    private fun field(obj: Any, name: String) = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun players(game: GameEngine) = field(game, "players").get(game) as List<*>
    @Suppress("UNCHECKED_CAST")
    private fun outcome(game: GameEngine, winner: Int?, health: Int = 32, aliveSeats: Int = 4) {
        val ps = players(game)
        (field(game, "aiSteps").get(game) as IntArray).fill(4)
        ps.forEachIndexed { id, value ->
            val p = value!!
            field(p, "health").set(p, if (id == 0) health else 32)
            field(p, "eliminatedRound").set(p, if (id >= aliveSeats) 1 else null)
            (field(p, "units").get(p) as MutableList<UnitSnapshot>).apply {
                clear()
                // Deliberately resolved empty-opponent battles exercise production settlement.
                if (id == winner) add(UnitSnapshot(1000 + id, "copper_guard", 1, 3))
            }
            (field(p, "shop").get(p) as MutableList<String?>).fill(null)
        }
        val pool = field(game, "pool").get(game) as MutableMap<String, Int>
        Catalog.heroList.forEach { h ->
            pool[h.id] = Catalog.poolSize(h.cost) - game.snapshot().players.sumOf { p ->
                p.units.filter { it.heroId == h.id }.sumOf { Catalog.copies(it.star) }
            }
        }
        assertTrue(game.ready().ok)
        game.advance(Catalog.STEP)
        game.assertInvariants()
    }
    private fun next(game: GameEngine) {
        assertEquals(Phase.TRANSITION, game.phase)
        game.advance(Catalog.TRANSITION_SECONDS + .1f)
        assertEquals(Phase.PREPARATION, game.phase)
    }
    @Test fun allRealResultsUseSharedStreakLedgerAndPayOnlyOnce() {
        val game = GameEngine(44, aiStyles = List(4) { AiStyle.FLEX }).also { it.start() }
        for (n in 1..7) {
            val before = game.snapshot().players
            outcome(game, winner = if (n % 3 == 1) 0 else if (n % 3 == 2) 2 else 1)
            // Pairings rotate, so both seat0 and its current real opponent are inspected.
            val end = game.snapshot()
            end.results.forEach { r ->
                val prev = before[r.playerId]
                val expected = when (r.result) {
                    "胜利" -> prev.streak.coerceAtLeast(0) + 1
                    "落败" -> prev.streak.coerceAtMost(0) - 1
                    else -> 0
                }
                assertEquals(expected, r.streak)
                assertEquals(expected, end.players[r.playerId].streak)
                assertEquals(Catalog.streakGold(expected), r.streakGold)
                assertEquals(prev.gold + r.winGold, r.interestBalance)
                assertEquals(5 + r.winGold + r.interest + r.streakGold, r.income)
                assertEquals(prev.gold + r.income, end.players[r.playerId].gold)
            }
            game.advance(.4f)
            assertEquals(end.players, game.snapshot().players)
            if (n < 7) next(game)
        }
    }
    @Test fun twoThreeFourAndFiveStreakRewardsMatchForWinAndLoss() {
        val game = GameEngine(11, aiStyles = List(4) { AiStyle.FLEX }).also { it.start() }
        // Keep the normal rotating opponent, grant only controlled outcomes.
        for (n in 1..6) {
            val opponent = listOf(1, 2, 3)[(n - 1) % 3]
            (1..3).forEach { id -> field(players(game)[id]!!, "streak").set(players(game)[id], -(n - 1)) }
            outcome(game, 0)
            val result = game.snapshot().results.first { it.playerId == 0 }
            val loser = game.snapshot().results.first { it.playerId == opponent }
            assertEquals(n, result.streak); assertEquals(-n, loser.streak)
            assertEquals(listOf(0, 1, 1, 2, 3, 3)[n - 1], result.streakGold)
            assertEquals(result.streakGold, loser.streakGold)
            if (n < 6) next(game)
        }
    }
    @Test fun reversalsAndDrawResetTheChainAndRestartClearsAllSeats() {
        val game = GameEngine(12).also { it.start() }
        outcome(game, 0); next(game)
        outcome(game, 0)
        assertEquals(2, game.snapshot().human.streak)
        next(game); outcome(game, 3)
        assertEquals(-1, game.snapshot().human.streak)
        next(game); outcome(game, null)
        assertEquals(0, game.snapshot().human.streak)
        assertTrue(game.snapshot().results.all { it.streak == 0 && it.streakGold == 0 })
        next(game); outcome(game, 0)
        assertEquals(1, game.snapshot().human.streak)
        next(game); outcome(game, 3, health = 1)
        assertEquals(Phase.FINISHED, game.phase)
        assertTrue(game.start().ok)
        assertTrue(game.snapshot().players.all { it.streak == 0 && it.gold == 10 })
    }
    @Test fun newStreakRewardDoesNotInflateItsOwnInterestSnapshot() {
        val game = GameEngine(13).also { it.start() }
        field(players(game)[0]!!, "gold").set(players(game)[0], 18)
        field(players(game)[0]!!, "streak").set(players(game)[0], 3)
        field(game, "round").set(game, 4)
        outcome(game, 0)
        val paid = game.snapshot().results.first { it.playerId == 0 }
        assertEquals(4, paid.streak); assertEquals(2, paid.streakGold)
        assertEquals(19, paid.interestBalance); assertEquals(1, paid.interest)
        assertEquals(27, game.snapshot().human.gold)
    }
    @Test fun lethalLossPaysExistingLossStreakOnceAndFreezesItAtTheTerminalState() {
        val game = GameEngine(14).also { it.start() }
        field(players(game)[0]!!, "streak").set(players(game)[0], -4)
        field(game, "round").set(game, 6) // Round6 pairs seat0 with seat3.
        outcome(game, 3, health = 1)
        val end = game.snapshot()
        assertEquals(Phase.FINISHED, game.phase)
        val paid = end.results.first { it.playerId == 0 }
        assertEquals(-5, paid.streak); assertEquals(3, paid.streakGold)
        assertTrue(paid.roundPaid)
        assertEquals(19, end.human.gold) // 10 +5 base +1 interest +3 streak.
        game.advance(9000f)
        assertEquals(end, game.snapshot())
    }
    @Test fun ghostUpdatesOnlyRealRecipientAndCannotExtendDonorStreak() {
        val game = GameEngine(15).also { it.start() }
        // Human is the solo seat in round1; both possible donors have a unit.
        val ps = players(game)
        field(ps[1]!!, "streak").set(ps[1], 0)
        field(ps[2]!!, "streak").set(ps[2], 0)
        outcome(game, 1, aliveSeats = 3)
        val end = game.snapshot()
        val ghost = end.battles.single { it.right.ghost }.right
        assertEquals(if (ghost.units.isEmpty()) 0 else -1, end.human.streak)
        assertEquals(1, end.players[1].streak)
        assertEquals(-1, end.players[2].streak)
        assertEquals(3, end.results.size)
        assertTrue(end.results.all { kotlin.math.abs(it.streak) <= 1 })
    }
}
