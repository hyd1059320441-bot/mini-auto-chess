package com.pico.miniautochess.rules

enum class Faction(val label: String) { DAWN("曙光"), FOREST("林地"), RUNE("符文"), EMBER("烬火") }
enum class Job(val label: String) { GUARD("卫士"), ARCHER("射手"), MAGE("法师"), SUPPORT("辅助") }
enum class Skill { SHIELD, VOLLEY, BOLT, HEAL, CLEAVE, NOVA }

data class HeroDef(
    val id: String, val name: String, val faction: Faction, val job: Job, val cost: Int,
    val health: Float, val attack: Float, val armor: Float, val interval: Float, val range: Float,
    val skill: Skill, val skillName: String, val skillDescription: String, val power: Float,
)

/** The only source of hero, economy, trait and timing values, shared by AI and UI. */
object Catalog {
    const val START_HEALTH = 32
    const val START_GOLD = 10
    const val MAX_LEVEL = 7
    const val MAX_ROUNDS = 10
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
    const val STEP = 0.1f
    const val TRANSITION_SECONDS = 5f
    val levelXp = listOf(2, 4, 6, 8, 10, 12)
    val baseDamage = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5)
    val heroList = listOf(
        HeroDef("copper_guard", "铜盾卫", Faction.DAWN, Job.GUARD, 1, 760f, 50f, 24f, 1.2f, 1.2f, Skill.SHIELD, "铜盾壁垒", "获得护盾，并反击邻近敌人", 220f),
        HeroDef("forest_ranger", "林间游侠", Faction.FOREST, Job.ARCHER, 1, 440f, 64f, 8f, 1f, 3.8f, Skill.VOLLEY, "穿林箭", "向最接近的两名敌人射出强化箭", 140f),
        HeroDef("rune_apprentice", "符灯学徒", Faction.RUNE, Job.MAGE, 1, 420f, 46f, 6f, 1.2f, 3.5f, Skill.BOLT, "符灯闪击", "对单个敌人造成法术伤害", 300f),
        HeroDef("ember_acolyte", "烬火侍者", Faction.EMBER, Job.SUPPORT, 1, 510f, 48f, 10f, 1.2f, 2.8f, Skill.HEAL, "温火续生", "治疗生命比例最低的友军", 250f),
        HeroDef("dawn_priest", "曙光祭司", Faction.DAWN, Job.SUPPORT, 2, 580f, 52f, 12f, 1.15f, 3f, Skill.HEAL, "曙光祈愿", "治疗生命比例最低的友军", 360f),
        HeroDef("moss_guard", "苔木守卫", Faction.FOREST, Job.GUARD, 2, 900f, 58f, 28f, 1.3f, 1.2f, Skill.SHIELD, "苔木屏障", "获得护盾，并反击邻近敌人", 340f),
        HeroDef("rune_archer", "符弩射手", Faction.RUNE, Job.ARCHER, 2, 540f, 78f, 10f, 1.1f, 4f, Skill.VOLLEY, "符弩齐射", "向最接近的两名敌人射出强化箭", 220f),
        HeroDef("flame_fighter", "焰刃斗士", Faction.EMBER, Job.GUARD, 2, 820f, 78f, 20f, 1.1f, 1.3f, Skill.CLEAVE, "焰刃横扫", "对身旁所有敌人造成伤害", 260f),
        HeroDef("dawn_archer", "曜羽弓手", Faction.DAWN, Job.ARCHER, 3, 620f, 96f, 12f, .9f, 4.2f, Skill.VOLLEY, "曜羽连射", "向最接近的两名敌人射出强化箭", 290f),
        HeroDef("forest_blessing", "森灵祝祷", Faction.FOREST, Job.SUPPORT, 3, 680f, 64f, 14f, 1.05f, 3.2f, Skill.HEAL, "森灵祝福", "治疗生命比例最低的友军", 480f),
        HeroDef("rune_sage", "星符贤者", Faction.RUNE, Job.MAGE, 3, 580f, 65f, 8f, 1.1f, 3.8f, Skill.NOVA, "星符涟漪", "对目标附近的敌人造成范围法术伤害", 360f),
        HeroDef("core_mage", "熔核术师", Faction.EMBER, Job.MAGE, 3, 650f, 68f, 12f, 1.2f, 3.6f, Skill.NOVA, "熔核爆发", "对目标附近的敌人造成范围法术伤害", 400f),
    )
    val heroes = heroList.associateBy { it.id }
    fun hero(id: String) = heroes.getValue(id)
    fun poolSize(cost: Int) = when (cost) { 1 -> 26; 2 -> 20; else -> 14 }
    fun odds(level: Int): List<Int> = when (level) {
        1, 2, 3 -> listOf(75, 25, 0)
        4 -> listOf(65, 30, 5)
        5 -> listOf(45, 40, 15)
        6 -> listOf(30, 45, 25)
        else -> listOf(20, 40, 40)
    }
    fun preparationSeconds(round: Int) = if (round == 1) 30f else 36f
    fun interest(gold: Int) = (gold / 10).coerceAtLeast(0)
    fun copies(star: Int) = when (star) { 1 -> 1; 2 -> 3; 3 -> 9; else -> error("Invalid star") }
    fun starMultiplier(star: Int) = when (star) { 1 -> 1f; 2 -> 1.8f; 3 -> 3.24f; else -> error("Invalid star") }
    fun roundDamage(round: Int): Int {
        require(round in 1..MAX_ROUNDS)
        return baseDamage[round - 1]
    }
    fun traitDescriptions(): List<Pair<String, String>> = listOf(
        "曙光 2 / 3" to "曙光英雄生命 +15% / +30%",
        "林地 2 / 3" to "林地英雄每秒恢复最大生命 1% / 2%",
        "符文 2 / 3" to "符文英雄技能强度 +20% / +40%",
        "烬火 2 / 3" to "烬火英雄攻击 +15% / +30%",
        "卫士 2 / 3" to "卫士护甲 +12 / +24",
        "射手 2 / 3" to "射手攻击速度 +15% / +30%",
        "法师 2 / 3" to "法师初始法力 +20 / +40",
        "辅助 2 / 3" to "辅助治疗 +20% / +40%",
    )
}

