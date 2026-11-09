package com.pico.miniautochess.content

import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color as BitmapColor
import androidx.compose.ui.graphics.Color
import com.pico.miniautochess.rules.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

class ChessRuntimeComponent : Component() {
    var controller: GameController? = null
}

/** Registered once for this Stage, with no global app/controller reference. */
class ChessSystem : com.pico.spatial.core.ecs.System() {
    override fun update(context: SceneUpdateContext) {
        context.scene.queryEntity(EntityQueryCondition.hasComponent(ChessRuntimeComponent::class.java))
            .forEach { it.components[ChessRuntimeComponent::class.java]?.controller?.frame(context.deltaTime) }
    }
}

/**
 * Unbounded Stage scene, all positions in meters. `owner` is identity beneath scenePlacement.
 * The tabletop is centered at (0, .24, -2.25); all four grid surfaces are y=.265.
 * 8x8 grid pitch .23, full footprint 1.84m. Bench is in a separate front strip.
 * Runtime bounds normalize each imported model before clones are made.
 */
class ChessScene(private val controller: GameController, context: Context) {
    val owner = Entity()
    private val placement = Entity()
    private val arena = Entity()
    private val opaqueTable = mutableListOf<Entity>()
    private val surfaceTiles = mutableListOf<Entity>()
    private var heightsAudited = false
    private val library = Entity()
    private val meshes = mutableMapOf<String, MeshResource>()
    private val materials = mutableMapOf<Color, UnlitMaterial>()
    private val fabricMaterials = mutableMapOf<Color, UnlitMaterial>()
    private val textures = mutableListOf<TextureResource>()
    private val decoratedMaterials = mutableMapOf<String, UnlitMaterial>()
    private lateinit var effects: CombatEffects
    private val templates = mutableMapOf<String, Entity>()
    private val templateHeights = mutableMapOf<String, Float>()
    private val taps = mutableMapOf<Entity, () -> Unit>()
    private val draggable = mutableMapOf<Entity, Int>()
    private val slotTargets = mutableMapOf<Entity, Int>()
    private val panels = mutableMapOf<String, Entity>()
    private val panelSizes = mutableMapOf<String, Pair<Float, Float>>()
    private val panelOrigins = mutableMapOf<String, Pair<Float, Float>>()
    private var shopArea: FloatArray? = null
    private val auditedStatusHeroes = mutableSetOf<String>()
    private var panelAuditTime = 0f
    private val auditedPanels = mutableSetOf<String>()
    private var draggingLastFrame = false
    private val pawns = mutableMapOf<Int, Pawn>()
    private val previews = Array(3) { mutableMapOf<Int, Pawn>() }
    private val previewRoots = List(3) { Entity() }
    private val legalMarkers = mutableListOf<Entity>()
    private val legalSlots = mutableMapOf<Entity, Int>()
    private val audio = ChessAudio(context)
    private val cues = CombatCueTracker()
    private var visualClock = 0f
    private var poseBattleKey = ""
    private var poseBattleElapsed = -1f
    private var poseBattleAnchor = 0f
    private val pavilionSupports = mutableListOf<Pair<Entity, Entity>>()
    private var pavilionAudited = false
    private val physicsMaterial = PhysicsMaterialResource().apply { toGlobal() }
    private val tileShape = ShapeResource.createBox(Vector3(.22f, .05f, .22f)).apply { toGlobal() }
    // Visual scale must not reduce the foot-level grab area. Width stays below
    // the .23m slot pitch, so neighbouring pieces do not share a hit volume.
    private val heroShape = ShapeResource.createBox(Vector3(.21f, .34f, .21f)).apply { toGlobal() }
    private var lastBattleKey = ""
    private var lastEvent = 0
    private var previewKey = ""
    private var disposed = false
    private var selected: Int? = null
    private var renderRevision = -1
    private var renderUnits = emptyList<UnitSnapshot>()
    private var renderFighters = emptyMap<Int, FighterSnapshot>()

    private data class Pawn(
        val root: Entity, val visual: Entity, val hp: Entity, val mana: Entity, val shield: Entity,
        val hpBack: Entity, val manaBack: Entity, val label: Entity, val ring: Entity,
        val stars: List<Entity>, val collider: Entity, val heroId: String,
        val status: Entity, val hpMarks: Entity, val hpDigits: List<Entity>, val manaDigits: List<Entity>,
        val hpMaximum: Entity, val dragMarker: Entity,
        val limbs: Map<String, Entity>,
        val tint: (Boolean) -> Unit,
        var starCount: Int,
        var highlighted: Boolean = false,
        var current: Vector3 = Vector3.ZERO,
        var maximumHealth: Float = -1f, var hpText: String = "", var manaText: String = "",
    )

    suspend fun initialize(content: SpatialViewContent) {
        owner.setName("placementOwner")
        placement.setName("scenePlacement")
        placement.transform().position = Vector3(0f, 0f, SCENE_FORWARD_OFFSET_Z)
        arena.setName("arena")
        library.setName("modelLibrary")
        content.addEntity(placement)
        placement.addChild(owner)
        owner.addChild(arena); owner.addChild(library)
        arena.transform().position = Vector3(0f, BOARD_ROOT_Y, BOARD_Z)
        owner.components.set(ChessRuntimeComponent().apply { this.controller = this@ChessScene.controller })
        createBoard()
        createPreviewBoards()
        createEnvironment()
        for ((index, def) in Catalog.heroList.withIndex()) {
            controller.loading = "布置英雄 ${index + 1} / ${Catalog.heroList.size}"
            val template = Entity()
            template.setName("template_${def.id}")
            library.addChild(template)
            if (def.id in GENERATED) {
                val model = Entity.loadSuspend("asset://models/${def.id}.glb")
                template.addChild(model)
                useBakedMaterials(model)
                model.transform().eulerAngles = EulerAngles(yaw = -90f)
                val source = model.getVisualBounds(template, recursive = true, enabledOnly = false)
                check(!source.isEmpty()) { "Empty runtime model bounds: ${def.id}" }
                val size = source.max - source.min
                val uniform = min(.25f / size.y, .195f / max(size.x, size.z))
                model.transform().scaleVector = Vector3(uniform, uniform, uniform)
                val scaled = model.getVisualBounds(template, recursive = true, enabledOnly = false)
                model.transform().position = Vector3(-scaled.center.x, -scaled.min.y, -scaled.center.z)
                val final = model.getVisualBounds(template, recursive = true, enabledOnly = false)
                check(abs(final.min.y) < .001f && final.max.x - final.min.x <= .196f && final.max.z - final.min.z <= .196f)
                Log.i("JadeChessBounds", "hero=${def.id} owner=template_${def.id} scale=$uniform min=${final.min} max=${final.max} support=0 numeric=PASS visual=UNOBSERVED")
            } else {
                createPrototypeHero(template, def)
            }
            templates[def.id] = template
            templateHeights[def.id] = template.getVisualBounds(template, recursive = true, enabledOnly = false).max.y
            template.enabled = false
        }
        (0..9).forEach { numberMaterial(it.toString()) }
        numberMaterial("/100")
        audio.initialize()
        effects = CombatEffects(arena)
        controller.sceneReady = true
        controller.loading = "棋会已布置"
        render(controller.engine.snapshot(), 0f)
        Log.i("JadeChess", "SCENE_READY generated=${GENERATED.size} templates=${templates.size} meshes=${meshes.size} materials=${materials.size}")
        Log.i("JadeChessPlacement", "sceneForwardOffsetZ=$SCENE_FORWARD_OFFSET_Z meters=0.30 direction=forward allContent=scenePlacement heroVisualScale=$HERO_VISUAL_SCALE grabBox=.21x.34x.21 footMinY=-.02 slotDrag=true")
    }

