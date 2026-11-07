package com.pico.miniautochess.rules

import kotlin.random.Random

enum class Phase(val label: String) {
    MENU("青玉棋会"), PREPARATION("备战"), BATTLE("交锋"), TRANSITION("回合结算"), FINISHED("终局")
}
data class ActionResult(val ok: Boolean, val message: String)
data class PlayerSnapshot(
    val id: Int, val name: String, val health: Int, val gold: Int, val level: Int, val xp: Int,
    val wins: Int, val units: List<UnitSnapshot>, val shop: List<String?>,
    val eliminatedRound: Int?,
) {
    val alive get() = eliminatedRound == null
    val deployed get() = units.count { it.onBoard }
    val traits get() = traits(units)
    val xpRequired get() = if (level < Catalog.MAX_LEVEL) Catalog.levelXp[level - 1] else 0
    val xpRemaining get() = (xpRequired - xp).coerceAtLeast(0)
}
data class Ranking(val playerId: Int, val name: String, val rank: Int, val health: Int, val wins: Int, val value: Int, val eliminatedRound: Int?)
data class RoundResult(
    val playerId: Int, val result: String, val damage: Int, val income: Int, val interest: Int,
    val winGold: Int = 0, val xpGain: Int = 0, val roundPaid: Boolean = false,
    val baseDamage: Int = 0, val survivorPopulation: Int = 0, val interestBalance: Int = 0,
) {
    val nominalDamage get() = baseDamage + survivorPopulation
}
data class MatchSnapshot(
    val revision: Int, val phase: Phase, val round: Int, val secondsLeft: Float, val elapsed: Float,
    val players: List<PlayerSnapshot>, val battles: List<BattleSnapshot>, val watchedBattle: Int,
    val ranking: List<Ranking>, val results: List<RoundResult>, val message: String,
    val paused: Boolean, val autoPlayHuman: Boolean,
) {
    val human get() = players.first()
}

/**
 * Single-threaded deterministic rule owner. Call on the app main thread.
 * No Android, Compose, SDK, account, rendering or device dependency.
 */
class GameEngine(private val seed: Int = 1, autoPlay: Boolean = false) {
    private class Player(val id: Int, val name: String) {
        var health = Catalog.START_HEALTH
        var gold = Catalog.START_GOLD
        var level = 3
        var xp = 0
        var wins = 0
        val units = mutableListOf<UnitSnapshot>()
        val shop = MutableList<String?>(Catalog.SHOP_SIZE) { null }
        var eliminatedRound: Int? = null
        var finalValue = 0
        fun snapshot() = PlayerSnapshot(id, name, health, gold, level, xp, wins,
            units.toList(), shop.toList(), eliminatedRound)
    }
    private var random = Random(seed)
    private var nextUid = 1
    private var revision = 0
    /** Allows renderer/UI adapters to reuse a snapshot between logical changes. */
    val stateRevision get() = revision
    private val players = (0..3).map { Player(it, listOf("你", "松隐", "灯岚", "赤砂")[it]) }
    private val pool = Catalog.heroList.associate { it.id to Catalog.poolSize(it.cost) }.toMutableMap()
    private var previousArmies = emptyMap<Int, Army>()
    private var battles = emptyList<Battle>()
    private val resolvedBattles = mutableSetOf<Int>()
    private var timer = 0f
    private var accumulator = 0f
    var phase = Phase.MENU
        private set
    var round = 0
        private set
    var elapsed = 0f
        private set
    var watchedBattle = 0
        private set
    var paused = false
        private set
    var autoPlayHuman = autoPlay
        private set
    var message = "1 位真人 · 3 位棋手 · 十回合青玉棋会"
        private set
    private var results = emptyList<RoundResult>()
    private val initialAutoPlay = autoPlay

