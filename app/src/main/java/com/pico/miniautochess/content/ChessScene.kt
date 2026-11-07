package com.pico.miniautochess.content

import android.util.Log
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
 * Unbounded Stage scene, all positions in meters. `owner` is identity.
 * The tabletop is centered at (0, .24, -2.25); all four grid surfaces are y=.265.
 * 8x8 grid pitch .23, full footprint 1.84m. Bench is in a separate front strip.
 * Runtime bounds normalize each imported model before clones are made.
 */
class ChessScene(private val controller: GameController) {
    val owner = Entity()
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
    private val taps = mutableMapOf<Entity, () -> Unit>()
    private val draggable = mutableMapOf<Entity, Int>()
    private val panels = mutableMapOf<String, Entity>()
    private val panelSizes = mutableMapOf<String, Pair<Float, Float>>()
    private var panelAuditTime = 0f
    private val auditedPanels = mutableSetOf<String>()
    private var draggingLastFrame = false
    private val pawns = mutableMapOf<Int, Pawn>()
    private val previews = Array(3) { mutableMapOf<Int, Pawn>() }
    private val previewRoots = List(3) { Entity() }
    private val legalMarkers = mutableListOf<Entity>()
    private val audio = ChessAudio()
    private val physicsMaterial = PhysicsMaterialResource().apply { toGlobal() }
    private val tileShape = ShapeResource.createBox(Vector3(.22f, .05f, .22f)).apply { toGlobal() }
    private val pawnShape = ShapeResource.createBox(Vector3(.205f, .30f, .205f)).apply { toGlobal() }
    private var lastBattleKey = ""
    private var lastEvent = 0
    private var previewKey = ""
    private var disposed = false
    private var selected: Int? = null
    private var renderRevision = -1
    private var renderUnits = emptyList<UnitSnapshot>()
    private var renderFighters = emptyMap<Int, FighterSnapshot>()

    private data class Pawn(
        val root: Entity, val visual: Entity, val hp: Entity, val ring: Entity,
        val stars: List<Entity>, val collider: Entity, val heroId: String,
        val limbs: Map<String, Entity>,
        var starCount: Int,
        var current: Vector3 = Vector3.ZERO,
    )

    suspend fun initialize(content: SpatialViewContent) {
        owner.setName("placementOwner")
        arena.setName("arena")
        library.setName("modelLibrary")
        content.addEntity(owner)
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
            template.enabled = false
        }
        audio.initialize(arena)
        effects = CombatEffects(arena)
        controller.sceneReady = true
        controller.loading = "棋会已布置"
        render(controller.engine.snapshot(), 0f)
        Log.i("JadeChess", "SCENE_READY generated=${GENERATED.size} templates=${templates.size} meshes=${meshes.size} materials=${materials.size}")
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
        for (row in 0 until Catalog.BATTLE_ROWS) for (col in 0 until Catalog.BOARD_COLS) {
            val pos = Vector3((col - BoardLayout.halfColumns) * PITCH, .02f, (BoardLayout.halfRows - row) * PITCH)
            val tile = box(arena, "cell_${row}_$col", Vector3(.222f, .01f, .222f),
                if ((row + col) % 2 == 0) ChessTheme.Slate else ChessTheme.Ink, pos)
            if (row == 0 && col == 0) surfaceTiles.add(tile)
            if (row < Catalog.BOARD_ROWS) {
                val slot = (Catalog.BOARD_ROWS - 1 - row) * Catalog.BOARD_COLS + col
                interactive(tile, tileShape) { controller.selectSlot(slot) }
                legalMarkers.add(box(arena, "legal_$slot", Vector3(.028f, .004f, .028f), ChessTheme.Jade,
                    pos + Vector3(-.077f, .008f, .077f)).apply { enabled = false })
            }
        }
        box(arena, "midline", Vector3(1.82f, .004f, .006f), ChessTheme.Brass, Vector3(0f, .027f, 0f))
        for (index in 0 until Catalog.BENCH_SIZE) {
            val p = BoardLayout.position(-index - 1)
            val e = box(arena, "bench_$index", Vector3(.21f, .012f, .21f), ChessTheme.Slate,
                Vector3(p.x, .023f, p.z), .012f)
            interactive(e, pawnShape) { controller.selectSlot(-index - 1) }
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
            for (row in 0 until Catalog.BATTLE_ROWS) for (col in 0 until Catalog.BOARD_COLS) {
                val tile = box(root, "pCell_${row}_$col",
                    Vector3(.222f, .014f, .222f), if ((row + col) % 2 == 0) ChessTheme.Slate else ChessTheme.Ink,
                    Vector3((col - BoardLayout.halfColumns) * PITCH, .008f, (row - BoardLayout.halfRows) * PITCH))
                if (row == 0 && col == 0) surfaceTiles.add(tile)
            }
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
                box(owner, "stoneColumn", Vector3(.16f, .68f, .16f), ChessTheme.Wood,
                    Vector3(side * 4.3f, .34f, z))
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
        box(owner, "courtyardRearBeam", Vector3(8.7f, .18f, .22f), ChessTheme.Wood, Vector3(0f, 2.4f, -7.5f))
        repeat(9) { i ->
            box(owner, "courtyardRafter", Vector3(.085f, .085f, 2.1f), ChessTheme.Wood,
                Vector3((i - 4) * 1f, 2.54f, -6.65f))
        }
        Log.i("JadeChess", "ENVIRONMENT_READY sky=inward_glb texture=2048x1024 floor=stone1024 greenery=deep_leaf wood=courtyard realtimeLights=0")
    }