    private fun Entity.transform() = checkNotNull(components[TransformComponent::class.java])
    private fun useBakedMaterials(entity: Entity) {
        entity.components[ModelComponent::class.java]?.let { model ->
            for (index in 0 until model.materials.size) {
                val original = model.materials[index] as? PhysicallyBasedMaterial ?: continue
                val texture = original.getBaseColorTexture()
                model.materials[index] = UnlitMaterial.create().apply {
                    setBaseColor(original.getBaseColor())
                    setBaseColorTexture(texture)
                    setApplyToneMapping(false)
                }
                Log.i("JadeChessMaterials", "node=${entity.getName()} slot=$index texture=$texture binding=PRESERVED")
            }
        }
        entity.getChildren().forEach(::useBakedMaterials)
    }
    private fun material(color: Color) = materials.getOrPut(color) {
        UnlitMaterial.create().apply { setBaseColor(ChessTheme.scene(color)); setApplyToneMapping(false); toGlobal() }
    }
    private fun fabric(color: Color) = fabricMaterials.getOrPut(color) {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        for (y in 0 until 128) for (x in 0 until 128) {
            val edge = min(min(x, 127 - x), min(y, 127 - y))
            val shading = (.58f + .32f * (1f - y / 127f) + if ((x + y) % 4 == 0) .07f else 0f) *
                if (edge < 5) .48f else 1f
            bitmap.setPixel(x, y, BitmapColor.rgb((color.red * shading * 255).toInt(),
                (color.green * shading * 255).toInt(), (color.blue * shading * 255).toInt()))
        }
        val texture = TextureResource.create(bitmap).apply { toGlobal() }
        bitmap.recycle(); textures.add(texture)
        UnlitMaterial.create().apply {
            setBaseColor(ChessTheme.scene(Color.White)); setBaseColorTexture(texture)
            setApplyToneMapping(false); toGlobal()
        }
    }
    private fun bitmapMaterial(name: String, bitmap: Bitmap, transparent: Boolean = false): UnlitMaterial {
        val texture = TextureResource.create(bitmap).apply { toGlobal() }
        bitmap.recycle(); textures.add(texture)
        return UnlitMaterial.create(if (transparent) BlendingMode.TRANSPARENT else BlendingMode.OPAQUE).apply {
            setBaseColor(ChessTheme.scene(Color.White)); setBaseColorTexture(texture)
            setApplyToneMapping(false); toGlobal()
        }.also { decoratedMaterials[name] = it }
    }
    private fun starMaterial() = decoratedMaterials["star"] ?: run {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = Path()
        repeat(10) { i ->
            val angle = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) 56f else 25f
            val x = 64f + cos(angle).toFloat() * r
            val y = 64f + sin(angle).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 8f, 0f, 120f,
            BitmapColor.rgb(255, 239, 175), BitmapColor.rgb(223, 149, 33), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        paint.color = BitmapColor.rgb(112, 72, 20)
        canvas.drawPath(path, paint)
        bitmapMaterial("star", bitmap, transparent = true)
    }
    private fun nameMaterial(hero: HeroDef) = labelMaterial("name_${hero.id}", hero.name)
    private fun labelMaterial(key: String, text: String) = decoratedMaterials[key] ?: run {
        val bitmap = Bitmap.createBitmap(512, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BitmapColor.rgb(21, 35, 34))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BitmapColor.rgb(244, 229, 189); textAlign = Paint.Align.CENTER
            textSize = 68f; isFakeBoldText = true
        }
        canvas.drawText(text, 256f, 80f, paint)
        paint.color = BitmapColor.rgb(162, 124, 59); paint.strokeWidth = 5f
        canvas.drawLine(0f, 109f, 512f, 109f, paint)
        bitmapMaterial(key, bitmap)
    }
    private fun stoneMaterial(): UnlitMaterial {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1024) for (x in 0 until 1024) {
            val edge = x % 64 < 3 || y % 64 < 3
            val tile = ((x / 64 * 7 + y / 64 * 11) % 9 - 4) * 3
            val grain = ((x * 17 + y * 31 + x * y) % 13) - 6
            val moss = if ((x / 64 + y / 64 * 3) % 7 == 0) 14 else 0
            val shade = if (edge) -27 else tile + grain
            bitmap.setPixel(x, y, BitmapColor.rgb(89 + shade - moss, 102 + shade, 94 + shade - moss))
        }
        return bitmapMaterial("stone", bitmap)
    }
    private fun jadeSurface(): UnlitMaterial = decoratedMaterials["jadeSurface"] ?: run {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1024) for (x in 0 until 1024) {
            val vein = sin(x * .017 + sin(y * .007) * 4) + sin(y * .012 + cos(x * .006) * 3)
            val grain = ((x * 17 + y * 37) % 7) - 3
            val v = (vein * 3).toInt() + grain
            bitmap.setPixel(x, y, BitmapColor.rgb(46 + v, 79 + v, 70 + v))
        }
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = BitmapColor.argb(170, 169, 158, 107)
        }
        canvas.drawRoundRect(20f, 20f, 1004f, 1004f, 68f, 68f, paint)
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(34f, 34f, 990f, 990f, 60f, 60f, paint)
        paint.color = BitmapColor.argb(45, 170, 217, 178)
        repeat(8) { i ->
            canvas.save(); canvas.rotate(i * 45f, 512f, 512f)
            canvas.drawOval(451f, 324f, 573f, 512f, paint); canvas.restore()
        }
        bitmapMaterial("jadeSurface", bitmap)
    }
    private fun numberMaterial(text: String): UnlitMaterial = decoratedMaterials["number_$text"] ?: run {
        val bitmap = Bitmap.createBitmap(if (text.length == 1) 64 else 256, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = if (text.length == 1) 80f else 67f
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            color = BitmapColor.rgb(11, 29, 27); style = Paint.Style.STROKE; strokeWidth = 6f
        }
        val y = 48f - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(text, bitmap.width / 2f, y, paint)
        paint.style = Paint.Style.FILL; paint.color = BitmapColor.rgb(242, 246, 227)
        canvas.drawText(text, bitmap.width / 2f, y, paint)
        bitmapMaterial("number_$text", bitmap, true)
    }
    private fun ticksMaterial(maximum: Float, step: Int): UnlitMaterial {
        val name = "ticks_${maximum}_$step"
        return decoratedMaterials[name] ?: run {
            val bitmap = Bitmap.createBitmap(1024, 64, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = BitmapColor.rgb(13, 32, 31); strokeWidth = 3f }
            StatusBarScale.marks(maximum, step).forEachIndexed { i, ratio ->
                paint.strokeWidth = if (step == 100 && (i + 1) % 5 == 0) 5f else 2.5f
                canvas.drawLine(ratio * 1024, 0f, ratio * 1024, 64f, paint)
            }
            bitmapMaterial(name, bitmap, true)
        }
    }
    private fun plane(parent: Entity, name: String, width: Float, height: Float, mat: UnlitMaterial, pos: Vector3): Entity {
        val mesh = meshes.getOrPut("plane_${width}_$height") { MeshResource.createPlane(width, height).apply { toGlobal() } }
        return Entity().apply {
            setName(name); parent.addChild(this); components.set(ModelComponent(mesh, mat)); transform().position = pos
        }
    }
    private fun box(parent: Entity, name: String, size: Vector3, color: Color, pos: Vector3, radius: Float = .003f): Entity {
        val key = "box_${size.x}_${size.y}_${size.z}_$radius"
        val mesh = meshes.getOrPut(key) { MeshResource.createBox(size, radius).apply { toGlobal() } }
        return Entity().apply {
            setName(name); components.set(ModelComponent(mesh, material(color)))
            transform().position = pos; parent.addChild(this)
        }
    }
    private fun sphere(parent: Entity, name: String, radius: Float, color: Color, pos: Vector3): Entity {
        val mesh = meshes.getOrPut("sphere_$radius") { MeshResource.createSphere(radius).apply { toGlobal() } }
        return Entity().apply {
            setName(name); components.set(ModelComponent(mesh, material(color)))
            transform().position = pos; parent.addChild(this)
        }
    }
    private fun cylinder(parent: Entity, name: String, height: Float, radius: Float, color: Color, pos: Vector3): Entity {
        val mesh = meshes.getOrPut("cylinder_${height}_$radius") { MeshResource.createCylinder(height, radius).apply { toGlobal() } }
        return Entity().apply {
            setName(name); components.set(ModelComponent(mesh, material(color)))
            transform().position = pos; parent.addChild(this)
        }
    }
    private fun interactive(entity: Entity, shape: ShapeResource, action: () -> Unit) {
        entity.components.set(CollisionComponent(listOf(shape), physicsMaterial))
        entity.components.set(InteractableComponent()); entity.components.set(HoverEffectComponent())
        taps[entity] = action
    }

    private fun createBoard() {
        opaqueTable.add(box(arena, "tableBrass", Vector3(2.02f, .07f, 2.42f), ChessTheme.Brass, Vector3(0f, -.035f, .19f), .055f))
        opaqueTable.add(box(arena, "tableInk", Vector3(2f, .06f, 2.40f), ChessTheme.Ink, Vector3(0f, -.015f, .19f), .052f))
        surfaceTiles.add(box(arena, "jadeArenaSurface", Vector3(1.84f, .01f, 1.84f), ChessTheme.Forest,
            Vector3(0f, .02f, 0f), .04f).apply { components[ModelComponent::class.java]!!.materials[0] = jadeSurface() })
        for (row in 0 until Catalog.BATTLE_ROWS) for (col in 0 until Catalog.BOARD_COLS) {
            val pos = Vector3((col - BoardLayout.halfColumns) * PITCH, .02f, (BoardLayout.halfRows - row) * PITCH)
            if (row < Catalog.BOARD_ROWS) {
                val tile = Entity().apply { setName("placement_${row}_$col"); arena.addChild(this); transform().position = pos }
                val slot = (Catalog.BOARD_ROWS - 1 - row) * Catalog.BOARD_COLS + col
                interactive(tile, tileShape) { controller.selectSlot(slot) }
                slotTargets[tile] = slot
                val marker = cylinder(arena, "legal_$slot", .002f, .028f, ChessTheme.Jade, pos + Vector3(0f, .011f, 0f))
                legalMarkers.add(marker); legalSlots[marker] = slot; marker.enabled = false
            }
        }
        for (index in 0 until Catalog.BENCH_SIZE) {
            val p = BoardLayout.position(-index - 1)
            val e = box(arena, "bench_$index", Vector3(.21f, .012f, .21f), ChessTheme.Slate,
                Vector3(p.x, .023f, p.z), .012f)
            interactive(e, tileShape) { controller.selectSlot(-index - 1) }
            slotTargets[e] = -index - 1
        }
        box(arena, "frontTrim", Vector3(1.84f, .012f, .018f), ChessTheme.Jade, Vector3(0f, .02f, 1.31f))
        box(arena, "pedestal", Vector3(.65f, .22f, .65f), ChessTheme.Slate, Vector3(0f, -.135f, 0f), .05f)
    }

    private fun createPreviewBoards() {
        // Preview grids are scaled, so match their visible top, rather than root y.
        val y = BOARD_ROOT_Y + .025f - .015f * .45f
        val positions = listOf(Vector3(-2.15f, y, -3.5f), Vector3(0f, y, -4.9f), Vector3(2.15f, y, -3.5f))
        previewRoots.forEachIndexed { index, root ->
            owner.addChild(root); root.setName("preview_${index + 1}")
            root.transform().position = positions[index]
            root.transform().scaleVector = Vector3(.45f, .45f, .45f)
            box(root, "previewTable", Vector3(2f, .06f, 2f), ChessTheme.Brass, Vector3(0f, -.03f, 0f), .05f)
            surfaceTiles.add(box(root, "previewJadeSurface", Vector3(1.84f, .014f, 1.84f), ChessTheme.Forest,
                Vector3(0f, .008f, 0f), .04f).apply { components[ModelComponent::class.java]!!.materials[0] = jadeSurface() })
        }
    }

    private suspend fun createEnvironment() {
        val sky = Entity.loadSuspend("asset://environment/jade_garden_sky.glb")
        sky.setName("jadeGardenSky"); owner.addChild(sky)
        useBakedMaterials(sky)
        sky.transform().position = Vector3(0f, 1.6f, 0f)
        box(owner, "gardenFloor", Vector3(22f, .025f, 22f), ChessTheme.Stone, Vector3(0f, -.03f, -2f))
        val floorMesh = meshes.getOrPut("stoneFloor") { MeshResource.createPlane(22f, 22f).apply { toGlobal() } }
        Entity().apply {
            setName("stonePaving"); owner.addChild(this)
            components.set(ModelComponent(floorMesh, stoneMaterial()))
            transform().position = Vector3(0f, -.016f, -2f)
            transform().eulerAngles = EulerAngles(pitch = -90f)
        }
        for (side in listOf(-1f, 1f)) {
            box(owner, "gardenBorder", Vector3(.22f, .15f, 7f), ChessTheme.Wood, Vector3(side * 3.9f, .03f, -3f))
            box(owner, "plantBed", Vector3(.95f, .09f, 5.7f), ChessTheme.DeepLeaf, Vector3(side * 3.2f, .01f, -3.5f))
            repeat(3) { i ->
                val x = side * (3.3f + (i % 2) * .3f)
                val z = -1.5f - i * 2.1f
                cylinder(owner, "gardenPlanter", .32f, .22f, ChessTheme.Slate, Vector3(x, .16f, z))
                repeat(3) { branch ->
                    val leaf = sphere(owner, "gardenLeaf", .18f, ChessTheme.DeepLeaf,
                        Vector3(x + (branch - 1) * .13f, .56f + (branch % 2) * .18f, z))
                    leaf.transform().scaleVector = Vector3(.7f, 1.6f, .7f)
                }
                box(owner, "stoneColumn", Vector3(.16f, .696f, .16f), ChessTheme.Wood,
                    Vector3(side * 4.3f, .332f, z))
                // Column top .68 touches the stone seat, which supports the lantern bottom .73.
                box(owner, "lanternSeat", Vector3(.31f, .05f, .31f), ChessTheme.Stone,
                    Vector3(side * 4.3f, .705f, z))
                box(owner, "lantern", Vector3(.27f, .3f, .27f), ChessTheme.Clay,
                    Vector3(side * 4.3f, .88f, z), .025f)
                box(owner, "lanternCap", Vector3(.34f, .04f, .34f), ChessTheme.Brass,
                    Vector3(side * 4.3f, 1.05f, z))
            }
            for (z in listOf(-5.8f, -7.5f)) {
                box(owner, "courtyardPost", Vector3(.16f, 2.4f, .16f), ChessTheme.Wood,
                    Vector3(side * 4.2f, 1.2f, z), .01f)
                cylinder(owner, "treeTrunk", 1.15f, .075f, ChessTheme.Wood, Vector3(side * 5.1f, .56f, z))
                val crown = sphere(owner, "treeCrown", .57f, ChessTheme.DeepLeaf, Vector3(side * 5.1f, 1.65f, z))
                crown.transform().scaleVector = Vector3(1.3f, 1.15f, 1f)
            }
            box(owner, "courtyardSideBeam", Vector3(.20f, .16f, 2.1f), ChessTheme.Wood,
                Vector3(side * 4.2f, 2.4f, -6.65f))
            box(owner, "frontPlanter", Vector3(.65f, .18f, .8f), ChessTheme.Stone, Vector3(side * 1.85f, .075f, -.65f))
            repeat(3) { i ->
                val bush = sphere(owner, "frontShrub", .18f, ChessTheme.Forest,
                    Vector3(side * 1.85f, .28f, -.9f + i * .24f))
                bush.transform().scaleVector = Vector3(1.15f, .8f, 1f)
            }
        }
        val frontBeam = box(owner, "courtyardFrontBeam", Vector3(8.7f, .18f, .22f), ChessTheme.Wood, Vector3(0f, 2.4f, -5.8f))
        val rearBeam = box(owner, "courtyardRearBeam", Vector3(8.7f, .18f, .22f), ChessTheme.Wood, Vector3(0f, 2.4f, -7.5f))
        repeat(9) { i ->
            val rafter = box(owner, "courtyardRafter_$i", Vector3(.085f, .085f, 2.1f), ChessTheme.Wood,
                Vector3((i - 4) * 1f, 2.5325f, -6.65f))
            pavilionSupports.add(rafter to frontBeam); pavilionSupports.add(rafter to rearBeam)
        }
        box(owner, "pavilionRoof", Vector3(9.0f, .045f, 2.25f), ChessTheme.DeepLeaf, Vector3(0f, 2.5975f, -6.65f), .015f)
        for (side in listOf(-1f, 1f)) {
            val brace = box(owner, "pavilionKneeBrace", Vector3(.08f, .42f, .09f), ChessTheme.Wood,
                Vector3(side * 4.04f, 2.2f, -5.8f))
            brace.transform().eulerAngles = EulerAngles(roll = side * -45f)
            box(owner, "gardenSeat", Vector3(.48f, .08f, 1.18f), ChessTheme.Wood, Vector3(side * 3.75f, .38f, -4.5f))
            for (z in listOf(-4.94f, -4.06f))
                box(owner, "gardenSeatLeg", Vector3(.10f, .34f, .13f), ChessTheme.Stone, Vector3(side * 3.75f, .17f, z))
            cylinder(owner, "lotusBasin", .20f, .42f, ChessTheme.Stone, Vector3(side * 2.8f, .1f, -6.6f))
            cylinder(owner, "stillWater", .003f, .36f, Color(0xFF417C83), Vector3(side * 2.8f, .202f, -6.6f))
            repeat(3) { i ->
                val petal = sphere(owner, "lotusPetal", .05f, Color(0xFFEAB6A6),
                    Vector3(side * 2.8f + (i - 1) * .033f, .232f, -6.6f))
                petal.transform().scaleVector = Vector3(.7f, .45f, 1.3f)
            }
        }
        Log.i("JadeChess", "ENVIRONMENT_READY sky=inward_glb texture=2048x1024 floor=stone1024 greenery=deep_leaf wood=courtyard realtimeLights=0")
    }

    /** Hero-specific articulated figures retain their own headwear and weapon silhouette. */
    private fun createPrototypeHero(parent: Entity, hero: HeroDef) {
        val accent = when (hero.faction) {
            Faction.DAWN -> ChessTheme.Dawn; Faction.FOREST -> ChessTheme.Forest
            Faction.RUNE -> ChessTheme.Rune; Faction.EMBER -> ChessTheme.Ember
        }
        sphere(parent, "torso", .046f, accent, Vector3(0f, .129f, 0f)).apply {
            transform().scaleVector = Vector3(if (hero.job == Job.GUARD) .98f else .78f, 1f, .58f)
        }
        box(parent, "cape", Vector3(.080f, .113f, .008f), accent, Vector3(0f, .121f, -.031f), .003f)
        box(parent, "breastplate", Vector3(.051f, .042f, .008f), ChessTheme.Steel, Vector3(0f, .135f, .028f), .005f)
        box(parent, "belt", Vector3(.075f, .015f, .05f), accent, Vector3(0f, .091f, 0f))
        sphere(parent, "head", .033f, ChessTheme.Skin, Vector3(0f, .198f, 0f))
        box(parent, "hair", Vector3(.061f, .019f, .050f), ChessTheme.Ink, Vector3(0f, .221f, -.007f), .008f)
        box(parent, "face", Vector3(.03f, .008f, .006f), ChessTheme.Ink, Vector3(0f, .202f, .031f))
        for (side in listOf(-1f, 1f)) {
            sphere(parent, "eye", .004f, ChessTheme.Ivory, Vector3(side * .012f, .204f, .032f))
            sphere(parent, "ear", .009f, ChessTheme.Skin, Vector3(side * .034f, .196f, 0f))
        }
        if (hero.job != Job.GUARD) cylinder(parent, "robeHem", .044f, .042f, accent, Vector3(0f, .083f, 0f))
        for (side in listOf(-1f, 1f)) {
            val leg = Entity().apply {
                setName(if (side < 0) "leftLegPivot" else "rightLegPivot")
                parent.addChild(this); transform().position = Vector3(side * .022f, .081f, 0f)
            }
            box(leg, "leg", Vector3(.026f, .066f, .03f), ChessTheme.Slate, Vector3(0f, -.033f, 0f))
            box(leg, "boot", Vector3(.033f, .019f, .047f), accent, Vector3(0f, -.072f, .008f), .006f)
            val arm = Entity().apply {
                setName(if (side < 0) "leftArmPivot" else "rightArmPivot")
                parent.addChild(this); transform().position = Vector3(side * .052f, .152f, 0f)
            }
            box(arm, "arm", Vector3(.023f, .060f, .03f), accent, Vector3(0f, -.03f, 0f), .005f)
            sphere(arm, "hand", .015f, ChessTheme.Skin, Vector3(0f, -.063f, .006f))
            box(parent, "shoulder", Vector3(.043f, .024f, .055f), accent,
                Vector3(side * .052f, .157f, 0f), .008f)
        }
        val weapon = parent.getChildren().first { it.getName() == "rightArmPivot" }
        when (hero.job) {
            Job.GUARD -> {
                val left = parent.getChildren().first { it.getName() == "leftArmPivot" }
                box(left, "shield", Vector3(.062f, .09f, .018f), accent, Vector3(0f, -.046f, .04f), .012f)
                box(weapon, "blade", Vector3(if (hero.id == "flame_fighter") .035f else .018f, .115f, .012f),
                    ChessTheme.Brass, Vector3(.018f, -.008f, .015f))
                box(parent, "helmet", Vector3(.071f, .030f, .06f), accent, Vector3(0f, .219f, 0f), .005f)
            }
            Job.ARCHER -> {
                if (hero.id == "rune_archer") {
                    box(weapon, "crossbow", Vector3(.112f, .018f, .026f), accent, Vector3(-.025f, -.040f, .043f))
                } else {
                    box(weapon, "bow", Vector3(.014f, .126f, .018f), accent, Vector3(.014f, -.027f, .04f))
                    for (side in listOf(-1f, 1f)) box(weapon, "bowTip", Vector3(.015f, .035f, .018f),
                        ChessTheme.Brass, Vector3(.025f, -.027f + side * .063f, .04f))
                }
                box(weapon, "arrow", Vector3(.009f, .009f, .10f), ChessTheme.Brass, Vector3(.014f, -.035f, .057f))
                box(parent, "quiver", Vector3(.04f, .10f, .027f), ChessTheme.Brass, Vector3(.01f, .13f, -.04f))
            }
            Job.MAGE -> {
                cylinder(weapon, "staff", .18f, .007f, ChessTheme.Brass, Vector3(.025f, -.030f, .015f))
                sphere(weapon, "core", .027f, accent, Vector3(.025f, .07f, .015f))
                box(parent, "hatBrim", Vector3(.11f, .015f, .09f), ChessTheme.Slate, Vector3(0f, .221f, 0f))
                box(parent, "hatCrown", Vector3(.06f, .046f, .05f), accent, Vector3(0f, .246f, 0f), .007f)
            }
            Job.SUPPORT -> {
                cylinder(weapon, "staff", .16f, .007f, ChessTheme.Brass, Vector3(.025f, -.030f, .015f))
                sphere(weapon, "blessing", .027f, accent, Vector3(.025f, .06f, .015f))
                box(parent, "mantle", Vector3(.12f, .032f, .06f), accent, Vector3(0f, .157f, -.015f), .010f)
            }
        }
        repeat(hero.faction.ordinal + 1) { i ->
            box(parent, "faction_$i", Vector3(.011f, .013f, .007f), ChessTheme.Brass, Vector3((i - hero.faction.ordinal / 2f) * .015f, .122f, .027f))
        }
        when (hero.id) {
            "dawn_priest" -> {
                cylinder(parent, "sunHalo", .018f, .046f, ChessTheme.Brass, Vector3(0f, .258f, 0f))
                box(weapon, "sunCross", Vector3(.066f, .012f, .016f), ChessTheme.Brass, Vector3(.025f, .056f, .015f))
            }
            "flame_fighter" -> {
                box(parent, "flameCrest", Vector3(.025f, .054f, .018f), ChessTheme.Ember, Vector3(0f, .25f, 0f))
                box(parent.getChildren().first { it.getName() == "leftArmPivot" }, "secondBlade",
                    Vector3(.020f, .12f, .01f), ChessTheme.Brass, Vector3(-.02f, -.04f, .018f))
            }
            "dawn_archer" -> repeat(3) { i ->
                box(parent, "wingFeather_$i", Vector3(.014f, .082f - i * .013f, .02f), ChessTheme.Dawn,
                    Vector3(.039f + i * .014f, .21f, -.022f))
            }
            "forest_blessing" -> {
                sphere(parent, "leafCrown", .022f, ChessTheme.Forest, Vector3(-.035f, .23f, 0f))
                sphere(parent, "leafCrownRight", .022f, ChessTheme.Forest, Vector3(.035f, .23f, 0f))
                box(weapon, "branchCross", Vector3(.07f, .012f, .012f), ChessTheme.Wood, Vector3(.025f, .054f, .015f))
            }
            "rune_sage" -> repeat(3) { i ->
                sphere(parent, "runeOrbit_$i", .014f, ChessTheme.Rune, Vector3((i - 1) * .047f, .28f, 0f))
            }
            "core_mage" -> {
                sphere(weapon, "moltenCore", .039f, ChessTheme.Ember, Vector3(.025f, .072f, .015f))
                for (side in listOf(-1f, 1f)) box(parent, "moltenHorn", Vector3(.017f, .06f, .02f),
                    ChessTheme.Ember, Vector3(side * .046f, .245f, 0f))
            }
            "dawn_oracle" -> {
                cylinder(weapon, "goldenBell", .043f, .029f, ChessTheme.Brass, Vector3(.025f, .066f, .015f))
                box(parent, "oracleCrown", Vector3(.078f, .033f, .025f), ChessTheme.Brass, Vector3(0f, .277f, 0f))
            }
            "forest_witch" -> {
                for (side in listOf(-1f, 1f)) {
                    val thorn = box(parent, "thornCrown", Vector3(.014f, .076f, .014f), ChessTheme.Forest, Vector3(side * .035f, .268f, 0f))
                    thorn.transform().eulerAngles = EulerAngles(roll = side * 24f)
                }
                sphere(weapon, "vineSeed", .035f, ChessTheme.Forest, Vector3(.025f, .071f, .015f))
            }
            "rune_sentinel" -> {
                box(parent.getChildren().first { it.getName() == "leftArmPivot" }, "crystalTowerShield",
                    Vector3(.052f, .15f, .022f), ChessTheme.Rune, Vector3(0f, -.033f, .055f), .008f)
                for (side in listOf(-1f, 1f)) box(parent, "shoulderCrystal", Vector3(.025f, .077f, .025f),
                    ChessTheme.Rune, Vector3(side * .058f, .19f, 0f))
            }
            "ember_breaker" -> {
                box(weapon, "greatHammer", Vector3(.085f, .043f, .04f), ChessTheme.Steel, Vector3(.018f, .055f, .015f), .008f)
                box(parent, "redVisor", Vector3(.064f, .015f, .016f), ChessTheme.Ember, Vector3(0f, .216f, .037f))
            }
        }
        fun dress(node: Entity) {
            if (node.getName() in setOf("torso", "cape", "arm", "mantle", "shield", "helmet", "hatCrown", "leg"))
                node.components[ModelComponent::class.java]?.materials?.set(0, fabric(if (node.getName() == "leg") ChessTheme.Slate else accent))
            node.getChildren().forEach(::dress)
        }
        dress(parent)
        Log.i("JadeChessMaterials", "hero=${hero.id} texture=cloth128 zones=skin,cloth,steel,brass prototype=MULTICOLOR")
    }

    private fun pawn(parent: Entity, unit: UnitSnapshot, interact: Boolean, preview: Boolean = false): Pawn {
        val root = Entity(); root.setName("pawn_${abs(unit.uid)}")
        parent.addChild(root)
        val visual = checkNotNull(templates.getValue(unit.heroId).clone(Entity.CloneOptions(true, true)))
        root.addChild(visual); visual.enabled = true
        val tintActions = mutableListOf<(Boolean) -> Unit>()
        fun rememberMaterials(node: Entity) {
            node.components[ModelComponent::class.java]?.let { model ->
                for (index in 0 until model.materials.size) {
                    val original = model.materials[index]
                    tintActions.add { active -> model.materials[index] = if (active) material(ChessTheme.Brass) else original }
                }
            }
            node.getChildren().forEach(::rememberMaterials)
        }
        rememberMaterials(visual)
        val ring = Entity(); root.addChild(ring)
        listOf(Vector3(0f, .003f, .084f), Vector3(0f, .003f, -.084f), Vector3(.084f, .003f, 0f), Vector3(-.084f, .003f, 0f))
            .forEachIndexed { index, pos ->
                box(ring, "selection_$index", if (index < 2) Vector3(.18f, .008f, .014f) else Vector3(.014f, .008f, .18f), ChessTheme.Brass, pos)
            }
        ring.enabled = false
        ring.transform().scaleVector = Vector3(HERO_VISUAL_SCALE, 1f, HERO_VISUAL_SCALE)
        val status = Entity().apply {
            setName("segmentedStatus"); root.addChild(this); transform().eulerAngles = EulerAngles(pitch = -50f)
        }
        val hpBack = plane(status, "hpBackground", .198f, .017f, material(ChessTheme.Ink), Vector3(0f, .024f, 0f))
        val manaBack = plane(status, "manaBackground", .198f, .013f, material(ChessTheme.Ink), Vector3(0f, -.025f, 0f))
        val hp = plane(status, "hp", .194f, .013f, material(ChessTheme.Jade), Vector3(0f, .024f, .001f))
        val mana = plane(status, "mana", .194f, .009f, material(Color(0xFF49A8FF)), Vector3(0f, -.025f, .001f))
        val shield = plane(status, "shield", .194f, .003f, material(ChessTheme.Ivory), Vector3(0f, .035f, .001f))
        val hpMarks = plane(status, "hp100Ticks", .194f, .013f, ticksMaterial(unit.hero.health, 100), Vector3(0f, .024f, .002f))
        plane(status, "mana5Ticks", .194f, .009f, ticksMaterial(100f, 5), Vector3(0f, -.025f, .002f))
        fun digits(name: String, count: Int, y: Float) = List(count) { i ->
            plane(status, "${name}_$i", .014f, .022f, numberMaterial("0"),
                Vector3(-.070f + i * .014f, y, .002f))
        }
        val hpDigits = digits("hpValue", 4, .048f)
        val manaDigits = digits("manaValue", 3, -.001f)
        val hpMaximum = plane(status, "hpMaxValue", .073f, .022f, numberMaterial("/${unit.hero.health.toInt()}"),
            Vector3(.04f, .048f, .002f))
        plane(status, "manaMaxValue", .062f, .022f, numberMaterial("/100"), Vector3(.033f, -.001f, .002f))
        val label = Entity().apply {
            setName("heroName_${unit.heroId}"); root.addChild(this)
            val mesh = meshes.getOrPut("namePlane") { MeshResource.createPlane(.212f, .046f).apply { toGlobal() } }
            components.set(ModelComponent(mesh, nameMaterial(unit.hero)))
            transform().eulerAngles = EulerAngles(pitch = -50f)
        }
        val dragMarker = plane(root, "dragSelected", .212f, .046f, labelMaterial("dragSelected", "拖动中"), Vector3.ZERO).apply {
            transform().eulerAngles = EulerAngles(pitch = -50f)
            enabled = false
        }
        val starMesh = meshes.getOrPut("starPlane") { MeshResource.createPlane(.038f, .038f).apply { toGlobal() } }
        val starMaterial = starMaterial()
        val stars = List(3) { i ->
            Entity().apply {
                setName("fivePointStar_$i"); root.addChild(this)
                components.set(ModelComponent(starMesh, starMaterial))
                transform().position = Vector3((i - (unit.star - 1) / 2f) * .041f, STAR_Y, .018f)
                transform().eulerAngles = EulerAngles(pitch = -50f)
                enabled = i < unit.star && !preview
            }
        }
        val collider = Entity(); root.addChild(collider)
        collider.transform().position = Vector3(0f, .15f, 0f)
        if (interact) interactive(collider, heroShape) { controller.selectUnit(unit.uid) }
        if (preview) { stars.forEach { it.enabled = false }; status.enabled = false; label.enabled = false }
        val limbs = visual.getChildren().filter { it.getName().endsWith("Pivot") }.associateBy { it.getName() }
        return Pawn(root, visual, hp, mana, shield, hpBack, manaBack, label, ring, stars, collider,
            unit.heroId, status, hpMarks, hpDigits, manaDigits, hpMaximum, dragMarker,
            limbs, { active -> tintActions.forEach { it(active) } }, 0)
    }
    private fun remove(pawn: Pawn) { taps.remove(pawn.collider); draggable.remove(pawn.collider); pawn.root.destroy() }
    private fun position(unit: UnitSnapshot) = BoardLayout.position(unit.slot).let { Vector3(it.x, .026f, it.z) }

    fun tap(entity: Entity?) { if (!disposed && controller.drag.uid == null && entity != null) taps[entity]?.invoke() }
    fun beginDrag(entity: Entity?) {
        val uid = draggable[entity] ?: slotTargets[entity]?.let { slot ->
            controller.engine.snapshot().human.units.firstOrNull { it.slot == slot }?.uid
        } ?: return
        controller.beginDrag(uid)
    }
    fun dragDelta(x: Float, y: Float, z: Float) {
        // UI Y grows downwards; upward pointing pulls toward the far rows.
        controller.drag.delta(x, y + z)
        render(controller.engine.snapshot(), 0f)
    }
    fun selection(uid: Int?) {
        selected = uid
        val activeUid = controller.drag.uid ?: uid
        pawns.forEach { (id, p) -> highlight(p, id == activeUid, id == controller.drag.uid) }
        legalMarkers.forEach { it.enabled = uid != null && controller.engine.canEditUnit(0, uid) &&
            (controller.engine.phase == Phase.PREPARATION || (legalSlots[it] ?: 0) < 0) }
    }
    private fun highlight(p: Pawn, active: Boolean, dragging: Boolean = false) {
        p.ring.enabled = active
        p.dragMarker.enabled = dragging
        val scale = HERO_VISUAL_SCALE * if (dragging) 1.4f + .08f * sin(visualClock * 5f) else 1f
        p.ring.transform().scaleVector = Vector3(scale, 1f, scale)
        if (p.highlighted != active) { p.tint(active); p.highlighted = active }
    }

    fun render(match: MatchSnapshot, dt: Float) {
        if (disposed || templates.size != Catalog.heroList.size) return
        auditPanels(dt)
        val visualStep = if (!match.paused && match.phase != Phase.FINISHED) dt.coerceAtLeast(0f) else 0f
        visualClock += visualStep
        syncMusic()
        controller.drag.validate()
        val dragging = controller.drag.uid != null
        val battle = if (match.phase in listOf(Phase.BATTLE, Phase.TRANSITION, Phase.FINISHED))
            match.battles.getOrNull(match.watchedBattle) else null
        // Interpolate pose time between 10Hz rule ticks; freeze with visualClock on pause.
        val poseKey = "${match.round}:${battle?.id}"
        if (poseKey != poseBattleKey || battle?.elapsed != poseBattleElapsed) {
            poseBattleKey = poseKey; poseBattleElapsed = battle?.elapsed ?: -1f; poseBattleAnchor = visualClock
        }
        val speed = if (battle?.outcome == null) battle?.speedMultiplier ?: 1 else 1
        val actionClock = (battle?.elapsed ?: 0f) + (visualClock - poseBattleAnchor) * speed
        if (renderRevision == match.revision && match.paused && !dragging && !draggingLastFrame) return
        draggingLastFrame = dragging
        val changed = renderRevision != match.revision
        if (renderRevision != match.revision) {
            renderUnits = if (battle != null) battle.fighters.map { fighter ->
                UnitSnapshot(fighter.uid, fighter.heroId, fighter.star, 0)
            } + match.human.units.filter { !it.onBoard }
            else if (match.phase == Phase.MENU) Catalog.heroList.take(5).mapIndexed { i, h -> UnitSnapshot(1000 + i, h.id, 1, Catalog.BOARD_COLS + i + 1) }
            else match.human.units
            renderFighters = battle?.fighters?.associateBy { it.uid }.orEmpty()
            updatePreviews(match)
            renderRevision = match.revision
        }
        val wanted = renderUnits
        val ids = wanted.map { it.uid }.toSet()
        pawns.keys.filter { it !in ids }.toList().forEach { remove(pawns.remove(it)!!) }
        val fighters = renderFighters
        for (unit in wanted) {
            if (pawns[unit.uid]?.heroId != unit.heroId) pawns.remove(unit.uid)?.let(::remove)
            val p = pawns.getOrPut(unit.uid) { pawn(arena, unit, battle == null && match.phase != Phase.MENU) }
            val f = fighters[unit.uid]
            val dragPoint = controller.drag.point.takeIf { controller.drag.uid == unit.uid }
            val target = if (dragPoint != null) {
                val zone = shopDropZone()
                val y = if (zone?.region?.contains(dragPoint) == true) {
                    val t = ((dragPoint.z - zone.region.minZ) / (zone.region.maxZ - zone.region.minZ)).coerceIn(0f, 1f)
                    zone.farY + (zone.nearY - zone.farY) * t + .045f
                } else .08f
                Vector3(dragPoint.x, y, dragPoint.z)
            }
                else if (f != null) Vector3((f.x - BoardLayout.halfColumns) * PITCH, .026f, (BoardLayout.halfRows - f.y) * PITCH)
                else position(unit)
            val blend = if (dt <= 0 || battle == null) 1f else (dt * 14f * speed).coerceAtMost(1f)
            val distance = (target - p.current).let { sqrt(it.x * it.x + it.z * it.z) }
            val moving = f != null && battle?.outcome == null && distance > .002f && !match.paused
            p.current += (target - p.current) * blend
            p.root.transform().position = p.current
            p.root.enabled = f == null || f.health > 0
            val starScale = 1f + (unit.star - 1) * .07f
            val modelScale = starScale * HERO_VISUAL_SCALE
            val victory = f != null && ((f.team == 0 && battle!!.outcome == BattleOutcome.LEFT) ||
                (f.team == 1 && battle!!.outcome == BattleOutcome.RIGHT))
            val action = HeroPresentation.action(f, moving, victory, f == null && !unit.onBoard, actionClock)
            val age = when (action) {
                HeroAction.CAST -> actionClock - f!!.lastCastTime
                HeroAction.ATTACK -> actionClock - f!!.lastAttackTime
                else -> 0f
            }
            val pose = HeroPresentation.pose(unit.hero, action, if (f != null) actionClock else visualClock, age, unit.uid)
            val lift = pose.lift * HERO_VISUAL_SCALE
            p.visual.transform().position = Vector3(0f, lift, 0f)
            p.visual.transform().scaleVector = Vector3(modelScale, modelScale, modelScale)
            val enemy = f?.targetUid?.let { fighters[it] }
            val yaw = if (enemy != null) atan2(enemy.x - f!!.x, -(enemy.y - f.y)) * 180 / PI.toFloat()
                else if (f?.team == 1) 0f else 180f
            p.visual.transform().eulerAngles = EulerAngles(pitch = pose.lean, yaw = yaw + pose.turn, roll = pose.roll)
            p.limbs.forEach { (name, limb) ->
                limb.transform().eulerAngles = EulerAngles(pitch = when (name) {
                    "leftArmPivot" -> pose.leftArm; "rightArmPivot" -> pose.rightArm
                    "leftLegPivot" -> pose.leftLeg; else -> pose.rightLeg
                })
            }
            // Read only the animated body, excluding labels, hit volumes and FX.
            // The SDK publishes synchronized bounds; newly created clones can
            // need one frame before these are available.
            val body = p.visual.getVisualBounds(p.root, recursive = true, enabledOnly = true)
            val bodyCenter = if (body.isEmpty()) Vector3.ZERO else body.center
            val head = (if (body.isEmpty()) templateHeights.getValue(unit.heroId) * modelScale + lift
                else body.max.y) + .042f
            if (!body.isEmpty() && auditedStatusHeroes.add(unit.heroId)) {
                Log.i("JadeChessStatus", "hero=${unit.heroId} scope=animated_body owner=${p.root.getName()} min=${body.min} max=${body.max} anchorX=${bodyCenter.x} anchorZ=${bodyCenter.z} statusY=${head + .025f} visual=UNOBSERVED")
            }
            val ratio = f?.let { (it.health / it.maxHealth).coerceIn(0f, 1f) } ?: 1f
            val stats = if (f == null && (changed || p.maximumHealth < 0f)) CombatMath.stats(unit, match.human.units) else null
            val maximum = f?.maxHealth ?: stats?.maxHealth ?: p.maximumHealth
            val manaValue = f?.mana ?: 0f
            val manaRatio = (manaValue / Catalog.MAX_MANA).coerceIn(0f, 1f)
            if (maximum != p.maximumHealth) {
                p.maximumHealth = maximum
                p.hpMarks.components[ModelComponent::class.java]!!.materials[0] = ticksMaterial(maximum, 100)
                p.hpMaximum.components[ModelComponent::class.java]!!.materials[0] =
                    numberMaterial("/${ceil(maximum).toInt()}")
                p.hpText = ""
            }
            if (p.starCount != unit.star) {
                p.stars.forEachIndexed { i, e ->
                    e.enabled = i < unit.star
                }
                p.starCount = unit.star
            }
            p.status.transform().position = Vector3(bodyCenter.x, head + .025f, bodyCenter.z)
            p.label.transform().position = Vector3(bodyCenter.x, head + .163f, bodyCenter.z)
            p.dragMarker.transform().position = Vector3(bodyCenter.x, head + .225f, bodyCenter.z)
            p.stars.forEachIndexed { i, e ->
                e.transform().position = Vector3(bodyCenter.x + (i - (unit.star - 1) / 2f) * .041f,
                    head + .116f, bodyCenter.z)
            }
            fun setDigits(entities: List<Entity>, text: String, y: Float) {
                entities.forEachIndexed { i, digit ->
                    digit.enabled = i < text.length
                    if (i < text.length) {
                        digit.components[ModelComponent::class.java]!!.materials[0] = numberMaterial(text[i].toString())
                        digit.transform().position = Vector3(-.06f + (i - (text.length - 1) / 2f) * .014f, y, .002f)
                    }
                }
            }
            val hpText = StatusBarScale.digits(f?.health ?: maximum, maximum)
            if (hpText != p.hpText) { setDigits(p.hpDigits, hpText, .048f); p.hpText = hpText }
            val manaText = StatusBarScale.digits(manaValue, 100f)
            if (manaText != p.manaText) { setDigits(p.manaDigits, manaText, -.001f); p.manaText = manaText }
            p.hp.enabled = ratio > 0f
            p.hp.transform().scaleVector = Vector3(ratio.coerceAtLeast(.001f), 1f, 1f)
            p.hp.transform().position = Vector3((ratio - 1f) * .097f, .024f, .001f)
            p.hp.components[ModelComponent::class.java]!!.materials[0] = material(if (f?.team == 1) ChessTheme.Clay else ChessTheme.Jade)
            p.mana.enabled = manaRatio > 0f
            p.mana.transform().scaleVector = Vector3(manaRatio.coerceAtLeast(.001f), 1f, 1f)
            p.mana.transform().position = Vector3((manaRatio - 1f) * .097f, -.025f, .001f)
            val shieldRatio = f?.let { (it.shield / it.maxHealth).coerceIn(0f, 1f) } ?: 0f
            p.shield.enabled = shieldRatio > 0
            p.shield.transform().scaleVector = Vector3(shieldRatio.coerceAtLeast(.001f), 1f, 1f)
            p.shield.transform().position = Vector3((shieldRatio - 1f) * .097f, .035f, .001f)
            highlight(p, (controller.drag.uid ?: selected) == unit.uid, controller.drag.uid == unit.uid)
            val canSelect = !match.autoPlayHuman && controller.engine.canEditUnit(0, unit.uid)
            if (canSelect && p.collider !in taps) interactive(p.collider, heroShape) { controller.selectUnit(unit.uid) }
            else if (!canSelect && p.collider in taps) {
                taps.remove(p.collider)
                p.collider.components.remove(HoverEffectComponent::class.java)
                p.collider.components.remove(InteractableComponent::class.java)
            }
            if (canSelect) draggable[p.collider] = unit.uid else draggable.remove(p.collider)
        }
        controller.updateDragPreview(isDragOverShop())
        val dragSlot = if (controller.dragOverShop) null else controller.drag.point?.let { BoardLayout.slotAt(it.x, it.z) }
        legalMarkers.forEach { marker ->
            marker.enabled = controller.selectedUid?.let { controller.engine.canEditUnit(0, it) } == true &&
                (match.phase == Phase.PREPARATION || (legalSlots[marker] ?: 0) < 0)
            val isTarget = dragging && legalSlots[marker] == dragSlot
            marker.transform().scaleVector = if (isTarget) Vector3(2.6f, 1f, 2.6f) else Vector3(1f, 1f, 1f)
            marker.components[ModelComponent::class.java]!!.materials[0] = material(if (isTarget) ChessTheme.Brass else ChessTheme.Jade)
        }
        if (!match.paused && match.phase != Phase.FINISHED) previews.forEach { map -> map.forEach { (uid, p) ->
            val pose = HeroPresentation.pose(Catalog.hero(p.heroId), HeroAction.READY, visualClock, 0f, uid)
            p.visual.transform().position = Vector3(0f, pose.lift * HERO_VISUAL_SCALE, 0f)
        } }
        updateEffects(match, battle, visualStep)
    }

    private fun updatePreviews(match: MatchSnapshot) {
        val key = match.players.drop(1).joinToString { p -> "${p.id}:${p.health}:${p.units.filter { it.onBoard }}" }
        if (key == previewKey) return
        previewKey = key
        match.players.drop(1).forEachIndexed { index, player ->
            val map = previews[index]
            val units = player.units.filter { it.onBoard }.take(7)
            map.keys.filter { id -> units.none { it.uid == id } }.toList().forEach { remove(map.remove(it)!!) }
            units.forEach { unit ->
                if (map[unit.uid]?.heroId != unit.heroId) map.remove(unit.uid)?.let(::remove)
                val p = map.getOrPut(unit.uid) { pawn(previewRoots[index], unit, false, preview = true) }
                p.root.transform().position = position(unit)
                val scale = (1f + (unit.star - 1) * .07f) * HERO_VISUAL_SCALE
                p.visual.transform().scaleVector = Vector3(scale, scale, scale)
                p.visual.transform().eulerAngles = EulerAngles(yaw = 180f)
            }
        }
    }

    private fun updateEffects(match: MatchSnapshot, battle: BattleSnapshot?, dt: Float) {
        val key = "${match.round}:${battle?.id}"
        if (key != lastBattleKey) {
            audio.silence()
            effects.clear()
            cues.clear()
            lastEvent = if (lastBattleKey.endsWith(":null")) 0 else battle?.events?.lastOrNull()?.sequence ?: 0
            lastBattleKey = key
        }
        if (battle != null) {
            battle.events.filter { it.sequence > lastEvent }.forEach { event ->
                if ((battle.elapsed - event.time) / battle.speedMultiplier > .6f) return@forEach
                val first = cues.first(event)
                if (!match.paused && first) pawns[event.actor]?.let { actor ->
                    audio.play(event, Catalog.hero(actor.heroId), actor.current + Vector3(0f, .14f, 0f), battle.elapsed)
                }
                if (!match.paused) {
                    val actor = renderFighters[event.actor] ?: return@forEach
                    // Preserve the resolved event's endpoints, including fallen or moving targets.
                    fun point(x: Float, y: Float) = Vector3((x - BoardLayout.halfColumns) * PITCH,
                        .026f, (BoardLayout.halfRows - y) * PITCH)
                    val a = if (event.fromX.isFinite()) point(event.fromX, event.fromY)
                        else pawns[event.actor]?.current ?: return@forEach
                    val b = if (event.toX.isFinite()) point(event.toX, event.toY)
                        else pawns[event.target]?.current ?: return@forEach
                    effects.play(event, Catalog.hero(actor.heroId), a, b, first)
                }
            }
            lastEvent = battle.events.lastOrNull()?.sequence ?: lastEvent
        }
        if (battle == null) effects.clear()
        else effects.update(dt, pawns.mapValues { it.value.current })
    }

    fun addPanel(entity: Entity, name: String, pos: Vector3, yaw: Float = 0f, pitch: Float = 0f) {
        entity.setName(name); owner.addChild(entity)
        entity.transform().position = pos; entity.transform().eulerAngles = EulerAngles(yaw = yaw, pitch = pitch)
        panels[name] = entity
    }
    fun measurePanel(name: String, widthMeters: Float, heightMeters: Float, rootX: Float = 0f, rootY: Float = 0f) {
        if (widthMeters <= 0 || heightMeters <= 0 || !widthMeters.isFinite() || !heightMeters.isFinite()) return
        val size = widthMeters to heightMeters
        if (panelSizes[name] != size) { panelSizes[name] = size; auditedPanels.remove(name) }
        panelOrigins[name] = rootX to rootY
    }
    fun measureShopArea(left: Float, top: Float, right: Float, bottom: Float) {
        shopArea = floatArrayOf(left, top, right, bottom)
    }
    private data class ShopDropZone(val region: BoardLayout.DropRegion, val farY: Float, val nearY: Float)
    private fun shopDropZone(): ShopDropZone? {
        val panel = panels["hud"] ?: return null
        val (width, height) = panelSizes["hud"] ?: return null
        val (rootX, rootY) = panelOrigins["hud"] ?: return null
        val area = shopArea ?: return null
        // Compose uses a top-left origin and downward Y; the attachment uses
        // its centre and upward Y. The SDK converts that plane into arena space.
        val corners = listOf(area[0], area[2]).flatMap { x ->
            listOf(area[1], area[3]).map { y ->
                panel.convertPositionTo(Vector3(x - rootX - width / 2, height / 2 - (y - rootY), 0f), arena)
            }
        }
        val minZ = corners.minOf { it.z }; val maxZ = corners.maxOf { it.z }
        if (maxZ - minZ < .001f) return null
        return ShopDropZone(BoardLayout.DropRegion(corners.minOf { it.x }, corners.maxOf { it.x }, minZ, maxZ),
            corners.minBy { it.z }.y, corners.maxBy { it.z }.y)
    }
    fun isDragOverShop() = controller.drag.point?.let { shopDropZone()?.region?.contains(it) } == true
    private data class PanelBounds(val min: Vector3, val max: Vector3)
    private fun panelBounds(entity: Entity, width: Float, height: Float): PanelBounds {
        val corners = listOf(-1f, 1f).flatMap { x ->
            listOf(-1f, 1f).map { y -> entity.convertPositionTo(Vector3(x * width / 2, y * height / 2, 0f), owner) }
        }
        return PanelBounds(Vector3(corners.minOf { it.x }, corners.minOf { it.y }, corners.minOf { it.z }),
            Vector3(corners.maxOf { it.x }, corners.maxOf { it.y }, corners.maxOf { it.z }))
    }
    private fun auditPanels(dt: Float) {
        panelAuditTime += dt
        if (panelAuditTime < 1f) return
        panelAuditTime = 0f
        if (!heightsAudited) {
            val heights = surfaceTiles.map { it.getVisualBounds(owner).max.y }
            Log.i("JadeChessPanels", "boardSurfaceHeights=$heights uniform=${if (heights.max() - heights.min() < .001f) "PASS" else "FAIL"}")
            heightsAudited = true
        }
        if (!pavilionAudited) {
            pavilionSupports.forEach { (rafter, beam) ->
                val a = rafter.getVisualBounds(owner); val b = beam.getVisualBounds(owner)
                val gap = a.min.y - b.max.y
                check(abs(gap) <= .001f) { "Pavilion support gap: $gap" }
                Log.i("JadeChessPlacement", "revision=V1.6 constraint=pavilion_rafter_beam target=${rafter.getName()} scope=body reference=${beam.getName()} owner=placementOwner finalMin=${a.min} finalMax=${a.max} referenceMax=${b.max} gap=$gap epsilon=.001 numeric=PASS visual=UNOBSERVED")
            }
            pavilionAudited = true
        }
        panels.filterKeys { it !in auditedPanels }.forEach { (name, entity) ->
            val (width, height) = panelSizes[name] ?: return@forEach
            val bounds = panelBounds(entity, width, height)
            if (name == "hud") {
                val frames = opaqueTable.map { it.getVisualBounds(owner) }
                val frontZ = frames.maxOf { it.max.z }
                val tableTop = frames.maxOf { it.max.y }
                val depthGap = bounds.min.z - frontZ
                if (depthGap < .10f - .001f) {
                    entity.transform().position += Vector3(0f, 0f, .10f - depthGap)
                    Log.i("JadeChessPanels", "panel=$name depthGap=$depthGap movedTo=${entity.transform().position} check=PENDING_FRAME_SYNC")
                    return@forEach
                }
                val viewerZ = -SCENE_FORWARD_OFFSET_Z
                val farDepth = max(abs(bounds.min.z - viewerZ), abs(bounds.max.z - viewerZ))
                val nearestFront = abs(frontZ - viewerZ)
                val ceiling = listOf(1.25f, 1.75f).minOf { eye ->
                    eye + (tableTop - eye) * farDepth / nearestFront
                } - .04f
                if (bounds.max.y > ceiling + .001f) {
                    val t = entity.transform()
                    t.position += Vector3(0f, ceiling - bounds.max.y, 0f)
                    // convertPositionTo reads the frame's synchronized transform.
                    // Verify next frame after the new position reaches that cache.
                    Log.i("JadeChessPanels", "panel=$name movedTo=${t.position} measuredMeters=${width}x$height check=PENDING_FRAME_SYNC")
                    return@forEach
                }
                Log.i("JadeChessPanels", "panel=$name measuredMeters=${width}x$height min=${bounds.min} max=${bounds.max} tableFrontZ=$frontZ depthGap=$depthGap rayCeiling=$ceiling numeric=${if (bounds.max.y <= ceiling + .001f && depthGap >= .099f) "PASS" else "FAIL"} visual=UNOBSERVED")
                shopDropZone()?.let { zone ->
                    val benchFront = BoardLayout.BENCH_Z + .10f
                    check(zone.region.minZ > benchFront) { "Shop sale area overlaps the bench" }
                    Log.i("JadeChessDrag", "shopDropRegion=${zone.region} owner=arena source=measured_hud_center benchFrontZ=$benchFront separated=PASS footGrabBox=.21x.34x.21 occupiedSlots=${slotTargets.size} visual=UNOBSERVED")
                }
            } else Log.i("JadeChessPanels", "panel=$name measuredMeters=${width}x$height min=${bounds.min} max=${bounds.max} numeric=MEASURED visual=UNOBSERVED")
            auditedPanels.add(name)
        }
    }
    fun syncMusic() { audio.setMusic(controller.musicEnabled && controller.isForeground && !controller.engine.paused) }
    fun silenceAudio() { audio.silence(); audio.setMusic(false); if (::effects.isInitialized) effects.clear() }
    fun destroy() {
        if (disposed) return
        disposed = true; pawns.clear(); previews.forEach { it.clear() }; taps.clear(); draggable.clear(); slotTargets.clear(); panels.clear()
        audio.destroyPlayers()
        if (::effects.isInitialized) effects.destroyEntities()
        placement.destroy()
        audio.closeResources()
        meshes.values.forEach { it.close() }
        materials.values.forEach { it.close() }
        fabricMaterials.values.forEach { it.close() }
        decoratedMaterials.values.forEach { it.close() }
        textures.forEach { it.close() }
        if (::effects.isInitialized) effects.closeResources()
        physicsMaterial.close(); tileShape.close(); heroShape.close()
        meshes.clear(); materials.clear(); templates.clear()
    }
    companion object {
        private const val PITCH = BoardLayout.PITCH
        private const val BOARD_ROOT_Y = .24f
        private const val BOARD_Z = -2.25f
        private const val SCENE_FORWARD_OFFSET_Z = -.30f
        private const val HERO_VISUAL_SCALE = .85f
        private const val STAR_Y = .36f // Initial transform only; render uses each hero's measured height.
        val GENERATED = setOf("copper_guard", "forest_ranger", "rune_apprentice", "ember_acolyte", "moss_guard")
    }
}
