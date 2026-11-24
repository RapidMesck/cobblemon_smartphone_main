package com.nbp.cobblemon_smartphone.client.ui.hd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdViewportTest {

    @Test
    fun `viewport scale is independent of minecraft guiScale`() {
        val vpScale1 = HdViewport.compute(1920, 1080, 1.0, 340f, 520f)
        val vpScale3 = HdViewport.compute(1920, 1080, 3.0, 340f, 520f)
        val vpScale4 = HdViewport.compute(1920, 1080, 4.0, 340f, 520f)

        assertEquals(vpScale1.scale, vpScale3.scale, 1e-5f)
        assertEquals(vpScale1.scale, vpScale4.scale, 1e-5f)
        assertEquals(vpScale1.originX, vpScale3.originX, 1e-5f)
        assertEquals(vpScale1.originY, vpScale3.originY, 1e-5f)
    }

    @Test
    fun `viewport scales proportionally from 720p to 4k`() {
        val vp720 = HdViewport.compute(1280, 720, 2.0, 340f, 520f)
        val vp1440 = HdViewport.compute(2560, 1440, 2.0, 340f, 520f)

        assertEquals(vp720.scale * 2f, vp1440.scale, 1e-4f)
        assertEquals(1280f, vp720.originX * 2f + 340f * vp720.scale, 1e-3f)
        assertEquals(1440f, vp1440.originY * 2f + 520f * vp1440.scale, 1e-3f)
    }

    @Test
    fun `mouse round-trip between mc gui space and dp space is exact`() {
        val vp = HdViewport.compute(1920, 1080, 3.0, 340f, 520f, userScale = 1.15f)
        val dpX = 123.45f
        val dpY = 267.89f
        val mcX = vp.toMcGuiX(dpX).toDouble()
        val mcY = vp.toMcGuiY(dpY).toDouble()

        assertEquals(dpX, vp.mcGuiMouseToDpX(mcX), 1e-3f)
        assertEquals(dpY, vp.mcGuiMouseToDpY(mcY), 1e-3f)
    }

    @Test
    fun `gl scissor flips Y axis to bottom-left origin and intersects cleanly`() {
        val vp = HdViewport.compute(1000, 1000, 2.0, 100f, 100f, maxWidthFraction = 1.0f, maxHeightFraction = 1.0f)
        // scale = 9.7 (fit clamped to 0.97)
        val outer = vp.toGlScissor(0f, 0f, 100f, 100f)
        val inner = vp.toGlScissor(10f, 10f, 30f, 20f)
        val intersected = outer.intersect(inner)

        assertFalse(intersected.isEmpty)
        assertEquals(inner, intersected)
        assertTrue(inner.y > 500, "Top-half dp region should map to upper half in bottom-left GL coords")
    }

    @Test
    fun `user scale is clamped within supported zoom range`() {
        val tooSmall = HdViewport.compute(1920, 1080, 3.0, 340f, 520f, userScale = 0.1f)
        val minValid = HdViewport.compute(1920, 1080, 3.0, 340f, 520f, userScale = HdViewport.MIN_USER_SCALE)
        assertEquals(minValid.scale, tooSmall.scale, 1e-5f)

        val tooBig = HdViewport.compute(1920, 1080, 3.0, 340f, 520f, userScale = 5.0f)
        assertTrue(tooBig.toPhysSize(520f) <= 1080f * 0.971f)
    }
}