    /** Seven articulated program figures, distinct from generated static heroes. */
    private fun createPrototypeHero(parent: Entity, hero: HeroDef) {
        val accent = when (hero.faction) {
            Faction.DAWN -> ChessTheme.Dawn; Faction.FOREST -> ChessTheme.Forest
            Faction.RUNE -> ChessTheme.Rune; Faction.EMBER -> ChessTheme.Ember
        }
        box(parent, "torso", Vector3(if (hero.job == Job.GUARD) .086f else .065f, .075f, .045f),
            accent, Vector3(0f, .127f, 0f), .011f)
        box(parent, "cape", Vector3(.080f, .113f, .008f), accent, Vector3(0f, .121f, -.031f), .003f)
        box(parent, "breastplate", Vector3(.051f, .042f, .008f), ChessTheme.Steel, Vector3(0f, .135f, .028f), .005f)
        box(parent, "belt", Vector3(.075f, .015f, .05f), accent, Vector3(0f, .091f, 0f))
        sphere(parent, "head", .033f, ChessTheme.Skin, Vector3(0f, .198f, 0f))
        box(parent, "hair", Vector3(.061f, .019f, .050f), ChessTheme.Ink, Vector3(0f, .221f, -.007f), .008f)
        box(parent, "face", Vector3(.03f, .008f, .006f), ChessTheme.Ink, Vector3(0f, .202f, .031f))
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
                box(parent, "shield", Vector3(.062f, .09f, .018f), accent, Vector3(-.06f, .106f, .04f), .012f)
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
        val ring = Entity(); root.addChild(ring)
        listOf(Vector3(0f, .003f, .084f), Vector3(0f, .003f, -.084f), Vector3(.084f, .003f, 0f), Vector3(-.084f, .003f, 0f))
            .forEachIndexed { index, pos ->
                box(ring, "selection_$index", if (index < 2) Vector3(.18f, .005f, .008f) else Vector3(.008f, .005f, .18f), ChessTheme.Jade, pos)
            }
        ring.enabled = false
        val hp = box(root, "hp", Vector3(.16f, .012f, .006f), ChessTheme.Jade, Vector3(0f, HP_Y, 0f))
        hp.enabled = false
        val starMesh = meshes.getOrPut("starPlane") { MeshResource.createPlane(.038f, .038f).apply { toGlobal() } }
        val starMaterial = starMaterial()
        val stars = List(3) { i ->
            Entity().apply {
                setName("fivePointStar_$i"); root.addChild(this)
                components.set(ModelComponent(starMesh, starMaterial))
                transform().position = Vector3((i - (unit.star - 1) / 2f) * .041f, STAR_Y, .018f)
                transform().eulerAngles = EulerAngles(pitch = -30f)
                enabled = i < unit.star && !preview
            }
        }
        if (!preview) Log.i("JadeChessStars", "uid=${unit.uid} star=${unit.star} shape=five_point size=.038 gap=.041 centered=true starY=$STAR_Y hpY=$HP_Y")
        val collider = Entity(); root.addChild(collider)
        collider.transform().position = Vector3(0f, .15f, 0f)
        if (interact) interactive(collider, pawnShape) { controller.selectUnit(unit.uid) }
        if (preview) { stars.forEach { it.enabled = false }; hp.enabled = false }
        val limbs = visual.getChildren().filter { it.getName().endsWith("Pivot") }.associateBy { it.getName() }
        return Pawn(root, visual, hp, ring, stars, collider, unit.heroId, limbs, unit.star)
    }
    private fun remove(pawn: Pawn) { taps.remove(pawn.collider); draggable.remove(pawn.collider); pawn.root.destroy() }
    private fun position(unit: UnitSnapshot) = BoardLayout.position(unit.slot).let { Vector3(it.x, .026f, it.z) }

