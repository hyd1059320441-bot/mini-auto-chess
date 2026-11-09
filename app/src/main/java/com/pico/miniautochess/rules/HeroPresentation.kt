package com.pico.miniautochess.rules

import kotlin.math.*

enum class HeroAction { BENCH, READY, WALK, ATTACK, CAST, VICTORY }
enum class SkillMotif { BASTION, LEAF_ARROW, LANTERN, WARM_FLAME, SUNRISE, ROOT,
    CROSSBOW, FIRE_BLADE, FEATHER, BLOSSOM, CONSTELLATION, MAGMA, BELL, THORN, CRYSTAL, HAMMER }
data class HeroLook(val motif: SkillMotif, val rgb: Int, val tempo: Float, val phase: Float)
data class HeroPose(val lift: Float = 0f, val lean: Float = 0f, val turn: Float = 0f, val roll: Float = 0f,
    val leftArm: Float = 0f, val rightArm: Float = 0f, val leftLeg: Float = 0f, val rightLeg: Float = 0f)

/** Presentation timing never consumes random state or changes combat. */
object HeroPresentation {
    private val colors = listOf(0xEFC36B, 0x79D963, 0x9D8DFF, 0xFFA66C,
        0xFFE4A1, 0x4AAB85, 0x67D5FF, 0xFF703D, 0xFFF2C0, 0x86FFC1,
        0xBF9CFF, 0xFFB02E, 0xFFD15A, 0xCB75E8, 0x77B0FF, 0xF54C41)
    val looks = Catalog.heroList.mapIndexed { i, h ->
        h.id to HeroLook(SkillMotif.entries[i], colors[i], .88f + i % 5 * .09f, i * .71f)
    }.toMap()
    fun action(f: FighterSnapshot?, moving: Boolean, victory: Boolean, bench: Boolean, time: Float): HeroAction = when {
        victory -> HeroAction.VICTORY
        f != null && time - f.lastCastTime in 0f.. .72f -> HeroAction.CAST
        f != null && time - f.lastAttackTime in 0f.. .34f -> HeroAction.ATTACK
        moving -> HeroAction.WALK
        bench -> HeroAction.BENCH
        else -> HeroAction.READY
    }
    fun pose(hero: HeroDef, action: HeroAction, clock: Float, age: Float, uid: Int): HeroPose {
        val look = looks.getValue(hero.id)
        val t = clock * look.tempo + look.phase + uid % 13 * .23f
        val breathe = sin(t * 2.2f)
        return when (action) {
            HeroAction.BENCH -> HeroPose(.002f + .002f * breathe, turn = sin(t * .8f) * 9f,
                roll = breathe * 1.5f, leftArm = -8f + breathe * 8f, rightArm = -12f - breathe * 9f)
            HeroAction.READY -> HeroPose(.0015f + .0015f * breathe, lean = 2f + breathe,
                leftArm = -12f + breathe * 3f, rightArm = -18f - breathe * 4f)
            HeroAction.WALK -> {
                val stride = sin(t * 13f)
                HeroPose(abs(stride) * .009f, lean = 5f, roll = stride * 3f,
                    leftArm = -stride * 23f, rightArm = stride * 23f,
                    leftLeg = stride * 29f, rightLeg = -stride * 29f)
            }
            HeroAction.ATTACK -> {
                val strike = sin((age / .34f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
                when (hero.job) {
                    Job.GUARD -> HeroPose(strike * .010f, strike * 14f, strike * 18f,
                        leftArm = -22f, rightArm = -strike * 105f, leftLeg = strike * 8f)
                    Job.ARCHER -> HeroPose(lean = -strike * 5f, turn = strike * -12f,
                        leftArm = -75f, rightArm = -40f - strike * 36f)
                    else -> HeroPose(strike * .006f, -strike * 8f,
                        leftArm = -strike * 35f, rightArm = -strike * 78f)
                }
            }
            HeroAction.CAST -> {
                val pulse = sin((age / .72f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
                val spin = if (hero.skill == Skill.CLEAVE) pulse * 100f else pulse * 12f
                HeroPose(pulse * (if (hero.job == Job.MAGE) .024f else .012f), -pulse * 7f, spin,
                    pulse * sin(look.phase) * 6f, -pulse * 95f, -pulse * 130f,
                    -pulse * 10f, pulse * 10f)
            }
            HeroAction.VICTORY -> {
                val cheer = sin(t * 4f)
                HeroPose((1f + cheer) * .014f, -4f, sin(t * 1.7f) * 18f, cheer * 6f,
                    -120f - cheer * 20f, -135f + cheer * 20f, cheer * 8f, -cheer * 8f)
            }
        }
    }
}

object StatusBarScale {
    const val HP_STEP = 100
    const val MANA_STEP = 5
    fun marks(maximum: Float, step: Int): List<Float> {
        require(maximum.isFinite() && maximum > 0f && step > 0)
        return (1 until ceil(maximum / step).toInt()).map { it * step / maximum }
    }
    fun digits(value: Float, maximum: Float): String =
        ceil(value.coerceIn(0f, maximum)).toInt().toString()
}

/** An AoE or shield plus retaliation emits one skill sound / primary motif. */
class CombatCueTracker {
    private val seen = linkedSetOf<Int>()
    fun first(event: CombatEvent): Boolean {
        val fresh = seen.add(event.actionId)
        while (seen.size > 256) seen.remove(seen.first())
        return fresh
    }
    fun clear() = seen.clear()
}
