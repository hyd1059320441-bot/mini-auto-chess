package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class FairPreparationTest {
    @Test fun botsSpendThroughSharedRulesAfterThinkDelayAndPauseFreezesTheirActions() {
        val game = GameEngine(541, autoPlay = true, aiStyles = List(4) { AiStyle.FLEX })
        game.start()
        val start = game.snapshot().players
        assertTrue(start.all { it.gold == 10 && it.xp == 0 && it.units.isEmpty() })
        game.advance(1.9f)
        assertEquals(start, game.snapshot().players)
        game.setPaused(true); game.advance(100f)
        assertEquals(start, game.snapshot().players)
        game.setPaused(false); game.advance(11f)
        assertTrue(game.snapshot().players.all { it.units.isNotEmpty() && it.gold in 0..10 })
        game.assertInvariants()
    }
    @Test fun repeatedMatchesHaveNewSeedsButFixedSeedSequencesRemainReproducible() {
        val a = GameEngine(912, aiStyles = List(4) { AiStyle.FLEX })
        val b = GameEngine(912, aiStyles = List(4) { AiStyle.FLEX })
        val seeds = mutableSetOf<Int>()
        var previous: List<List<String?>>? = null
        repeat(4) {
            a.start(); b.start()
            assertTrue(seeds.add(a.currentMatchSeed))
            assertEquals(a.currentMatchSeed, b.currentMatchSeed)
            val shops = a.snapshot().players.map { it.shop }
            assertEquals(shops, b.snapshot().players.map { it.shop })
            if (previous != null) assertNotEquals(previous, shops)
            previous = shops
            for (game in listOf(a, b)) game.javaClass.getDeclaredField("phase").apply { isAccessible = true }.set(game, Phase.FINISHED)
        }
    }
    @Test fun onlyTerminalMatchShowsVictoryOrFailure() {
        val game = GameEngine().also { it.start() }
        val state = game.snapshot()
        assertNull(MatchResultPresentation.from(state))
        val won = state.copy(phase = Phase.FINISHED, ranking = listOf(Ranking(0, "你", 1, 10, 5, 10, null)))
        assertEquals("胜利", MatchResultPresentation.from(won)!!.title)
        val lost = won.copy(players = won.players.map { if (it.id == 0) it.copy(health = 0, eliminatedRound = 8) else it },
            ranking = listOf(Ranking(0, "你", 3, 0, 2, 10, 8)))
        assertEquals("失败", MatchResultPresentation.from(lost)!!.title)
        assertTrue(MatchResultPresentation.from(lost)!!.detail.contains("第 3 名"))
    }
}
