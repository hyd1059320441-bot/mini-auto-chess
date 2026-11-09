package com.pico.miniautochess.rules

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Army(val playerId: Int, val units: List<UnitSnapshot>, val ghost: Boolean = false)
enum class BattleOutcome { LEFT, RIGHT, DRAW }
enum class CombatEventKind { HIT, SHOT, CAST, HEAL, BLOCK, FALL }
data class CombatEvent(val sequence: Int, val kind: CombatEventKind, val actor: Int, val target: Int, val amount: Int,
    val actionId: Int = sequence,
    val fromX: Float = Float.NaN, val fromY: Float = Float.NaN,
    val toX: Float = Float.NaN, val toY: Float = Float.NaN, val time: Float = 0f)
data class FighterSnapshot(
    val uid: Int, val heroId: String, val star: Int, val team: Int,
    val x: Float, val y: Float, val health: Float, val maxHealth: Float,
    val shield: Float, val mana: Float, val targetUid: Int?, val actionTime: Float,
    val attacks: Int = 0, val skills: Int = 0, val maxBlockedSeconds: Float = 0f,
    val firstAttackTime: Float? = null,
    val lastAttackTime: Float = -10f, val lastCastTime: Float = -10f,
)
data class BattleSnapshot(
    val id: Int, val left: Army, val right: Army, val elapsed: Float,
    val fighters: List<FighterSnapshot>, val outcome: BattleOutcome?, val events: List<CombatEvent>,
    val wallElapsed: Float = elapsed, val speedMultiplier: Int = 1,
) {
    val overtime get() = speedMultiplier > 1
    val label get() = "${if (left.playerId == 0) "你" else "棋手 ${left.playerId}"}  vs  ${if (right.ghost) "棋手 ${right.playerId} 的镜像" else if (right.playerId == 0) "你" else "棋手 ${right.playerId}"}"
}

/** Fixed-step simultaneous intents. Rendering and spectating never mutate this object. */
class Battle(val id: Int, val left: Army, val right: Army, val limit: Float = Catalog.BATTLE_LIMIT) {
    private class Fighter(val unit: UnitSnapshot, val team: Int, army: Army) {
        val def = unit.hero
        private val stats = CombatMath.stats(unit, army.units)
        val maxHealth = stats.maxHealth
        val damage = stats.attack
        val armor = stats.armor
        val magicResist = stats.magicResist
        val interval = stats.interval
        val spell = stats.spell
        val regen = stats.regen
        var health = maxHealth
        var shield = 0f
        var mana = stats.initialMana
        // Player preparation slots: row zero is the front line.
        var x = (if (team == 0) unit.slot % Catalog.BOARD_COLS else Catalog.BOARD_COLS - 1 - unit.slot % Catalog.BOARD_COLS).toFloat()
        var y = (if (team == 0) Catalog.BOARD_ROWS - 1 - unit.slot / Catalog.BOARD_COLS
            else Catalog.BOARD_ROWS + unit.slot / Catalog.BOARD_COLS).toFloat()
        var cooldown = .2f
        var target: Int? = null
        var actionTime = -10f
        var lastAttackTime = -10f
        var lastCastTime = -10f
        var attacks = 0
        var skills = 0
        var firstAttackTime: Float? = null
        var blockedSeconds = 0f
        var maxBlockedSeconds = 0f
        var path = emptyList<Pair<Float, Float>>()
        var nextPlan = 0f
        val alive get() = health > 0f
        fun snapshot() = FighterSnapshot(unit.uid, unit.heroId, unit.star, team, x, y,
            health, maxHealth, shield, mana, target, actionTime,
            attacks, skills, maxBlockedSeconds, firstAttackTime, lastAttackTime, lastCastTime)
    }
    private data class Intent(val source: Fighter, val target: Fighter, val amount: Float, val magic: Boolean,
        val kind: CombatEventKind, val actionId: Int)
    private val fighters = left.units.map { Fighter(it, 0, left) } + right.units.map { Fighter(it, 1, right) }
    var elapsed = 0f
        private set
    var wallElapsed = 0f
        private set
    val speedMultiplier get() = if (wallElapsed + .001f >= limit) Catalog.OVERTIME_SPEED else 1
    private val recoveryMultiplier get() = if (speedMultiplier > 1) Catalog.OVERTIME_RECOVERY else 1f
    var outcome: BattleOutcome? = null
        private set
    private var sequence = 0
    private var actionSequence = 0
    private val events = ArrayDeque<CombatEvent>()
    init { resolveIfFinished() }
    private fun distance(a: Fighter, b: Fighter) = hypot(a.x - b.x, a.y - b.y)
    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val t = if (dx * dx + dy * dy < .000001f) 0f
            else (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(px - ax - dx * t, py - ay - dy * t)
    }
    private fun attackLine(a: Fighter, b: Fighter, alive: List<Fighter>, x: Float = a.x, y: Float = a.y) =
        a.def.range > 2f || alive.none { it !== a && it !== b &&
            segmentDistance(it.x, it.y, x, y, b.x, b.y) < .29f }
    private fun clearMove(a: Fighter, x: Float, y: Float, nx: Float, ny: Float, alive: List<Fighter>) =
        nx in 0f..7f && ny in 0f..7f && alive.none { it !== a &&
            segmentDistance(it.x, it.y, x, y, nx, ny) < CLEARANCE }

