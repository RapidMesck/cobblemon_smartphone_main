package com.nbp.cobblemon_smartphone.client.ui.hd

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.nbp.cobblemon_smartphone.client.gui.PlayerHeads
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

class TextNode(
    var text: String,
    var fontSizeDp: Float = 10f,
    var weight: HdTextLayout.Weight = HdTextLayout.Weight.REGULAR,
    var fontId: String = HdTextLayout.DEFAULT_FONT_ID,
    var color: HdColorSpec = HdColorSpec.Token("text_dark"),
    var align: HdAlign = HdAlign.START,
    var wrap: Boolean = false,
    var maxLines: Int = Int.MAX_VALUE,
    var ellipsis: Boolean = true,
    var socialMarkup: Boolean = false,
    var onHashtagClick: ((String) -> Unit)? = null
) : HdNode() {

    private var cachedLines: List<HdTextLayout.WrappedLine> = emptyList()
    private var cachedLineHeight: Float = 12f
    private var cachedScale: Float = 1f
    private var cachedWrapW: Float = -1f

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        cachedScale = ctx.viewportScale
        cachedLineHeight = HdTextLayout.lineHeightDp(fontSizeDp, weight, ctx.viewportScale, fontId)

        if (text.isEmpty()) {
            cachedLines = emptyList()
            cachedWrapW = availContentW
            return 0f to cachedLineHeight
        }

        if (!wrap) {
            val display = if (availContentW.isFinite() && ellipsis) {
                HdTextLayout.truncateDp(text, availContentW, fontSizeDp, weight, ctx.viewportScale, fontId = fontId)
            } else {
                text
            }
            val spans = if (socialMarkup) HdTextLayout.parseSocialSpans(display) else listOf(HdTextLayout.StyledSpan(display))
            val w = HdTextLayout.measureWidthDp(display, fontSizeDp, weight, ctx.viewportScale, fontId)
            cachedLines = listOf(HdTextLayout.WrappedLine(spans, w))
            cachedWrapW = availContentW
            return w to cachedLineHeight
        }

        val targetW = if (availContentW.isFinite()) availContentW else 10_000f
        val wrapped = if (socialMarkup) {
            HdTextLayout.wrapSocialText(text, targetW, fontSizeDp, ctx.viewportScale, fontId)
        } else {
            HdTextLayout.wrapPlainText(text, targetW, fontSizeDp, weight, ctx.viewportScale, fontId).map { line ->
                val lw = HdTextLayout.measureWidthDp(line, fontSizeDp, weight, ctx.viewportScale, fontId)
                HdTextLayout.WrappedLine(listOf(HdTextLayout.StyledSpan(line)), lw)
            }
        }
        val limited = if (wrapped.size > maxLines) wrapped.take(maxLines) else wrapped
        cachedLines = limited
        cachedWrapW = targetW
        val maxLineW = limited.maxOfOrNull { it.widthDp } ?: 0f
        val totalH = (limited.size.coerceAtLeast(1) * cachedLineHeight)
        return maxLineW to totalH
    }

    override fun renderContent(rc: HdRenderContext) {
        if (cachedLines.isEmpty()) return
        val inner = bounds.inset(padding)
        val baseColor = color.resolve(rc.colors)
        val mentionCol = rc.colors.primary
        val hashtagCol = rc.colors.contentGold

        var cy = inner.y
        for (line in cachedLines) {
            val startX = when (align) {
                HdAlign.START, HdAlign.STRETCH -> inner.x
                HdAlign.CENTER -> inner.x + (inner.width - line.widthDp) * 0.5f
                HdAlign.END -> inner.x + (inner.width - line.widthDp)
            }
            var penX = startX
            for (span in line.spans) {
                val spanCol = when (span.kind) {
                    HdTextLayout.StyledSpan.Kind.PLAIN -> baseColor
                    HdTextLayout.StyledSpan.Kind.MENTION -> mentionCol
                    HdTextLayout.StyledSpan.Kind.HASHTAG -> hashtagCol
                }
                val spanWeight = if (span.kind == HdTextLayout.StyledSpan.Kind.PLAIN) weight else HdTextLayout.Weight.BOLD
                penX += rc.canvas.text(span.text, penX, cy, fontSizeDp, spanCol, spanWeight, fontId)
            }
            cy += cachedLineHeight
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (button == 0 && socialMarkup && onHashtagClick != null && cachedLines.isNotEmpty()) {
            val inner = bounds.inset(padding)
            val idx = ((dpY - inner.y) / cachedLineHeight).toInt().coerceIn(0, cachedLines.lastIndex)
            val line = cachedLines[idx]
            val tagSpan = line.spans.firstOrNull { it.kind == HdTextLayout.StyledSpan.Kind.HASHTAG }
            if (tagSpan != null) {
                onHashtagClick?.invoke(tagSpan.text.removePrefix("#"))
                return true
            }
        }
        return super.onMouseDown(dpX, dpY, button, state)
    }
}

