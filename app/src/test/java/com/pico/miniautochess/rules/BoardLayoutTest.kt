package com.pico.miniautochess.rules

import org.junit.Assert.*
import org.junit.Test

class BoardLayoutTest {
    @Test fun shopDropRegionIncludesItsEdgesAndRejectsOutsideAndInvalidPoints() {
        val region = BoardLayout.DropRegion(-.5f, .5f, 1.4f, 1.8f)
        for (x in listOf(-.5f, 0f, .5f)) for (z in listOf(1.4f, 1.6f, 1.8f))
            assertTrue(region.contains(BoardLayout.Point(x, z)))
        for (p in listOf(BoardLayout.Point(.501f, 1.6f), BoardLayout.Point(0f, 1.399f),
            BoardLayout.Point(0f, BoardLayout.BENCH_Z), BoardLayout.Point(Float.NaN, 1.6f),
            BoardLayout.Point(0f, Float.POSITIVE_INFINITY)))
            assertFalse(region.contains(p))
    }
    @Test fun allThirtyTwoCellsAndEightBenchSlotsRoundTrip() {
        (0 until Catalog.BOARD_SIZE).toList().plus((-8..-1).toList()).forEach { slot ->
            val p = BoardLayout.position(slot)
            assertEquals(slot, BoardLayout.slotAt(p.x, p.z))
        }
    }
    @Test fun enemyHalfTableEdgesAndGapsDoNotSnapToLegalCells() {
        assertNull(BoardLayout.slotAt(0f, -.23f))
        assertNull(BoardLayout.slotAt(.93f, .115f))
        assertNull(BoardLayout.slotAt(-.93f, .115f))
        assertNull(BoardLayout.slotAt(0f, .115f)) // gap between neighboring cells
        assertNull(BoardLayout.slotAt(0f, 1f)) // gap before bench
        assertNull(BoardLayout.slotAt(Float.NaN, 0f))
        assertNull(BoardLayout.slotAt(0f, Float.POSITIVE_INFINITY))
    }
    @Test fun battleMirrorsAllCornersAndUsesFullEightByEightGrid() {
        for (slot in listOf(0, 7, 24, 31)) {
            val units = listOf(UnitSnapshot(1, "copper_guard", 1, slot))
            val b = Battle(0, Army(0, units), Army(1, units.map { it.copy(uid = 2) }))
            val (left, right) = b.snapshot().fighters
            assertEquals(7f, left.x + right.x, 0f)
            assertEquals(7f, left.y + right.y, 0f)
            assertTrue(left.y in 0f..3f)
            assertTrue(right.y in 4f..7f)
            repeat(250) {
                b.step()
                assertTrue(b.snapshot().fighters.all { it.x in 0f..7f && it.y in 0f..7f })
            }
        }
    }
    @Test fun aiPreferencesCoverEveryCellAndSeparateFrontFromBack() {
        val front = BoardLayout.preferredSlots(true)
        val back = BoardLayout.preferredSlots(false)
        assertEquals((0..31).toSet(), front.toSet())
        assertEquals((0..31).toSet(), back.toSet())
        assertTrue(front.take(7).all { it < 8 })
        assertTrue(back.take(7).all { it >= 24 })
    }
}
