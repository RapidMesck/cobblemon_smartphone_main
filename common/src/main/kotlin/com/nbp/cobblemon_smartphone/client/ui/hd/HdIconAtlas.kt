package com.nbp.cobblemon_smartphone.client.ui.hd

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * High-DPI vector icon atlas rasterized via Java2D [Path2D] strokes at 64x64 per icon
 * with mipmapped linear filtering, eliminating pixel-art icon quantization.
 */
object HdIconAtlas {

    enum class Icon {
        FEED,
        MESSAGE,
        PLUS,
        BELL,
        SEND,
        PHONE,
        TRASH,
        HEART,
        HEART_FILLED,
        BACK,
        CLOSE,
        SEARCH,
        USER,
        FLAG,
        GLOBE,
        USERS,
        CHECK,
        IMAGE,
        SPARKLES,
        CAMERA,
        POLL,
        SWORDS,
        TRADE,
        CHEVRON_DOWN,
        CHEVRON_UP,
        CHEVRON_RIGHT,
        MINUS,
        SETTINGS,
        FILTER,
        REFRESH,
        STAR,
        STAR_FILLED,
        INFO,
        WARNING,
        LOCK,
        EDIT,
        COMPASS
    }

    internal const val CELL_SIZE = 64
    internal const val GRID_COLS = 8
    internal const val MAX_ICONS = GRID_COLS * GRID_COLS
    private const val ATLAS_SIZE = CELL_SIZE * GRID_COLS // 512x512

    internal const val PIXEL_CELL_SIZE = 12
    private const val PIXEL_ATLAS_SIZE = PIXEL_CELL_SIZE * GRID_COLS // 96x96

    private var texture: DynamicTexture? = null
    private val uvs = HashMap<Icon, FloatArray>()

    private var pixelTexture: DynamicTexture? = null
    private val pixelUvs = HashMap<Icon, FloatArray>()

    private fun ensureBuiltPixel(): Int {
        pixelTexture?.let { return it.id }

        val dyn = DynamicTexture(PIXEL_ATLAS_SIZE, PIXEL_ATLAS_SIZE, false)
        val pixels: NativeImage = dyn.pixels!!
        for (y in 0 until PIXEL_ATLAS_SIZE) {
            for (x in 0 until PIXEL_ATLAS_SIZE) {
                pixels.setPixelRGBA(x, y, 0)
            }
        }

        Icon.entries.forEachIndexed { idx, icon ->
            val col = idx % GRID_COLS
            val row = idx / GRID_COLS
            val cx = col * PIXEL_CELL_SIZE
            val cy = row * PIXEL_CELL_SIZE

            val sprite = pixelSprite(icon)
            for (py in 0 until PIXEL_CELL_SIZE) {
                val line = sprite.getOrNull(py) ?: continue
                for (px in 0 until PIXEL_CELL_SIZE) {
                    if (px < line.length && line[px] == '#') {
                        pixels.setPixelRGBA(cx + px, cy + py, 0xFFFFFFFF.toInt())
                    }
                }
            }

            val eps = 0.001f / PIXEL_ATLAS_SIZE
            pixelUvs[icon] = floatArrayOf(
                cx.toFloat() / PIXEL_ATLAS_SIZE + eps,
                cy.toFloat() / PIXEL_ATLAS_SIZE + eps,
                (cx + PIXEL_CELL_SIZE).toFloat() / PIXEL_ATLAS_SIZE - eps,
                (cy + PIXEL_CELL_SIZE).toFloat() / PIXEL_ATLAS_SIZE - eps
            )
        }

        dyn.upload()
        dyn.bind()
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        pixelTexture = dyn
        return dyn.id
    }

    private fun ensureBuilt(): Int {
        texture?.let { return it.id }

        val img = BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            Icon.entries.forEachIndexed { idx, icon ->
                val col = idx % GRID_COLS
                val row = idx / GRID_COLS
                val cx = col * CELL_SIZE
                val cy = row * CELL_SIZE

                val g2 = g.create() as Graphics2D
                try {
                    g2.translate(cx.toDouble(), cy.toDouble())
                    // Normalized 24x24 vector design space scaled to 64x64 cell with padding
                    val scale = CELL_SIZE / 24.0
                    g2.scale(scale, scale)
                    g2.color = Color.WHITE
                    g2.stroke = BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    drawVectorIcon(g2, icon)
                } finally {
                    g2.dispose()
                }

                // Inset half a texel to prevent bleeding across cells
                val eps = 0.5f / ATLAS_SIZE
                uvs[icon] = floatArrayOf(
                    cx.toFloat() / ATLAS_SIZE + eps,
                    cy.toFloat() / ATLAS_SIZE + eps,
                    (cx + CELL_SIZE).toFloat() / ATLAS_SIZE - eps,
                    (cy + CELL_SIZE).toFloat() / ATLAS_SIZE - eps
                )
            }
        } finally {
            g.dispose()
        }