class IconNode(
    var icon: HdIconAtlas.Icon,
    var sizeDp: Float = 14f,
    var color: HdColorSpec = HdColorSpec.Token("text_dark")
) : HdNode() {

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        sizeDp to sizeDp

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        val s = minOf(sizeDp, inner.width, inner.height)
        val ix = inner.x + (inner.width - s) * 0.5f
        val iy = inner.y + (inner.height - s) * 0.5f
        rc.canvas.icon(icon, ix, iy, s, color.resolve(rc.colors))
    }
}

class ImageNode(
    var texture: ResourceLocation,
    var imageWidthDp: Float = 32f,
    var imageHeightDp: Float = 32f,
    var u0: Float = 0f,
    var v0: Float = 0f,
    var u1: Float = 1f,
    var v1: Float = 1f,
    var radius: Float = 0f,
    var borderWidth: Float = 0f,
    var borderColor: HdColorSpec = HdColorSpec.TRANSPARENT,
    var tint: HdColorSpec = HdColorSpec.Fixed(0xFFFFFFFF.toInt()),
    var filter: HdCanvas.TextureFilter = HdCanvas.TextureFilter.LINEAR
) : HdNode() {

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        imageWidthDp to imageHeightDp

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        rc.canvas.texture(
            location = texture,
            x = inner.x,
            y = inner.y,
            w = inner.width,
            h = inner.height,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
            radius = radius,
            borderW = borderWidth,
            borderColor = borderColor.resolve(rc.colors),
            tint = tint.resolve(rc.colors),
            filter = filter
        )
    }
}

class AppIconNode(
    var theme: com.nbp.cobblemon_smartphone.theme.SmartphoneTheme,
    var actionId: String?,
    var defaultTexture: ResourceLocation,
    var sizeDp: Float = 30f,
    var alphaScale: Float = 1f
) : HdNode() {

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        sizeDp to sizeDp

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        val glyph = com.nbp.cobblemon_smartphone.theme.SmartphoneAppIconRenderer.resolveBuiltInGlyph(
            theme = theme,
            actionId = actionId,
            defaultTexture = defaultTexture,
            hovered = false
        )
        if (glyph == null) {
            val resolved = if (actionId != null) {
                theme.getAppIcon(actionId!!, defaultTexture)
            } else {
                defaultTexture
            }
            val alphaByte = (255 * alphaScale).toInt().coerceIn(0, 255)
            val tintArgb = (alphaByte shl 24) or 0x00FFFFFF
            rc.canvas.texture(
                location = resolved,
                x = inner.x,
                y = inner.y,
                w = inner.width,
                h = inner.height,
                tint = tintArgb,
                filter = HdCanvas.TextureFilter.NEAREST
            )
            return
        }

        val palette = com.nbp.cobblemon_smartphone.theme.SmartphoneAppIconRenderer.computePalette(
            colors = rc.colors,
            hovered = false,
            alphaScale = alphaScale
        )
        rc.canvas.texture(
            location = com.nbp.cobblemon_smartphone.theme.SmartphoneAppIconRenderer.SQUIRCLE_BODY,
            x = inner.x,
            y = inner.y,
            w = inner.width,
            h = inner.height,
            tint = palette.bodyArgb,
            filter = HdCanvas.TextureFilter.NEAREST
        )
        rc.canvas.texture(
            location = com.nbp.cobblemon_smartphone.theme.SmartphoneAppIconRenderer.SQUIRCLE_SHEEN,
            x = inner.x,
            y = inner.y,
            w = inner.width,
            h = inner.height,
            tint = palette.sheenArgb,
            filter = HdCanvas.TextureFilter.NEAREST
        )
        rc.canvas.texture(
            location = com.nbp.cobblemon_smartphone.theme.SmartphoneAppIconRenderer.SQUIRCLE_BORDER,
            x = inner.x,
            y = inner.y,
            w = inner.width,
            h = inner.height,
            tint = palette.borderArgb,
            filter = HdCanvas.TextureFilter.NEAREST
        )
        rc.canvas.texture(
            location = glyph,
            x = inner.x,
            y = inner.y,
            w = inner.width,
            h = inner.height,
            tint = palette.glyphArgb,
            filter = HdCanvas.TextureFilter.NEAREST
        )
    }
}