    fun start(): ActionResult {
        if (phase != Phase.MENU && phase != Phase.FINISHED) return fail("对局已经开始")
        random = Random(seed)
        nextUid = 1
        pool.clear()
        Catalog.heroList.forEach { pool[it.id] = Catalog.poolSize(it.cost) }
        players.forEach {
            it.health = Catalog.START_HEALTH; it.gold = Catalog.START_GOLD; it.level = 3
            it.xp = 0; it.wins = 0; it.units.clear(); it.shop.fill(null)
            it.eliminatedRound = null; it.finalValue = 0
        }
        battles = emptyList(); resolvedBattles.clear(); previousArmies = emptyMap(); results = emptyList()
        timer = 0f; elapsed = 0f; accumulator = 0f; round = 0; watchedBattle = 0
        paused = false; autoPlayHuman = initialAutoPlay
        beginPreparation()
        return success("商店已就绪，招募英雄后拖入己方棋格")
    }
    fun setPaused(value: Boolean) { paused = value; revision++ }
    fun setAutoPlay(value: Boolean) {
        autoPlayHuman = value
        if (value && phase == Phase.PREPARATION && players[0].eliminatedRound == null) manageAi(0)
        revision++
    }
    fun watch(index: Int): ActionResult {
        if (index !in battles.indices) return fail("这场交锋尚未开始")
        watchedBattle = index
        return success("正在观察 ${battles[index].snapshot().label}")
    }
    /** Time accumulation is shared by both battles; no elapsed time is discarded. */
    fun advance(seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f)
        if (paused || phase == Phase.MENU || phase == Phase.FINISHED) return
        accumulator += seconds
        while (accumulator + .00001f >= Catalog.STEP && phase != Phase.FINISHED && !paused) {
            accumulator -= Catalog.STEP
            elapsed += Catalog.STEP
            tick()
        }
    }
    private fun tick() {
        if (phase == Phase.BATTLE) {
            battles.forEach { it.step() }
            battles.filter { it.outcome != null && it.id !in resolvedBattles }.forEach(::resolveBattle)
            if (battles.all { it.outcome != null }) settle()
        } else {
            timer = (timer - Catalog.STEP).coerceAtLeast(0f)
            if (timer < .001f) when (phase) {
                Phase.TRANSITION -> beginPreparation()
                Phase.PREPARATION -> beginBattle()
                else -> Unit
            }
        }
        revision++
    }
    private fun beginPreparation() {
        round++
        phase = Phase.PREPARATION
        timer = Catalog.preparationSeconds(round)
        message = "第 $round 回合：准备阵容，卫士在前、射手和法师在后"
        val preparationMessage = message
        // Rotating shop order prevents one AI always drawing first from the shared pool.
        val order = (0..3).map { (it + round - 1) % 4 }.filter { players[it].eliminatedRound == null }
        order.forEach { fillShop(players[it]) }
        order.filter { it != 0 || autoPlayHuman }.forEach { manageAi(it) }
        // AI purchases must not replace the human's preparation instruction.
        message = preparationMessage
        revision++
    }
    private fun beginBattle() {
        val alive = players.filter { it.eliminatedRound == null }.map { it.id }
        alive.forEach { fillEmptyPopulation(players[it]) }
        val armies = alive.associateWith { id ->
            Army(id, players[id].units.filter { it.onBoard })
        }
        val pairs = if (alive.size == 4) {
            when ((round - 1) % 3) {
                0 -> listOf(0 to 1, 2 to 3)
                1 -> listOf(0 to 2, 1 to 3)
                else -> listOf(0 to 3, 1 to 2)
            }.mapIndexed { i, (a, b) -> Battle(i, armies.getValue(a), armies.getValue(b)) }
        } else if (alive.size == 3) {
            val solo = alive[(round - 1) % 3]
            val pair = alive.filter { it != solo }
            val donor = pair[(round - 1) % 2]
            val source = previousArmies[donor] ?: armies.getValue(donor)
            // Ghost identifiers cannot collide with real fighters in the watched scene.
            val ghost = Army(donor, source.units.map { it.copy(uid = -it.uid) }, ghost = true)
            listOf(Battle(0, armies.getValue(pair[0]), armies.getValue(pair[1])),
                Battle(1, armies.getValue(solo), ghost))
        } else if (alive.size == 2) {
            listOf(Battle(0, armies.getValue(alive[0]), armies.getValue(alive[1])))
        } else {
            phase = Phase.FINISHED
            message = "棋会结束"
            return
        }
        previousArmies = armies
        battles = pairs
        results = emptyList()
        resolvedBattles.clear()
        watchedBattle = battles.indexOfFirst { (it.left.playerId == 0 && !it.left.ghost) || (it.right.playerId == 0 && !it.right.ghost) }.coerceAtLeast(0)
        phase = Phase.BATTLE
        message = "两场交锋同时进行，可切换观战"
        revision++
    }
    /** Each real seat participates once. Ghost results never affect the donor. */
    private fun resolveBattle(battle: Battle) {
        val outcome = checkNotNull(battle.outcome)
        if (!resolvedBattles.add(battle.id)) return
        listOf(battle.left, battle.right).forEachIndexed { side, army ->
            if (army.ghost) return@forEachIndexed
            val p = players[army.playerId]
            check(results.none { it.playerId == p.id })
            val won = (side == 0 && outcome == BattleOutcome.LEFT) || (side == 1 && outcome == BattleOutcome.RIGHT)
            val lost = outcome != BattleOutcome.DRAW && !won
            val loss = if (lost) CombatMath.playerDamage(round, battle.survivors(1 - side), p.health)
                else PlayerDamage(0, 0, 0)
            val damage = loss.actual
            val winGold = if (won) 1 else 0
            p.health = (p.health - damage).coerceAtLeast(0)
            p.gold += winGold
            if (won) p.wins++
            results = results + RoundResult(p.id, if (won) "胜利" else if (lost) "落败" else "平局",
                damage, winGold, 0, winGold = winGold, baseDamage = loss.base, survivorPopulation = loss.population)
            if (p.health == 0) eliminate(p)
            if (p.id == 0) message = if (p.eliminatedRound != null)
                "你已淘汰 · 本场落败，生命归零 · 可继续观战"
                else if (won) "本回合胜利 · +1 金币已到账"
                else if (lost) "落败：回合${loss.base} + 存活人口${loss.population} = ${loss.nominal} · 已扣 $damage 生命"
                else "本回合平局 · 不扣生命"
        }
    }
    private fun eliminate(p: Player) {
        if (p.eliminatedRound == null) {
            p.finalValue = p.units.sumOf { it.value }
            p.eliminatedRound = round
            p.units.forEach { pool[it.heroId] = pool.getValue(it.heroId) + Catalog.copies(it.star) }
            p.units.clear()
            releaseShop(p)
        }
    }
    private fun settle() {
        check(phase == Phase.BATTLE && resolvedBattles.size == battles.size)
        // Interest uses the balance including the immediately paid victory coin.
        results = results.map { result ->
            val p = players[result.playerId]
            check(!result.roundPaid)
            val interestBalance = p.gold
            val interest = Catalog.interest(interestBalance)
            val income = Catalog.BASE_INCOME + interest
            p.gold += income
            val xpGain = if (p.level < Catalog.MAX_LEVEL) Catalog.ROUND_XP else 0
            addXp(p, xpGain)
            result.copy(income = result.income + income, interest = interest, xpGain = xpGain,
                roundPaid = true, interestBalance = interestBalance)
        }
        if (players.count { it.eliminatedRound == null } <= 1 || round >= Catalog.MAX_ROUNDS) {
            phase = Phase.FINISHED
            timer = 0f
            val position = ranking().first { it.playerId == 0 }.rank
            message = if (position == 1) "棋会结束 · 你获得第 1 名！" else "棋会结束 · 你获得第 $position 名"
        } else {
            phase = Phase.TRANSITION
            timer = Catalog.TRANSITION_SECONDS
            message = results.firstOrNull { it.playerId == 0 }?.let {
                "${it.result} · 已扣 ${it.damage}生命 · 收入 ${it.income}金（基础5 / 胜利${it.winGold} / 利息${it.interest}）· 经验 +${it.xpGain}"
            } ?: "你已离席，可以继续观察其他棋手"
        }
    }
    fun ready(): ActionResult {
        if (paused) return fail("请先继续对局")
        if (phase != Phase.PREPARATION) return fail("现在无法开始交锋")
        beginBattle()
        return success("阵容已锁定，开始交锋")
    }
    private fun canAct(id: Int): String? = when {
        id !in players.indices -> "棋手不存在"
        phase != Phase.PREPARATION -> "仅备战阶段可操作"
        paused -> "请先继续对局"
        players[id].eliminatedRound != null -> "该棋手已经离席"
        else -> null
    }
    fun buy(id: Int, index: Int): ActionResult {
        canAct(id)?.let { return fail(it) }
        val p = players[id]
        if (index !in p.shop.indices) return fail("商店位置不存在")
        val heroId = p.shop[index] ?: return fail("这个棋子已经被招募")
        val def = Catalog.hero(heroId)
        if (p.gold < def.cost) return fail("金币不足，需要 ${def.cost} 金币")
        val freeBench = (-1 downTo -Catalog.BENCH_SIZE).firstOrNull { slot -> p.units.none { it.slot == slot } }
        val immediateMerge = p.units.count { it.heroId == heroId && it.star == 1 } >= 2
        if (freeBench == null && !immediateMerge) return fail("备战席已满，请先上场或卖出棋子")
        p.gold -= def.cost
        p.shop[index] = null
        p.units.add(UnitSnapshot(nextUid++, heroId, 1, freeBench ?: -99))
        val merged = merge(p)
        return success(if (merged) "${def.name}完成升星" else "已招募${def.name}，点击备战席棋子后选择上场格")
    }
    private fun merge(p: Player): Boolean {
        var changed = false
        for (star in 1..2) {
            for (def in Catalog.heroList) {
                while (p.units.count { it.heroId == def.id && it.star == star } >= 3) {
                    val group = p.units.filter { it.heroId == def.id && it.star == star }
                        .sortedWith(compareBy<UnitSnapshot> { !it.onBoard }
                            .thenBy { it.slot == -99 }.thenBy { it.uid }).take(3)
                    val keep = group.first()
                    p.units.removeAll(group.toSet())
                    p.units.add(keep.copy(star = star + 1))
                    changed = true
                }
            }
        }
        check(p.units.none { it.slot == -99 }) { "Temporary purchase slot survived merge" }
        return changed
    }
    fun move(id: Int, uid: Int, slot: Int): ActionResult {
        canAct(id)?.let { return fail(it) }
        if (slot !in 0 until Catalog.BOARD_SIZE && slot !in -Catalog.BENCH_SIZE..-1) return fail("目标位置不存在")
        val p = players[id]
        val unit = p.units.firstOrNull { it.uid == uid } ?: return fail("棋子不存在")
        val occupied = p.units.firstOrNull { it.slot == slot && it.uid != uid }
        val deployedDelta = (if (slot >= 0) 1 else 0) - (if (unit.onBoard) 1 else 0) +
            (if (occupied != null && unit.onBoard) 1 else 0) - (if (occupied?.onBoard == true) 1 else 0)
        if (p.units.count { it.onBoard } + deployedDelta > p.level) return fail("人口已满，请先升级或替换上场棋子")
        replace(p, unit, unit.copy(slot = slot))
        if (occupied != null) replace(p, occupied, occupied.copy(slot = unit.slot))
        return success(if (occupied != null) "两枚棋子交换位置" else if (slot >= 0) "${unit.hero.name}已上场" else "${unit.hero.name}已返回备战席")
    }
    fun sell(id: Int, uid: Int): ActionResult {
        canAct(id)?.let { return fail(it) }
        val p = players[id]
        val unit = p.units.firstOrNull { it.uid == uid } ?: return fail("棋子不存在")
        p.units.remove(unit); p.gold += unit.value
        pool[unit.heroId] = pool.getValue(unit.heroId) + Catalog.copies(unit.star)
        return success("卖出${unit.hero.name}，返还 ${unit.value} 金币")
    }
    fun refresh(id: Int): ActionResult {
        canAct(id)?.let { return fail(it) }
        val p = players[id]
        if (p.gold < Catalog.REFRESH_COST) return fail("刷新需要 2 金币")
        p.gold -= Catalog.REFRESH_COST
        fillShop(p)
        return success("已花费 2 金币刷新商店")
    }
    fun buyXp(id: Int): ActionResult {
        canAct(id)?.let { return fail(it) }
        val p = players[id]
        if (p.level >= Catalog.MAX_LEVEL) return fail("已达到最高人口 7")
        if (p.gold < Catalog.XP_COST) return fail("购买经验需要 4 金币")
        p.gold -= Catalog.XP_COST
        addXp(p, Catalog.XP_GAIN)
        return success("增加 4 经验，当前人口 ${p.level}")
    }
    private fun addXp(p: Player, amount: Int) {
        p.xp += amount
        while (p.level < Catalog.MAX_LEVEL && p.xp >= Catalog.levelXp[p.level - 1]) {
            p.xp -= Catalog.levelXp[p.level - 1]; p.level++
        }
        if (p.level == Catalog.MAX_LEVEL) p.xp = 0
    }
    fun arrange(id: Int): ActionResult {
        canAct(id)?.let { return fail(it) }
        arrangeArmy(players[id])
        return success("按强度安排上场，卫士前排、远程后排")
    }
    private fun replace(p: Player, before: UnitSnapshot, after: UnitSnapshot) { p.units[p.units.indexOf(before)] = after }
    private fun releaseShop(p: Player) {
        p.shop.filterNotNull().forEach { pool[it] = pool.getValue(it) + 1 }
        p.shop.fill(null)
    }
    private fun fillShop(p: Player) {
        releaseShop(p)
        val odds = Catalog.odds(p.level)
        p.shop.indices.forEach { i ->
            val roll = random.nextInt(100)
            val cost = if (roll < odds[0]) 1 else if (roll < odds[0] + odds[1]) 2 else 3
            var candidates = Catalog.heroList.filter { it.cost == cost && pool.getValue(it.id) > 0 }
            if (candidates.isEmpty()) candidates = Catalog.heroList.filter { pool.getValue(it.id) > 0 }
            val total = candidates.sumOf { pool.getValue(it.id) }
            if (total > 0) {
                var ticket = random.nextInt(total)
                val selected = candidates.first { ticket -= pool.getValue(it.id); ticket < 0 }
                p.shop[i] = selected.id
                pool[selected.id] = pool.getValue(selected.id) - 1
            }
        }
    }
    private fun strength(unit: UnitSnapshot, all: List<UnitSnapshot>): Float {
        val def = unit.hero
        val synergy = all.distinctBy { it.heroId }.count { it.hero.faction == def.faction || it.hero.job == def.job }
        return def.cost * Catalog.starMultiplier(unit.star) * 10f + synergy * 3f
    }
    /** Lock-time fill only. Manual placements and economy stay intact. */
    private fun fillEmptyPopulation(p: Player) {
        val occupied = p.units.filter { it.onBoard }.map { it.slot }.toMutableSet()
        val missing = (p.level - occupied.size).coerceAtLeast(0)
        p.units.filter { !it.onBoard }
            .sortedWith(compareByDescending<UnitSnapshot> { strength(it, p.units) }.thenBy { it.uid })
            .take(missing).sortedBy { it.hero.job != Job.GUARD }.forEach { unit ->
                val slot = BoardLayout.preferredSlots(unit.hero.job == Job.GUARD).first { it !in occupied }
                occupied.add(slot)
                replace(p, unit, unit.copy(slot = slot))
            }
    }
    private fun arrangeArmy(p: Player) {
        val ranked = p.units.sortedWith(compareByDescending<UnitSnapshot> { strength(it, p.units) }.thenBy { it.uid })
        val selected = ranked.take(p.level)
        val rest = ranked.drop(p.level)
        val boardSlots = mutableSetOf<Int>()
        val placements = mutableMapOf<Int, Int>()
        selected.sortedBy { it.hero.job != Job.GUARD }.forEach { unit ->
            val preference = BoardLayout.preferredSlots(unit.hero.job == Job.GUARD)
            val slot = preference.first { it !in boardSlots }
            boardSlots.add(slot); placements[unit.uid] = slot
        }
        rest.forEachIndexed { i, unit -> placements[unit.uid] = -i - 1 }
        check(rest.size <= Catalog.BENCH_SIZE)
        for (i in p.units.indices) p.units[i] = p.units[i].copy(slot = placements.getValue(p.units[i].uid))
    }
    private fun manageAi(id: Int) {
        val p = players[id]
        // No cheating: every purchase and reroll goes through the same public rules.
        val budget = p.gold
        val reserve = if (round <= 2) 0 else if (round <= 5 && p.health > 16) 10 else 0
        val ranked = p.shop.indices.sortedByDescending { i ->
            val def = p.shop[i]?.let { Catalog.hero(it) } ?: return@sortedByDescending -100
            p.units.count { it.heroId == def.id } * 30 +
                p.units.distinctBy { it.heroId }.count { it.hero.faction == def.faction } * 6 + def.cost * 4
        }
        ranked.forEach { i ->
            val heroId = p.shop[i] ?: return@forEach
            val def = Catalog.hero(heroId)
            if (p.gold >= def.cost + reserve &&
                (p.units.size < p.level + 3 || p.units.count { it.heroId == heroId && it.star == 1 } >= 1)) buy(id, i)
        }
        arrangeArmy(p)
        if (p.level < Catalog.MAX_LEVEL && p.units.size >= p.level && p.gold >= 4 + reserve) buyXp(id)
        // At most two refreshes per round; do not burn all gold or award free money.
        repeat(if (round >= 4) 2 else 0) {
            if (p.gold >= 7 + reserve) {
                refresh(id)
                p.shop.indices.sortedByDescending { i ->
                    val h = p.shop[i] ?: return@sortedByDescending -1
                    p.units.count { it.heroId == h && it.star < 3 } * 10 + Catalog.hero(h).cost
                }.forEach { i ->
                    val h = p.shop[i] ?: return@forEach
                    val copy = p.units.any { it.heroId == h && it.star == 1 }
                    if (p.gold >= Catalog.hero(h).cost + reserve && (copy || p.units.size < p.level + 2)) buy(id, i)
                }
            }
        }
        arrangeArmy(p)
        check(p.gold >= 0 && p.gold <= budget)
    }
    fun poolRemaining() = pool.toMap()
    fun assertInvariants() {
        players.forEach { p ->
            check(p.gold >= 0 && p.level in 3..7 && p.health in 0..Catalog.START_HEALTH)
            check(p.units.count { it.onBoard } <= p.level)
            check(p.units.count { !it.onBoard } <= Catalog.BENCH_SIZE)
            check(p.units.map { it.slot }.distinct().size == p.units.size)
            check(p.units.all { it.star in 1..3 &&
                (it.slot in 0 until Catalog.BOARD_SIZE || it.slot in -Catalog.BENCH_SIZE..-1) })
            check(p.eliminatedRound == null || (p.units.isEmpty() && p.shop.all { it == null }))
        }
        check(players.flatMap { it.units }.map { it.uid }.distinct().size == players.sumOf { it.units.size })
        Catalog.heroList.forEach { def ->
            val owned = players.sumOf { p -> p.units.filter { it.heroId == def.id }.sumOf { Catalog.copies(it.star) } }
            val reserved = players.sumOf { p -> p.shop.count { it == def.id } }
            check(pool.getValue(def.id) >= 0 && owned + reserved + pool.getValue(def.id) == Catalog.poolSize(def.cost)) {
                "Pool conservation failed: ${def.id}, owned=$owned reserved=$reserved remaining=${pool[def.id]}"
            }
        }
        if (phase == Phase.BATTLE) {
            val real = battles.flatMap { listOf(it.left, it.right) }.filter { !it.ghost }
            check(real.map { it.playerId }.distinct().size == real.size)
            check(players.filter { it.eliminatedRound == null }.all { p -> real.any { it.playerId == p.id } })
            check(battles.filter { it.id !in resolvedBattles }.flatMap { listOf(it.left, it.right) }
                .filter { !it.ghost }.all { players[it.playerId].eliminatedRound == null })
            check(results.map { it.playerId }.distinct().size == results.size)
        }
    }
    fun ranking(): List<Ranking> {
        data class Entry(val p: Player, val value: Int)
        val entries = players.map { Entry(it, if (it.eliminatedRound == null) it.units.sumOf { unit -> unit.value } else it.finalValue) }
            .sortedWith(compareByDescending<Entry> { it.p.eliminatedRound == null }
                .thenByDescending { it.p.eliminatedRound ?: Int.MAX_VALUE }
                .thenByDescending { it.p.health }.thenByDescending { it.p.wins }.thenByDescending { it.value })
        var rank = 1
        return entries.mapIndexed { i, e ->
            if (i > 0) {
                val prev = entries[i - 1]
                if (prev.p.eliminatedRound != e.p.eliminatedRound || prev.p.health != e.p.health ||
                    prev.p.wins != e.p.wins || prev.value != e.value) rank = i + 1
            }
            Ranking(e.p.id, e.p.name, rank, e.p.health, e.p.wins, e.value, e.p.eliminatedRound)
        }
    }
    fun snapshot() = MatchSnapshot(revision, phase, round,
        if (phase == Phase.BATTLE) (Catalog.BATTLE_LIMIT - (battles.maxOfOrNull { it.elapsed } ?: 0f)).coerceAtLeast(0f) else timer,
        elapsed, players.map { it.snapshot() }, battles.map { it.snapshot() }, watchedBattle,
        ranking(), results.toList(), message, paused, autoPlayHuman)
    private fun success(text: String): ActionResult { message = text; revision++; return ActionResult(true, text) }
    private fun fail(text: String): ActionResult { message = text; revision++; return ActionResult(false, text) }
}
