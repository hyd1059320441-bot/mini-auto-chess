package com.pico.miniautochess.rules

import kotlin.math.min
import kotlin.math.sqrt

/**
 * A bounded preparation-time estimate, not an alternate combat simulator.
 * Uses deployed traits and actual star stats; purchase price is never power.
 */
object ArmyEvaluator {
    private const val HORIZON = 12f
    private const val BEAM_WIDTH = 40

    fun score(units: List<UnitSnapshot>): Float {
        if (units.isEmpty()) return 0f
        val army = units.mapIndexed { i, u -> u.copy(slot = i) }
        val tiers = traits(army).associate { it.label to it.tier }
        var durability = 0f
        var damage = 0f
        var healing = 0f
        var frontline = 0f
        for (unit in army) {
            val hero = unit.hero
            val s = CombatMath.statsWithTiers(unit, tiers)
            val manaRate = Catalog.manaOnAttack(hero.job) / s.interval + Catalog.manaPerSecond(hero.job) +
                Catalog.manaOnHit(hero.job) * if (hero.job == Job.GUARD) 1.2f else .25f
            val cycle = Catalog.MAX_MANA / manaRate + .35f
            val casts = (HORIZON * manaRate + s.initialMana) / Catalog.MAX_MANA
            // Fixed reference opponent mix; does not inspect hidden enemy units.
            val reduction = .6f * 100f / (100f + s.armor) + .4f * 100f / (100f + s.magicResist)
            var health = s.maxHealth * (1f + s.regen * HORIZON)
            var dps = CombatMath.physicalDamage(s.attack, 25f) / (s.interval + .35f * s.interval / cycle)
            when (hero.skill) {
                Skill.SHIELD -> {
                    health += s.spell * min(2f, casts)
                    dps += CombatMath.magicDamage(s.spell * .35f * 1.4f, 30f) * casts / HORIZON
                }
                Skill.HEAL -> healing += s.spell * casts / HORIZON * .7f
                Skill.BOLT -> dps += CombatMath.magicDamage(s.spell, 30f) * casts / HORIZON
                Skill.NOVA -> dps += CombatMath.magicDamage(s.spell *
                    min(army.size.toFloat(), 1f + .8f * Catalog.NOVA_SPLASH), 30f) * casts / HORIZON
                Skill.VOLLEY -> dps += CombatMath.physicalDamage(s.spell *
                    min(army.size.toFloat(), 1.7f), 25f) * casts / HORIZON
                Skill.CLEAVE -> dps += CombatMath.physicalDamage(s.spell * 1.6f, 25f) * casts / HORIZON
            }
            durability += health / reduction
            damage += dps
            if (hero.job == Job.GUARD) frontline += health / reduction
        }
        // Healing needs damage to convert time into a win and frontliners to buy
        // that time; a pile of healers cannot score as a pile of damage dealers.
        val usefulHealing = min(healing, damage * .65f) * HORIZON
        val coverage = if (army.size < 3) 1f else .85f + .15f * min(1f, frontline / (durability * .3f))
        return sqrt((durability + usefulHealing) * damage) * coverage
    }

    /** Locked human placements remain fixed; beam search examines whole groups. */
    fun select(all: List<UnitSnapshot>, capacity: Int, locked: List<UnitSnapshot> = emptyList()): List<UnitSnapshot> {
        require(capacity in 0..Catalog.MAX_LEVEL && locked.size <= capacity)
        val fixed = locked.map { it.uid }.toSet()
        val candidates = all.filter { it.uid !in fixed }.sortedBy { it.uid }
        val needed = min(capacity - locked.size, candidates.size)
        if (needed == candidates.size) return locked + candidates
        data class Choice(val indices: List<Int>, val value: Float)
        var beam = listOf(Choice(emptyList(), score(locked)))
        repeat(needed) { depth ->
            val expanded = beam.flatMap { choice ->
                val next = (choice.indices.lastOrNull() ?: -1) + 1
                (next until candidates.size - (needed - depth - 1)).map { index ->
                    val indices = choice.indices + index
                    Choice(indices, score(locked + indices.map { candidates[it] }))
                }
            }
            beam = expanded.sortedWith(compareByDescending<Choice> { it.value }
                .thenBy { it.indices.joinToString(",") }).take(BEAM_WIDTH)
        }
        return locked + beam.first().indices.map { candidates[it] }
    }
}