class PlayerAvatarNode(
    var uuid: java.util.UUID,
    var playerName: String,
    var skinUrl: String? = null,
    var sizeDp: Float = 26f,
    var borderWidth: Float = 1.1f,
    var borderColor: HdColorSpec = HdColorSpec.Token("primary")
) : HdNode() {

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        sizeDp to sizeDp

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        val s = minOf(inner.width, inner.height)
        rc.canvas.playerAvatar(
            skin = PlayerHeads.skinFor(uuid, playerName, skinUrl),
            x = inner.x + (inner.width - s) * 0.5f,
            y = inner.y + (inner.height - s) * 0.5f,
            size = s,
            borderW = borderWidth,
            borderColor = borderColor.resolve(rc.colors)
        )
    }
}

class ItemIconNode(
    var stack: ItemStack,
    var sizeDp: Float = 24f,
    var showCount: Boolean = true
) : HdNode() {

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        sizeDp to sizeDp

    override fun renderContent(rc: HdRenderContext) {
        if (stack.isEmpty) return
        val inner = bounds.inset(padding)
        val s = minOf(inner.width, inner.height)
        val x = inner.x + (inner.width - s) * 0.5f
        val y = inner.y + (inner.height - s) * 0.5f
        val mcScale = rc.canvas.viewport.toMcGuiScale(s / 16f)

        rc.canvas.flush()
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest()
        val matrices = rc.guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(
            rc.canvas.viewport.toMcGuiX(x),
            rc.canvas.viewport.toMcGuiY(y),
            0f
        )
        matrices.scale(mcScale, mcScale, 1f)
        rc.guiGraphics.renderItem(stack, 0, 0)
        if (showCount && stack.count > 1) {
            rc.guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0)
        }
        matrices.popPose()
        rc.guiGraphics.flush()
    }
}

/**
 * Renders a 3D [net.minecraft.client.resources.model.BakedModel] (such as a Smartphone / Rotom
 * hand model or custom skin model) centered and auto-fitted to its bounding box, with optional
 * continuous Y-axis rotation.
 */
