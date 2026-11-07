package com.pico.miniautochess.content

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.container.SpatialContainerStateEvent
import com.pico.spatial.core.container.SpatialContainerStateObserver
import com.pico.spatial.core.container.SpatialContainerStateOwner
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.miniautochess.R
import com.pico.miniautochess.rules.*
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.LocalSpatialContainerStateOwner
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import kotlin.math.ceil

@Composable
fun HomeStage() {
    val controller = remember { GameController() }
    val scene = remember { ChessScene(controller).also { controller.scene = it } }
    val context = LocalContext.current
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val stateOwner = LocalSpatialContainerStateOwner.current
    DisposableEffect(stateOwner) {
        val observer = object : SpatialContainerStateObserver {
            override fun onStateChanged(source: SpatialContainerStateOwner, event: SpatialContainerStateEvent) {
                when (event) {
                    SpatialContainerStateEvent.ON_UNFOCUSED -> controller.background()
                    SpatialContainerStateEvent.ON_FOCUSED -> controller.foreground()
                    else -> Unit
                }
                if (event == SpatialContainerStateEvent.ON_UNFOCUSED || event == SpatialContainerStateEvent.ON_FOCUSED)
                    Log.i("JadeChess", "container=$event phase=${controller.engine.phase} time=${controller.engine.elapsed}")
            }
        }
        stateOwner.stateObservable.addObserver(observer)
        onDispose { stateOwner.stateObservable.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        Component.register(ChessRuntimeComponent::class.java)
        com.pico.spatial.core.ecs.System.register(ChessSystem::class.java)
        onDispose {
            controller.dispose()
            com.pico.spatial.core.ecs.System.unregister(ChessSystem::class.java)
            Component.unregister(ChessRuntimeComponent::class.java)
        }
    }
    SpatialView(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectSpatialTapGesture(context, targetedToEntity = TargetEntity.any()) { scene.tap(it.targetEntity) }
        }.pointerInput(context, density, converter) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }
            detectSpatialDragGesture(context, targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value -> scene.beginDrag(value.targetEntity) },
                onDragEnd = { controller.releaseDrag() }, onDragCancel = { controller.cancelDrag() },
                onDrag = { value -> value.dragAmount.let { scene.dragDelta(meters(it.x), meters(it.y), meters(it.z)) } })
        },
        initial = { content, attachments ->
            try {
                scene.initialize(content)
                attachments.entity("hud")?.let { panel ->
                    scene.addPanel(panel, "hud", Vector3(0f, .15f, -.54f), pitch = -64f)
                }
                attachments.entity("status")?.let { panel ->
                    scene.addPanel(panel, "status", Vector3(0f, 1.16f, -2.75f))
                }
            } catch (error: Exception) {
                controller.sceneReady = false
                controller.loading = "布置失败：${error.message}"
                Log.e("JadeChess", "Scene initialization failed", error)
            }
        },
        attachments = {
            AttachmentPanel("hud") { PicoTheme { BottomHud(controller) } }
            AttachmentPanel("status") { PicoTheme { Status(controller) } }
        },
    )
}

@Composable
private fun Surface(name: String, controller: GameController, width: Int, height: Int,
                    content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    Column(Modifier.width(width.dp).height(height.dp).onSizeChanged { px ->
        with(density) { controller.scene?.measurePanel(name,
            converter.dpToLength(px.width.toDp(), LengthUnit.Meters),
            converter.dpToLength(px.height.toDp(), LengthUnit.Meters)) }
    }.clip(RoundedCornerShape(18.dp)).background(ChessTheme.Ink).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun Action(label: String, enabled: Boolean = true, selected: Boolean = false,
                   modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ChessTheme.Jade else ChessTheme.Slate,
            contentColor = if (selected) ChessTheme.Ink else ChessTheme.Ivory)) {
        Text(label, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp))
    }
}

