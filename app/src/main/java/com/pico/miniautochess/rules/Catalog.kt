package com.pico.miniautochess.rules

enum class Faction(val label: String) { DAWN("曙光"), FOREST("林地"), RUNE("符文"), EMBER("烬火") }
enum class Job(val label: String) { GUARD("卫士"), ARCHER("射手"), MAGE("法师"), SUPPORT("辅助") }
enum class Skill { SHIELD, VOLLEY, BOLT, HEAL, CLEAVE, NOVA }

data class HeroDef(
    val id: String, val name: String, val faction: Faction, val job: Job, val cost: Int,
    val health: Float, val attack: Float, val armor: Float, val interval: Float, val range: Float,
    val skill: Skill, val skillName: String, val skillDescription: String, val power: Float,
    val skillPowers: List<Float> = listOf(power, power * 1.8f, power * 3.24f),
    val magicResist: Float = 20f,
) {
    init {
        require(skillPowers.size == 3 && skillPowers.first() == power)
        require(skillPowers.all { it.isFinite() && it > 0f } && skillPowers.zipWithNext().all { it.second > it.first })
        require(magicResist.isFinite() && magicResist >= 0f)
    }
    fun skillPower(star: Int): Float {
        require(star in 1..3)
        return skillPowers[star - 1]
    }
}