data class UnitSnapshot(
    val uid: Int, val heroId: String, val star: Int, val slot: Int,
) {
    val hero get() = Catalog.hero(heroId)
    val onBoard get() = slot >= 0
    val value get() = hero.cost * Catalog.copies(star)
}

data class TraitSnapshot(val label: String, val count: Int, val tier: Int) {
    val threshold get() = if (tier >= 2) 3 else 2
    val effect get() = if (tier == 0) "未激活 · 差${2 - count}位" else when (label) {
        "曙光" -> "曙光生命 +${15 * tier}%"
        "林地" -> "林地每秒回血 ${tier}%"
        "符文" -> "符文技能 +${20 * tier}%"
        "烬火" -> "烬火攻击 +${15 * tier}%"
        "卫士" -> "卫士护甲 +${12 * tier}"
        "射手" -> "射手攻速 +${15 * tier}%"
        "法师" -> "法师初始法力 +${20 * tier}"
        "辅助" -> "辅助治疗 +${20 * tier}%"
        else -> error("Unknown trait $label")
    }
}
fun traits(units: List<UnitSnapshot>): List<TraitSnapshot> {
    val unique = units.filter { it.onBoard }.distinctBy { it.heroId }.map { it.hero }
    return Faction.entries.map { f ->
        val n = unique.count { it.faction == f }
        TraitSnapshot(f.label, n, if (n >= 3) 2 else if (n >= 2) 1 else 0)
    } + Job.entries.map { job ->
        val n = unique.count { it.job == job }
        TraitSnapshot(job.label, n, if (n >= 3) 2 else if (n >= 2) 1 else 0)
    }
}
