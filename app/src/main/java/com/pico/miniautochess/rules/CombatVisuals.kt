package com.pico.miniautochess.rules

import kotlin.math.PI
import kotlin.math.sin

/** Presentation-only timing in wall seconds; never changes combat damage or random state. */
object CombatVisuals {
    const val EFFECT_POOL = 40
    const val PROJECTILE_POOL = 32
    const val SPARKS_PER_EFFECT = 6
    fun skillSeconds(skill: Skill) = when (skill) {
        Skill.SHIELD -> 1.8f
        Skill.HEAL -> 1.6f
        Skill.NOVA -> 1.5f
        Skill.BOLT -> 1.3f
        Skill.CLEAVE -> 1.2f
        Skill.VOLLEY -> 1.35f
    }
    fun projectileSeconds(distanceMeters: Float) = (.28f + distanceMeters.coerceAtLeast(0f) / 3.5f).coerceIn(.30f, .55f)
    fun brightness(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p <= .6f) 1f else ((1f - p) / .4f).coerceAtLeast(0f)
    }
    data class Flight(val x: Float, val y: Float, val z: Float)
    fun flight(progress: Float, dx: Float, dy: Float, dz: Float, arrow: Boolean): Flight {
        val p = progress.coerceIn(0f, 1f)
        return Flight(dx * p, dy * p + if (arrow) sin(p * PI.toFloat()) * .045f else 0f, dz * p)
    }
}
