package com.pico.miniautochess.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private enum class Form { SLASH, RING, BOLT, TRAIL, ARROW, GLOW, SHIELD, HEAL, SIGIL }
    private val plane = MeshResource.createPlane(1f, 1f).apply { toGlobal() }
    private val orb = MeshResource.createSphere(.5f).apply { toGlobal() }
    private val textures = Form.entries.associateWith { texture(it).apply { toGlobal() } }
    private val sigils = SkillMotif.entries.associateWith { sigilTexture(it).apply { toGlobal() } }
    private val pool = List(CombatVisuals.EFFECT_POOL) { i ->
        val root = Entity().apply { setName("combat_fx_$i"); parent.addChild(this); enabled = false }
        val mat = UnlitMaterial.create(BlendingMode.ADD).apply {
            setApplyToneMapping(false); setDepthWrite(false); toGlobal()
        }
        val visual = Entity().apply { root.addChild(this); components.set(ModelComponent(plane, mat)) }
        val sparks = List(CombatVisuals.SPARKS_PER_EFFECT) {
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
        var start: Vector3 = Vector3.ZERO, var end: Vector3 = Vector3.ZERO, var color: Color4 = Color4(1f, 1f, 1f, 1f),
        var motif: SkillMotif? = null, var priority: Int = 0,
        var followUid: Int? = null, var followHeight: Float = 0f)
    private data class Projectile(val root: Entity, val body: Entity, val head: Entity, val tail: Entity,
        val material: UnlitMaterial, val headMaterial: UnlitMaterial, val tailMaterial: UnlitMaterial,
        var age: Float = 0f, var life: Float = .4f, var arrow: Boolean = false,
        var start: Vector3 = Vector3.ZERO, var end: Vector3 = Vector3.ZERO,
        var color: Color4 = Color4(1f, 1f, 1f, 1f), var empowered: Boolean = false)
    // Shots have their own pool: a burst of arrows cannot evict a skill's readable motif.
    private val projectiles = List(CombatVisuals.PROJECTILE_POOL) { i ->
        val root = Entity().apply { setName("combat_projectile_$i"); parent.addChild(this); enabled = false }
        val mat = UnlitMaterial.create(BlendingMode.ADD).apply {
            setApplyToneMapping(false); setDepthWrite(false); toGlobal()
        }
        val headMat = UnlitMaterial.create(BlendingMode.ADD).apply {
            setApplyToneMapping(false); setDepthWrite(false); toGlobal()
        }
        val tailMat = UnlitMaterial.create(BlendingMode.ADD).apply {
            setApplyToneMapping(false); setDepthWrite(false); setBaseColorTexture(textures.getValue(Form.TRAIL)); toGlobal()
        }
        val body = Entity().apply { root.addChild(this); components.set(ModelComponent(plane, mat)) }
        val head = Entity().apply { root.addChild(this); components.set(ModelComponent(orb, headMat)) }
        val tail = Entity().apply { root.addChild(this); components.set(ModelComponent(plane, tailMat)) }
        Projectile(root, body, head, tail, mat, headMat, tailMat)
    }
    private var spawned = 0
    private var dropped = 0
    private var highWater = 0
    private var projectileSpawned = 0
    private var projectileDropped = 0
    private var projectileHighWater = 0

    init {
        Log.i("JadeChessFx", "FX_READY effectPool=${CombatVisuals.EFFECT_POOL} sparkMeshCap=${CombatVisuals.EFFECT_POOL * CombatVisuals.SPARKS_PER_EFFECT} projectilePool=${CombatVisuals.PROJECTILE_POOL} skillSeconds=1.2..1.8 wallTime=true realtimeLights=0")
    }

    fun play(event: CombatEvent, hero: HeroDef, actor: Vector3, target: Vector3, primary: Boolean = true) {
        val a = actor + Vector3(0f, .13f, 0f); val b = target + Vector3(0f, .13f, 0f)
        val look = HeroPresentation.looks.getValue(hero.id)
        val rgb = look.rgb
        val color = Color4(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f, 1f)
        val skill = event.kind in listOf(CombatEventKind.CAST, CombatEventKind.BLOCK, CombatEventKind.HEAL)
        val life = CombatVisuals.skillSeconds(hero.skill)
        if (skill && primary) {
            val center = if (hero.skill in listOf(Skill.SHIELD, Skill.CLEAVE)) actor else target
            spawn(Form.SIGIL, center + Vector3(0f, .055f, 0f), b, color,
                if (hero.skill == Skill.NOVA) .66f else .40f, life, 6, look.motif, priority = 4,
                followUid = if (hero.skill == Skill.SHIELD) event.actor else if (hero.skill == Skill.HEAL) event.target else null,
                followHeight = .055f)
        }
        if (skill && !primary && hero.skill != Skill.VOLLEY) {
            spawn(Form.GLOW, b, b, color, .16f, .6f, 0, priority = 1)
            return
        }
        when (event.kind) {
            CombatEventKind.HIT -> spawn(Form.SLASH, b, b, color, .22f, .32f, 10)
            CombatEventKind.SHOT -> launch(a, b, color, hero.job == Job.ARCHER)
            CombatEventKind.BLOCK -> {
                spawn(Form.SHIELD, a, a, color, .34f, life, 6, priority = 3,
                    followUid = event.actor, followHeight = .13f)
                spawn(Form.RING, actor + Vector3(0f, .012f, 0f), a, color, .43f, life, 0, priority = 2,
                    followUid = event.actor, followHeight = .012f)
            }
            CombatEventKind.HEAL -> {
                val green = Color4(.19f, 1f, .53f, 1f)
                spawn(Form.HEAL, target + Vector3(0f, .025f, 0f), b, green, .32f, life, 6, priority = 3,
                    followUid = event.target, followHeight = .025f)
                spawn(Form.RING, target + Vector3(0f, .014f, 0f), b, green, .38f, life, 0, priority = 2,
                    followUid = event.target, followHeight = .014f)
            }
            CombatEventKind.CAST -> when (hero.skill) {
                Skill.BOLT -> {
                    spawn(Form.BOLT, a, b, color, .08f, .8f, 0, priority = 2)
                    spawn(Form.GLOW, b, b, color, .31f, life, 6, priority = 2)
                }
                Skill.NOVA -> {
                    spawn(Form.RING, target + Vector3(0f, .018f, 0f), b, color, .78f, life, 6, priority = 2)
                    spawn(Form.GLOW, b, b, color, .43f, 1.05f, 0, priority = 2)
                }
                Skill.CLEAVE -> {
                    spawn(Form.SLASH, a, b, color, .48f, .8f, 6, priority = 2)
                    spawn(Form.RING, actor + Vector3(0f, .015f, 0f), b, color, .55f, life, 0, priority = 2)
                }
                Skill.VOLLEY -> {
                    launch(a, b, color, arrow = true, empowered = true)
                }
                else -> spawn(Form.RING, target + Vector3(0f, .01f, 0f), b, color, .43f, life, 6, priority = 2)
            }
            CombatEventKind.FALL -> spawn(Form.RING, target + Vector3(0f, .02f, 0f), b, color, .30f, .55f, 8)
        }
    }
    private fun spawn(form: Form, start: Vector3, end: Vector3, color: Color4, size: Float, life: Float, count: Int,
                      motif: SkillMotif? = null, priority: Int = 0, followUid: Int? = null, followHeight: Float = 0f) {
        val fx = pool.firstOrNull { !it.root.enabled } ?: pool.filter { it.priority < priority }
            .maxByOrNull { it.age / it.life } ?: run { dropped++; return }
        fx.form = form; fx.age = 0f; fx.life = life; fx.size = size
        fx.start = start; fx.end = end; fx.color = color; fx.motif = motif
        fx.priority = priority; fx.followUid = followUid; fx.followHeight = followHeight
        fx.material.setBaseColor(color); fx.material.setBaseColorTexture(if (motif != null) sigils.getValue(motif) else textures.getValue(form))
        fx.visual.components.set(ModelComponent(if (form == Form.SHIELD) orb else plane, fx.material))
        fx.root.enabled = true
        fx.root.components[TransformComponent::class.java]!!.position = start
        fx.sparks.forEachIndexed { index, spark -> spark.enabled = index < count.coerceAtMost(fx.sparks.size) }
        spawned++
        highWater = max(highWater, pool.count { it.root.enabled })
        animate(fx)
    }
    private fun launch(start: Vector3, end: Vector3, color: Color4, arrow: Boolean, empowered: Boolean = false) {
        val shot = projectiles.firstOrNull { !it.root.enabled } ?: run { projectileDropped++; return }
        val d = end - start
        shot.age = 0f; shot.life = CombatVisuals.projectileSeconds(sqrt(d.x * d.x + d.y * d.y + d.z * d.z))
        shot.start = start; shot.end = end; shot.arrow = arrow; shot.color = color; shot.empowered = empowered
        shot.material.setBaseColorTexture(textures.getValue(if (arrow) Form.ARROW else Form.TRAIL))
        shot.material.setBaseColor(color); shot.headMaterial.setBaseColor(color)
        shot.tailMaterial.setBaseColor(Color4(color.red * .65f, color.green * .65f, color.blue * .65f, 1f))
        shot.root.enabled = true
        animateProjectile(shot)
        projectileSpawned++
        projectileHighWater = max(projectileHighWater, projectiles.count { it.root.enabled })
    }
    fun update(dt: Float, anchors: Map<Int, Vector3>) {
        pool.filter { it.root.enabled }.forEach {
            it.age += dt.coerceAtLeast(0f)
            if (it.age >= it.life) release(it) else {
                it.followUid?.let { uid -> anchors[uid]?.let { anchor ->
                    it.root.components[TransformComponent::class.java]!!.position = anchor + Vector3(0f, it.followHeight, 0f)
                } }
                animate(it)
            }
        }
        projectiles.filter { it.root.enabled }.forEach {
            it.age += dt.coerceAtLeast(0f)
            if (it.age >= it.life) {
                it.root.enabled = false
                spawn(Form.GLOW, it.end, it.end, it.color,
                    if (it.empowered) .24f else .10f, if (it.empowered) .55f else .22f, 0, priority = 1)
            } else animateProjectile(it)
        }
    }
    private fun animateProjectile(shot: Projectile) {
        val p = (shot.age / shot.life).coerceIn(0f, 1f)
        val d = shot.end - shot.start
        val flight = CombatVisuals.flight(p, d.x, d.y, d.z, shot.arrow)
        shot.root.components[TransformComponent::class.java]!!.position = shot.start + Vector3(flight.x, flight.y, flight.z)
        val angle = -atan2(d.z, d.x) * 180f / PI.toFloat()
        val scale = if (shot.empowered) 1.4f else 1f
        shot.body.components[TransformComponent::class.java]!!.apply {
            position = Vector3.ZERO
            eulerAngles = EulerAngles(pitch = -90f, yaw = angle)
            scaleVector = Vector3(.16f * scale, .045f * scale, 1f)
        }
        shot.head.enabled = !shot.arrow
        shot.head.components[TransformComponent::class.java]!!.scaleVector = Vector3(.05f, .05f, .05f) * scale
        val length = sqrt(d.x * d.x + d.z * d.z)
        shot.tail.components[TransformComponent::class.java]!!.apply {
            eulerAngles = EulerAngles(pitch = -90f, yaw = angle)
            val behind = if (length > .001f) d * (-.09f / length) else Vector3.ZERO
            position = behind
            scaleVector = Vector3(.16f * scale, .019f * scale, 1f)
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
            Form.SIGIL -> {
                val rises = fx.motif in listOf(SkillMotif.SUNRISE, SkillMotif.WARM_FLAME, SkillMotif.BLOSSOM, SkillMotif.BELL)
                trans.eulerAngles = EulerAngles(pitch = if (rises) -50f else -90f,
                    yaw = if (rises) 0f else t * (45f + (fx.motif?.ordinal ?: 0) * 8f))
                trans.position = Vector3(0f, if (rises) t * .14f else .008f, 0f)
                size = fx.size * (.4f + sin(t * PI.toFloat() * .75f) * .8f)
            }
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
        val fade = CombatVisuals.brightness(t)
        fx.material.setBaseColor(Color4(fx.color.red * fade, fx.color.green * fade, fx.color.blue * fade, 1f))
        fx.sparks.forEachIndexed { index, spark ->
            if (spark.enabled) {
                val angle = index * 2.3999632f
                val speed = .16f + (index % 4) * .055f
                val age = fx.age
                val transform = spark.components[TransformComponent::class.java]!!
                transform.position = Vector3(
                    cos(angle) * speed * age,
                    (.22f + (index % 3) * .045f) * age - .075f * age * age,
                    sin(angle) * speed * age,
                )
                val sparkSize = .022f * fade
                transform.scaleVector = Vector3(sparkSize, sparkSize, sparkSize)
            }
        }
    }
    private fun release(fx: Effect) {
        fx.sparks.forEach { it.enabled = false }
        fx.root.enabled = false
    }
    fun clear() {
        pool.filter { it.root.enabled }.forEach(::release)
        projectiles.forEach { it.root.enabled = false }
        if (spawned > 0 || projectileSpawned > 0) Log.i("JadeChessFx", "spawned=$spawned highWater=$highWater pool=${CombatVisuals.EFFECT_POOL} dropped=$dropped projectileSpawned=$projectileSpawned projectileHighWater=$projectileHighWater projectilePool=${CombatVisuals.PROJECTILE_POOL} projectileDropped=$projectileDropped")
        spawned = 0; dropped = 0; highWater = 0
        projectileSpawned = 0; projectileDropped = 0; projectileHighWater = 0
    }
    fun destroyEntities() { clear(); pool.forEach { it.root.destroy() }; projectiles.forEach { it.root.destroy() } }
    fun closeResources() {
        pool.forEach { it.material.close() }
        projectiles.forEach { it.material.close(); it.headMaterial.close(); it.tailMaterial.close() }
        plane.close(); orb.close(); textures.values.forEach { it.close() }; sigils.values.forEach { it.close() }
    }

    /** Sixteen independently drawn readable emblems, rather than recolored generic rings. */
    private fun sigilTexture(motif: SkillMotif): TextureResource {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PixelColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
        fun line(a: Float, b: Float, c: Float, d: Float) = canvas.drawLine(a, b, c, d, p)
        fun polygon(points: List<Pair<Float, Float>>) {
            val path = Path(); path.moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { path.lineTo(it.first, it.second) }; path.close(); canvas.drawPath(path, p)
        }
        fun radial(count: Int, action: () -> Unit) = repeat(count) { i ->
            canvas.save(); canvas.rotate(i * 360f / count, 96f, 96f); action(); canvas.restore()
        }
        when (motif) {
            SkillMotif.BASTION -> {
                polygon(listOf(48f to 40f, 144f to 40f, 136f to 117f, 96f to 159f, 56f to 117f))
                line(96f, 46f, 96f, 143f); line(60f, 83f, 132f, 83f)
            }
            SkillMotif.LEAF_ARROW -> {
                polygon(listOf(96f to 23f, 132f to 74f, 105f to 65f, 105f to 164f, 87f to 164f, 87f to 65f, 60f to 74f))
                canvas.drawOval(41f, 105f, 87f, 152f, p); canvas.drawOval(105f, 105f, 151f, 152f, p)
            }
            SkillMotif.LANTERN -> {
                canvas.drawRoundRect(52f, 48f, 140f, 137f, 18f, 18f, p)
                line(68f, 34f, 124f, 34f); line(96f, 137f, 96f, 168f)
                polygon(listOf(96f to 63f, 118f to 91f, 96f to 120f, 74f to 91f))
            }
            SkillMotif.WARM_FLAME -> {
                val path = Path(); path.moveTo(96f, 160f); path.cubicTo(21f, 126f, 68f, 73f, 76f, 70f)
                path.cubicTo(68f, 110f, 126f, 69f, 103f, 24f); path.cubicTo(177f, 96f, 151f, 145f, 96f, 160f)
                canvas.drawPath(path, p); canvas.drawCircle(96f, 127f, 18f, p)
            }
            SkillMotif.SUNRISE -> {
                canvas.drawCircle(96f, 96f, 34f, p)
                radial(12) { line(96f, 28f, 96f, 47f) }; line(45f, 151f, 147f, 151f)
            }
            SkillMotif.ROOT -> radial(5) {
                val path = Path(); path.moveTo(96f, 96f); path.cubicTo(49f, 68f, 133f, 44f, 96f, 22f)
                canvas.drawPath(path, p); line(96f, 57f, 112f, 45f)
            }
            SkillMotif.CROSSBOW -> {
                line(96f, 31f, 96f, 165f); line(44f, 80f, 148f, 80f)
                polygon(listOf(44f to 80f, 61f to 46f, 131f to 46f, 148f to 80f))
                line(79f, 49f, 96f, 30f); line(113f, 49f, 96f, 30f)
            }
            SkillMotif.FIRE_BLADE -> {
                polygon(listOf(140f to 28f, 139f to 71f, 73f to 139f, 54f to 120f))
                line(42f, 108f, 87f, 153f); line(65f, 132f, 41f, 158f)
                canvas.drawArc(26f, 27f, 170f, 164f, 183f, 109f, false, p)
            }
            SkillMotif.FEATHER -> {
                radial(3) { canvas.drawOval(73f, 22f, 119f, 101f, p); line(96f, 33f, 96f, 143f) }
            }
            SkillMotif.BLOSSOM -> {
                radial(6) { canvas.drawOval(73f, 28f, 119f, 103f, p) }
                canvas.drawCircle(96f, 96f, 14f, p)
            }
            SkillMotif.CONSTELLATION -> {
                val points = listOf(39f to 56f, 111f to 33f, 148f to 112f, 66f to 149f, 39f to 56f)
                points.zipWithNext().forEach { (a, b) -> line(a.first, a.second, b.first, b.second) }
                points.dropLast(1).forEach { canvas.drawCircle(it.first, it.second, 9f, p) }
                radial(4) { line(87f, 96f, 105f, 96f) }
            }
            SkillMotif.MAGMA -> {
                radial(7) { polygon(listOf(96f to 25f, 106f to 64f, 92f to 56f, 83f to 88f)) }
                canvas.drawCircle(96f, 96f, 25f, p)
            }
            SkillMotif.BELL -> {
                val path = Path(); path.moveTo(51f, 134f); path.quadTo(69f, 105f, 67f, 72f)
                path.quadTo(96f, 26f, 125f, 72f); path.quadTo(123f, 105f, 141f, 134f); path.close()
                canvas.drawPath(path, p); canvas.drawCircle(96f, 145f, 9f, p)
                canvas.drawArc(21f, 46f, 171f, 149f, 175f, 59f, false, p)
            }
            SkillMotif.THORN -> radial(8) {
                polygon(listOf(96f to 23f, 107f to 82f, 96f to 68f, 85f to 82f))
            }
            SkillMotif.CRYSTAL -> {
                polygon(listOf(96f to 23f, 133f to 65f, 121f to 131f, 96f to 168f, 71f to 131f, 59f to 65f))
                line(96f, 23f, 96f, 168f); line(60f, 65f, 132f, 65f)
                line(71f, 131f, 121f, 131f)
            }
            SkillMotif.HAMMER -> {
                polygon(listOf(49f to 38f, 142f to 38f, 142f to 78f, 49f to 78f))
                polygon(listOf(88f to 78f, 104f to 78f, 104f to 143f, 88f to 143f))
                line(36f, 163f, 67f, 143f); line(77f, 163f, 111f, 155f); line(134f, 150f, 157f, 167f)
            }
        }
        return TextureResource.create(bitmap).also { bitmap.recycle() }
    }

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
            Form.ARROW -> {
                paint.strokeWidth = 6f
                canvas.drawLine(14f, 64f, 112f, 64f, paint)
                canvas.drawLine(86f, 41f, 114f, 64f, paint)
                canvas.drawLine(86f, 87f, 114f, 64f, paint)
                canvas.drawLine(17f, 44f, 37f, 64f, paint)
                canvas.drawLine(17f, 84f, 37f, 64f, paint)
            }
            Form.SHIELD -> {
                paint.strokeWidth = 3f
                repeat(5) { i -> canvas.drawLine(i * 32f, 0f, i * 32f, 128f, paint); canvas.drawLine(0f, i * 32f, 128f, i * 32f, paint) }
            }
            Form.TRAIL, Form.GLOW, Form.SIGIL -> {
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
