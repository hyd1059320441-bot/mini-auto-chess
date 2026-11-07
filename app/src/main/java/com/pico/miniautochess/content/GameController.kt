package com.pico.miniautochess.content

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.miniautochess.rules.*

/** Main-thread UI adapter. Scene snapshots never participate in parent composition. */
class GameController {
    val engine = GameEngine(seed = (java.lang.System.currentTimeMillis() % Int.MAX_VALUE).toInt())
    val drag = DragPlacement(engine)
    var ui by mutableStateOf(engine.snapshot())
        private set
    var selectedUid by mutableStateOf<Int?>(null)
        private set
    var notice by mutableStateOf("点选后点棋格，或按住棋子拖动、松手落格")
        private set
    var sceneReady by mutableStateOf(false)
    var loading by mutableStateOf("正在布置棋会")
    var showRules by mutableStateOf(false)
    private var uiTime = 0f
    private var priorPhase = Phase.MENU
    private var sceneSnapshot = ui
    private var disposed = false
    private var foreground = true
    private var discardNextDelta = true
    private var loggedCombatRound = -1
    var scene: ChessScene? = null

    fun frame(dt: Float) {
        if (disposed || !sceneReady || !foreground || !dt.isFinite() || dt < 0) return
        // Initial loading and returning from the background must not catch up
        // unattended time. Foreground UI stalls are elapsed playing time.
        val step = if (discardNextDelta) 0f else dt
        discardNextDelta = false
        engine.advance(step)
        if (sceneSnapshot.revision != engine.stateRevision) sceneSnapshot = engine.snapshot()
        val fresh = sceneSnapshot
        scene?.render(fresh, step)
        uiTime += step
        val resultChanged = fresh.results != ui.results || fresh.players.map { it.health } != ui.players.map { it.health }
        if (resultChanged) {
            Log.i("JadeChessResults", "round=${fresh.round} phase=${fresh.phase} results=${fresh.results} humanGold=${fresh.human.gold} humanXp=${fresh.human.xp} humanLevel=${fresh.human.level} ranking=${fresh.ranking.map { "${it.name}:${it.health}:rank${it.rank}" }}")
            if (!fresh.human.alive) { drag.cancel(); clearSelection(); showRules = false }
        }
        if (fresh.phase != priorPhase) {
            if (priorPhase == Phase.BATTLE) logCombat(fresh)
            drag.cancel()
            selectedUid = null
            scene?.selection(null)
            notice = fresh.message
            Log.i("JadeChess", "phase=${fresh.phase} round=${fresh.round} time=${fresh.elapsed} humanArmy=${fresh.human.deployed}/${fresh.human.level} bench=${fresh.human.units.count { !it.onBoard }}")
            priorPhase = fresh.phase
            publish()
        } else if (resultChanged) {
            // A battle can finish while its concurrent neighbour is still running.
            notice = fresh.message
            publish()
        } else if (uiTime >= .5f) publish()
    }

    private fun publish() {
        uiTime = 0f
        ui = engine.snapshot()
        sceneSnapshot = ui
        if (selectedUid != null && ui.human.units.none { it.uid == selectedUid }) selectedUid = null
        scene?.selection(selectedUid)
    }
    fun perform(action: () -> ActionResult): Boolean {
        val result = action()
        notice = result.message
        publish()
        scene?.render(ui, 0f)
        Log.i("JadeChess", "action ok=${result.ok} message=${result.message}")
        return result.ok
    }
    fun start(): Boolean {
        loggedCombatRound = -1
        clearSelection(); showRules = false
        val ok = perform { engine.start() }
        if (ok) Log.i("JadeChessResults", "START_SHOP_READY phase=${ui.phase} round=${ui.round} cards=${ui.human.shop.count { it != null }} elapsed=${ui.elapsed}")
        return ok
    }
    fun clearSelection() { selectedUid = null; scene?.selection(null) }
    private fun logCombat(state: MatchSnapshot) {
        if (loggedCombatRound == state.round) return
        loggedCombatRound = state.round
        state.battles.forEach { b ->
            Log.i("JadeChessCombat", "round=${state.round} battle=${b.id} elapsed=${b.elapsed} outcome=${b.outcome} fighters=" +
                b.fighters.joinToString(";") { "${it.uid}:${it.heroId}:attacks=${it.attacks}:skills=${it.skills}:first=${it.firstAttackTime}:blocked=${it.maxBlockedSeconds}" })
        }
    }
    fun buy(index: Int) = perform { engine.buy(0, index) }
    fun pause() {
        cancelDrag()
        engine.setPaused(!engine.paused)
        notice = if (engine.paused) "对局暂停" else "继续对局"
        if (engine.paused) scene?.silenceAudio()
        publish()
    }
    fun autoPlay() { cancelDrag(); selectedUid = null; engine.setAutoPlay(!engine.autoPlayHuman); notice = if (engine.autoPlayHuman) "棋手托管：按相同经济规则经营" else "已接回经营"; publish() }
    fun background() {
        cancelDrag()
        foreground = false
        discardNextDelta = true
        if (engine.phase != Phase.MENU && engine.phase != Phase.FINISHED) {
            engine.setPaused(true)
            notice = "已离开棋会，对局暂停；返回后点击继续"
            publish()
        }
        scene?.silenceAudio()
    }
    fun foreground() { foreground = true; discardNextDelta = true }
    fun selectUnit(uid: Int) {
        if (drag.uid != null || engine.phase != Phase.PREPARATION || engine.paused || engine.autoPlayHuman) return
        val p = engine.snapshot().human
        val unit = p.units.firstOrNull { it.uid == uid } ?: return
        selectedUid = if (selectedUid == uid) null else uid
        notice = if (selectedUid == null) "取消选择" else "${unit.hero.name}：点目标格，或按住拖动后松手"
        scene?.selection(selectedUid)
    }
    fun selectSlot(slot: Int) {
        if (engine.phase != Phase.PREPARATION || engine.paused || engine.autoPlayHuman) return
        val uid = selectedUid
        val occupant = engine.snapshot().human.units.firstOrNull { it.slot == slot }
        if (uid == null) {
            if (occupant != null) selectUnit(occupant.uid) else { notice = "先选择备战席或棋盘上的英雄" }
        } else if (perform { engine.move(0, uid, slot) }) {
            selectedUid = null
            scene?.selection(null)
        }
    }
    fun sell() {
        val uid = selectedUid ?: return
        if (perform { engine.sell(0, uid) }) selectedUid = null
    }
    fun beginDrag(uid: Int) {
        if (drag.begin(uid)) {
            selectedUid = uid
            notice = "拖动${engine.snapshot().human.units.first { it.uid == uid }.hero.name}，松手放入己方棋格或备战席"
            scene?.selection(uid)
        }
    }
    fun releaseDrag() {
        if (drag.uid == null) return
        perform { drag.release() }
        selectedUid = null
        scene?.selection(null)
    }
    fun cancelDrag() {
        if (drag.uid == null) return
        drag.cancel(); selectedUid = null
        notice = "拖动已取消，棋子回到原位"
        scene?.selection(null)
        scene?.render(engine.snapshot(), 0f)
    }
    fun dispose() { disposed = true; scene?.destroy(); scene = null }
}
