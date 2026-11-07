package com.pico.miniautochess.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as PixelColor
import android.util.Log
import com.pico.miniautochess.rules.*
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

/** Additive meshes and pooled spark meshes, using the public SDK 6.1.9 API. */
class CombatEffects(parent: Entity) {
    private enum class Form { SLASH, RING, BOLT, TRAIL, GLOW, SHIELD, HEAL }
    private val plane = MeshResource.createPlane(1f, 1f).apply { toGlobal() }
    private val orb = MeshResource.createSphere(.5f).apply { toGlobal() }
    private val textures = Form.entries.associateWith { texture(it).apply { toGlobal() } }
    private val pool = List(24) { i ->
        val root = Entity().apply { setName("combat_fx_$i"); parent.addChild(this); enabled = false }
        val mat = UnlitMaterial.create(BlendingMode.ADD).apply {
            setApplyToneMapping(false); setDepthWrite(false); toGlobal()
        }
        val visual = Entity().apply { root.addChild(this); components.set(ModelComponent(plane, mat)) }
        val sparks = List(8) {
            Entity().apply {
                root.addChild(this)
                components.set(ModelComponent(orb, mat))
                enabled = false
            }
        }
        Effect(root, visual, sparks, mat)
    }
    private data class Effect(val root: Entity, val visual: Entity, val sparks: List<Entity>, val material: UnlitMaterial,
        var form: Form = Form.GLOW, var age: Float = 0f, var life: Float = .5f, var size: Float = .2f,
        var start: Vector3 = Vector3.ZERO, var end: Vector3 = Vector3.ZERO, var color: Color4 = Color4(1f, 1f, 1f, 1f))
    private var spawned = 0
    private var dropped = 0
    private var highWater = 0