class Model3DNode(
    var modelLocation: ResourceLocation,
    var fallbackStack: ItemStack = ItemStack.EMPTY,
    var sizeDp: Float = 44f,
    var tiltXDegrees: Float = 14f,
    var spinDegreesPerSec: Float = 65f,
    var initialAngleY: Float = 25f
) : HdNode() {

    private data class ModelBounds(
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float,
        val maxExtent: Float
    )

    companion object {
        // Dummy non-smartphone ItemStack so ItemRendererMixin does not swap our explicit 3D BakedModel.
        private val DUMMY_STACK: ItemStack by lazy {
            ItemStack(net.minecraft.world.item.Items.PAPER)
        }
        private val boundsCache = java.util.WeakHashMap<net.minecraft.client.resources.model.BakedModel, ModelBounds>()

        private fun computeBounds(model: net.minecraft.client.resources.model.BakedModel): ModelBounds {
            boundsCache[model]?.let { return it }
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY

            val rand = net.minecraft.util.RandomSource.create(42L)
            val dirs: List<net.minecraft.core.Direction?> = listOf(null) + net.minecraft.core.Direction.entries
            for (dir in dirs) {
                val quads = try {
                    model.getQuads(null, dir, rand)
                } catch (_: Exception) {
                    emptyList()
                }
                for (quad in quads) {
                    val verts = quad.vertices
                    val stride = verts.size / 4
                    if (stride < 3) continue
                    for (v in 0 until 4) {
                        val base = v * stride
                        val x = Float.fromBits(verts[base])
                        val y = Float.fromBits(verts[base + 1])
                        val z = Float.fromBits(verts[base + 2])
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (z < minZ) minZ = z
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                        if (z > maxZ) maxZ = z
                    }
                }
            }

            val result = if (minX.isFinite() && maxX > minX) {
                val sx = (maxX - minX).coerceAtLeast(0.01f)
                val sy = (maxY - minY).coerceAtLeast(0.01f)
                val sz = (maxZ - minZ).coerceAtLeast(0.01f)
                // Use horizontal diagonal so the model never clips horizontally while spinning 360°.
                val horizDiag = kotlin.math.hypot(sx.toDouble(), sz.toDouble()).toFloat()
                val extent = maxOf(sy, horizDiag).coerceAtLeast(0.1f)
                ModelBounds(
                    centerX = (minX + maxX) * 0.5f,
                    centerY = (minY + maxY) * 0.5f,
                    centerZ = (minZ + maxZ) * 0.5f,
                    maxExtent = extent
                )
            } else {
                ModelBounds(0.5f, 0.5f, 0.5f, 1.0f)
            }
            boundsCache[model] = result
            return result
        }
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        sizeDp to sizeDp

    override fun renderContent(rc: HdRenderContext) {
        val mc = Minecraft.getInstance()
        val mrl = net.minecraft.client.resources.model.ModelResourceLocation(modelLocation, "inventory")
        val baked = mc.modelManager.getModel(mrl)
        val inner = bounds.inset(padding)
        val s = minOf(inner.width, inner.height)

        if (baked == mc.modelManager.missingModel) {
            if (!fallbackStack.isEmpty) {
                val x = inner.x + (inner.width - s) * 0.5f
                val y = inner.y + (inner.height - s) * 0.5f
                val mcScale = rc.canvas.viewport.toMcGuiScale(s / 16f)
                rc.canvas.flush()
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest()
                val matrices = rc.guiGraphics.pose()
                matrices.pushPose()
                matrices.translate(rc.canvas.viewport.toMcGuiX(x), rc.canvas.viewport.toMcGuiY(y), 0f)
                matrices.scale(mcScale, mcScale, 1f)
                rc.guiGraphics.renderItem(fallbackStack, 0, 0)
                matrices.popPose()
                rc.guiGraphics.flush()
            }
            return
        }

        val modelBounds = computeBounds(baked)
        val cx = rc.canvas.viewport.toMcGuiX(inner.x + inner.width * 0.5f)
        val cy = rc.canvas.viewport.toMcGuiY(inner.y + inner.height * 0.5f)
        val targetMcSize = rc.canvas.viewport.toMcGuiScale(s * 0.86f)
        val modelScale = targetMcSize / modelBounds.maxExtent

        val angleY = if (spinDegreesPerSec != 0f) {
            initialAngleY + ((System.currentTimeMillis() % 360000L) / 1000f) * spinDegreesPerSec
        } else {
            initialAngleY
        }

        rc.canvas.flush()
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest()
        com.mojang.blaze3d.platform.Lighting.setupForFlatItems()

        val matrices = rc.guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(cx, cy, 150f)
        matrices.scale(modelScale, -modelScale, modelScale)
        if (tiltXDegrees != 0f) {
            matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tiltXDegrees))
        }
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angleY))
        // ItemRenderer.render translates by (-0.5, -0.5, -0.5) internally; compensate so the model pivots around its true center.
        matrices.translate(0.5f - modelBounds.centerX, 0.5f - modelBounds.centerY, 0.5f - modelBounds.centerZ)

        mc.itemRenderer.render(
            DUMMY_STACK,
            net.minecraft.world.item.ItemDisplayContext.NONE,
            false,
            matrices,
            rc.guiGraphics.bufferSource(),
            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            baked
        )
        rc.guiGraphics.flush()
        matrices.popPose()
    }
}

