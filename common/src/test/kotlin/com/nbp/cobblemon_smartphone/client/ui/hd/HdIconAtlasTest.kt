package com.nbp.cobblemon_smartphone.client.ui.hd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdIconAtlasTest {

    @Test
    fun `all icons fit within the atlas grid capacity`() {
        assertTrue(
            HdIconAtlas.Icon.entries.size <= HdIconAtlas.MAX_ICONS,
            "Icon count (${HdIconAtlas.Icon.entries.size}) exceeds atlas capacity (${HdIconAtlas.MAX_ICONS})"
        )
    }

    @Test
    fun `every icon rasterizes visible pixels in both HD and pixel modes`() {
        for (icon in HdIconAtlas.Icon.entries) {
            val hdImg = HdIconAtlas.rasterizeIcon(icon, HdIconAtlas.CELL_SIZE, antialiased = true)
            val hdOpaque = countVisiblePixels(hdImg, threshold = 32)
            assertTrue(hdOpaque >= 24, "HD icon $icon rendered too few visible pixels ($hdOpaque)")

            val pxImg = HdIconAtlas.rasterizeIcon(icon, HdIconAtlas.PIXEL_CELL_SIZE, antialiased = false)
            val pxOpaque = countVisiblePixels(pxImg, threshold = 96)
            assertTrue(pxOpaque >= 4, "Pixel icon $icon rendered too few visible pixels ($pxOpaque)")
        }
    }

    @Test
    fun `every icon produces a distinct HD and pixel silhouette`() {
        val hdSignatures = mutableMapOf<String, HdIconAtlas.Icon>()
        val pxSignatures = mutableMapOf<String, HdIconAtlas.Icon>()
        for (icon in HdIconAtlas.Icon.entries) {
            val sprite = HdIconAtlas.pixelSprite(icon)
            assertEquals(HdIconAtlas.PIXEL_CELL_SIZE, sprite.size, "Pixel sprite $icon must have ${HdIconAtlas.PIXEL_CELL_SIZE} rows")
            for (row in sprite) {
                assertEquals(HdIconAtlas.PIXEL_CELL_SIZE, row.length, "Pixel sprite $icon row must be ${HdIconAtlas.PIXEL_CELL_SIZE} chars")
            }

            val hdImg = HdIconAtlas.rasterizeIcon(icon, HdIconAtlas.CELL_SIZE, antialiased = true)
            val hdSig = signatureOf(hdImg, threshold = 96)
            val hdDup = hdSignatures.putIfAbsent(hdSig, icon)
            assertEquals(null, hdDup, "HD icon $icon produced identical raster output to $hdDup")

            val pxImg = HdIconAtlas.rasterizeIcon(icon, HdIconAtlas.PIXEL_CELL_SIZE, antialiased = false)
            val pxSig = signatureOf(pxImg, threshold = 96)
            val pxDup = pxSignatures.putIfAbsent(pxSig, icon)
            assertEquals(null, pxDup, "Pixel icon $icon produced identical raster output to $pxDup")
        }
    }

    private fun signatureOf(img: java.awt.image.BufferedImage, threshold: Int): String {
        val sb = StringBuilder(img.width * img.height)
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val a = (img.getRGB(x, y) ushr 24) and 0xFF
                sb.append(if (a >= threshold) '#' else '.')
            }
        }
        return sb.toString()
    }

    @Test
    fun `new UI component icons are present in the atlas enum`() {
        val expected = listOf(
            "CHEVRON_DOWN",
            "CHEVRON_UP",
            "CHEVRON_RIGHT",
            "MINUS",
            "SETTINGS",
            "FILTER",
            "REFRESH",
            "STAR",
            "STAR_FILLED",
            "INFO",
            "WARNING",
            "LOCK",
            "EDIT",
            "COMPASS"
        )
        val available = HdIconAtlas.Icon.entries.map { it.name }.toSet()
        for (name in expected) {
            assertTrue(name in available, "Expected icon $name to exist in HdIconAtlas.Icon")
        }
    }

    private fun countVisiblePixels(img: java.awt.image.BufferedImage, threshold: Int): Int {
        var count = 0
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val alpha = (img.getRGB(x, y) ushr 24) and 0xFF
                if (alpha >= threshold) count++
            }
        }
        return count
    }
}