@Composable
private fun Status(controller: GameController) {
    val state = controller.ui
    val result = state.results.firstOrNull { it.playerId == 0 }
    val lossAlert = !state.human.alive ||
        (state.phase in listOf(Phase.BATTLE, Phase.TRANSITION) && result?.result == "落败")
    val damageText = result?.let {
        if (it.result == "落败") "扣血：回合${it.baseDamage} + 存活人口${it.survivorPopulation} = ${it.nominalDamage}，实际扣${it.damage}"
        else "本回合不扣生命"
    }.orEmpty()
    val incomeText = result?.let {
        if (it.roundPaid) "收入${it.income}金：基础5 + 胜利${it.winGold} + 利息${it.interest}（计息余额${it.interestBalance}）· 经验+${it.xpGain}"
        else if (it.winGold > 0) "胜利 +1金已到账 · 等待另一场结束后发基础5金和利息"
        else "等待另一场结束后发基础5金和利息"
    }.orEmpty()
    val resultText = when {
        state.phase == Phase.FINISHED -> state.message + " · 再开一局" +
            if (result != null) "\n$damageText\n$incomeText" else ""
        state.phase in listOf(Phase.BATTLE, Phase.TRANSITION) && result != null ->
            "${if (!state.human.alive) "已淘汰 · " else ""}${result.result} · $damageText\n$incomeText"
        !state.human.alive -> "你已淘汰 · 生命归零 · 可继续观战"
        state.phase == Phase.BATTLE -> "交锋进行中 · 胜利立即 +1 金币 · 落败立即扣血"
        state.phase == Phase.MENU -> "1 位真人 · 3 位棋手 · 点击开始即可招募"
        else -> "每回合基础5金、经验+2 · 先发胜利1金，再按余额每10金利息+1"
    }
    Surface("status", controller, 1280, 600) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("青玉棋会", color = ChessTheme.Jade, style = PicoTheme.typography.titleMedium.copy(fontSize = 36.sp))
            Text("${state.phase.label} · 第 ${state.round.coerceAtLeast(1)} / 10 回合", color = ChessTheme.Ivory,
                style = PicoTheme.typography.bodyMedium.copy(fontSize = 32.sp))
            Text(if (state.paused) "暂停" else if (state.phase in listOf(Phase.MENU, Phase.FINISHED)) " " else "${ceil(state.secondsLeft).toInt()} 秒", color = ChessTheme.Brass,
                style = PicoTheme.typography.bodyMedium.copy(fontSize = 36.sp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.ranking.forEach { standing ->
                val p = state.players.first { it.id == standing.playerId }
                Text("${standing.rank}. ${p.name}  ${if (p.alive) "♥${p.health}  ${p.deployed}/${p.level}人" else "已淘汰"}",
                    color = if (p.id == 0) ChessTheme.Jade else ChessTheme.Ivory,
                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 32.sp))
            }
        }
        Text(resultText, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (lossAlert) ChessTheme.Clay else ChessTheme.Slate).padding(10.dp),
            color = if (lossAlert) ChessTheme.Ink else ChessTheme.Ivory, maxLines = 3,
            style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
        Text("你的羁绊 · 仅计算上场的不同英雄", color = ChessTheme.Brass,
            style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp))
        state.human.traits.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { trait ->
                    Column(Modifier.weight(1f)) {
                        Text("${trait.label} ${trait.count}/${trait.threshold}${if (trait.tier > 0) "  生效" else ""}",
                            color = if (trait.tier > 0) ChessTheme.Jade else ChessTheme.Ivory,
                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp))
                        Text(trait.effect, color = if (trait.tier > 0) ChessTheme.Jade else ChessTheme.Ivory,
                            maxLines = 1, style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp))
                    }
                }
            }
        }
    }
}

private val portraits = mapOf(
    "copper_guard" to R.drawable.hero_copper_guard, "forest_ranger" to R.drawable.hero_forest_ranger,
    "rune_apprentice" to R.drawable.hero_rune_apprentice, "ember_acolyte" to R.drawable.hero_ember_acolyte,
    "dawn_priest" to R.drawable.hero_dawn_priest, "moss_guard" to R.drawable.hero_moss_guard,
    "rune_archer" to R.drawable.hero_rune_archer, "flame_fighter" to R.drawable.hero_flame_fighter,
    "dawn_archer" to R.drawable.hero_dawn_archer, "forest_blessing" to R.drawable.hero_forest_blessing,
    "rune_sage" to R.drawable.hero_rune_sage, "core_mage" to R.drawable.hero_core_mage,
)