        val dyn = DynamicTexture(ATLAS_SIZE, ATLAS_SIZE, false)
        val pixels: NativeImage = dyn.pixels!!
        for (y in 0 until ATLAS_SIZE) {
            for (x in 0 until ATLAS_SIZE) {
                val alpha = (img.getRGB(x, y) ushr 24) and 0xFF
                pixels.setPixelRGBA(x, y, (alpha shl 24) or 0x00FFFFFF)
            }
        }
        dyn.upload()
        dyn.bind()
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        texture = dyn
        return dyn.id
    }

    private fun drawVectorIcon(g: Graphics2D, icon: Icon) {
        when (icon) {
            Icon.FEED -> {
                g.draw(RoundRectangle2D.Float(3f, 4f, 18f, 16f, 3f, 3f))
                g.draw(Line2D.Float(7f, 9f, 17f, 9f))
                g.draw(Line2D.Float(7f, 13f, 17f, 13f))
                g.draw(Line2D.Float(7f, 16.5f, 13f, 16.5f))
            }
            Icon.MESSAGE -> {
                val p = Path2D.Float().apply {
                    moveTo(21f, 11.5f)
                    curveTo(21f, 15.8f, 17f, 19f, 12f, 19f)
                    curveTo(10.6f, 19f, 9.2f, 18.7f, 8f, 18.2f)
                    lineTo(3.5f, 20f)
                    lineTo(4.9f, 15.9f)
                    curveTo(3.7f, 14.6f, 3f, 13.1f, 3f, 11.5f)
                    curveTo(3f, 7.2f, 7f, 4f, 12f, 4f)
                    curveTo(17f, 4f, 21f, 7.2f, 21f, 11.5f)
                    closePath()
                }
                g.draw(p)
            }
            Icon.PLUS -> {
                g.draw(Line2D.Float(12f, 5f, 12f, 19f))
                g.draw(Line2D.Float(5f, 12f, 19f, 12f))
            }
            Icon.BELL -> {
                val p = Path2D.Float().apply {
                    moveTo(18f, 8.5f)
                    curveTo(18f, 5.2f, 15.3f, 3f, 12f, 3f)
                    curveTo(8.7f, 3f, 6f, 5.2f, 6f, 8.5f)
                    curveTo(6f, 14.5f, 3.5f, 16.5f, 3.5f, 16.5f)
                    lineTo(20.5f, 16.5f)
                    curveTo(20.5f, 16.5f, 18f, 14.5f, 18f, 8.5f)
                    closePath()
                }
                g.draw(p)
                g.draw(Arc2D.Float(9.8f, 17.5f, 4.4f, 3.2f, 180f, 180f, Arc2D.OPEN))
            }
            Icon.SEND -> {
                val p = Path2D.Float().apply {
                    moveTo(21f, 3.5f)
                    lineTo(10f, 14f)
                    moveTo(21f, 3.5f)
                    lineTo(14.5f, 20.5f)
                    lineTo(10f, 14f)
                    lineTo(3.5f, 9.5f)
                    closePath()
                }
                g.draw(p)
            }
            Icon.PHONE -> {
                val p = Path2D.Float().apply {
                    moveTo(6.5f, 4f)
                    lineTo(9.5f, 4f)
                    lineTo(11f, 8f)
                    lineTo(8.8f, 9.8f)
                    curveTo(10f, 12.2f, 11.8f, 14f, 14.2f, 15.2f)
                    lineTo(16f, 13f)
                    lineTo(20f, 14.5f)
                    lineTo(20f, 17.5f)
                    curveTo(20f, 19f, 18.7f, 20.2f, 17f, 20f)
                    curveTo(9.5f, 19.2f, 4.8f, 14.5f, 4f, 7f)
                    curveTo(3.8f, 5.3f, 5f, 4f, 6.5f, 4f)
                    closePath()
                }
                g.draw(p)
            }
            Icon.TRASH -> {
                g.draw(Line2D.Float(4f, 6f, 20f, 6f))
                g.draw(RoundRectangle2D.Float(9f, 3.5f, 6f, 2.5f, 1f, 1f))
                val p = Path2D.Float().apply {
                    moveTo(6f, 6f)
                    lineTo(7.2f, 19.5f)
                    curveTo(7.3f, 20.3f, 8f, 21f, 8.8f, 21f)
                    lineTo(15.2f, 21f)
                    curveTo(16f, 21f, 16.7f, 20.3f, 16.8f, 19.5f)
                    lineTo(18f, 6f)
                }
                g.draw(p)
                g.draw(Line2D.Float(10f, 10f, 10f, 17f))
                g.draw(Line2D.Float(14f, 10f, 14f, 17f))
            }
            Icon.HEART, Icon.HEART_FILLED -> {
                val p = Path2D.Float().apply {
                    moveTo(12f, 20.2f)
                    curveTo(12f, 20.2f, 3.5f, 14.5f, 3.5f, 8.8f)
                    curveTo(3.5f, 5.8f, 5.8f, 4f, 8.2f, 4f)
                    curveTo(10f, 4f, 11.4f, 5f, 12f, 6.4f)
                    curveTo(12.6f, 5f, 14f, 4f, 15.8f, 4f)
                    curveTo(18.2f, 4f, 20.5f, 5.8f, 20.5f, 8.8f)
                    curveTo(20.5f, 14.5f, 12f, 20.2f, 12f, 20.2f)
                    closePath()
                }
                if (icon == Icon.HEART_FILLED) {
                    g.fill(p)
                } else {
                    g.draw(p)
                }
            }
            Icon.BACK -> {
                val p = Path2D.Float().apply {
                    moveTo(15f, 5f)
                    lineTo(8f, 12f)
                    lineTo(15f, 19f)
                }
                g.draw(p)
            }
            Icon.CLOSE -> {
                g.draw(Line2D.Float(6f, 6f, 18f, 18f))
                g.draw(Line2D.Float(18f, 6f, 6f, 18f))
            }
            Icon.SEARCH -> {
                g.draw(Ellipse2D.Float(4f, 4f, 12.5f, 12.5f))
                g.draw(Line2D.Float(15f, 15f, 20.5f, 20.5f))
            }
            Icon.USER -> {
                g.draw(Ellipse2D.Float(8.5f, 4f, 7f, 7f))
                val p = Path2D.Float().apply {
                    moveTo(4.5f, 20f)
                    curveTo(4.5f, 15.8f, 7.8f, 13.5f, 12f, 13.5f)
                    curveTo(16.2f, 13.5f, 19.5f, 15.8f, 19.5f, 20f)
                }
                g.draw(p)
            }
            Icon.FLAG -> {
                g.draw(Line2D.Float(5f, 4f, 5f, 21f))
                val p = Path2D.Float().apply {
                    moveTo(5f, 4.5f)
                    lineTo(18.5f, 4.5f)
                    lineTo(16f, 9f)
                    lineTo(18.5f, 13.5f)
                    lineTo(5f, 13.5f)
                }
                g.draw(p)
            }
            Icon.GLOBE -> {
                g.draw(Ellipse2D.Float(3.5f, 3.5f, 17f, 17f))
                g.draw(Ellipse2D.Float(8.2f, 3.5f, 7.6f, 17f))
                g.draw(Line2D.Float(3.8f, 12f, 20.2f, 12f))
            }
            Icon.USERS -> {
                g.draw(Ellipse2D.Float(5.5f, 5f, 6f, 6f))
                val p1 = Path2D.Float().apply {
                    moveTo(2.5f, 19.5f)
                    curveTo(2.5f, 16f, 5.2f, 14f, 8.5f, 14f)
                    curveTo(11.8f, 14f, 14.5f, 16f, 14.5f, 19.5f)
                }
                g.draw(p1)
                g.draw(Ellipse2D.Float(13.5f, 6f, 5f, 5f))
                val p2 = Path2D.Float().apply {
                    moveTo(16f, 14.2f)
                    curveTo(19f, 14.6f, 21.5f, 16.5f, 21.5f, 19.5f)
                }
                g.draw(p2)
            }
            Icon.CHECK -> {
                val p = Path2D.Float().apply {
                    moveTo(5f, 12.5f)
                    lineTo(10f, 17.5f)
                    lineTo(19.5f, 7f)
                }
                g.draw(p)
            }
            Icon.IMAGE -> {
                g.draw(RoundRectangle2D.Float(3.5f, 4.5f, 17f, 15f, 2.5f, 2.5f))
                g.fill(Ellipse2D.Float(7.5f, 8f, 2.5f, 2.5f))
                val p = Path2D.Float().apply {
                    moveTo(4.5f, 17.5f)
                    lineTo(10f, 12f)
                    lineTo(14f, 15.5f)
                    lineTo(16.5f, 13f)
                    lineTo(19.5f, 17.5f)
                }
                g.draw(p)
            }
            Icon.SPARKLES -> {
                val star = Path2D.Float().apply {
                    moveTo(12f, 3f)
                    lineTo(14.1f, 9.9f)
                    lineTo(21f, 12f)
                    lineTo(14.1f, 14.1f)
                    lineTo(12f, 21f)
                    lineTo(9.9f, 14.1f)
                    lineTo(3f, 12f)
                    lineTo(9.9f, 9.9f)
                    closePath()
                }
                g.fill(star)
            }
            Icon.CAMERA -> {
                g.draw(RoundRectangle2D.Float(3f, 6.5f, 18f, 13f, 2.5f, 2.5f))
                val top = Path2D.Float().apply {
                    moveTo(8f, 6.5f)
                    lineTo(9.5f, 4f)
                    lineTo(14.5f, 4f)
                    lineTo(16f, 6.5f)
                }
                g.draw(top)
                g.draw(Ellipse2D.Float(8.5f, 9.5f, 7f, 7f))
            }
            Icon.POLL -> {
                g.draw(Line2D.Float(5f, 19f, 5f, 11f))
                g.draw(Line2D.Float(12f, 19f, 12f, 5f))
                g.draw(Line2D.Float(19f, 19f, 19f, 9f))
            }
            Icon.SWORDS -> {
                g.draw(Line2D.Float(4.5f, 19.5f, 19.5f, 4.5f))
                g.draw(Line2D.Float(14f, 4.5f, 19.5f, 4.5f))
                g.draw(Line2D.Float(19.5f, 4.5f, 19.5f, 10f))
                g.draw(Line2D.Float(5.5f, 13.5f, 10.5f, 18.5f))
                g.draw(Line2D.Float(4.5f, 4.5f, 19.5f, 19.5f))
                g.draw(Line2D.Float(13.5f, 18.5f, 18.5f, 13.5f))
            }
            Icon.TRADE -> {
                g.draw(Line2D.Float(4f, 8.5f, 19f, 8.5f))
                g.draw(Line2D.Float(15.5f, 5f, 19f, 8.5f))
                g.draw(Line2D.Float(15.5f, 12f, 19f, 8.5f))
                g.draw(Line2D.Float(20f, 15.5f, 5f, 15.5f))
                g.draw(Line2D.Float(8.5f, 12f, 5f, 15.5f))
                g.draw(Line2D.Float(8.5f, 19f, 5f, 15.5f))
            }
            Icon.CHEVRON_DOWN -> {
                val p = Path2D.Float().apply {
                    moveTo(6f, 9f)
                    lineTo(12f, 15f)
                    lineTo(18f, 9f)
                }
                g.draw(p)
            }
            Icon.CHEVRON_UP -> {
                val p = Path2D.Float().apply {
                    moveTo(6f, 15f)
                    lineTo(12f, 9f)
                    lineTo(18f, 15f)
                }
                g.draw(p)
            }
            Icon.CHEVRON_RIGHT -> {
                val p = Path2D.Float().apply {
                    moveTo(9f, 6f)
                    lineTo(15f, 12f)
                    lineTo(9f, 18f)
                }
                g.draw(p)
            }
            Icon.MINUS -> {
                g.draw(Line2D.Float(5f, 12f, 19f, 12f))
            }
            Icon.SETTINGS -> {
                g.draw(Ellipse2D.Float(8.5f, 8.5f, 7f, 7f))
                g.draw(Ellipse2D.Float(5.5f, 5.5f, 13f, 13f))
                for (i in 0 until 8) {
                    val rad = Math.toRadians(i * 45.0)
                    val cos = kotlin.math.cos(rad).toFloat()
                    val sin = kotlin.math.sin(rad).toFloat()
                    g.draw(Line2D.Float(12f + cos * 6.5f, 12f + sin * 6.5f, 12f + cos * 9.5f, 12f + sin * 9.5f))
                }
            }
            Icon.FILTER -> {
                val p = Path2D.Float().apply {
                    moveTo(4f, 5f)
                    lineTo(20f, 5f)
                    lineTo(14f, 12.5f)
                    lineTo(14f, 19f)
                    lineTo(10f, 17f)
                    lineTo(10f, 12.5f)
                    closePath()
                }
                g.draw(p)
            }
            Icon.REFRESH -> {
                g.draw(Arc2D.Float(4.5f, 4.5f, 15f, 15f, 35f, 290f, Arc2D.OPEN))
                val head = Path2D.Float().apply {
                    moveTo(16.5f, 4.5f)
                    lineTo(19.5f, 8.2f)
                    lineTo(15.2f, 8.8f)
                }
                g.draw(head)
            }
            Icon.STAR, Icon.STAR_FILLED -> {
                val p = Path2D.Float()
                val cx = 12.0
                val cy = 12.2
                val outerR = 8.8
                val innerR = 3.8
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outerR else innerR
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val px = (cx + kotlin.math.cos(angle) * r).toFloat()
                    val py = (cy + kotlin.math.sin(angle) * r).toFloat()
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.closePath()
                if (icon == Icon.STAR_FILLED) {
                    g.fill(p)
                } else {
                    g.draw(p)
                }
            }
            Icon.INFO -> {
                g.draw(Ellipse2D.Float(3.5f, 3.5f, 17f, 17f))
                g.fill(Ellipse2D.Float(11.1f, 7.2f, 1.8f, 1.8f))
                g.draw(Line2D.Float(12f, 11.2f, 12f, 16.5f))
            }
            Icon.WARNING -> {
                val p = Path2D.Float().apply {
                    moveTo(12f, 4f)
                    lineTo(20.5f, 19f)
                    lineTo(3.5f, 19f)
                    closePath()
                }
                g.draw(p)
                g.draw(Line2D.Float(12f, 9.5f, 12f, 13.8f))
                g.fill(Ellipse2D.Float(11.1f, 15.8f, 1.8f, 1.8f))
            }
            Icon.LOCK -> {
                g.draw(RoundRectangle2D.Float(5f, 10.5f, 14f, 9.5f, 2.5f, 2.5f))
                g.draw(Arc2D.Float(7.5f, 4f, 9f, 13f, 0f, 180f, Arc2D.OPEN))
                g.fill(Ellipse2D.Float(10.8f, 14.2f, 2.4f, 2.4f))
            }
            Icon.EDIT -> {
                val p = Path2D.Float().apply {
                    moveTo(15.5f, 4.5f)
                    lineTo(19.5f, 8.5f)
                    lineTo(9f, 19f)
                    lineTo(4.5f, 19.5f)
                    lineTo(5f, 15f)
                    closePath()
                }
                g.draw(p)
                g.draw(Line2D.Float(13.5f, 6.5f, 17.5f, 10.5f))
            }
            Icon.COMPASS -> {
                g.draw(Ellipse2D.Float(3.5f, 3.5f, 17f, 17f))
                val needle = Path2D.Float().apply {
                    moveTo(15.8f, 8.2f)
                    lineTo(13.4f, 13.4f)
                    lineTo(8.2f, 15.8f)
                    lineTo(10.6f, 10.6f)
                    closePath()
                }
                g.draw(needle)
            }
        }
    }

    internal fun pixelSprite(icon: Icon): Array<String> = when (icon) {
        Icon.FEED -> arrayOf(
            ".##########.",
            "#..........#",
            "#.########.#",
            "#..........#",
            "#.########.#",
            "#..........#",
            "#.########.#",
            "#..........#",
            "#.#####....#",
            "#..........#",
            ".##########.",
            "............"
        )
        Icon.MESSAGE -> arrayOf(
            "..########..",
            ".#........#.",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            ".#........#.",
            "..###..###..",
            "..#..##.....",
            ".##.........",
            "............",
            "............"
        )
        Icon.PLUS -> arrayOf(
            ".....##.....",
            ".....##.....",
            ".....##.....",
            ".....##.....",
            ".##########.",
            ".##########.",
            ".....##.....",
            ".....##.....",
            ".....##.....",
            ".....##.....",
            "............",
            "............"
        )
        Icon.BELL -> arrayOf(
            ".....##.....",
            "....####....",
            "...#....#...",
            "...#....#...",
            "...#....#...",
            "..#......#..",
            "..#......#..",
            ".##########.",
            "............",
            "....####....",
            ".....##.....",
            "............"
        )
        Icon.SEND -> arrayOf(
            ".........##.",
            ".......####.",
            ".....###..#.",
            "...###...#..",
            ".###....#...",
            "##.....#....",
            "..##..#.....",
            "....###.....",
            ".....##.....",
            "......#.....",
            "............",
            "............"
        )
        Icon.PHONE -> arrayOf(
            "..###.......",
            ".#...#......",
            ".#...#......",
            "..#..#......",
            "...#........",
            "....#.......",
            ".....#......",
            "......#..#..",
            "......#...#.",
            ".......###..",
            "............",
            "............"
        )
        Icon.TRASH -> arrayOf(
            "....####....",
            ".##########.",
            "..#......#..",
            "..#.#..#.#..",
            "..#.#..#.#..",
            "..#.#..#.#..",
            "..#.#..#.#..",
            "..#.#..#.#..",
            "..#......#..",
            "...######...",
            "............",
            "............"
        )
        Icon.HEART -> arrayOf(
            "............",
            "..###..###..",
            ".#...##...#.",
            "#..........#",
            "#..........#",
            ".#........#.",
            "..#......#..",
            "...#....#...",
            "....#..#....",
            ".....##.....",
            "............",
            "............"
        )
        Icon.HEART_FILLED -> arrayOf(
            "............",
            "..###..###..",
            ".##########.",
            "############",
            "############",
            ".##########.",
            "..########..",
            "...######...",
            "....####....",
            ".....##.....",
            "............",
            "............"
        )
        Icon.BACK -> arrayOf(
            "............",
            "......##....",
            ".....##.....",
            "....##......",
            "...##.......",
            "..##........",
            "...##.......",
            "....##......",
            ".....##.....",
            "......##....",
            "............",
            "............"
        )
        Icon.CLOSE -> arrayOf(
            "............",
            "..##....##..",
            "...##..##...",
            "....####....",
            ".....##.....",
            ".....##.....",
            "....####....",
            "...##..##...",
            "..##....##..",
            "............",
            "............",
            "............"
        )
        Icon.SEARCH -> arrayOf(
            "..#####.....",
            ".#.....#....",
            "#.......#...",
            "#.......#...",
            "#.......#...",
            ".#.....#....",
            "..#####.....",
            ".......##...",
            "........##..",
            ".........##.",
            "............",
            "............"
        )
        Icon.USER -> arrayOf(
            "....####....",
            "...#....#...",
            "...#....#...",
            "....####....",
            "............",
            "...######...",
            "..#......#..",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            "............",
            "............"
        )
        Icon.FLAG -> arrayOf(
            "..#.........",
            "..#########.",
            "..#.......#.",
            "..#......#..",
            "..#.......#.",
            "..#########.",
            "..#.........",
            "..#.........",
            "..#.........",
            "..#.........",
            "............",
            "............"
        )
        Icon.GLOBE -> arrayOf(
            "...######...",
            "..#..##..#..",
            ".#..#..#..#.",
            "#...#..#...#",
            "############",
            "#...#..#...#",
            ".#..#..#..#.",
            "..#..##..#..",
            "...######...",
            "............",
            "............",
            "............"
        )
        Icon.USERS -> arrayOf(
            "..###...##..",
            ".#...#.#..#.",
            "..###...##..",
            "............",
            ".#####..###.",
            "#.....#....#",
            "#.....#....#",
            "#.....#....#",
            "............",
            "............",
            "............",
            "............"
        )
        Icon.CHECK -> arrayOf(
            "............",
            "............",
            ".........##.",
            "........##..",
            ".......##...",
            ".##...##....",
            "..##.##.....",
            "...###......",
            "....#.......",
            "............",
            "............",
            "............"
        )
        Icon.IMAGE -> arrayOf(
            ".##########.",
            "#..........#",
            "#..##......#",
            "#..##......#",
            "#.......#..#",
            "#...#..###.#",
            "#..###.###.#",
            "#.########.#",
            "#..........#",
            ".##########.",
            "............",
            "............"
        )
        Icon.SPARKLES -> arrayOf(
            ".....##.....",
            ".....##.....",
            "....####....",
            "....####....",
            "..########..",
            "############",
            "..########..",
            "....####....",
            "....####....",
            ".....##.....",
            ".....##.....",
            "............"
        )
        Icon.CAMERA -> arrayOf(
            "....####....",
            "...#....#...",
            ".##########.",
            "#..........#",
            "#....##....#",
            "#...#..#...#",
            "#...#..#...#",
            "#....##....#",
            "#..........#",
            ".##########.",
            "............",
            "............"
        )
        Icon.POLL -> arrayOf(
            ".....##.....",
            ".....##.....",
            ".....##.....",
            ".....##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            "............",
            "............"
        )
        Icon.SWORDS -> arrayOf(
            ".#........#.",
            ".##......##.",
            "..##....##..",
            "...##..##...",
            "....####....",
            "....####....",
            "...##..##...",
            "..##....##..",
            ".#.#....#.#.",
            "#...#..#...#",
            "............",
            "............"
        )
        Icon.TRADE -> arrayOf(
            ".......#....",
            "........#...",
            ".#########..",
            "........#...",
            ".......#....",
            "............",
            "....#.......",
            "...#........",
            "..#########.",
            "...#........",
            "....#.......",
            "............"
        )
        Icon.CHEVRON_DOWN -> arrayOf(
            "............",
            "............",
            "............",
            ".#........#.",
            ".##......##.",
            "..##....##..",
            "...##..##...",
            "....####....",
            ".....##.....",
            "............",
            "............",
            "............"
        )
        Icon.CHEVRON_UP -> arrayOf(
            "............",
            "............",
            ".....##.....",
            "....####....",
            "...##..##...",
            "..##....##..",
            ".##......##.",
            ".#........#.",
            "............",
            "............",
            "............",
            "............"
        )
        Icon.CHEVRON_RIGHT -> arrayOf(
            "............",
            "....##......",
            ".....##.....",
            "......##....",
            ".......##...",
            "........##..",
            ".......##...",
            "......##....",
            ".....##.....",
            "....##......",
            "............",
            "............"
        )
        Icon.MINUS -> arrayOf(
            "............",
            "............",
            "............",
            "............",
            "............",
            ".##########.",
            ".##########.",
            "............",
            "............",
            "............",
            "............",
            "............"
        )
        Icon.SETTINGS -> arrayOf(
            ".....##.....",
            "..#..##..#..",
            ".##########.",
            "..##....##..",
            "..#......#..",
            "###......###",
            "###......###",
            "..#......#..",
            "..##....##..",
            ".##########.",
            "..#..##..#..",
            ".....##....."
        )
        Icon.FILTER -> arrayOf(
            "............",
            ".##########.",
            ".##......##.",
            "..##....##..",
            "...##..##...",
            "....####....",
            "....#..#....",
            "....#..#....",
            "....#..#....",
            ".....##.....",
            "............",
            "............"
        )
        Icon.REFRESH -> arrayOf(
            "...######...",
            "..#......##.",
            ".#......###.",
            ".#.......#..",
            ".#..........",
            ".#..........",
            ".#.........#",
            ".#........#.",
            "..#......#..",
            "...######...",
            "............",
            "............"
        )
        Icon.STAR -> arrayOf(
            ".....##.....",
            ".....##.....",
            "....#..#....",
            ".####..####.",
            ".#........#.",
            "..#......#..",
            "...#....#...",
            "..#..##..#..",
            "..#.#..#.#..",
            "..##....##..",
            "............",
            "............"
        )
        Icon.STAR_FILLED -> arrayOf(
            ".....##.....",
            ".....##.....",
            "....####....",
            ".##########.",
            ".##########.",
            "..########..",
            "...######...",
            "..########..",
            "..###..###..",
            "..##....##..",
            "............",
            "............"
        )
        Icon.INFO -> arrayOf(
            "...######...",
            "..#......#..",
            ".#...##...#.",
            ".#...##...#.",
            ".#........#.",
            ".#...##...#.",
            ".#...##...#.",
            ".#...##...#.",
            "..#......#..",
            "...######...",
            "............",
            "............"
        )
        Icon.WARNING -> arrayOf(
            ".....##.....",
            "....#..#....",
            "...#....#...",
            "...#.##.#...",
            "..#..##..#..",
            "..#..##..#..",
            ".#........#.",
            ".#...##...#.",
            "#..........#",
            "############",
            "............",
            "............"
        )
        Icon.LOCK -> arrayOf(
            "....####....",
            "...#....#...",
            "...#....#...",
            "...#....#...",
            "..########..",
            "..#......#..",
            "..#..##..#..",
            "..#..##..#..",
            "..#......#..",
            "..########..",
            "............",
            "............"
        )
        Icon.EDIT -> arrayOf(
            "........##..",
            ".......####.",
            "......##..#.",
            ".....##..#..",
            "....##..#...",
            "...##..#....",
            "..##..#.....",
            "..#..#......",
            "..###.......",
            "..##........",
            "............",
            "............"
        )
        Icon.COMPASS -> arrayOf(
            "...######...",
            "..#......#..",
            ".#.....##.#.",
            ".#....###.#.",
            ".#...##.#.#.",
            ".#..#.##..#.",
            ".#..###...#.",
            ".#..##....#.",
            "..#......#..",
            "...######...",
            "............",
            "............"
        )
    }

    internal fun rasterizeIcon(
        icon: Icon,
        cellSize: Int = CELL_SIZE,
        antialiased: Boolean = true
    ): BufferedImage {
        if (!antialiased) {
            val img = BufferedImage(PIXEL_CELL_SIZE, PIXEL_CELL_SIZE, BufferedImage.TYPE_INT_ARGB)
            val sprite = pixelSprite(icon)
            for (y in 0 until PIXEL_CELL_SIZE) {
                val line = sprite.getOrNull(y) ?: continue
                for (x in 0 until PIXEL_CELL_SIZE) {
                    if (x < line.length && line[x] == '#') {
                        img.setRGB(x, y, 0xFFFFFFFF.toInt())
                    }
                }
            }
            return img
        }
        val img = BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.stroke = BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val scale = cellSize / 24.0
            g.scale(scale, scale)
            g.color = Color.WHITE
            drawVectorIcon(g, icon)
        } finally {
            g.dispose()
        }
        return img
    }

    fun drawIcon(
        icon: Icon,
        physX: Float,
        physY: Float,
        physSize: Float,
        colorArgb: Int,
        pixelated: Boolean = false
    ) {
        if (((colorArgb ushr 24) and 0xFF) == 0) return
        val texId = if (pixelated) ensureBuiltPixel() else ensureBuilt()
        val uv = (if (pixelated) pixelUvs[icon] else uvs[icon]) ?: return
        val drawX: Float
        val drawY: Float
        val drawSize: Float
        if (pixelated) {
            // Snap pixel-art icons to integer multiples of PIXEL_CELL_SIZE on integer pixel coords
            // so GL_NEAREST never shears, skips, or doubles individual rows/columns.
            val intScale = (physSize / PIXEL_CELL_SIZE).toInt().coerceAtLeast(1)
            drawSize = (PIXEL_CELL_SIZE * intScale).toFloat()
            drawX = kotlin.math.round(physX + (physSize - drawSize) * 0.5f)
            drawY = kotlin.math.round(physY + (physSize - drawSize) * 0.5f)
        } else {
            drawX = physX
            drawY = physY
            drawSize = physSize
        }
        HdBatch.pushQuad(
            x = drawX,
            y = drawY,
            w = drawSize,
            h = drawSize,
            u0 = uv[0],
            v0 = uv[1],
            u1 = uv[2],
            v1 = uv[3],
            mode = HdBatch.MODE_ALPHA_ATLAS,
            colorTop = colorArgb,
            textureId = texId
        )
    }

    fun clear() {
        texture?.close()
        texture = null
        uvs.clear()
        pixelTexture?.close()
        pixelTexture = null
        pixelUvs.clear()
    }
}