class PokemonViewNode(
    var speciesId: String,
    var aspects: Set<String> = emptySet(),
    var sizeDp: Float = 56f,
    var rotationDegrees: Float = 30f,
    var poseType: PoseType = PoseType.PROFILE,
    var floatingState: FloatingState = FloatingState(),
    var showShinyBadge: Boolean = true
) : HdNode() {

    private var cachedKey: String? = null
    private var cachedRenderable: RenderablePokemon? = null

    private fun resolveRenderable(): RenderablePokemon? {
        val key = "$speciesId|${aspects.sorted().joinToString(",")}"
        if (key != cachedKey) {
            cachedKey = key
            val normalized = if (':' in speciesId) speciesId else "cobblemon:$speciesId"
            val loc = ResourceLocation.tryParse(normalized)
            cachedRenderable = loc?.let { PokemonSpecies.getByIdentifier(it) }
                ?.let { RenderablePokemon(it, aspects) }
        }
        return cachedRenderable
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        sizeDp to sizeDp

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        val s = minOf(inner.width, inner.height)
        val bx = inner.x + (inner.width - s) * 0.5f
        val by = inner.y + (inner.height - s) * 0.5f

        val renderable = resolveRenderable()
        if (renderable != null && !rc.isModalOverlayOpen) {
            rc.canvas.clipped(bx + 1f, by + 1f, s - 2f, s - 2f) {
                rc.canvas.flush()
                val matrices = rc.guiGraphics.pose()
                matrices.pushPose()
                matrices.translate(
                    rc.canvas.viewport.toMcGuiX(bx + s * 0.5f).toDouble(),
                    rc.canvas.viewport.toMcGuiY(by + s * 0.12f).toDouble(),
                    0.0
                )
                drawProfilePokemon(
                    renderable,
                    matrices,
                    Quaternionf().rotateY(Math.toRadians(rotationDegrees.toDouble()).toFloat()),
                    poseType,
                    floatingState,
                    0f,
                    rc.canvas.viewport.toMcGuiScale(s * 0.40f)
                )
                matrices.popPose()
                rc.guiGraphics.flush()
            }
        }
        if (showShinyBadge && aspects.contains("shiny")) {
            rc.canvas.icon(HdIconAtlas.Icon.SPARKLES, bx + 3f, by + 3f, 10f, rc.colors.gold)
        }
    }
}

class ProgressBarNode(
    var progress: Float = 0f,
    var barHeightDp: Float = 8f,
    var radius: Float = 4f,
    var trackColor: HdColorSpec = HdColorSpec.Token("surface_alt"),
    var fillColor: HdColorSpec = HdColorSpec.Token("primary"),
    var borderColor: HdColorSpec = HdColorSpec.Token("primary_border"),
    var label: String? = null
) : HdNode() {

    init {
        width = HdSizeSpec.Fill
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val w = if (availContentW.isFinite()) availContentW else 120f
        return w to barHeightDp
    }

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        val h = inner.height.coerceAtLeast(2f)
        val r = minOf(radius, h * 0.5f)
        rc.canvas.rect(inner.x, inner.y, inner.width, h, trackColor.resolve(rc.colors), r, 0.7f, borderColor.resolve(rc.colors))
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0f && inner.width > 2f) {
            val fillW = ((inner.width - 1.5f) * clamped).coerceAtLeast(minOf(r * 2f, inner.width - 1.5f))
            rc.canvas.rect(inner.x + 0.75f, inner.y + 0.75f, fillW, h - 1.5f, fillColor.resolve(rc.colors), (r - 0.5f).coerceAtLeast(0f))
        }
        label?.let { txt ->
            rc.canvas.textCentered(txt, inner.centerX, inner.y + (h - 8f) * 0.5f, 7.8f, rc.colors.textPrimary, HdTextLayout.Weight.BOLD)
        }
    }
}