@Composable
private fun BottomHud(controller: GameController) {
    val state = controller.ui
    val human = state.human
    val canAct = controller.sceneReady && state.phase == Phase.PREPARATION && human.alive && !state.paused && !state.autoPlayHuman
    val selected = human.units.firstOrNull { it.uid == controller.selectedUid }
    val cards = if (state.phase == Phase.MENU) Catalog.heroList.take(5).map { it.id } else human.shop
    // The real Attachment host caps content at 1280dp. Design inside that width.
    Surface("hud", controller, 1280, 512) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("金币 ${human.gold}", modifier = Modifier.weight(1f),
                color = ChessTheme.Brass, style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp))
            Text("预计利息 ${Catalog.interest(human.gold)}金", modifier = Modifier.weight(1f),
                color = ChessTheme.Brass, style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp))
            Text("人口 ${human.deployed}/${human.level}", modifier = Modifier.weight(1f),
                color = ChessTheme.Brass, style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (human.level < Catalog.MAX_LEVEL)
                    "经验 ${human.xp}/${human.xpRequired}\n升级还差 ${human.xpRemaining}" else "最高人口 7\n无需再升级",
                    color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                Action("经验+4 4金", canAct && human.gold >= 4 && human.level < 7, modifier = Modifier.fillMaxWidth()) {
                    controller.perform { controller.engine.buyXp(0) }
                }
                Action("刷新 · 2金", canAct && human.gold >= 2, modifier = Modifier.fillMaxWidth()) {
                    controller.perform { controller.engine.refresh(0) }
                }
                Action("自动布阵", canAct, modifier = Modifier.fillMaxWidth()) { controller.perform { controller.engine.arrange(0) } }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (controller.showRules) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("三枚同名同星合成；最多上场7人、备战8人。每轮基础5金、经验+2；先发胜利1金，再按余额每10金利息+1，胜负均有利息。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Text("扣血 = 回合基础 + 对方存活人口。回合1—10基础为1/1/2/2/3/3/4/4/5/5；每个存活英雄算1人口，落败当场扣至最低0，平局不扣。卫士靠前，射手法师靠后。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Text("回血来源：辅助英雄的治疗技能、林地羁绊的持续恢复；没有吸血机制。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Catalog.traitDescriptions().forEach { Text("${it.first}：${it.second}",
                            color = ChessTheme.Jade, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp)) }
                    }
                } else if (selected != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${selected.hero.name} ${"★".repeat(selected.star)} · ${selected.hero.skillName} · ${selected.hero.skillDescription}", color = ChessTheme.Ivory,
                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Action("卖出 · ${selected.value}金", canAct) { controller.sell() }
                            Action("返回商店") { controller.clearSelection() }
                        }
                        Text("点选目标棋格，或按住英雄拖动布阵", color = ChessTheme.Jade,
                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp))
                    }
                } else if (state.phase == Phase.FINISHED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        state.ranking.forEach { Text("第${it.rank}名\n${it.name}\n生命 ${it.health}\n胜场 ${it.wins}",
                            color = if (it.playerId == 0) ChessTheme.Jade else ChessTheme.Ivory,
                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 32.sp)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEachIndexed { index, id ->
                            val hero = id?.let(Catalog::hero)
                            Button(onClick = { controller.buy(index) },
                                enabled = canAct && hero != null && human.gold >= hero.cost,
                                modifier = Modifier.weight(1f).height(240.dp).onSizeChanged {
                                    if (state.phase == Phase.MENU) Log.i("JadeChessPanels", "card=$index measuredPixels=${it.width}x${it.height}")
                                }.semantics {
                                    contentDescription = hero?.let { "招募${it.name}，${it.cost}金币，${it.job.label}" } ?: "已招募"
                                }, contentPadding = PaddingValues(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ChessTheme.Slate, contentColor = ChessTheme.Ivory)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (hero != null) Image(painterResource(portraits.getValue(hero.id)), "${hero.name}画像",
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(8.dp)))
                                    Text(hero?.name ?: "已招募", style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp), maxLines = 1)
                                    if (hero != null) Text("${hero.job.label} ${hero.cost}金",
                                        style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp))
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.phase == Phase.MENU || state.phase == Phase.FINISHED) {
                    Action(if (state.phase == Phase.MENU) "开始棋会" else "再开一局", controller.sceneReady, true,
                        Modifier.fillMaxWidth()) { controller.start() }
                } else {
                    Action(if (state.paused) "继续" else "暂停", selected = state.paused, modifier = Modifier.fillMaxWidth()) { controller.pause() }
                    if (state.phase == Phase.PREPARATION) Action("准备完毕", canAct && human.units.isNotEmpty(), true,
                        Modifier.fillMaxWidth()) { controller.perform { controller.engine.ready() } }
                    else if (state.phase == Phase.BATTLE || state.phase == Phase.TRANSITION)
                        Action("观战 ${state.watchedBattle + 1} ⇄", enabled = state.battles.size > 1, modifier = Modifier.fillMaxWidth()) {
                            controller.perform { controller.engine.watch((state.watchedBattle + 1) % state.battles.size) }
                        }
                    Action(if (state.autoPlayHuman) "接回经营" else "棋手托管", human.alive,
                        state.autoPlayHuman, Modifier.fillMaxWidth()) { controller.autoPlay() }
                }
                Action(if (controller.showRules) "返回商店" else "玩法与羁绊", modifier = Modifier.fillMaxWidth()) {
                    controller.showRules = !controller.showRules
                }
            }
        }
        Text(if (!controller.sceneReady) controller.loading else if (state.phase == Phase.MENU)
            "招募英雄 → 拖进己方棋格 → 准备完毕；开战自动补齐空位"
            else controller.notice, color = ChessTheme.Ivory,
            modifier = Modifier.fillMaxWidth().height(128.dp).padding(10.dp),
            maxLines = 3, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 34.sp))
    }
}
