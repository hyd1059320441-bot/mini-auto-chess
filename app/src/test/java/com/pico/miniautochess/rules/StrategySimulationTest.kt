package com.pico.miniautochess.rules

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class StrategySimulationTest {
    @Test fun differentEconomyPoliciesFinishWithoutFreeCopiesOrGold() {
        val records = mutableListOf<String>()
        val stats = mutableMapOf<AiStyle, MutableList<Int>>()
        // Rotate each strategy through the human seat; early human termination
        // prevents treating the surviving AI's provisional rank as a champion.
        val styles = listOf(AiStyle.FLEX, AiStyle.REROLL, AiStyle.LEVEL)
        for (offset in styles.indices) for (seed in 201..250) {
            val seats = List(4) { styles[(it + offset) % styles.size] }
            val game = GameEngine(seed, autoPlay = true, aiStyles = seats)
            game.start()
            while (game.phase != Phase.FINISHED && game.elapsed < 9000f && game.round < 120) {
                game.advance(Catalog.STEP)
                game.assertInvariants()
            }
            assertEquals("$seats seed $seed", Phase.FINISHED, game.phase)
            val end = game.snapshot()
            val rank = end.ranking.first { it.playerId == 0 }.rank
            assertTrue(!end.human.alive || rank == 1 && end.players.count { it.alive } <= 1)
            assertTrue(end.results.all { it.roundPaid })
            stats.getOrPut(seats[0]) { mutableListOf() }.add(rank)
            records += "${seats[0]},$seed,$rank,${end.round},${end.elapsed},${end.human.units.sumOf { it.value }}"
            game.advance(9000f)
            assertEquals(end, game.snapshot())
        }
        val folder = File("../docs/verification")
        File(folder, "strategy-v1.10.0-150.csv").writeText(
            "human_strategy,seed,human_rank,rounds,foreground_seconds,terminal_board_gold\n" +
                records.joinToString("\n") + "\n")
        val report = buildString {
            appendLine("# V1.10.0：150局不同运营策略模拟")
            appendLine()
            appendLine("策略轮换真人席，种子201–250：灵活运营、偏搜低费、偏升级高费，各50局。")
            appendLine("均调用生产购买、刷新、升级和布阵规则；每步检查共享卡池、金币、人口和配对守恒。")
            appendLine("真人淘汰即停止，其余AI的实时名次不作为最终名次。下列结果仅检验这些经营策略，不代表玩家胜率。")
            stats.forEach { (style, ranks) ->
                appendLine("- $style：50局；真人夺冠${ranks.count { it == 1 }}局；平均个人名次${ranks.average()}。")
            }
            appendLine("150局均结束，终局再次推进9000秒状态不变。逐局结果见同名CSV。")
        }
        File(folder, "strategy-v1.10.0-150.md").writeText(report)
        println(report)
    }
}