    fun tap(entity: Entity?) { if (!disposed && controller.drag.uid == null && entity != null) taps[entity]?.invoke() }
    fun beginDrag(entity: Entity?) {
        val uid = draggable[entity] ?: return
        controller.beginDrag(uid)
    }
    fun dragDelta(x: Float, y: Float, z: Float) {
        // UI Y grows downwards; upward pointing pulls toward the far rows.
        controller.drag.delta(x, y + z)
        render(controller.engine.snapshot(), 0f)
    }
    fun selection(uid: Int?) {
        if (selected == uid) return
        selected = uid
        pawns.forEach { (id, p) -> p.ring.enabled = id == uid }
        legalMarkers.forEach { it.enabled = uid != null && controller.engine.phase == Phase.PREPARATION }
    }

    fun render(match: MatchSnapshot, dt: Float) {
        if (disposed || templates.size != Catalog.heroList.size) return
        auditPanels(dt)
        controller.drag.validate()
        val dragging = controller.drag.uid != null
        val battle = if (match.phase == Phase.BATTLE || match.phase == Phase.TRANSITION) match.battles.getOrNull(match.watchedBattle) else null
        if (renderRevision == match.revision && (battle == null || match.paused) && !dragging && !draggingLastFrame) return
        draggingLastFrame = dragging
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
            val target = if (dragPoint != null) Vector3(dragPoint.x, .08f, dragPoint.z)
                else if (f != null) Vector3((f.x - BoardLayout.halfColumns) * PITCH, .026f, (BoardLayout.halfRows - f.y) * PITCH)
                else position(unit)
            val blend = if (dt <= 0 || battle == null) 1f else (dt * 14f).coerceAtMost(1f)
            val distance = (target - p.current).let { sqrt(it.x * it.x + it.z * it.z) }
            val moving = f != null && distance > .002f && !match.paused
            p.current += (target - p.current) * blend
            p.root.transform().position = p.current
            p.root.enabled = f == null || f.health > 0
            val starScale = 1f + (unit.star - 1) * .07f
            val attack = if (f != null) (battle!!.elapsed - f.actionTime).let { age ->
                if (age in 0f.. .28f) sin(age / .28f * PI.toFloat()) else 0f
            } else 0f
            val stride = if (moving) sin(battle!!.elapsed * 14f + unit.uid) else 0f
            p.visual.transform().position = Vector3(0f, attack * .012f + abs(stride) * .008f, 0f)
            p.visual.transform().scaleVector = Vector3(starScale, starScale, starScale)
            val enemy = f?.targetUid?.let { fighters[it] }
            val yaw = if (enemy != null) atan2(enemy.x - f!!.x, -(enemy.y - f.y)) * 180 / PI.toFloat()
                else if (f?.team == 1) 0f else 180f
            p.visual.transform().eulerAngles = EulerAngles(pitch = attack * (if (unit.hero.job == Job.GUARD) 12f else -6f), yaw = yaw)
            p.limbs.forEach { (name, limb) ->
                val sign = if (name.startsWith("left")) 1f else -1f
                limb.transform().eulerAngles = EulerAngles(pitch =
                    if (name.contains("Leg")) stride * sign * 24f
                    else -stride * sign * 18f - attack * (if (unit.hero.job == Job.GUARD) 65f else 40f))
            }
            p.hp.enabled = f != null && f.health > 0
            if (f != null) {
                val ratio = (f.health / f.maxHealth).coerceIn(.001f, 1f)
                p.hp.transform().scaleVector = Vector3(ratio, 1f, 1f)
                p.hp.transform().position = Vector3((ratio - 1f) * .08f, HP_Y, 0f)
                p.hp.components[ModelComponent::class.java]!!.materials[0] = material(if (f.team == 0) ChessTheme.Jade else ChessTheme.Clay)
            }
            if (p.starCount != unit.star) {
                p.stars.forEachIndexed { i, e ->
                    e.enabled = i < unit.star
                    e.transform().position = Vector3((i - (unit.star - 1) / 2f) * .041f, STAR_Y, .018f)
                }
                p.starCount = unit.star
                Log.i("JadeChessStars", "uid=${unit.uid} star=${unit.star} centered=true starY=$STAR_Y hpY=$HP_Y")
            }
            p.ring.enabled = selected == unit.uid
            val canSelect = match.phase == Phase.PREPARATION && !match.paused && !match.autoPlayHuman && match.human.units.any { it.uid == unit.uid }
            if (canSelect && p.collider !in taps) interactive(p.collider, pawnShape) { controller.selectUnit(unit.uid) }
            else if (!canSelect && p.collider in taps) {
                taps.remove(p.collider)
                p.collider.components.remove(HoverEffectComponent::class.java)
                p.collider.components.remove(InteractableComponent::class.java)
            }
            if (canSelect) draggable[p.collider] = unit.uid else draggable.remove(p.collider)
        }
        legalMarkers.forEach { it.enabled = controller.selectedUid != null && match.phase == Phase.PREPARATION }
        updateEffects(match, battle, dt)
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
                val scale = 1f + (unit.star - 1) * .07f
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
            lastEvent = battle?.events?.lastOrNull()?.sequence ?: 0
            lastBattleKey = key
        } else if (battle != null && match.phase == Phase.BATTLE) {
            battle.events.filter { it.sequence > lastEvent }.forEach { event ->
                if (!match.paused) pawns[event.actor]?.let { actor ->
                    audio.play(event.kind, actor.current + Vector3(0f, .14f, 0f), battle.elapsed)
                }
                if (!match.paused) {
                    val a = pawns[event.actor]; val b = pawns[event.target]
                    if (a != null && b != null) effects.play(event, Catalog.hero(a.heroId), a.current, b.current)
                }
            }
            lastEvent = battle.events.lastOrNull()?.sequence ?: lastEvent
        }
        if (match.paused || match.phase != Phase.BATTLE) effects.clear() else effects.update(dt)
    }

    fun addPanel(entity: Entity, name: String, pos: Vector3, yaw: Float = 0f, pitch: Float = 0f) {
        entity.setName(name); owner.addChild(entity)
        entity.transform().position = pos; entity.transform().eulerAngles = EulerAngles(yaw = yaw, pitch = pitch)
        panels[name] = entity
    }
    fun measurePanel(name: String, widthMeters: Float, heightMeters: Float) {
        if (widthMeters <= 0 || heightMeters <= 0 || !widthMeters.isFinite() || !heightMeters.isFinite()) return
        val size = widthMeters to heightMeters
        if (panelSizes[name] != size) { panelSizes[name] = size; auditedPanels.remove(name) }
    }
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
                val farDepth = max(abs(bounds.min.z), abs(bounds.max.z))
                val nearestFront = abs(frontZ)
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
            } else Log.i("JadeChessPanels", "panel=$name measuredMeters=${width}x$height min=${bounds.min} max=${bounds.max} numeric=MEASURED visual=UNOBSERVED")
            auditedPanels.add(name)
        }
    }
    fun silenceAudio() { audio.silence(); if (::effects.isInitialized) effects.clear() }
    fun destroy() {
        if (disposed) return
        disposed = true; pawns.clear(); previews.forEach { it.clear() }; taps.clear(); draggable.clear(); panels.clear()
        audio.destroyPlayers()
        if (::effects.isInitialized) effects.destroyEntities()
        owner.destroy()
        audio.closeResources()
        meshes.values.forEach { it.close() }
        materials.values.forEach { it.close() }
        fabricMaterials.values.forEach { it.close() }
        decoratedMaterials.values.forEach { it.close() }
        textures.forEach { it.close() }
        if (::effects.isInitialized) effects.closeResources()
        physicsMaterial.close(); tileShape.close(); pawnShape.close()
        meshes.clear(); materials.clear(); templates.clear()
    }
    companion object {
        private const val PITCH = BoardLayout.PITCH
        private const val BOARD_ROOT_Y = .24f
        private const val BOARD_Z = -2.25f
        // Tallest prototype: .269m * 1.14 (three stars) + .020m animation = .327m.
        // Star lower edge is .3435m; the health bar has an additional vertical gap.
        private const val STAR_Y = .36f
        private const val HP_Y = .405f
        val GENERATED = setOf("copper_guard", "forest_ranger", "rune_apprentice", "ember_acolyte", "moss_guard")
    }
}