class ButtonNode(
    var label: String = "",
    var icon: HdIconAtlas.Icon? = null,
    var variant: Variant = Variant.PRIMARY,
    var fontSizeDp: Float = 9.5f,
    var iconSizeDp: Float = 12f,
    var radius: Float = 9f,
    var enabled: Boolean = true
) : HdNode() {

    enum class Variant { PRIMARY, SECONDARY, SURFACE, DANGER, GHOST }

    init {
        padding = HdPadding.symmetric(horizontal = 10f, vertical = 5f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val iconW = if (icon != null) iconSizeDp + (if (label.isNotEmpty()) 5f else 0f) else 0f
        val textW = if (label.isNotEmpty()) {
            HdTextLayout.measureWidthDp(label, fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        } else 0f
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        val contentH = maxOf(lineH, if (icon != null) iconSizeDp else 0f)
        return (iconW + textW) to contentH
    }

    override fun renderBackground(rc: HdRenderContext) {
        val c = rc.colors
        val hovered = enabled && isHovered(rc)
        val bgCol = when (variant) {
            Variant.PRIMARY -> if (!enabled) c.buttonDisabledBg else if (hovered) c.primaryHover else c.primary
            Variant.SECONDARY -> if (!enabled) c.buttonDisabledBg else if (hovered) c.primary else c.headerNavyLight
            Variant.SURFACE -> if (!enabled) c.buttonDisabledBg else if (hovered) c.surfaceHover else c.surface
            Variant.DANGER -> if (!enabled) c.buttonDisabledBg else if (hovered) HdColors.mix(c.danger, 0xFFFFFFFF.toInt(), 0.15f) else c.danger
            Variant.GHOST -> if (hovered) 0x22FFFFFF else 0x00000000
        }
        val brdCol = when (variant) {
            Variant.SURFACE -> c.primaryBorder
            Variant.SECONDARY -> 0x33FFFFFF
            Variant.GHOST -> 0x00000000
            else -> 0x00000000
        }
        val brdW = if (brdCol != 0) 0.8f else 0f
        if (((bgCol ushr 24) and 0xFF) > 0 || brdW > 0f) {
            rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, bgCol, radius, brdW, brdCol)
        }
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val textCol = when {
            !enabled -> c.textMuted
            variant == Variant.SURFACE -> c.textDark
            else -> c.textPrimary
        }
        val textW = if (label.isNotEmpty()) rc.canvas.measureText(label, fontSizeDp, HdTextLayout.Weight.BOLD) else 0f
        val iconW = if (icon != null) iconSizeDp else 0f
        val gap = if (icon != null && label.isNotEmpty()) 5f else 0f
        val totalW = iconW + gap + textW
        var curX = inner.x + (inner.width - totalW) * 0.5f

        icon?.let { ic ->
            rc.canvas.icon(ic, curX, inner.y + (inner.height - iconSizeDp) * 0.5f, iconSizeDp, textCol)
            curX += iconW + gap
        }
        if (label.isNotEmpty()) {
            val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.BOLD)
            rc.canvas.text(label, curX, inner.y + (inner.height - lineH) * 0.5f, fontSizeDp, textCol, HdTextLayout.Weight.BOLD)
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled) return false
        return super.onMouseDown(dpX, dpY, button, state)
    }
}

class PillNode(
    var label: String,
    var icon: HdIconAtlas.Icon? = null,
    var active: Boolean = false,
    var fontSizeDp: Float = 8.8f,
    var radius: Float = 10f
) : HdNode() {

    init {
        padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val iconW = if (icon != null) 11f + 4f else 0f
        val textW = HdTextLayout.measureWidthDp(label, fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        return (iconW + textW) to maxOf(lineH, 11f)
    }

    override fun renderBackground(rc: HdRenderContext) {
        val c = rc.colors
        val hovered = isHovered(rc)
        val bg = when {
            active -> c.primary
            hovered -> c.surfaceHover
            else -> c.surface
        }
        rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, bg, radius, 0.8f, c.primaryBorder)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val fg = if (active) c.textPrimary else c.textDark
        val iconFg = if (active) c.textPrimary else c.textDarkMuted
        var curX = inner.x
        icon?.let { ic ->
            rc.canvas.icon(ic, curX, inner.y + (inner.height - 11f) * 0.5f, 11f, iconFg)
            curX += 15f
        }
        val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.BOLD)
        val maxTxtW = (inner.right - curX).coerceAtLeast(0f)
        val trimmed = rc.canvas.truncate(label, maxTxtW, fontSizeDp, HdTextLayout.Weight.BOLD)
        rc.canvas.text(trimmed, curX, inner.y + (inner.height - lineH) * 0.5f, fontSizeDp, fg, HdTextLayout.Weight.BOLD)
    }
}

