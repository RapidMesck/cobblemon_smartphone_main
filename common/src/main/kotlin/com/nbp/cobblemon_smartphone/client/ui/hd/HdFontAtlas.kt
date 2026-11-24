package com.nbp.cobblemon_smartphone.client.ui.hd

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil

/**
 * Dynamic physical-pixel-matched font glyph atlas using Java2D anti-aliased rasterization
 * and Minecraft's [DynamicTexture] with linear filtering for crisp HD text at any scale.
 */
object HdFontAtlas {

    private const val MAX_CACHED_PAGES = 12
    private const val GLYPH_PAD = 2

    data class GlyphEntry(
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val drawOffsetX: Float,
        val drawOffsetY: Float,
        val widthPx: Int,
        val heightPx: Int,
        val advancePx: Float
    )

    private data class PageKey(
        val fontId: String,
        val weight: HdTextLayout.Weight,
        val pixelSize: Int
    )

    class FontPage(
        val fontId: String,
        val weight: HdTextLayout.Weight,
        val pixelSize: Int
    ) {
        val atlasSize: Int = if (pixelSize > 34) 1024 else 512
        val texture = DynamicTexture(atlasSize, atlasSize, false)
        val glyphs = HashMap<Int, GlyphEntry>()

        private var cursorX = GLYPH_PAD
        private var cursorY = GLYPH_PAD
        private var rowHeight = 0
        private var dirty = false

        val lineAscentPx: Float
        val lineHeightPx: Float

        init {
            val m = HdTextLayout.metrics(weight, pixelSize, fontId)
            lineAscentPx = m.ascent
            lineHeightPx = ceil((m.ascent + m.descent + m.leading).toDouble()).toFloat()

            // NativeImage allocates uninitialized native memory; clear to transparent black so
            // linear filtering never samples garbage texels from padding gaps.
            texture.pixels?.let { pixels ->
                for (py in 0 until atlasSize) {
                    for (px in 0 until atlasSize) {
                        pixels.setPixelRGBA(px, py, 0)
                    }
                }
            }

            texture.setFilter(true, false)
            texture.bind()
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

            // Pre-bake printable ASCII and common Latin-1 characters in one upload.
            for (cp in 32..126) {
                bakeGlyph(cp)
            }
            for (cp in 0xC0..0xFF) {
                bakeGlyph(cp)
            }
            uploadIfDirty()
        }

        fun getOrBake(codePoint: Int): GlyphEntry {
            glyphs[codePoint]?.let { return it }
            // Flush any pending quads referencing old texture coordinates/contents before re-uploading.
            HdBatch.flush()
            val entry = bakeGlyph(codePoint)
            uploadIfDirty()
            return entry
        }

        private fun bakeGlyph(codePoint: Int): GlyphEntry {
            glyphs[codePoint]?.let { return it }

            val font = HdTextLayout.fontForCodePoint(codePoint, weight, pixelSize, fontId)
            val str = String(Character.toChars(codePoint))
            val gv = font.createGlyphVector(HdTextLayout.fontRenderContext, str)
            val advance = gv.getGlyphMetrics(0).advanceX
            val bounds = gv.getGlyphPixelBounds(0, HdTextLayout.fontRenderContext, 0f, 0f)

            val gw = bounds.width.coerceAtLeast(0)
            val gh = bounds.height.coerceAtLeast(0)

            if (gw == 0 || gh == 0) {
                val empty = GlyphEntry(0f, 0f, 0f, 0f, 0f, 0f, 0, 0, advance)
                glyphs[codePoint] = empty
                return empty
            }

            // Expand by 2px transparent margin around Java2D bounds so GL_LINEAR never bleeds into
            // neighboring glyphs or clips anti-aliased edge pixels.
            val margin = 2
            val cellW = gw + margin * 2
            val cellH = gh + margin * 2

            if (cursorX + cellW + GLYPH_PAD > atlasSize) {
                cursorX = GLYPH_PAD
                cursorY += rowHeight + GLYPH_PAD
                rowHeight = 0
            }
            if (cursorY + cellH + GLYPH_PAD > atlasSize) {
                val fallback = glyphs[63] ?: GlyphEntry(0f, 0f, 0f, 0f, 0f, 0f, 0, 0, advance)
                glyphs[codePoint] = fallback
                return fallback
            }

            val img = BufferedImage(cellW, cellH, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                g.color = Color.WHITE
                g.font = font
                val drawX = (margin - bounds.x).toFloat()
                val drawY = (margin - bounds.y).toFloat()
                g.drawGlyphVector(gv, drawX, drawY)
            } finally {
                g.dispose()
            }

            val pixels: NativeImage = texture.pixels ?: return GlyphEntry(0f, 0f, 0f, 0f, 0f, 0f, 0, 0, advance)
            for (py in 0 until cellH) {
                for (px in 0 until cellW) {
                    val argb = img.getRGB(px, py)
                    val alpha = (argb ushr 24) and 0xFF
                    // White RGB + coverage alpha (ABGR == ARGB when R=G=B=255)
                    pixels.setPixelRGBA(cursorX + px, cursorY + py, (alpha shl 24) or 0x00FFFFFF)
                }
            }

            val entry = GlyphEntry(
                u0 = cursorX.toFloat() / atlasSize,
                v0 = cursorY.toFloat() / atlasSize,
                u1 = (cursorX + cellW).toFloat() / atlasSize,
                v1 = (cursorY + cellH).toFloat() / atlasSize,
                drawOffsetX = (bounds.x - margin).toFloat(),
                drawOffsetY = (bounds.y - margin).toFloat(),
                widthPx = cellW,
                heightPx = cellH,
                advancePx = advance
            )
            glyphs[codePoint] = entry
            cursorX += cellW + GLYPH_PAD
            if (cellH > rowHeight) rowHeight = cellH
            dirty = true
            return entry
        }

        private fun uploadIfDirty() {
            if (!dirty) return
            texture.upload()
            texture.setFilter(true, false)
            texture.bind()
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            dirty = false
        }

        fun close() {
            texture.close()
        }
    }