    fun play(event: CombatEvent, hero: HeroDef, actor: Vector3, target: Vector3) {
        val a = actor + Vector3(0f, .13f, 0f); val b = target + Vector3(0f, .13f, 0f)
        val color = when (hero.faction) {
            Faction.DAWN -> Color4(1f, .73f, .23f, 1f)
            Faction.FOREST -> Color4(.15f, 1f, .52f, 1f)
            Faction.RUNE -> Color4(.5f, .37f, 1f, 1f)
            Faction.EMBER -> Color4(1f, .26f, .06f, 1f)
        }
        when (event.kind) {
            CombatEventKind.HIT -> spawn(Form.SLASH, b, b, color, .22f, .32f, 10)
            CombatEventKind.SHOT -> spawn(Form.TRAIL, a, b, color, .025f, .26f, 0)
            CombatEventKind.BLOCK -> {
                spawn(Form.SHIELD, actor + Vector3(0f, .13f, 0f), a, color, .34f, .9f, 18)
                spawn(Form.RING, actor + Vector3(0f, .012f, 0f), a, color, .43f, .7f, 0)
            }
            CombatEventKind.HEAL -> {
                val green = Color4(.19f, 1f, .53f, 1f)
                spawn(Form.HEAL, target + Vector3(0f, .025f, 0f), b, green, .32f, .85f, 20)
                spawn(Form.RING, target + Vector3(0f, .014f, 0f), b, green, .38f, .7f, 0)
            }
            CombatEventKind.CAST -> when (hero.skill) {
                Skill.BOLT -> {
                    spawn(Form.BOLT, a, b, color, .10f, .32f, 0)
                    spawn(Form.GLOW, b, b, color, .31f, .45f, 20)
                }
                Skill.NOVA -> {
                    spawn(Form.RING, target + Vector3(0f, .018f, 0f), b, color, .78f, .65f, 24)
                    spawn(Form.GLOW, b, b, color, .43f, .5f, 0)
                }
                Skill.CLEAVE -> {
                    spawn(Form.SLASH, a, b, color, .48f, .42f, 20)
                    spawn(Form.RING, actor + Vector3(0f, .015f, 0f), b, color, .55f, .45f, 0)
                }
                Skill.VOLLEY -> {
                    spawn(Form.TRAIL, a, b, color, .055f, .3f, 0)
                    spawn(Form.GLOW, b, b, color, .25f, .4f, 12)
                }
                else -> spawn(Form.RING, target + Vector3(0f, .01f, 0f), b, color, .43f, .5f, 12)
            }
            CombatEventKind.FALL -> spawn(Form.RING, target + Vector3(0f, .02f, 0f), b, color, .30f, .55f, 8)
        }
    }
    private fun spawn(form: Form, start: Vector3, end: Vector3, color: Color4, size: Float, life: Float, count: Int) {
        val fx = pool.firstOrNull { !it.root.enabled } ?: run { dropped++; return }
        fx.form = form; fx.age = 0f; fx.life = life; fx.size = size
        fx.start = start; fx.end = end; fx.color = color
        fx.material.setBaseColor(color); fx.material.setBaseColorTexture(textures.getValue(form))
        fx.visual.components.set(ModelComponent(if (form == Form.SHIELD) orb else plane, fx.material))
        fx.root.enabled = true
        fx.root.components[TransformComponent::class.java]!!.position = start
        fx.sparks.forEachIndexed { index, spark -> spark.enabled = index < count.coerceAtMost(fx.sparks.size) }
        spawned++
        highWater = max(highWater, pool.count { it.root.enabled })
        animate(fx)
    }
    fun update(dt: Float) {
        pool.filter { it.root.enabled }.forEach {
            it.age += dt.coerceAtLeast(0f)
            if (it.age >= it.life) release(it) else animate(it)
        }
    }
    private fun animate(fx: Effect) {
        val t = (fx.age / fx.life).coerceIn(0f, 1f)
        val trans = fx.visual.components[TransformComponent::class.java]!!
        trans.position = Vector3.ZERO
        trans.eulerAngles = EulerAngles(pitch = -42f)
        var size = fx.size * (.65f + t * .8f)
        when (fx.form) {
            Form.RING -> { trans.eulerAngles = EulerAngles(pitch = -90f); size = fx.size * (.25f + t) }
            Form.HEAL -> {
                trans.eulerAngles = EulerAngles(pitch = -90f, yaw = t * 260f)
                trans.position = Vector3(0f, t * .23f, 0f)
            }
            Form.SHIELD -> { trans.eulerAngles = EulerAngles(yaw = t * 100f); size = fx.size }
            Form.SLASH -> trans.eulerAngles = EulerAngles(pitch = -50f, roll = -45f + t * 130f)
            Form.BOLT, Form.TRAIL -> {
                val delta = fx.end - fx.start
                val length = sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z)
                // Lie ribbons parallel to the board; a vertical Z-facing ribbon
                // becomes edge-on for front-to-back attacks in the overhead view.
                trans.eulerAngles = EulerAngles(pitch = -90f, yaw = -atan2(delta.z, delta.x) * 180 / PI.toFloat())
                trans.position = if (fx.form == Form.BOLT) delta * .5f else delta * t
                trans.scaleVector = Vector3(if (fx.form == Form.BOLT) length else .17f, fx.size, 1f)
            }
            else -> Unit
        }
        if (fx.form !in listOf(Form.BOLT, Form.TRAIL)) trans.scaleVector = Vector3(size, size, size)
        val fade = (1f - t).coerceAtLeast(0f)
        fx.material.setBaseColor(Color4(fx.color.red * fade, fx.color.green * fade, fx.color.blue * fade, 1f))
        fx.sparks.forEachIndexed { index, spark ->
            if (spark.enabled) {
                val angle = index * 2.3999632f
                val speed = .16f + (index % 4) * .055f
                val age = fx.age.coerceAtMost(.42f)
                val transform = spark.components[TransformComponent::class.java]!!
                transform.position = Vector3(
                    cos(angle) * speed * age,
                    (.18f + (index % 3) * .045f) * age - .11f * age * age,
                    sin(angle) * speed * age,
                )
                val sparkSize = (.024f * (1f - age / .42f)).coerceAtLeast(0f)
                transform.scaleVector = Vector3(sparkSize, sparkSize, sparkSize)
                if (fx.age >= .42f) spark.enabled = false
            }
        }
    }
    private fun release(fx: Effect) {
        fx.sparks.forEach { it.enabled = false }
        fx.root.enabled = false
    }
    fun clear() {
        pool.filter { it.root.enabled }.forEach(::release)
        if (spawned > 0) Log.i("JadeChessFx", "spawned=$spawned highWater=$highWater pool=24 sparkMeshCap=192 dropped=$dropped")
        spawned = 0; dropped = 0; highWater = 0
    }
    fun destroyEntities() { clear(); pool.forEach { it.root.destroy() } }
    fun closeResources() { pool.forEach { it.material.close() }; plane.close(); orb.close(); textures.values.forEach { it.close() } }

    private fun texture(form: Form): TextureResource {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PixelColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f }
        when (form) {
            Form.RING, Form.HEAL -> {
                canvas.drawCircle(64f, 64f, 48f, paint)
                paint.strokeWidth = 1.5f; canvas.drawCircle(64f, 64f, 40f, paint)
                repeat(8) { i ->
                    val angle = i * PI / 4
                    canvas.drawLine(64f + cos(angle).toFloat() * 43f, 64f + sin(angle).toFloat() * 43f,
                        64f + cos(angle).toFloat() * 54f, 64f + sin(angle).toFloat() * 54f, paint)
                }
            }
            Form.SLASH -> { paint.strokeWidth = 9f; canvas.drawArc(12f, 18f, 116f, 110f, 200f, 130f, false, paint) }
            Form.BOLT -> {
                val points = listOf(0f to 64f, 24f to 45f, 42f to 81f, 61f to 34f, 81f to 78f, 103f to 53f, 128f to 64f)
                points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.first, a.second, b.first, b.second, paint) }
            }
            Form.SHIELD -> {
                paint.strokeWidth = 3f
                repeat(5) { i -> canvas.drawLine(i * 32f, 0f, i * 32f, 128f, paint); canvas.drawLine(0f, i * 32f, 128f, i * 32f, paint) }
            }
            Form.TRAIL, Form.GLOW -> {
                for (y in 0 until 128) for (x in 0 until 128) {
                    val r = if (form == Form.TRAIL) abs(y - 64f) / 64f else hypot(x - 64f, y - 64f) / 64f
                    val value = ((1f - r).coerceIn(0f, 1f).pow(3) * 255).toInt()
                    bitmap.setPixel(x, y, PixelColor.argb(value, value, value, value))
                }
            }
        }
        return TextureResource.create(bitmap).also { bitmap.recycle() }
    }
}