class TabsNode(
    var items: List<TabItem>,
    var activeId: String,
    var style: Style = Style.BOTTOM_NAV,
    var onSelect: ((String) -> Unit)? = null
) : HdNode() {

    enum class Style { PILLS, BOTTOM_NAV, FLOATING_DOCK }

    data class TabItem(
        val id: String,
        val label: String,
        val icon: HdIconAtlas.Icon? = null,
        val hasBadge: Boolean = false
    )

    init {
        width = HdSizeSpec.Fill
        height = HdSizeSpec.Fixed(defaultHeight())
    }

    private fun defaultHeight(): Float = when (style) {
        Style.BOTTOM_NAV -> 34f
        Style.FLOATING_DOCK -> 40f
        Style.PILLS -> 24f
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val w = if (availContentW.isFinite()) availContentW else 200f
        return w to defaultHeight()
    }

    private fun floatingDockBounds(inner: HdBounds): HdBounds {
        val marginX = 7f
        val marginTop = 3f
        val marginBottom = 7f
        return HdBounds(
            inner.x + marginX,
            inner.y + marginTop,
            (inner.width - marginX * 2f).coerceAtLeast(24f),
            (inner.height - marginTop - marginBottom).coerceAtLeast(22f)
        )
    }

    override fun renderContent(rc: HdRenderContext) {
        if (items.isEmpty()) return
        val c = rc.colors
        val inner = bounds.inset(padding)

        if (style == Style.FLOATING_DOCK) {
            val dock = floatingDockBounds(inner)
            rc.canvas.shadow(dock.x, dock.y, dock.width, dock.height, 14f, blur = 8f, offsetY = 2f, color = 0x45000000)
            rc.canvas.rect(dock.x, dock.y, dock.width, dock.height, c.headerNavy, 14f, 0.8f, 0x33FFFFFF)

            val pad = 4f
            val gap = 4f
            val contentW = (dock.width - pad * 2f - gap * (items.size - 1)).coerceAtLeast(1f)
            val slotW = contentW / items.size
            val slotH = (dock.height - pad * 2f).coerceAtLeast(14f)
            val sy = dock.y + pad

            items.forEachIndexed { idx, item ->
                val sx = dock.x + pad + idx * (slotW + gap)
                val slotBounds = HdBounds(sx, sy, slotW, slotH)
                val hovered = slotBounds.contains(rc.mouseDpX, rc.mouseDpY)
                val active = item.id == activeId

                if (active) {
                    val bg = if (hovered) c.primaryHover else c.primary
                    rc.canvas.rect(sx, sy, slotW, slotH, bg, 10f)
                } else if (hovered) {
                    rc.canvas.rect(sx, sy, slotW, slotH, 0x18FFFFFF, 10f)
                }

                val fg = if (active) c.textPrimary else c.textMuted
                val iconSize = 12f
                val showLabel = active && item.label.isNotEmpty()
                val labelGap = if (showLabel && item.icon != null) 4f else 0f
                val maxTxtW = (slotW - 10f - (if (item.icon != null) iconSize + labelGap else 0f)).coerceAtLeast(0f)
                val trimmed = if (showLabel) rc.canvas.truncate(item.label, maxTxtW, 8.2f, HdTextLayout.Weight.BOLD) else ""
                val txtW = if (trimmed.isNotEmpty()) rc.canvas.measureText(trimmed, 8.2f, HdTextLayout.Weight.BOLD) else 0f
                val totalW = (if (item.icon != null) iconSize else 0f) + labelGap + txtW
                var penX = sx + (slotW - totalW) * 0.5f

                item.icon?.let { ic ->
                    val iy = sy + (slotH - iconSize) * 0.5f
                    rc.canvas.icon(ic, penX, iy, iconSize, fg)
                    if (item.hasBadge) {
                        rc.canvas.circle(penX + iconSize + 0.5f, iy + 1f, 2.8f, c.danger, 0.8f, if (active) c.primary else c.headerNavy)
                    }
                    penX += iconSize + labelGap
                }
                if (trimmed.isNotEmpty()) {
                    val lineH = rc.canvas.lineHeight(8.2f, HdTextLayout.Weight.BOLD)
                    rc.canvas.text(trimmed, penX, sy + (slotH - lineH) * 0.5f, 8.2f, fg, HdTextLayout.Weight.BOLD)
                }
            }
            return
        }

        val slotW = inner.width / items.size

        if (style == Style.BOTTOM_NAV) {
            rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, c.headerNavy)
            rc.canvas.rect(bounds.x, bounds.y, bounds.width, 0.8f, 0x2EFFFFFF)
        }

        items.forEachIndexed { idx, item ->
            val sx = inner.x + idx * slotW
            val sy = inner.y
            val sh = inner.height
            val slotBounds = HdBounds(sx, sy, slotW, sh)
            val hovered = slotBounds.contains(rc.mouseDpX, rc.mouseDpY)
            val active = item.id == activeId

            if (style == Style.BOTTOM_NAV) {
                if (active || hovered) {
                    rc.canvas.rect(sx + 5f, sy + 4f, slotW - 10f, sh - 8f, if (active) 0x22FFFFFF else 0x14FFFFFF, 8f)
                }
                val col = when {
                    active -> c.gold
                    hovered -> c.textPrimary
                    else -> c.textMuted
                }
                val iconSize = 13f
                val ix = sx + (slotW - iconSize) * 0.5f
                val iy = sy + 5.5f
                item.icon?.let { ic ->
                    rc.canvas.icon(ic, ix, iy, iconSize, col)
                }
                val lbl = rc.canvas.truncate(item.label, slotW - 8f, 7.2f, HdTextLayout.Weight.BOLD)
                rc.canvas.textCentered(lbl, sx + slotW * 0.5f, sy + 20.5f, 7.2f, col, HdTextLayout.Weight.BOLD)
                if (item.hasBadge) {
                    rc.canvas.circle(ix + iconSize + 1f, iy + 1f, 3.2f, c.danger, 0.9f, c.headerNavy)
                }
            } else {
                val bg = when {
                    active -> c.primary
                    hovered -> c.surfaceHover
                    else -> c.surface
                }
                rc.canvas.rect(sx + 2f, sy + 1f, slotW - 4f, sh - 2f, bg, 8f, 0.8f, c.primaryBorder)
                val fg = if (active) c.textPrimary else c.textDark
                val lbl = rc.canvas.truncate(item.label, slotW - 12f, 8.6f, HdTextLayout.Weight.BOLD)
                rc.canvas.textCentered(lbl, sx + slotW * 0.5f, sy + (sh - 10f) * 0.5f, 8.6f, fg, HdTextLayout.Weight.BOLD)
            }
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (button != 0 || items.isEmpty()) return false
        val inner = bounds.inset(padding)
        if (style == Style.FLOATING_DOCK) {
            val dock = floatingDockBounds(inner)
            if (!dock.contains(dpX, dpY)) return false
            val pad = 4f
            val contentW = (dock.width - pad * 2f).coerceAtLeast(1f)
            val idx = ((dpX - (dock.x + pad)) / (contentW / items.size)).toInt().coerceIn(0, items.lastIndex)
            onSelect?.invoke(items[idx].id)
            return true
        }
        if (!inner.contains(dpX, dpY)) return false
        val slotW = inner.width / items.size
        val idx = ((dpX - inner.x) / slotW).toInt().coerceIn(0, items.lastIndex)
        onSelect?.invoke(items[idx].id)
        return true
    }
}

