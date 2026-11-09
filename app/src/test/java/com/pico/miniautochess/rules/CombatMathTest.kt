package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class CombatMathTest {
    private fun army(vararg ids: String) = ids.mapIndexed { i, id -> UnitSnapshot(i + 1, id, 1, i) }
    private fun stats(ids: List<UnitSnapshot>, star: Int = 1) = CombatMath.stats(ids.first().copy(star = star), ids)
    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, .002f)
    private fun fighterHealth(battle: Battle, index: Int, health: Float) {
        val field = battle.javaClass.getDeclaredField("fighters").apply { isAccessible = true }
        val fighter = (field.get(battle) as List<*>)[index]!!
        fighter.javaClass.getDeclaredField("health").apply { isAccessible = true }.set(fighter, health)
    }

    @Test fun dawnOnlyBoostsDawnHealthAtBothThresholds() {
        val ids = army("copper_guard", "dawn_priest", "dawn_archer", "dawn_oracle")
        close(760f, stats(ids.take(1)).maxHealth)
        close(874f, stats(ids.take(2)).maxHealth)
        close(874f, stats(ids.take(3)).maxHealth)
        close(988f, stats(ids).maxHealth)
        val other = UnitSnapshot(10, "rune_apprentice", 1, 9)
        close(420f, CombatMath.stats(other, ids + other).maxHealth)
    }
    @Test fun forestRestoresOneOrTwoPercentOfMaximumPerSecond() {
        val ids = army("forest_ranger", "moss_guard", "forest_blessing", "forest_witch")
        close(0f, stats(ids.take(1)).regen); close(.01f, stats(ids.take(2)).regen); close(.02f, stats(ids).regen)
        close(108.8f, CombatMath.regenerate(100f, 440f, stats(ids).regen, 1f))
        close(440f, CombatMath.regenerate(439f, 440f, .02f, 1f))
    }
    @Test fun runeMultipliesSpellWithoutChangingNormalAttack() {
        val ids = army("rune_apprentice", "rune_archer", "rune_sage", "rune_sentinel")
        close(330f, stats(ids.take(1)).spell); close(396f, stats(ids.take(2)).spell); close(462f, stats(ids).spell)
        close(46f, stats(ids).attack)
    }
    @Test fun emberMultipliesAttackWithoutChangingHealSpell() {
        val ids = army("ember_acolyte", "flame_fighter", "core_mage", "ember_breaker")
        close(48f, stats(ids.take(1)).attack); close(55.2f, stats(ids.take(2)).attack); close(62.4f, stats(ids).attack)
        close(287.5f, stats(ids).spell)
    }
    @Test fun guardAddsEightTwentyOrThirtySixArmor() {
        val ids = army("copper_guard", "moss_guard", "flame_fighter", "rune_sentinel", "ember_breaker")
        close(32f, stats(ids.take(1)).armor); close(32f, stats(ids.take(2)).armor)
        close(44f, stats(ids.take(3)).armor); close(60f, stats(ids).armor)
        close(1.2f, stats(ids).interval)
    }
    @Test fun archerAttackSpeedDividesIntervalRatherThanSubtractingPercent() {
        val ids = army("forest_ranger", "rune_archer", "dawn_archer")
        close(.9090909f, stats(ids.take(1)).interval)
        close(.8f, stats(ids.take(2)).interval); close(.7142857f, stats(ids).interval)
        close(64f, stats(ids).attack)
    }
    @Test fun mageStartsWithTwentyThirtyOrFortyMana() {
        val ids = army("rune_apprentice", "rune_sage", "core_mage", "dawn_oracle", "forest_witch")
        close(20f, stats(ids.take(1)).initialMana)
        close(20f, stats(ids.take(2)).initialMana); close(30f, stats(ids.take(3)).initialMana)
        close(40f, stats(ids).initialMana)
    }
    @Test fun supportHealingUsesFifteenOrFortyPercent() {
        val ids = army("ember_acolyte", "dawn_priest", "forest_blessing")
        close(287.5f, stats(ids.take(1)).spell); close(287.5f, stats(ids.take(2)).spell); close(350f, stats(ids).spell)
        close(510f, stats(ids).maxHealth)
    }
    @Test fun starsScaleHealthAndAttackButUseIndependentSkillCurves() {
        val ids = army("rune_apprentice", "rune_archer", "rune_sage")
        val s = stats(ids, 2)
        close(756f, s.maxHealth); close(82.8f, s.attack); close(594f, s.spell)
        val t = stats(ids, 3)
        close(1360.8f, t.maxHealth); close(149.04f, t.attack); close(891f, t.spell)
        close(6f, t.armor); close(1.2f, t.interval); close(20f, t.initialMana)
    }
    @Test fun duplicateStarsAndBenchCannotActivateTraitsAndTierStopsAtThree() {
        val one = UnitSnapshot(1, "copper_guard", 3, 0)
        val ids = listOf(one, one.copy(uid = 2, slot = 1), UnitSnapshot(3, "dawn_priest", 1, -1))
        assertEquals(1, traits(ids).first { it.label == "曙光" }.count)
        close(2462.4f, CombatMath.stats(one, ids).maxHealth)
        val guards = army("copper_guard", "moss_guard", "flame_fighter")
        assertEquals(2, traits(guards).first { it.label == "卫士" }.tier)
    }
    @Test fun physicalArmorAndShieldDoNotAmplifyDamageOrCreateNegativeShield() {
        close(100f, CombatMath.physicalDamage(100f, 0f))
        close(80f, CombatMath.physicalDamage(100f, 25f))
        close(50f, CombatMath.physicalDamage(100f, 100f))
        assertEquals(LifeResult(100f, 20f), CombatMath.resolveLife(100f, 200f, 100f, 80f, 0f))
        assertEquals(LifeResult(150f, 0f), CombatMath.resolveLife(200f, 200f, 30f, 80f, 0f))
        close(200f, CombatMath.addShield(170f, 100f, 200f))
    }
    @Test fun healingClampsAndSimultaneousRescueCannotRevivePreviousDeaths() {
        assertEquals(LifeResult(100f, 0f), CombatMath.resolveLife(90f, 100f, 0f, 0f, 50f))
        assertEquals(LifeResult(15f, 0f), CombatMath.resolveLife(10f, 100f, 0f, 20f, 25f))
        assertEquals(LifeResult(0f, 0f), CombatMath.resolveLife(10f, 100f, 0f, 200f, 25f))
        assertEquals(LifeResult(0f, 0f), CombatMath.resolveLife(0f, 100f, 0f, 0f, 200f))
        close(0f, CombatMath.regenerate(0f, 100f, .02f, 10f))
        assertEquals(LifeResult(50f, 0f), CombatMath.resolveLife(50f, 100f, 0f, 0f, 0f))
    }
    @Test fun damageUsesAllTenRoundBracketsAndEachLivingHeroIsOnePopulation() {
        val expected = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5)
        for (r in 1..10) for (population in 0..7) {
            val d = CombatMath.playerDamage(r, population, 32)
            assertEquals(expected[r - 1], d.base); assertEquals(population, d.population)
            assertEquals(expected[r - 1] + population, d.actual)
            assertEquals(1, CombatMath.playerDamage(r, population, 1).actual)
        }
    }
    @Test fun battleUsesTraitStatsAndPhysicalDamageWithoutAttackHealing() {
        val units = army("forest_ranger", "rune_archer", "dawn_archer")
        val enemy = army("copper_guard").map { it.copy(uid = 100, slot = 3) }
        val battle = Battle(0, Army(0, units), Army(1, enemy))
        val before = battle.snapshot()
        close(440f, before.fighters[0].maxHealth)
        fighterHealth(battle, 0, 100f)
        repeat(10) { battle.step() }
        val shot = battle.snapshot().events.first { it.kind == CombatEventKind.SHOT && it.actor == 1 }
        assertEquals(48, shot.amount) // Solo guard has 24 + 8 armor.
        close(100f, battle.snapshot().fighters.first { it.uid == 1 }.health)
        assertTrue(battle.snapshot().fighters.first { it.uid == 100 }.health < 760f)
    }
    @Test fun battleForestRegenerationUsesMaximumHealthAndDoesNotRevive() {
        for (health in listOf(100f, 439.8f, 0f)) {
            val units = army("forest_ranger", "moss_guard", "forest_blessing", "forest_witch")
            val battle = Battle(0, Army(0, units),
                Army(1, listOf(UnitSnapshot(100, "copper_guard", 3, 0))))
            fighterHealth(battle, 0, health)
            battle.step()
            close(when (health) { 100f -> 100.88f; 0f -> 0f; else -> 440f }, battle.snapshot().fighters[0].health)
        }
    }
    @Test fun battleMagicUsesResistanceAndMageInitialMana() {
        val units = army("rune_apprentice", "rune_sage", "core_mage")
        val battle = Battle(0, Army(0, units),
            Army(1, listOf(UnitSnapshot(100, "copper_guard", 3, 3))))
        close(30f, battle.snapshot().fighters[0].mana)
        repeat(50) { battle.step() }
        val spells = battle.snapshot().events.filter { it.actor == 1 && it.kind == CombatEventKind.CAST }
        assertTrue(spells.isNotEmpty())
        assertTrue(spells.all { it.amount in 337..338 }) // 300 * 1.2 * 1.2 / 1.28; armor remains separate.
    }
    @Test fun actualArcherAttackCountsRetainTenTwentyFiveAndFortyPercentOverSixtySeconds() {
        val ids = army("forest_ranger", "rune_archer", "dawn_archer")
        for ((count, expected) in listOf(1 to 66, 2 to 75, 3 to 84)) {
            val battle = Battle(0, Army(0, ids.take(count)),
                Army(1, listOf(UnitSnapshot(100, "rune_apprentice", 1, 3))), limit = 65f)
            // Durable inactive target isolates attack timing without killing the target.
            val fighters = battle.javaClass.getDeclaredField("fighters").apply { isAccessible = true }.get(battle) as List<*>
            val target = fighters.last()!!
            target.javaClass.getDeclaredField("shield").apply { isAccessible = true }.set(target, 1_000_000f)
            target.javaClass.getDeclaredField("cooldown").apply { isAccessible = true }.set(target, 1_000_000f)
            // Mana now casts independently of attack cooldown; disable target spell damage too.
            target.javaClass.getDeclaredField("spell").apply { isAccessible = true }.set(target, 0f)
            repeat(600) { battle.step() }
            assertNull(battle.outcome)
            assertEquals(expected, battle.snapshot().fighters.first { it.uid == 1 }.attacks)
        }
    }
}
