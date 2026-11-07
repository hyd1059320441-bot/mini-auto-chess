package com.pico.miniautochess.rules

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SimulationTest {
    @Test fun oneHundredNormalSpeedMatchesCompleteWithConservedPools() {
        data class Record(val seed: Int, val seconds: Float, val rounds: Int, val winner: Int,
            val eliminated: List<Int?>, val heroes: List<String>, val traits: List<String>)
        val records = mutableListOf<Record>()
        var ghostBattles = 0
        var battleCount = 0
        var timeoutCount = 0
        val wallStart = System.nanoTime()
        repeat(100) { index ->
            val engine = GameEngine(seed = index + 1, autoPlay = true)
            engine.start()
            var prevPhase = engine.phase
            // Full ten rounds can legally take 654 seconds at battle timeout.
            while (engine.phase != Phase.FINISHED && engine.elapsed < 660f) {
                engine.advance(Catalog.STEP)
                if (engine.phase != prevPhase) {
                    engine.assertInvariants()
                    if (engine.phase == Phase.BATTLE) {
                        val s = engine.snapshot()
                        ghostBattles += s.battles.count { it.right.ghost }
                        battleCount += s.battles.size
                    } else if (prevPhase == Phase.BATTLE) {
                        timeoutCount += engine.snapshot().battles.count { it.elapsed + .001f >= Catalog.BATTLE_LIMIT }
                    }
                    prevPhase = engine.phase
                }
            }
            engine.assertInvariants()
            assertEquals("Seed ${index + 1} did not finish", Phase.FINISHED, engine.phase)
            val s = engine.snapshot()
            assertTrue(s.round in 1..10)
            assertTrue(s.players.all { it.gold >= 0 })
            val winner = s.ranking.first().playerId
            val champion = s.players[winner]
            records.add(Record(index + 1, s.elapsed, s.round, winner,
                s.players.map { it.eliminatedRound },
                champion.units.filter { it.onBoard }.map { "${it.hero.name} ${it.star}★" },
                champion.traits.filter { it.tier > 0 }.map { "${it.label} ${it.count}" }))
        }
        val wallSeconds = (System.nanoTime() - wallStart) / 1e9
        val times = records.map { it.seconds }.sorted()
        fun percentile(p: Double) = times[((times.size - 1) * p).toInt()]
        val csv = "seed,normal_seconds,rounds,winner,elimination_rounds,winning_heroes,winning_traits\n" +
            records.joinToString("\n") { r ->
                "${r.seed},${"%.1f".format(java.util.Locale.US, r.seconds)},${r.rounds},${r.winner},\"${r.eliminated.joinToString("|") { it?.toString() ?: "存活" }}\",\"${r.heroes.joinToString("|")}\",\"${r.traits.joinToString("|")}\""
            } + "\n"
        val output = File("../docs/verification").apply { mkdirs() }
        File(output, "simulation-100.csv").writeText(csv)
        fun time(t: Float) = "${"%.1f".format(java.util.Locale.US, t)} 秒"
        val heroFrequency = records.flatMap { it.heroes }.groupingBy { it.substringBefore(" ") }.eachCount().toList().sortedByDescending { it.second }
        val report = buildString {
            appendLine("# 青玉棋会：100 局规则模拟")
            appendLine()
            appendLine("使用正式规则模块，种子 1–100，四席均调用同一 AI 经营策略。没有免费补款、跳过准备阶段或战斗加速。")
            appendLine("这是逻辑时间模拟；不代表真实佩戴时长、手柄可用性或真机帧率。")
            appendLine()
            appendLine("- 正常速度逻辑时长：最短 ${time(times.first())}；P10 ${time(percentile(.1))}；中位 ${time(percentile(.5))}；P90 ${time(percentile(.9))}；最长 ${time(times.last())}。")
            appendLine("- 平均回合数：${"%.2f".format(java.util.Locale.US, records.map { it.rounds }.average())}。")
            appendLine("- 8–12 分钟范围内：${records.count { it.seconds in 480f..720f }} / 100 局。")
            appendLine("- 共 $battleCount 场战斗，$ghostBattles 场 Ghost，$timeoutCount 场达到 25 秒超时。")
            appendLine("- 模拟实际计算耗时：${"%.2f".format(java.util.Locale.US, wallSeconds)} 秒。")
            appendLine("- 所有阶段转换检查金币、人口、备战席、位置唯一性、英雄池守恒和独立配对。")
            appendLine()
            appendLine("## 冠军席位")
            records.groupingBy { it.winner }.eachCount().toSortedMap().forEach { (seat, count) -> appendLine("- 席位 $seat：$count 局") }
            appendLine()
            appendLine("## 淘汰轮次")
            records.flatMap { it.eliminated }.filterNotNull().groupingBy { it }.eachCount().toSortedMap().forEach { (round, count) -> appendLine("- 第 $round 轮：$count 人") }
            appendLine()
            appendLine("## 冠军阵容英雄出现次数")
            heroFrequency.forEach { (hero, count) -> appendLine("- $hero：$count") }
            appendLine()
            appendLine("完整逐局结果见 simulation-100.csv。")
        }
        File(output, "simulation-100.md").writeText(report)
        println(report)
    }
}
