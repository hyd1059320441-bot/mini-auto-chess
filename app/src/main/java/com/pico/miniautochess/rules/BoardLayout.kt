package com.pico.miniautochess.rules

import kotlin.math.abs
import kotlin.math.roundToInt

/** Meter-space tabletop mapping, shared by rendering and gesture hit testing. */
object BoardLayout {
    const val PITCH = .23f
    const val BENCH_PITCH = .23f
    const val BENCH_Z = 1.17f
    val halfColumns get() = (Catalog.BOARD_COLS - 1) / 2f
    val halfRows get() = (Catalog.BATTLE_ROWS - 1) / 2f
    data class Point(val x: Float, val z: Float)
    data class DropRegion(val minX: Float, val maxX: Float, val minZ: Float, val maxZ: Float) {
        fun contains(point: Point) = point.x.isFinite() && point.z.isFinite() &&
            point.x in minX..maxX && point.z in minZ..maxZ
    }

    fun position(slot: Int): Point {
        require(slot in 0 until Catalog.BOARD_SIZE || slot in -Catalog.BENCH_SIZE..-1)
        return if (slot >= 0) Point((slot % Catalog.BOARD_COLS - halfColumns) * PITCH,
            (.5f + slot / Catalog.BOARD_COLS) * PITCH)
        else Point((-slot - 1 - (Catalog.BENCH_SIZE - 1) / 2f) * BENCH_PITCH, BENCH_Z)
    }

    /** Gaps, enemy half and space outside the tabletop are never clamped. */
    fun slotAt(x: Float, z: Float): Int? {
        if (!x.isFinite() || !z.isFinite()) return null
        val bench = (x / BENCH_PITCH + (Catalog.BENCH_SIZE - 1) / 2f).roundToInt()
        if (bench in 0 until Catalog.BENCH_SIZE &&
            abs(x - position(-bench - 1).x) <= BENCH_PITCH * .46f &&
            abs(z - BENCH_Z) <= .10f) return -bench - 1
        val col = (x / PITCH + halfColumns).roundToInt()
        val row = (z / PITCH - .5f).roundToInt()
        if (col !in 0 until Catalog.BOARD_COLS || row !in 0 until Catalog.BOARD_ROWS) return null
        val slot = row * Catalog.BOARD_COLS + col
        val p = position(slot)
        return slot.takeIf { abs(x - p.x) <= PITCH * .48f && abs(z - p.z) <= PITCH * .48f }
    }

    fun preferredSlots(melee: Boolean): List<Int> {
        val cols = (0 until Catalog.BOARD_COLS).sortedWith(
            compareBy<Int> { abs(it - halfColumns) }.thenBy { it })
        val rows = if (melee) (0 until Catalog.BOARD_ROWS).toList()
            else (Catalog.BOARD_ROWS - 1 downTo 0).toList()
        return rows.flatMap { row -> cols.map { row * Catalog.BOARD_COLS + it } }
    }
}

/** A drag owns only a preview; the rule owner is called exactly once on release. */
class DragPlacement(private val engine: GameEngine) {
    var uid: Int? = null
        private set
    var point: BoardLayout.Point? = null
        private set
    private var sourceSlot: Int? = null

    private fun allowed(unitId: Int) = !engine.autoPlayHuman && engine.canEditUnit(0, unitId)
    fun begin(unitId: Int): Boolean {
        cancel()
        if (!allowed(unitId)) return false
        val unit = engine.snapshot().human.units.firstOrNull { it.uid == unitId } ?: return false
        uid = unitId; sourceSlot = unit.slot; point = BoardLayout.position(unit.slot)
        return true
    }
    fun validate(): Boolean {
        val id = uid ?: return false
        if (!allowed(id) || engine.snapshot().human.units.none { it.uid == id && it.slot == sourceSlot }) {
            cancel(); return false
        }
        return true
    }
    fun delta(x: Float, z: Float) {
        if (!validate()) return
        if (!x.isFinite() || !z.isFinite()) { cancel(); return }
        point = point!!.let { BoardLayout.Point(it.x + x, it.z + z) }
    }
    fun release(sell: Boolean = false): ActionResult {
        if (!validate()) return ActionResult(false, "拖动已取消，棋子保留原位")
        val id = uid!!
        val slot = point!!.let { BoardLayout.slotAt(it.x, it.z) }
        cancel()
        if (sell) return engine.sell(0, id)
        if (slot == null) return ActionResult(false, "落点不在己方棋格或备战席，已回原位")
        val result = engine.move(0, id, slot)
        val location = if (slot >= 0) "己方第 ${slot / Catalog.BOARD_COLS + 1} 排、第 ${slot % Catalog.BOARD_COLS + 1} 格"
            else "备战席 ${-slot}"
        return if (result.ok) result.copy(message = "${result.message} · $location")
            else result.copy(message = "${result.message}，已回原位")
    }
    fun cancel() { uid = null; point = null; sourceSlot = null }
}
