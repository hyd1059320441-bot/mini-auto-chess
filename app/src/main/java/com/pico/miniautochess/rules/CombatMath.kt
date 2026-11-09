package com.pico.miniautochess.rules

import kotlin.math.min

data class CombatStats(
    val maxHealth: Float, val attack: Float, val armor: Float, val interval: Float,
    val spell: Float, val regen: Float, val initialMana: Float,
    val magicResist: Float = 0f,
)
data class LifeResult(val health: Float, val shield: Float)
data class PlayerDamage(val base: Int, val population: Int, val actual: Int) {
    val nominal get() = base + population
}

/** Pure rules used by Battle and the round ledger, with no render-side modifiers. */
object CombatMath {
    fun stats(unit: UnitSnapshot, army: List<UnitSnapshot>): CombatStats {
        val tiers = traits(army).associate { it.label to it.tier }
        return statsWithTiers(unit, tiers)
    }
    internal fun statsWithTiers(unit: UnitSnapshot, tiers: Map<String, Int>): CombatStats {
        val hero = unit.hero
        val faction = tiers.getValue(hero.faction.label)
        val job = tiers.getValue(hero.job.label)
        val factor = Catalog.starMultiplier(unit.star)
        return CombatStats(
            hero.health * factor * if (hero.faction == Faction.DAWN) 1f + .15f * faction else 1f,
            hero.attack * factor * if (hero.faction == Faction.EMBER) 1f + .15f * faction else 1f,
            hero.armor + if (hero.job == Job.GUARD) listOf(0f, 8f, 20f, 36f)[job] else 0f,
            hero.interval / if (hero.job == Job.ARCHER) 1f + listOf(0f, .10f, .25f, .40f)[job] else 1f,
            hero.skillPower(unit.star) * (if (hero.faction == Faction.RUNE) 1f + .2f * faction else 1f) *
                when (hero.job) {
                    Job.SUPPORT -> 1f + listOf(0f, .15f, .40f)[job]
                    Job.MAGE -> 1f + listOf(0f, .10f, .20f, .35f)[job]
                    else -> 1f
                },
            if (hero.faction == Faction.FOREST) .01f * faction else 0f,
            if (hero.job == Job.MAGE) listOf(0f, 20f, 30f, 40f)[job] else 0f,
            hero.magicResist + (if (hero.job == Job.GUARD) listOf(0f, 8f, 20f, 36f)[job] else 0f) +
                listOf(0f, 10f, 25f)[tiers.getValue(Job.SUPPORT.label)],
        )
    }

    fun physicalDamage(attack: Float, armor: Float): Float {
        require(attack.isFinite() && armor.isFinite() && attack >= 0 && armor >= 0)
        return attack * (100f / (100f + armor))
    }
    fun magicDamage(power: Float, resistance: Float): Float = physicalDamage(power, resistance)

    fun regenerate(health: Float, maxHealth: Float, rate: Float, seconds: Float): Float =
        if (health <= 0f) 0f else min(maxHealth, health + maxHealth * rate * seconds)

    /** Intents for units alive at tick start resolve together; previous deaths cannot revive. */
    fun resolveLife(health: Float, maxHealth: Float, shield: Float, damage: Float, healing: Float): LifeResult {
        require(maxHealth > 0 && shield >= 0 && damage >= 0 && healing >= 0)
        if (health <= 0) return LifeResult(0f, shield)
        val absorbed = min(shield, damage)
        return LifeResult((health - damage + absorbed + healing).coerceIn(0f, maxHealth), shield - absorbed)
    }

    fun addShield(shield: Float, amount: Float, maxHealth: Float) = min(maxHealth, shield + amount)

    fun playerDamage(round: Int, survivors: Int, health: Int): PlayerDamage {
        require(survivors in 0..Catalog.MAX_LEVEL && health >= 0)
        val base = Catalog.roundDamage(round)
        return PlayerDamage(base, survivors, min(health, base + survivors))
    }
}
