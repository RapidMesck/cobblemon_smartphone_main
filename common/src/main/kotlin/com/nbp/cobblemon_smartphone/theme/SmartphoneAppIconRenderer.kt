package com.nbp.cobblemon_smartphone.theme

import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.roundToInt

/**
 * Renders built-in minimalist app icons using Style B dynamic theme tinting:
 * 1. Squircle body tinted with theme `headerNavy` + `primary` (semi-transparent glass)
 * 2. Subtle top sheen + inner rim
 * 3. 1px squircle border tinted with theme `primary` (or bright `primaryHover` on hover)
 * 4. Monochrome glyph tinted with theme `textSecondary` + white (pure white on hover)
 *
 * Custom theme `app_icons` overrides and external datapack icons bypass dynamic tinting
 * and render their own texture untouched.
 */
object SmartphoneAppIconRenderer {
    val SQUIRCLE_BODY: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/base/squircle_body.png"
    )
    val SQUIRCLE_SHEEN: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/base/squircle_sheen.png"
    )
    val SQUIRCLE_BORDER: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/base/squircle_border.png"
    )

    val BUILTIN_GLYPH_STEMS: Set<String> = setOf(
        "ae2", "app_store", "cloud", "cobbledollars", "crafting", "fishnav", "gps", "heal",
        "patchouli", "pc", "pokedex", "pokeinfo", "pokenav", "refinedstorage",
        "rotom", "social", "structure_compass", "toms_storage", "trainer",
        "waystone", "wireless"
    )

    data class StyleBPalette(
        val bodyArgb: Int,
        val sheenArgb: Int,
        val borderArgb: Int,
        val glyphArgb: Int
    )

    fun computePalette(colors: SmartphoneThemeColors, hovered: Boolean, alphaScale: Float = 1f): StyleBPalette {
        val navy = colors.headerNavy
        val primary = colors.primary
        val primaryHover = colors.primaryHover
        val textSecondary = colors.textSecondary

        val bodyRgb = mixRgb(navy, primary, if (hovered) 0.28f else 0.14f)
        val bodyAlpha = ((if (hovered) 212 else 188) * alphaScale).roundToInt().coerceIn(0, 255)

        val sheenAlpha = ((if (hovered) 255 else 185) * alphaScale).roundToInt().coerceIn(0, 255)

        val borderRgb = if (hovered) {
            mixRgb(primaryHover, 0xFFFFFF, 0.50f)
        } else {
            mixRgb(navy, primary, 0.55f)
        }
        val borderAlpha = (238 * alphaScale).roundToInt().coerceIn(0, 255)

        val glyphRgb = if (hovered) {
            0xFFFFFF
        } else {
            mixRgb(textSecondary, 0xFFFFFF, 0.35f)
        }
        val glyphAlpha = (255 * alphaScale).roundToInt().coerceIn(0, 255)

        return StyleBPalette(
            bodyArgb = withAlpha(bodyRgb, bodyAlpha),
            sheenArgb = withAlpha(0xFFFFFF, sheenAlpha),
            borderArgb = withAlpha(borderRgb, borderAlpha),
            glyphArgb = withAlpha(glyphRgb, glyphAlpha)
        )
    }

    /**
     * Returns the built-in glyph texture if this icon should be rendered dynamically with Style B,
     * or null if it has a custom theme override or is an external/datapack texture.
     */
    fun resolveBuiltInGlyph(
        theme: SmartphoneTheme,
        actionId: String?,
        defaultTexture: ResourceLocation,
        hovered: Boolean = false
    ): ResourceLocation? {
        if (actionId != null) {
            val hasOverride = if (hovered) {
                theme.hasCustomAppHoverIcon(actionId, defaultTexture)
            } else {
                theme.hasCustomAppIcon(actionId, defaultTexture)
            }
            if (hasOverride) return null
        }

        if (defaultTexture.namespace != CobblemonSmartphone.ID) return null
        val prefix = "textures/gui/buttons/"
        if (!defaultTexture.path.startsWith(prefix) || !defaultTexture.path.endsWith(".png")) return null
        val fileName = defaultTexture.path.removePrefix(prefix)
        if (fileName.contains('/')) return null
        val stem = fileName.removeSuffix(".png").removeSuffix("_hover")
        if (stem !in BUILTIN_GLYPH_STEMS) return null

        return ResourceLocation.fromNamespaceAndPath(
            CobblemonSmartphone.ID,
            "textures/gui/buttons/glyphs/$stem.png"
        )
    }

    fun render(
        guiGraphics: GuiGraphics,
        theme: SmartphoneTheme,
        actionId: String,
        defaultTexture: ResourceLocation,
        defaultHoverTexture: ResourceLocation,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        hovered: Boolean
    ) {
        val matrices = guiGraphics.pose()
        val glyphTexture = resolveBuiltInGlyph(
            theme = theme,
            actionId = actionId,
            defaultTexture = if (hovered) defaultHoverTexture else defaultTexture,
            hovered = hovered
        )

        if (glyphTexture == null) {
            val texture = if (hovered) {
                theme.getAppHoverIcon(actionId, defaultHoverTexture)
            } else {
                theme.getAppIcon(actionId, defaultTexture)
            }
            blitk(
                matrixStack = matrices,
                texture = texture,
                x = x,
                y = y,
                width = width,
                height = height
            )
            return
        }

        val palette = computePalette(theme.colors, hovered)
        blitTinted(guiGraphics, SQUIRCLE_BODY, x, y, width, height, palette.bodyArgb)
        blitTinted(guiGraphics, SQUIRCLE_SHEEN, x, y, width, height, palette.sheenArgb)
        blitTinted(guiGraphics, SQUIRCLE_BORDER, x, y, width, height, palette.borderArgb)
        blitTinted(guiGraphics, glyphTexture, x, y, width, height, palette.glyphArgb)
    }

    private fun blitTinted(
        guiGraphics: GuiGraphics,
        texture: ResourceLocation,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        argb: Int
    ) {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        blitk(
            matrixStack = guiGraphics.pose(),
            texture = texture,
            x = x,
            y = y,
            width = width,
            height = height,
            red = r,
            green = g,
            blue = b,
            alpha = a
        )
    }

    internal fun mixRgb(c1: Int, c2: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF
        val r = (r1 + (r2 - r1) * clamped).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * clamped).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * clamped).roundToInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int =
        ((alpha and 0xFF) shl 24) or (rgb and 0x00FFFFFF)
}
