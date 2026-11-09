package com.pico.miniautochess.content

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
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
    val context = LocalContext.current
    val scene = remember { ChessScene(controller, context.applicationContext).also { controller.scene = it } }
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
    MatchResultDialog(controller)
}

@Composable
private fun Surface(name: String, controller: GameController, width: Int, height: Int,
                    content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    Column(Modifier.width(width.dp).height(height.dp).onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInRoot()
        with(density) { controller.scene?.measurePanel(name,
            converter.dpToLength(coordinates.size.width.toDp(), LengthUnit.Meters),
            converter.dpToLength(coordinates.size.height.toDp(), LengthUnit.Meters),
            converter.dpToLength(origin.x.toDp(), LengthUnit.Meters),
            converter.dpToLength(origin.y.toDp(), LengthUnit.Meters)) }
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
        Text(label, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
    }
}

@Composable
private fun Status(controller: GameController) {
    val state = controller.ui
    val overtime = state.phase == Phase.BATTLE && state.battles.any { it.outcome == null && it.overtime }
    val result = state.results.firstOrNull { it.playerId == 0 }
    val lossAlert = !state.human.alive ||
        (state.phase in listOf(Phase.BATTLE, Phase.TRANSITION) && result?.result == "落败")
    val damageText = result?.let {
        if (it.result == "落败") "扣血：回合${it.baseDamage} + 存活人口${it.survivorPopulation} = ${it.nominalDamage}，实际扣${it.damage}"
        else "本回合不扣生命"
    }.orEmpty()
    val incomeText = result?.let {
        if (it.roundPaid) "收入${it.income}金：基础5 + 胜利${it.winGold} + ${it.streakLabel}奖励${it.streakGold} + 利息${it.interest}（余额${it.interestBalance}）· 经验+${it.xpGain}"
        else if (it.winGold > 0) "胜利 +1金已到账 · 等待另一场结束后发基础5金和利息"
        else "等待另一场结束后发基础5金和利息"
    }.orEmpty()
    val resultText = when {
        state.phase == Phase.FINISHED -> state.message + " · 再开一局" +
            if (result != null) "\n$damageText\n$incomeText" else ""
        state.phase in listOf(Phase.BATTLE, Phase.TRANSITION) && result != null ->
            "${if (!state.human.alive) "已淘汰 · " else ""}${result.result} · $damageText\n$incomeText"
        !state.human.alive -> "你已淘汰 · 本局结束 · 再开一局"
        state.phase == Phase.BATTLE -> "${state.battles.getOrNull(state.watchedBattle)?.label.orEmpty()}\n交锋进行中 · 胜利立即 +1 金币 · 落败立即扣血"
        state.phase == Phase.MENU -> "1 位真人 · 3 位棋手 · 点击开始即可招募"
        else -> "每回合基础5金、经验+2 · 先发胜利1金，再按余额每10金利息+1"
    }
    Surface("status", controller, 1280, 600) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("青玉棋会", color = ChessTheme.Jade, style = PicoTheme.typography.titleMedium.copy(fontSize = 36.sp, lineHeight = 45.sp))
            Text("${state.phase.label} · 第 ${state.round.coerceAtLeast(1)} 回合", color = ChessTheme.Ivory,
                style = PicoTheme.typography.bodyMedium.copy(fontSize = 32.sp, lineHeight = 40.sp))
            Text(if (state.paused) "暂停" else if (state.phase in listOf(Phase.MENU, Phase.FINISHED)) " "
                else "${if (overtime) "2倍加速 · " else ""}${ceil(state.secondsLeft).toInt()} 秒", color = ChessTheme.Brass,
                style = PicoTheme.typography.bodyMedium.copy(fontSize = 36.sp, lineHeight = 45.sp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.ranking.forEach { standing ->
                val p = state.players.first { it.id == standing.playerId }
                val standingLabel = if (state.phase == Phase.FINISHED && !state.human.alive && p.alive)
                    "未完赛 · " else "${standing.rank}. "
                Text("$standingLabel${p.name}  ${if (p.alive) "♥${p.health}  ${p.deployed}/${p.level}人" else "已淘汰"}",
                    modifier = Modifier.weight(1f), maxLines = 2,
                    color = if (p.id == 0) ChessTheme.Jade else ChessTheme.Ivory,
                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 32.sp, lineHeight = 40.sp))
            }
        }
        Text(resultText, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (lossAlert) ChessTheme.Clay else ChessTheme.Slate).padding(10.dp),
            color = if (lossAlert) ChessTheme.Ink else ChessTheme.Ivory, maxLines = 3,
            style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
        Text("你的羁绊 · 仅计算上场的不同英雄", color = ChessTheme.Brass,
            style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
        state.human.traits.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { trait ->
                    Column(Modifier.weight(1f)) {
                        Text("${trait.label} ${trait.count} · ${trait.progress}",
                            color = if (trait.tier > 0) ChessTheme.Jade else ChessTheme.Ivory,
                            maxLines = 1, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
                        Text(trait.effect, color = if (trait.tier > 0) ChessTheme.Jade else ChessTheme.Ivory,
                            maxLines = 2, style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp))
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
    "dawn_oracle" to R.drawable.hero_dawn_oracle, "forest_witch" to R.drawable.hero_forest_witch,
    "rune_sentinel" to R.drawable.hero_rune_sentinel, "ember_breaker" to R.drawable.hero_ember_breaker,
)

private fun qualityColor(cost: Int?) = when (cost) {
    1 -> Color(0xFFF5F5F5); 2 -> Color(0xFF55C76B); 3 -> Color(0xFF4B91FF)
    else -> ChessTheme.Slate
}
private fun qualityName(cost: Int) = when (cost) { 1 -> "白色"; 2 -> "绿色"; else -> "蓝色" }

@Composable
private fun HeroPortrait(hero: HeroDef) {
    Image(painterResource(portraits.getValue(hero.id)), "${hero.name}画像",
        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(8.dp)))
}

@Composable
private fun OddsDetails(state: MatchSnapshot) {
    val heroPool = state.pool.toMutableMap()
    // Refresh returns this shop before drawing the first new slot.
    state.human.shop.filterNotNull().forEach { heroPool[it] = heroPool.getValue(it) + 1 }
    val weights = Catalog.availableWeights(state.human.level, heroPool)
    val total = weights.sum()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("共享卡池 · 每英雄1费26 / 2费20 / 3费14份",
            color = ChessTheme.Brass, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
        Text("四位棋手共享；商店占用份数，刷新归还未买卡；卖出与淘汰归还1/3/9份。某费用抽空后只在有概率且有余卡的费用间重分配。",
            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp))
        Text("当前刷新首格实际概率：" + weights.mapIndexed { index, w ->
            "${index + 1}费 ${if (total == 0) "0" else "%.1f".format(java.util.Locale.US, 100f * w / total)}%"
        }.joinToString(" / "), color = ChessTheme.Jade, style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp))
        Text("刷新概率只随人口等级变化，与回合无关：人口 → 1费 / 2费 / 3费",
            color = ChessTheme.Brass, style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp))
        (1..Catalog.MAX_LEVEL).forEach { level ->
            Text("${level}人口  " + Catalog.odds(level).joinToString(" / ") { "$it%" },
                color = if (level == state.human.level) ChessTheme.Jade else ChessTheme.Ivory,
                style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp))
        }
        Catalog.heroList.forEach { hero ->
            Text("${hero.name} · ${hero.cost}费 · 池内可抽 ${state.pool.getValue(hero.id)}/${Catalog.poolSize(hero.cost)}",
                color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp))
        }
    }
}