/** The only source of hero, economy, trait and timing values, shared by AI and UI. */
object Catalog {
    const val START_HEALTH = 32
    const val START_GOLD = 10
    const val MAX_LEVEL = 7
    const val BOARD_COLS = 8
    const val BOARD_ROWS = 4
    const val BATTLE_ROWS = BOARD_ROWS * 2
    const val BOARD_SIZE = BOARD_COLS * BOARD_ROWS
    const val BENCH_SIZE = 8
    const val SHOP_SIZE = 5
    const val REFRESH_COST = 2
    const val XP_COST = 4
    const val XP_GAIN = 4
    const val ROUND_XP = 2
    const val BASE_INCOME = 5
    const val BATTLE_LIMIT = 25f
    const val OVERTIME_SECONDS = 15f
    const val OVERTIME_SPEED = 2
    const val STEP = 0.1f
    const val TRANSITION_SECONDS = 5f
    val levelXp = listOf(2, 4, 6, 8, 10, 12)
    val baseDamage = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5)
    val heroList = listOf(
        HeroDef("copper_guard", "铜盾卫", Faction.DAWN, Job.GUARD, 1, 760f, 50f, 24f, 1.2f, 1.2f, Skill.SHIELD, "铜盾壁垒", "获得护盾，并反击邻近敌人", 220f),
        HeroDef("forest_ranger", "林间游侠", Faction.FOREST, Job.ARCHER, 1, 440f, 64f, 8f, 1f, 3.8f, Skill.VOLLEY, "穿林箭", "向最接近的两名敌人射出强化箭", 140f),
        HeroDef("rune_apprentice", "符灯学徒", Faction.RUNE, Job.MAGE, 1, 420f, 46f, 6f, 1.2f, 3.5f, Skill.BOLT, "符灯闪击", "对单个敌人造成法术伤害", 300f, listOf(300f, 450f, 675f)),
        HeroDef("ember_acolyte", "烬火侍者", Faction.EMBER, Job.SUPPORT, 1, 510f, 48f, 10f, 1.2f, 2.8f, Skill.HEAL, "温火续生", "治疗生命比例最低的友军", 250f),
        HeroDef("dawn_priest", "曙光祭司", Faction.DAWN, Job.SUPPORT, 2, 580f, 52f, 12f, 1.15f, 3f, Skill.HEAL, "曙光祈愿", "治疗生命比例最低的友军", 360f),
        HeroDef("moss_guard", "苔木守卫", Faction.FOREST, Job.GUARD, 2, 900f, 58f, 28f, 1.3f, 1.2f, Skill.SHIELD, "苔木屏障", "获得护盾，并反击邻近敌人", 340f),
        HeroDef("rune_archer", "符弩射手", Faction.RUNE, Job.ARCHER, 2, 540f, 78f, 10f, 1.1f, 4f, Skill.VOLLEY, "符弩齐射", "向最接近的两名敌人射出强化箭", 220f),
        HeroDef("flame_fighter", "焰刃斗士", Faction.EMBER, Job.GUARD, 2, 820f, 78f, 20f, 1.1f, 1.3f, Skill.CLEAVE, "焰刃横扫", "对身旁所有敌人造成伤害", 260f),
        HeroDef("dawn_archer", "曜羽弓手", Faction.DAWN, Job.ARCHER, 3, 620f, 96f, 12f, .9f, 4.2f, Skill.VOLLEY, "曜羽连射", "向最接近的两名敌人射出强化箭", 290f),
        HeroDef("forest_blessing", "森灵祝祷", Faction.FOREST, Job.SUPPORT, 3, 680f, 64f, 14f, 1.05f, 3.2f, Skill.HEAL, "森灵祝福", "治疗生命比例最低的友军", 480f),
        HeroDef("rune_sage", "星符贤者", Faction.RUNE, Job.MAGE, 3, 580f, 65f, 8f, 1.1f, 3.8f, Skill.NOVA, "星符涟漪", "对目标附近的敌人造成范围法术伤害", 360f, listOf(360f, 540f, 810f)),
        HeroDef("core_mage", "熔核术师", Faction.EMBER, Job.MAGE, 3, 650f, 68f, 12f, 1.2f, 3.6f, Skill.NOVA, "熔核爆发", "对目标附近的敌人造成范围法术伤害", 400f, listOf(400f, 600f, 900f)),
        HeroDef("dawn_oracle", "晨钟先知", Faction.DAWN, Job.MAGE, 2, 500f, 50f, 10f, 1.2f, 3.5f, Skill.BOLT, "晨钟裁决", "以金色光束打击单个敌人", 360f, listOf(360f, 540f, 810f)),
        HeroDef("forest_witch", "藤冠巫女", Faction.FOREST, Job.MAGE, 2, 520f, 48f, 9f, 1.25f, 3.4f, Skill.NOVA, "荆棘绽放", "对目标周围敌人造成范围法术伤害", 250f, listOf(250f, 375f, 565f)),
        HeroDef("rune_sentinel", "晶碑守望", Faction.RUNE, Job.GUARD, 3, 1040f, 70f, 30f, 1.3f, 1.2f, Skill.SHIELD, "晶碑结界", "获得护盾，并反击邻近敌人", 450f),
        HeroDef("ember_breaker", "赤锤破阵", Faction.EMBER, Job.GUARD, 1, 700f, 58f, 18f, 1.35f, 1.3f, Skill.CLEAVE, "赤锤震击", "对身旁所有敌人造成物理伤害", 190f),
    )
    val heroes = heroList.associateBy { it.id }
    fun hero(id: String) = heroes.getValue(id)
    fun poolSize(cost: Int) = when (cost) { 1 -> 26; 2 -> 20; else -> 14 }
    /** TFT-style level odds; its 4/5-cost weights join this game's highest, 3-cost tier. */
    fun odds(level: Int): List<Int> {
        require(level in 1..MAX_LEVEL)
        return when (level) {
            1, 2 -> listOf(100, 0, 0)
            3 -> listOf(75, 25, 0)
            4 -> listOf(55, 30, 15)
            5 -> listOf(45, 33, 22)
            6 -> listOf(30, 40, 30)
            else -> listOf(16, 30, 54)
        }
    }
    /** Empty cost pools lose their weight; zero-probability tiers never enter the draw. */
    fun availableWeights(level: Int, pool: Map<String, Int>): List<Int> =
        odds(level).mapIndexed { index, weight ->
            if (heroList.any { it.cost == index + 1 && pool.getValue(it.id) > 0 }) weight else 0
        }
    fun preparationSeconds(round: Int) = if (round == 1) 30f else 36f
    fun interest(gold: Int) = (gold / 10).coerceAtLeast(0)
    /** Identical win/loss bonuses, paid at round settlement after the interest snapshot. */
    fun streakGold(streak: Int) = when (kotlin.math.abs(streak.toLong())) {
        0L, 1L -> 0
        2L, 3L -> 1
        4L -> 2
        else -> 3
    }
    fun streakLabel(streak: Int) = if (streak > 0) "连胜${streak}场"
        else if (streak < 0) "连败${-streak}场" else "无连胜连败"
    fun copies(star: Int) = when (star) { 1 -> 1; 2 -> 3; 3 -> 9; else -> error("Invalid star") }
    fun starMultiplier(star: Int) = when (star) { 1 -> 1f; 2 -> 1.8f; 3 -> 3.24f; else -> error("Invalid star") }
    fun roundDamage(round: Int): Int {
        require(round >= 1)
        return baseDamage.getOrElse(round - 1) { 6 + (round - 11) / 2 }
    }
    val traitDefinitions = listOf(
        TraitDef("曙光", listOf(2, 4), listOf("曙光生命 +15%", "曙光生命 +30%")),
        TraitDef("林地", listOf(2, 4), listOf("林地每秒回血 1%", "林地每秒回血 2%")),
        TraitDef("符文", listOf(2, 4), listOf("符文技能 +20%", "符文技能 +40%")),
        TraitDef("烬火", listOf(2, 4), listOf("烬火攻击 +15%", "烬火攻击 +30%")),
        TraitDef("卫士", listOf(1, 3, 5), listOf("卫士护甲/魔抗 +8", "卫士护甲/魔抗 +20", "卫士护甲/魔抗 +36")),
        TraitDef("射手", listOf(1, 2, 3), listOf("射手攻速 +10%", "射手攻速 +25%", "射手攻速 +40%")),
        TraitDef("法师", listOf(1, 3, 5), listOf("法术+10% · 起蓝20", "法术+20% · 起蓝30", "法术+35% · 起蓝40")),
        TraitDef("辅助", listOf(1, 3), listOf("辅助治疗+15% · 全队魔抗+10", "辅助治疗+40% · 全队魔抗+25")),
    )
    fun traitDescriptions() = traitDefinitions.map { "${it.label} ${it.thresholds.joinToString("/")}" to it.effects.joinToString(" / ") }
    fun manaOnAttack(job: Job) = when (job) { Job.GUARD -> 20f; Job.ARCHER -> 22f; Job.MAGE -> 30f; Job.SUPPORT -> 25f }
    fun manaOnHit(job: Job) = if (job == Job.GUARD) 12f else 8f
    fun manaPerSecond(job: Job) = when (job) { Job.MAGE -> 4f; Job.SUPPORT -> 3f; else -> 0f }
    const val MAX_MANA = 100f
    const val NOVA_SPLASH = .65f
    const val OVERTIME_RECOVERY = .5f
}

