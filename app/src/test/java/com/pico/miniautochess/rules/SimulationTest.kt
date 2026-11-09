package com.pico.miniautochess.rules

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SimulationTest {
    @Test fun oneHundredMatchesFinishAtHumanEliminationOrVictoryWithConservedPools() {
        data class Record(val seed: Int, val seconds: Float, val rounds: Int, val humanRank: Int,
            val reason: String, val livingAi: Int, val eliminated: List<Int?>)
        val records = mutableListOf<Record>()
        var ghostBattles = 0
        var battleCount = 0
        var timeoutCount = 0
        var overtimeCount = 0
        var unfinishedCount = 0
        val wallStart = System.nanoTime()
        repeat(100) { index ->
            val engine = GameEngine(seed = index + 1, autoPlay = true)
            engine.start()
            var prevPhase = engine.phase
            // Guard only the test harness; the game has no round-count ending.
            while (engine.phase != Phase.FINISHED && engine.elapsed < 9000f && engine.round <= 120) {
                engine.advance(Catalog.STEP)
                engine.assertInvariants()
                val current = engine.snapshot()
                if (!current.human.alive) assertEquals(Phase.FINISHED, current.phase)
                if (engine.phase != prevPhase) {
                    if (engine.phase == Phase.BATTLE) {
                        ghostBattles += current.battles.count { it.right.ghost }
                        battleCount += current.battles.size
                    } else if (prevPhase == Phase.BATTLE) {
                        overtimeCount += current.battles.count { it.overtime }
                        timeoutCount += current.battles.count { it.outcome != null &&
                            it.wallElapsed + .001f >= Catalog.BATTLE_LIMIT + Catalog.OVERTIME_SECONDS }
                        unfinishedCount += current.battles.count { it.outcome == null }
                    }
                    prevPhase = engine.phase
                }
            }
            assertEquals("Seed ${index + 1} did not finish", Phase.FINISHED, engine.phase)
            val end = engine.snapshot()
            assertTrue(end.round >= 1)
            assertTrue("Seed ${index + 1}: premature finish", !end.human.alive || end.players.count { it.alive } <= 1)
            assertTrue(end.results.all { it.roundPaid })
            assertTrue(end.players.all { it.gold >= 0 })
            val humanRank = end.ranking.first { it.playerId == 0 }.rank
            if (end.human.alive) assertEquals(1, humanRank)
            records.add(Record(index + 1, end.elapsed, end.round, humanRank,
                if (end.human.alive) "human_victory" else "human_eliminated",
                end.players.drop(1).count { it.alive }, end.players.map { it.eliminatedRound }))
            engine.advance(9000f)
            assertEquals("Seed ${index + 1}: terminal match must remain frozen", end, engine.snapshot())
        }
        val wallSeconds = (System.nanoTime() - wallStart) / 1e9
        val times = records.map { it.seconds }.sorted()
        fun percentile(p: Double) = times[((times.size - 1) * p).toInt()]
        assertTrue("Expected matches to continue beyond ten rounds", records.any { it.rounds > 10 })
        assertTrue(records.any { it.reason == "human_eliminated" && it.livingAi > 1 })
        val csv = "seed,foreground_seconds,rounds,human_rank,finish_reason,living_ai,elimination_rounds\n" +
            records.joinToString("\n") { r ->
                "${r.seed},${"%.1f".format(java.util.Locale.US, r.seconds)},${r.rounds},${r.humanRank},${r.reason},${r.livingAi},\"${r.eliminated.joinToString("|") { it?.toString() ?: "存活" }}\""
            } + "\n"
        val output = File("../docs/verification").apply { mkdirs() }
        File(output, "simulation-v1.10.0-100.csv").writeText(csv)
        fun time(t: Float) = "${"%.1f".format(java.util.Locale.US, t)} 秒"
        val report = buildString {
            appendLine("# 青玉棋会 V1.10.0：100 局规则模拟")
            appendLine()
            appendLine("种子1–100，真人托管为灵活运营，三AI风格每局随机分配，全部分批延迟经营。真人淘汰当步终局；真人存活时继续至夺冠。保留镜像与战斗加速。")
            appendLine("这是逻辑时间模拟；不代表真实佩戴时长、拖动高亮表现或真机帧率。")
            appendLine()
            appendLine("- 前台逻辑时长：最短 ${time(times.first())}；P10 ${time(percentile(.1))}；中位 ${time(percentile(.5))}；P90 ${time(percentile(.9))}；最长 ${time(times.last())}。")
            appendLine("- 平均回合数：${"%.2f".format(java.util.Locale.US, records.map { it.rounds }.average())}；${records.count { it.rounds > 10 }}局超过第十回合；最晚第${records.maxOf { it.rounds }}回合结束。")
            appendLine("- 真人淘汰结束 ${records.count { it.reason == "human_eliminated" }}局；真人夺冠 ${records.count { it.reason == "human_victory" }}局。")
            appendLine("- 真人淘汰时仍有多名AI存活 ${records.count { it.reason == "human_eliminated" && it.livingAi > 1 }}局。剩余AI未完赛，不将实时排名第一者视为冠军。")
            appendLine("- 共 $battleCount 场战斗，$ghostBattles 场镜像，$overtimeCount 场加速，$timeoutCount 场加速后判定；真人淘汰中断 $unfinishedCount 场未结束的并行战斗。")
            appendLine("- 每个规则步核验卡池守恒、人口、金币与独立配对；真人死亡当步必须FINISHED；100局终局再推进9000秒均保持完整快照不变。")
            appendLine("- 仅已完成战斗的真实参战者领取当轮收入/经验，镜像本体和中断战斗不额外结算。")
            appendLine("- 实际计算耗时：${"%.2f".format(java.util.Locale.US, wallSeconds)}秒。")
            appendLine()
            appendLine("## 真人名次")
            records.groupingBy { it.humanRank }.eachCount().toSortedMap().forEach { (rank, count) -> appendLine("- 第$rank 名：$count 局") }
            appendLine()
            appendLine("完整逐局结果见 simulation-v1.10.0-100.csv。")
        }
        File(output, "simulation-v1.10.0-100.md").writeText(report)
        println(report)
    }
}