@Composable
private fun BottomHud(controller: GameController) {
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val state = controller.ui
    val human = state.human
    val canAct = controller.sceneReady && state.phase in listOf(Phase.PREPARATION, Phase.BATTLE, Phase.TRANSITION) && human.alive &&
        !state.paused && !state.autoPlayHuman && !controller.isDragging
    val selected = human.units.firstOrNull { it.uid == controller.selectedUid }
    val cards = if (state.phase == Phase.MENU) Catalog.heroList.take(5).map { it.id } else human.shop
    // The real Attachment host caps content at 1280dp. Design inside that width.
    Surface("hud", controller, 1280, 600) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("金币 ${human.gold}", modifier = Modifier.weight(1f),
                color = ChessTheme.Brass, style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp, lineHeight = 43.sp))
            Text("${human.streakLabel} · 加成${human.streakGold}金\n预计利息 ${Catalog.interest(human.gold)}金",
                modifier = Modifier.weight(1f), color = ChessTheme.Brass,
                style = PicoTheme.typography.titleMedium.copy(fontSize = 27.sp, lineHeight = 34.sp))
            Text(if (state.phase == Phase.BATTLE) "本场 ${human.deployed} · 下轮 ${human.level}"
                else "人口 ${human.deployed}/${human.level}", modifier = Modifier.weight(1f),
                color = ChessTheme.Brass, style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp, lineHeight = 43.sp))
        }
        Button(onClick = { controller.clearSelection(); controller.showRules = false; controller.showOdds = !controller.showOdds },
            modifier = Modifier.fillMaxWidth().height(48.dp), contentPadding = PaddingValues(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ChessTheme.Slate, contentColor = ChessTheme.Ivory)) {
            Text("${human.level}人口 · 刷新概率 " +
                Catalog.odds(human.level).mapIndexed { i, p -> "${i + 1}费 $p%" }.joinToString(" / ") +
                if (controller.showOdds) " · 返回商店" else " · 查看卡池",
                style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 33.sp), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (human.level < Catalog.MAX_LEVEL)
                    "经验 ${human.xp}/${human.xpRequired}\n升级还差 ${human.xpRemaining}" else "最高人口 7\n无需再升级",
                    color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                Action("经验+4 4金", canAct && human.gold >= 4 && human.level < 7, modifier = Modifier.fillMaxWidth()) {
                    controller.perform { controller.engine.buyXp(0) }
                }
                Action("刷新 · 2金", canAct && human.gold >= 2, modifier = Modifier.fillMaxWidth()) {
                    controller.perform { controller.engine.refresh(0) }
                }
                Action("自动布阵", canAct && state.phase == Phase.PREPARATION, modifier = Modifier.fillMaxWidth()) { controller.perform { controller.engine.arrange(0) } }
            }
            Box(Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                with(density) { controller.scene?.measureShopArea(
                    converter.dpToLength(bounds.left.toDp(), LengthUnit.Meters),
                    converter.dpToLength(bounds.top.toDp(), LengthUnit.Meters),
                    converter.dpToLength(bounds.right.toDp(), LengthUnit.Meters),
                    converter.dpToLength(bounds.bottom.toDp(), LengthUnit.Meters)) }
            }.then(if (controller.dragOverShop) Modifier.border(4.dp, ChessTheme.Brass, RoundedCornerShape(12.dp)) else Modifier)) {
                if (controller.showOdds) {
                    OddsDetails(state)
                } else if (controller.showRules) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("交战期间可买牌、刷新、买经验、整理或卖出备战棋子。上场阵容锁定；只合成备战席，涉及上场棋子的合成下轮处理。最多上场7人、备战8人。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Text("每轮基础5金、经验+2；先发胜利1金，再按余额每10金利息+1。连胜/连败2–3场+1金、4场+2金、5场以上+3金，平局清零；连胜败奖励不参与当轮计息。真人和人机规则一致。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Text("扣血 = 回合基础 + 对方存活人口。1—10回合基础为1/1/2/2/3/3/4/4/5/5；11回合起为6，每两回合+1。你淘汰或夺冠即结束本局；每个存活英雄算1人口，平局不扣。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Text("回血来源：辅助治疗、林地持续恢复。基础魔抗20；卫士提升双防，辅助提供全队魔抗。范围法术主目标全额，副目标65%。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Text("蓝满100释放技能；普攻、受击回蓝，法师/辅助还会持续回蓝。法师偏范围爆发、卫士护盾与承伤、射手持续输出、辅助治疗。三人存活时，落单者随机打另外两人之一的当前阵容镜像；镜像不影响本体血量或卡池。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
                        Text("25秒未结束进入2倍加速，治疗、回血和新增护盾减半；再15秒仍僵持才比较人数与剩余生命比例。棋子本体或脚下槽位都能拖；拖到商店中央松手卖出。",
                            color = ChessTheme.Ivory, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
                        Catalog.traitDescriptions().forEach { Text("${it.first}：${it.second}",
                            color = ChessTheme.Jade, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp)) }
                    }
                } else if (selected != null && !controller.isDragging) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${selected.hero.name} ${"★".repeat(selected.star)} · ${selected.hero.faction.label}/${selected.hero.job.label}\n${selected.hero.skillName} · ${selected.hero.skillDescription}", color = ChessTheme.Ivory,
                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Action("卖出 · ${selected.value}金", canAct && controller.engine.canEditUnit(0, selected.uid)) { controller.sell() }
                            Action("返回商店") { controller.clearSelection() }
                        }
                        Text("拖动时棋子保持金色、脚下外框脉动并显示“拖动中”；拖到占用位置双向互换；拖到商店中央区域松手卖出", color = ChessTheme.Jade,
                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp))
                    }
                } else if (state.phase == Phase.FINISHED) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ranking.forEach { standing ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(ChessTheme.Slate).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val unfinished = !human.alive && standing.eliminatedRound == null
                                Text(if (unfinished) "未完赛" else "第${standing.rank}名", color = ChessTheme.Brass, maxLines = 1,
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 34.sp))
                                Text(standing.name, color = if (standing.playerId == 0) ChessTheme.Jade else ChessTheme.Ivory,
                                    maxLines = 1, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                                Text("生命 ${standing.health}\n胜场 ${standing.wins}", color = ChessTheme.Ivory,
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp, lineHeight = 38.sp))
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEachIndexed { index, id ->
                            val hero = id?.let(Catalog::hero)
                            Button(onClick = { controller.buy(index) },
                                enabled = canAct && hero != null && human.gold >= hero.cost,
                                modifier = Modifier.weight(1f).height(282.dp)
                                    .border(3.dp, qualityColor(hero?.cost), RoundedCornerShape(12.dp)).onSizeChanged {
                                    if (state.phase == Phase.MENU) Log.i("JadeChessPanels", "card=$index measuredPixels=${it.width}x${it.height}")
                                }.semantics {
                                    contentDescription = hero?.let { "招募${it.name}，${qualityName(it.cost)}品质，${it.cost}金币，${it.faction.label}${it.job.label}，卡池剩余${state.pool.getValue(it.id)}" } ?: "商店空位"
                                }, contentPadding = PaddingValues(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ChessTheme.Slate, contentColor = ChessTheme.Ivory)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (hero != null) HeroPortrait(hero)
                                    Text(hero?.name ?: "空位", style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 35.sp), maxLines = 1)
                                    if (hero != null) {
                                        Text("${hero.cost}金 · ${hero.faction.label}\n${hero.job.label}", maxLines = 2,
                                            style = PicoTheme.typography.bodyMedium.copy(fontSize = 25.sp, lineHeight = 30.sp))
                                        Text("池余${state.pool.getValue(hero.id)}", style = PicoTheme.typography.bodyMedium.copy(fontSize = 22.sp, lineHeight = 28.sp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (controller.dragOverShop && selected != null) {
                    Text("松手卖出${selected.hero.name} · 返还 ${selected.value} 金币",
                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp))
                            .background(ChessTheme.Ink).padding(16.dp),
                        color = ChessTheme.Brass, style = PicoTheme.typography.bodyMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
                }
            }
            Column(Modifier.width(200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    controller.showOdds = false
                    controller.showRules = !controller.showRules
                }
                Action(if (controller.musicEnabled) "音乐：开" else "音乐：关", selected = controller.musicEnabled,
                    modifier = Modifier.fillMaxWidth()) { controller.toggleMusic() }
            }
        }
        Text(if (!controller.sceneReady) controller.loading else if (state.phase == Phase.MENU)
            "招募英雄 → 拖进己方棋格 → 准备完毕；开战自动补齐空位"
            else controller.notice, color = ChessTheme.Ivory,
            modifier = Modifier.fillMaxWidth().height(86.dp).padding(6.dp),
            maxLines = 2, style = PicoTheme.typography.bodyMedium.copy(fontSize = 28.sp, lineHeight = 34.sp))
    }
}
