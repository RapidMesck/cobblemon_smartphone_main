package com.nbp.cobblemon_smartphone.client.ui.hd

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11

/**
 * Ergonomic `dp`-space HD UI drawing canvas backed by [HdBatch], [HdFontAtlas], and [HdIconAtlas].
 * Every coordinate and size is expressed in floating-point density-independent points (`dp`)
 * and converted to exact physical monitor pixels through [viewport].
 */
class HdCanvas(
    val viewport: HdViewport,
    val style: HdUiStyle = HdUiStyle.HD
) {
    val isPixelStyle: Boolean get() = style == HdUiStyle.PIXEL

    private val clipStack = ArrayDeque<HdViewport.GlScissorRect>()
    private var guiGraphics: GuiGraphics? = null

    fun begin(guiGraphics: GuiGraphics) {
        this.guiGraphics = guiGraphics
        guiGraphics.flush()
        clipStack.clear()
        HdBatch.beginFrame(viewport.windowWidth, viewport.windowHeight)
    }

    fun flush() {
        HdBatch.flush()
    }

    fun end() {
        HdBatch.flush()
        while (clipStack.isNotEmpty()) {
            clipStack.removeLast()
        }
        RenderSystem.disableScissor()
        this.guiGraphics = null
    }

    fun pushClip(dpX: Float, dpY: Float, dpW: Float, dpH: Float): Boolean {
        HdBatch.flush()
        val rect = viewport.toGlScissor(dpX, dpY, dpW, dpH)
        val combined = if (clipStack.isEmpty()) rect else clipStack.last().intersect(rect)
        clipStack.addLast(combined)
        RenderSystem.enableScissor(combined.x, combined.y, combined.width, combined.height)
        return !combined.isEmpty
    }

    fun popClip() {
        HdBatch.flush()
        if (clipStack.isNotEmpty()) {
            clipStack.removeLast()
        }
        if (clipStack.isEmpty()) {
            RenderSystem.disableScissor()
        } else {
            val top = clipStack.last()
            RenderSystem.enableScissor(top.x, top.y, top.width, top.height)
        }
    }

    inline fun clipped(dpX: Float, dpY: Float, dpW: Float, dpH: Float, block: () -> Unit) {
        val visible = pushClip(dpX, dpY, dpW, dpH)
        try {
            if (visible) block()
        } finally {
            popClip()
        }
    }

    fun rect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        radius: Float = 0f,
        borderW: Float = 0f,
        borderColor: Int = 0
    ) {
        rectCorners(x, y, w, h, radius, radius, radius, radius, color, color, borderW, borderColor)
    }

    fun gradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        colorTop: Int,
        colorBottom: Int,
        radius: Float = 0f,
        borderW: Float = 0f,
        borderColor: Int = 0
    ) {
        rectCorners(x, y, w, h, radius, radius, radius, radius, colorTop, colorBottom, borderW, borderColor)
    }

    fun rectCorners(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rTL: Float,
        rTR: Float,
        rBR: Float,
        rBL: Float,
        colorTop: Int,
        colorBottom: Int = colorTop,
        borderW: Float = 0f,
        borderColor: Int = 0
    ) {
        val s = viewport.scale
        val effTL = if (isPixelStyle) 0f else rTL * s
        val effTR = if (isPixelStyle) 0f else rTR * s
        val effBR = if (isPixelStyle) 0f else rBR * s
        val effBL = if (isPixelStyle) 0f else rBL * s
        val effBorder = if (isPixelStyle && borderW > 0f) maxOf(1.25f, borderW) * s else borderW * s
        HdBatch.pushQuad(
            x = viewport.toPhysX(x),
            y = viewport.toPhysY(y),
            w = w * s,
            h = h * s,
            rTL = effTL,
            rTR = effTR,
            rBR = effBR,
            rBL = effBL,
            borderW = effBorder,
            mode = HdBatch.MODE_ROUNDED_RECT,
            colorTop = colorTop,
            colorBottom = colorBottom,
            borderColor = borderColor
        )
    }

    fun shadow(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        blur: Float = 8f,
        offsetY: Float = 2f,
        color: Int = 0x3A000000
    ) {
        if (isPixelStyle) {
            val hardShadowColor = HdColors.multiplyAlpha(color, 0.7f)
            rect(x + 2f, y + maxOf(1.5f, offsetY), w, h, hardShadowColor, radius = 0f)
            return
        }
        val s = viewport.scale
        val pad = blur
        val r = radius * s
        HdBatch.pushQuad(
            x = viewport.toPhysX(x - pad),
            y = viewport.toPhysY(y - pad + offsetY),
            w = (w + pad * 2f) * s,
            h = (h + pad * 2f) * s,
            rTL = r,
            rTR = r,
            rBR = r,
            rBL = r,
            softness = blur * s,
            mode = HdBatch.MODE_DROP_SHADOW,
            colorTop = color
        )
    }

    fun circle(
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        borderW: Float = 0f,
        borderColor: Int = 0
    ) {
        rect(cx - radius, cy - radius, radius * 2f, radius * 2f, color, radius, borderW, borderColor)
    }

    enum class TextureFilter { KEEP, LINEAR, NEAREST }

    fun texture(
        location: ResourceLocation,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        u0: Float = 0f,
        v0: Float = 0f,
        u1: Float = 1f,
        v1: Float = 1f,
        radius: Float = 0f,
        borderW: Float = 0f,
        borderColor: Int = 0,
        tint: Int = 0xFFFFFFFF.toInt(),
        smoothFilter: Boolean = false,
        filter: TextureFilter = if (smoothFilter) TextureFilter.LINEAR else TextureFilter.KEEP
    ) {
        val tex = Minecraft.getInstance().textureManager.getTexture(location)
        val texId = tex.id
        val effectiveFilter = if (isPixelStyle) TextureFilter.NEAREST else filter
        if (effectiveFilter != TextureFilter.KEEP) {
            HdBatch.flush()
            val glFilter = if (effectiveFilter == TextureFilter.LINEAR) GL11.GL_LINEAR else GL11.GL_NEAREST
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, glFilter)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, glFilter)
        }
        val s = viewport.scale
        val r = if (isPixelStyle) 0f else radius * s
        val effBorder = if (isPixelStyle && borderW > 0f) maxOf(1.25f, borderW) * s else borderW * s
        HdBatch.pushQuad(
            x = viewport.toPhysX(x),
            y = viewport.toPhysY(y),
            w = w * s,
            h = h * s,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
            rTL = r,
            rTR = r,
            rBR = r,
            rBL = r,
            borderW = effBorder,
            mode = HdBatch.MODE_TEXTURED_ROUNDED,
            colorTop = tint,
            borderColor = borderColor,
            textureId = texId
        )
    }

    /** Draws a Minecraft player head (circular in HD style, classic square in Pixel style). */
    fun playerAvatar(
        skin: PlayerSkin,
        x: Float,
        y: Float,
        size: Float,
        borderW: Float = 1f,
        borderColor: Int = 0x40FFFFFF
    ) {
        val radius = if (isPixelStyle) 0f else size * 0.5f
        val loc = skin.texture()
        // Base face (8..16 on 64x64)
        texture(
            location = loc,
            x = x,
            y = y,
            w = size,
            h = size,
            u0 = 8f / 64f,
            v0 = 8f / 64f,
            u1 = 16f / 64f,
            v1 = 16f / 64f,
            radius = radius
        )
        // Hat overlay (40..48 on 64x64)
        texture(
            location = loc,
            x = x,
            y = y,
            w = size,
            h = size,
            u0 = 40f / 64f,
            v0 = 8f / 64f,
            u1 = 48f / 64f,
            v1 = 16f / 64f,
            radius = radius,
            borderW = borderW,
            borderColor = borderColor
        )
    }

    fun icon(
        icon: HdIconAtlas.Icon,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        HdIconAtlas.drawIcon(
            icon = icon,
            physX = viewport.toPhysX(x),
            physY = viewport.toPhysY(y),
            physSize = viewport.toPhysSize(size),
            colorArgb = color,
            pixelated = isPixelStyle
        )
    }

    fun text(
        text: String,
        x: Float,
        y: Float,
        sizeDp: Float,
        color: Int,
        weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ): Float {
        if (text.isEmpty()) return 0f
        if (isPixelStyle) {
            val gg = guiGraphics ?: return measureText(text, sizeDp, weight, fontId)
            val font = Minecraft.getInstance().font
            val dpScale = HdTextLayout.pixelFontDpScale(sizeDp, viewport.scale)
            val mcScale = viewport.toMcGuiScale(dpScale)
            HdBatch.flush()
            val pose = gg.pose()
            pose.pushPose()
            val snappedX = kotlin.math.floor(viewport.toPhysX(x).toDouble()).toFloat() / viewport.guiScale.toFloat()
            val snappedY = kotlin.math.floor(viewport.toPhysY(y + sizeDp * 0.08f).toDouble()).toFloat() / viewport.guiScale.toFloat()
            pose.translate(snappedX, snappedY, 0f)
            pose.scale(mcScale, mcScale, 1f)
            gg.drawString(font, text, 0, 0, color, false)
            pose.popPose()
            gg.flush()
            return font.width(text) * dpScale
        }
        val pxSize = HdTextLayout.resolvePixelSize(sizeDp, viewport.scale)
        val drawnPx = HdFontAtlas.drawText(
            text = text,
            physX = viewport.toPhysX(x),
            physY = viewport.toPhysY(y),
            weight = weight,
            pixelSize = pxSize,
            colorArgb = color,
            fontId = fontId
        )
        return drawnPx / viewport.scale
    }

    fun textCentered(
        text: String,
        centerX: Float,
        y: Float,
        sizeDp: Float,
        color: Int,
        weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ) {
        val w = measureText(text, sizeDp, weight, fontId)
        text(text, centerX - w * 0.5f, y, sizeDp, color, weight, fontId)
    }

    fun measureText(
        text: String,
        sizeDp: Float,
        weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ): Float = HdTextLayout.measureWidthDp(text, sizeDp, weight, viewport.scale, fontId)

    fun lineHeight(
        sizeDp: Float,
        weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ): Float = HdTextLayout.lineHeightDp(sizeDp, weight, viewport.scale, fontId)

    fun truncate(
        text: String,
        maxWidthDp: Float,
        sizeDp: Float,
        weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
        fontId: String = HdTextLayout.DEFAULT_FONT_ID
    ): String = HdTextLayout.truncateDp(text, maxWidthDp, sizeDp, weight, viewport.scale, fontId = fontId)
}