class CheckboxNode(
    var label: String,
    var checked: Boolean = false,
    var enabled: Boolean = true,
    var fontSizeDp: Float = 8.8f,
    var onToggle: ((Boolean) -> Unit)? = null
) : HdNode() {

    init {
        padding = HdPadding.symmetric(horizontal = 2f, vertical = 2f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val textW = HdTextLayout.measureWidthDp(label, fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale)
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale)
        return (12f + 5f + textW) to maxOf(12f, lineH)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val boxSize = 11f
        val boxY = inner.y + (inner.height - boxSize) * 0.5f
        val hovered = enabled && isHovered(rc)
        val boxBg = when {
            !enabled -> c.buttonDisabledBg
            checked -> c.primary
            hovered -> c.surfaceHover
            else -> c.surface
        }
        val borderCol = when {
            !enabled -> c.textMuted
            checked || hovered -> c.primary
            else -> c.primaryBorder
        }
        rc.canvas.rect(inner.x, boxY, boxSize, boxSize, boxBg, 3f, 0.9f, borderCol)
        if (checked) {
            rc.canvas.icon(HdIconAtlas.Icon.CHECK, inner.x + 1.5f, boxY + 1.5f, 8f, if (enabled) c.textPrimary else c.textMuted)
        }
        val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.MEDIUM)
        val textCol = if (enabled) c.textDark else c.textDarkMuted
        val maxTxtW = (inner.width - boxSize - 5f).coerceAtLeast(0f)
        val trimmed = rc.canvas.truncate(label, maxTxtW, fontSizeDp, HdTextLayout.Weight.MEDIUM)
        rc.canvas.text(trimmed, inner.x + boxSize + 5f, inner.y + (inner.height - lineH) * 0.5f, fontSizeDp, textCol, HdTextLayout.Weight.MEDIUM)
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled || button != 0) return false
        onToggle?.invoke(!checked)
        return true
    }
}