data class TraitDef(val label: String, val thresholds: List<Int>, val effects: List<String>)
data class UnitSnapshot(
    val uid: Int, val heroId: String, val star: Int, val slot: Int,
) {
    val hero get() = Catalog.hero(heroId)
    val onBoard get() = slot >= 0
    val value get() = hero.cost * Catalog.copies(star)
}

data class TraitSnapshot(val label: String, val count: Int, val tier: Int) {
    private val definition get() = Catalog.traitDefinitions.first { it.label == label }
    val thresholds get() = definition.thresholds
    val threshold get() = thresholds.getOrElse(tier) { thresholds.last() }
    val effect get() = if (tier == 0) "未激活 · 差${threshold - count}位" else definition.effects[tier - 1]
    val progress get() = thresholds.joinToString("/") { if (count >= it) "[$it]" else "$it" }
}
fun traits(units: List<UnitSnapshot>): List<TraitSnapshot> {
    val unique = units.filter { it.onBoard }.distinctBy { it.heroId }.map { it.hero }
    return Faction.entries.map { f ->
        val n = unique.count { it.faction == f }
        TraitSnapshot(f.label, n, Catalog.traitDefinitions.first { it.label == f.label }.thresholds.count { n >= it })
    } + Job.entries.map { job ->
        val n = unique.count { it.job == job }
        TraitSnapshot(job.label, n, Catalog.traitDefinitions.first { it.label == job.label }.thresholds.count { n >= it })
    }
}