    private val pages = object : LinkedHashMap<PageKey, FontPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageKey, FontPage>): Boolean {
            if (size > MAX_CACHED_PAGES) {
                eldest.value.close()
                return true
            }
            return false
        }
    }

    fun getPage(
        weight: HdTextLayout.Weight,
        pixelSize: Int,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ): FontPage {
        val clampedPx = pixelSize.coerceIn(8, 128)
        return pages.getOrPut(PageKey(fontId, weight, clampedPx)) {
            FontPage(fontId, weight, clampedPx)
        }
    }

    fun drawText(
        text: String,
        physX: Float,
        physY: Float,
        weight: HdTextLayout.Weight,
        pixelSize: Int,
        colorArgb: Int,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ): Float {
        if (text.isEmpty() || ((colorArgb ushr 24) and 0xFF) == 0) return 0f
        val page = getPage(weight, pixelSize, fontId)
        val texId = page.texture.id
        var penX = physX
        val baselineY = physY + page.lineAscentPx

        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val glyph = page.getOrBake(cp)
            if (glyph.widthPx > 0 && glyph.heightPx > 0) {
                HdBatch.pushQuad(
                    x = penX + glyph.drawOffsetX,
                    y = baselineY + glyph.drawOffsetY,
                    w = glyph.widthPx.toFloat(),
                    h = glyph.heightPx.toFloat(),
                    u0 = glyph.u0,
                    v0 = glyph.v0,
                    u1 = glyph.u1,
                    v1 = glyph.v1,
                    mode = HdBatch.MODE_ALPHA_ATLAS,
                    colorTop = colorArgb,
                    textureId = texId
                )
            }
            penX += glyph.advancePx
            i += Character.charCount(cp)
        }
        return penX - physX
    }

    fun clear() {
        pages.values.forEach { it.close() }
        pages.clear()
    }
}