    /**
     * Half-cell A* to any reachable attack position. Every edge checks swept body
     * clearance, so a rear fighter goes around blockers instead of through them.
     */
    private fun plan(a: Fighter, enemies: List<Fighter>, alive: List<Fighter>): Pair<Fighter, List<Pair<Float, Float>>>? {
        val side = 15
        val start = side * side
        fun point(node: Int) = if (node == start) a.x to a.y else (node % side * .5f) to (node / side * .5f)
        fun heuristic(x: Float, y: Float) = enemies.minOf { (hypot(x - it.x, y - it.y) - a.def.range + .06f).coerceAtLeast(0f) }
        val cost = FloatArray(start + 1) { Float.POSITIVE_INFINITY }
        val prev = IntArray(start + 1) { -1 }
        val closed = BooleanArray(start + 1)
        val open = mutableSetOf(start)
        cost[start] = 0f
        while (open.isNotEmpty()) {
            val node = open.minWith(compareBy<Int> { val p = point(it); cost[it] + heuristic(p.first, p.second) }.thenBy { it })
            open.remove(node); closed[node] = true
            val (x, y) = point(node)
            val target = enemies.firstOrNull { hypot(x - it.x, y - it.y) <= a.def.range - .045f &&
                attackLine(a, it, alive, x, y) }
            if (target != null) {
                val path = mutableListOf<Pair<Float, Float>>()
                var n = node
                while (n != start) { path.add(point(n)); n = prev[n] }
                return target to path.asReversed()
            }
            val neighbors = if (node == start) (0 until start).filter {
                val p = point(it); hypot(x - p.first, y - p.second) <= .76f
            } else (-1..1).flatMap { dy -> (-1..1).mapNotNull { dx ->
                val col = node % side + dx; val row = node / side + dy
                if ((dx != 0 || dy != 0) && col in 0 until side && row in 0 until side) row * side + col else null
            } }
            for (next in neighbors) {
                if (closed[next]) continue
                val p = point(next)
                if (!clearMove(a, x, y, p.first, p.second, alive)) continue
                val c = cost[node] + hypot(x - p.first, y - p.second)
                if (c < cost[next]) { cost[next] = c; prev[next] = node; open.add(next) }
            }
        }
        return null
    }
    private fun event(kind: CombatEventKind, a: Fighter, b: Fighter, amount: Float, actionId: Int = ++actionSequence) {
        events.addLast(CombatEvent(++sequence, kind, a.unit.uid, b.unit.uid, amount.toInt(), actionId,
            a.x, a.y, b.x, b.y, elapsed))
        while (events.size > 128) events.removeFirst()
    }
    private fun cast(a: Fighter, b: Fighter, enemies: List<Fighter>, alive: List<Fighter>, intents: MutableList<Intent>): Boolean {
        if (a.mana < Catalog.MAX_MANA) return false
        val ranged = enemies.filter { distance(a, it) <= a.def.range + .015f }
        if (a.def.skill in listOf(Skill.BOLT, Skill.NOVA, Skill.VOLLEY) && ranged.isEmpty()) return false
        if (a.def.skill == Skill.CLEAVE && enemies.none { distance(a, it) <= 1.8f }) return false
        a.mana = 0f
        a.skills++
        a.actionTime = elapsed
        a.lastCastTime = elapsed
        val actionId = ++actionSequence
        a.cooldown = max(a.cooldown, .35f)
        when (a.def.skill) {
            Skill.HEAL -> {
                val friend = alive.filter { it.team == a.team }
                    .minWith(compareBy<Fighter> { it.health / it.maxHealth }.thenBy { it.unit.uid })
                intents.add(Intent(a, friend, a.spell, true, CombatEventKind.HEAL, actionId))
            }
            Skill.SHIELD -> {
                val amount = a.spell * recoveryMultiplier
                a.shield = CombatMath.addShield(a.shield, amount, a.maxHealth)
                event(CombatEventKind.BLOCK, a, a, amount, actionId)
                enemies.filter { distance(a, it) <= 1.5f }.forEach {
                    intents.add(Intent(a, it, a.spell * .35f, true, CombatEventKind.CAST, actionId))
                }
            }
            Skill.VOLLEY -> ranged.take(2).forEach { intents.add(Intent(a, it, a.spell, false, CombatEventKind.CAST, actionId)) }
            Skill.BOLT -> intents.add(Intent(a, ranged.first(), a.spell, true, CombatEventKind.CAST, actionId))
            Skill.CLEAVE -> enemies.filter { distance(a, it) <= 1.8f }.forEach {
                intents.add(Intent(a, it, a.spell, false, CombatEventKind.CAST, actionId))
            }
            Skill.NOVA -> {
                val center = ranged.firstOrNull { it === b } ?: ranged.first()
                enemies.filter { distance(center, it) <= 1.6f }.forEach {
                    intents.add(Intent(a, it, a.spell * if (it === center) 1f else Catalog.NOVA_SPLASH,
                        true, CombatEventKind.CAST, actionId))
                }
            }
        }
        return true
    }
    fun step() {
        if (outcome != null) return
        // One step is always .1s of foreground playing time. Combat ticks
        // retain their fixed duration, including cooldowns, movement and mana.
        val speed = speedMultiplier
        wallElapsed += Catalog.STEP
        repeat(speed) { if (outcome == null) combatTick() }
        if (outcome == null && wallElapsed + .001f >= limit + Catalog.OVERTIME_SECONDS) {
            val a = fighters.filter { it.team == 0 && it.alive }
            val b = fighters.filter { it.team == 1 && it.alive }
            outcome = compareAtTimeout(a.size, b.size, a.sumOf { (it.health / it.maxHealth).toDouble() },
                b.sumOf { (it.health / it.maxHealth).toDouble() })
        }
    }
    private fun combatTick() {
        elapsed += Catalog.STEP
        val alive = fighters.filter { it.alive }
        val intents = mutableListOf<Intent>()
        val movements = mutableListOf<Triple<Fighter, Float, Float>>()
        for (a in alive) {
            a.health = CombatMath.regenerate(a.health, a.maxHealth, a.regen * recoveryMultiplier, Catalog.STEP)
            a.mana = min(Catalog.MAX_MANA, a.mana + Catalog.manaPerSecond(a.def.job) * Catalog.STEP)
            // Preserve sub-tick overshoot so e.g. +30% attack speed remains +30%.
            // Idle/movement cannot bank missed attacks and cause a later burst.
            a.cooldown = max(-Catalog.STEP, a.cooldown - Catalog.STEP)
            val enemies = alive.filter { it.team != a.team }.sortedWith(compareBy<Fighter> { distance(a, it) }.thenBy { it.unit.uid })
            var b = enemies.firstOrNull { distance(a, it) <= a.def.range + .015f && attackLine(a, it, alive) }
                ?: enemies.firstOrNull { it.unit.uid == a.target } ?: enemies.firstOrNull() ?: continue
            a.target = b.unit.uid
            // Full mana from incoming damage or regeneration casts without waiting for a basic attack.
            if (cast(a, b, enemies, alive, intents)) {
                a.path = emptyList(); a.blockedSeconds = 0f
                continue
            }
            val d = distance(a, b)
            if (d > a.def.range + .015f || !attackLine(a, b, alive)) {
                val amount = min(1.5f * Catalog.STEP, (d - a.def.range + .06f).coerceAtLeast(.06f))
                val directX = a.x + (b.x - a.x) / d * amount
                val directY = a.y + (b.y - a.y) / d * amount
                val approachX = a.x + (b.x - a.x) / d * (d - a.def.range + .06f).coerceAtLeast(0f)
                val approachY = a.y + (b.y - a.y) / d * (d - a.def.range + .06f).coerceAtLeast(0f)
                var move: Pair<Float, Float>? = null
                if (attackLine(a, b, alive) && clearMove(a, a.x, a.y, approachX, approachY, alive)) {
                    move = directX to directY
                    a.path = emptyList()
                } else {
                    while (a.path.isNotEmpty() && hypot(a.x - a.path[0].first, a.y - a.path[0].second) < .025f)
                        a.path = a.path.drop(1)
                    if (elapsed >= a.nextPlan || a.path.isEmpty()) {
                        val route = plan(a, enemies, alive)
                        a.nextPlan = elapsed + .6f
                        a.path = route?.second.orEmpty()
                        route?.let { b = it.first; a.target = b.unit.uid }
                    }
                    a.path.firstOrNull()?.let { (x, y) ->
                        val gap = hypot(x - a.x, y - a.y)
                        val step = min(.15f, gap)
                        if (gap > .0001f) {
                            val nx = a.x + (x - a.x) / gap * step
                            val ny = a.y + (y - a.y) / gap * step
                            if (clearMove(a, a.x, a.y, nx, ny, alive)) move = nx to ny
                            else { a.path = emptyList(); a.nextPlan = elapsed }
                        }
                    }
                }
                if (move != null) {
                    movements.add(Triple(a, move.first, move.second)); a.blockedSeconds = 0f
                } else {
                    a.blockedSeconds += Catalog.STEP
                    a.maxBlockedSeconds = max(a.maxBlockedSeconds, a.blockedSeconds)
                }
            } else if (a.cooldown <= 0f) {
                a.blockedSeconds = 0f; a.path = emptyList()
                a.cooldown += a.interval
                a.actionTime = elapsed
                a.lastAttackTime = elapsed
                a.attacks++
                if (a.firstAttackTime == null) a.firstAttackTime = elapsed
                a.mana = min(Catalog.MAX_MANA, a.mana + Catalog.manaOnAttack(a.def.job))
                intents.add(Intent(a, b, a.damage, false, if (a.def.range > 2f) CombatEventKind.SHOT else CombatEventKind.HIT, ++actionSequence))
            }
        }
        // Movement resolves together; separation prevents same-side pawns occupying one point.
        movements.forEach { (f, x, y) ->
            f.x = x.coerceIn(0f, (Catalog.BOARD_COLS - 1).toFloat())
            f.y = y.coerceIn(0f, (Catalog.BATTLE_ROWS - 1).toFloat())
        }
        for (i in alive.indices) for (j in i + 1 until alive.size) {
            val a = alive[i]; val b = alive[j]
            val d = distance(a, b)
            if (d < .62f) {
                val dx = if (d < .001f) (if (i % 2 == 0) 1f else -1f) else (a.x - b.x) / d
                val dy = if (d < .001f) 0f else (a.y - b.y) / d
                val push = (.62f - d) * .5f
                a.x = (a.x + dx * push).coerceIn(0f, (Catalog.BOARD_COLS - 1).toFloat())
                a.y = (a.y + dy * push).coerceIn(0f, (Catalog.BATTLE_ROWS - 1).toFloat())
                b.x = (b.x - dx * push).coerceIn(0f, (Catalog.BOARD_COLS - 1).toFloat())
                b.y = (b.y + dy * push).coerceIn(0f, (Catalog.BATTLE_ROWS - 1).toFloat())
            }
        }
        val damage = mutableMapOf<Fighter, Float>()
        val healing = mutableMapOf<Fighter, Float>()
        intents.forEach { intent ->
            val raw = when {
                intent.kind == CombatEventKind.HEAL -> intent.amount * recoveryMultiplier
                intent.magic -> CombatMath.magicDamage(intent.amount, intent.target.magicResist)
                else -> CombatMath.physicalDamage(intent.amount, intent.target.armor)
            }
            val table = if (intent.kind == CombatEventKind.HEAL) healing else damage
            table[intent.target] = (table[intent.target] ?: 0f) + raw
            event(intent.kind, intent.source, intent.target, raw, intent.actionId)
        }
        alive.forEach { f ->
            val total = damage[f] ?: 0f
            val life = CombatMath.resolveLife(f.health, f.maxHealth, f.shield, total, healing[f] ?: 0f)
            f.shield = life.shield
            f.health = life.health
            if (total > 0 && f.alive) f.mana = min(Catalog.MAX_MANA, f.mana + Catalog.manaOnHit(f.def.job))
            if (!f.alive) event(CombatEventKind.FALL, f, f, 0f)
        }
        resolveIfFinished()
    }
    private fun resolveIfFinished() {
        val a = fighters.filter { it.team == 0 && it.alive }
        val b = fighters.filter { it.team == 1 && it.alive }
        outcome = when {
            a.isEmpty() && b.isEmpty() -> BattleOutcome.DRAW
            a.isEmpty() -> BattleOutcome.RIGHT
            b.isEmpty() -> BattleOutcome.LEFT
            else -> null
        }
    }
    fun snapshot() = BattleSnapshot(id, left, right, elapsed, fighters.map { it.snapshot() }, outcome, events.toList(),
        wallElapsed, speedMultiplier)
    fun survivors(team: Int) = fighters.count { it.team == team && it.alive }
    companion object {
        private const val CLEARANCE = .605f
        fun compareAtTimeout(leftCount: Int, rightCount: Int, leftRatio: Double, rightRatio: Double) = when {
            leftCount > rightCount -> BattleOutcome.LEFT
            rightCount > leftCount -> BattleOutcome.RIGHT
            abs(leftRatio - rightRatio) < .0001 -> BattleOutcome.DRAW
            leftRatio > rightRatio -> BattleOutcome.LEFT
            else -> BattleOutcome.RIGHT
        }
    }
}
