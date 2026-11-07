package com.pico.miniautochess.rules

import kotlin.math.min

data class CombatStats(
    val maxHealth: Float, val attack: Float, val armor: Float, val interval: Float,
    val spell: Float, val regen: Float, val initialMana: Float,
)
data class LifeResult(val health: Float, val shield: Float)
data class PlayerDamage(val base: Int, val population: Int, val actual: Int) {
    val nominal get() = base + population
}

/** Pure rules used by Battle and the round ledger, with no render-side modifiers. */
object CombatMath {
    fun stats(unit: UnitSnapshot, army: List<UnitSnapshot>): CombatStats {
        val hero = unit.hero
        val tiers = traits(army).associate { it.label to it.tier }
        val faction = tiers.getValue(hero.faction.label)
        val job = tiers.getValue(hero.job.label)
        val factor = Catalog.starMultiplier(unit.star)
        return CombatStats(
            hero.health * factor * if (hero.faction == Faction.DAWN) 1f + .15f * faction else 1f,
            hero.attack * factor * if (hero.faction == Faction.EMBER) 1f + .15f * faction else 1f,
            hero.armor + if (hero.job == Job.GUARD) 12f * job else 0f,
            hero.interval / if (hero.job == Job.ARCHER) 1f + .15f * job else 1f,
            hero.power * factor * (if (hero.faction == Faction.RUNE) 1f + .2f * faction else 1f) *
                if (hero.job == Job.SUPPORT && hero.skill == Skill.HEAL) 1f + .2f * job else 1f,
            if (hero.faction == Faction.FOREST) .01f * faction else 0f,
            if (hero.job == Job.MAGE) 20f * job else 0f,
        )
    }

    fun physicalDamage(attack: Float, armor: Float): Float {
        require(attack.isFinite() && armor.isFinite() && attack >= 0 && armor >= 0)
        return attack * (100f / (100f + armor))
    }

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
