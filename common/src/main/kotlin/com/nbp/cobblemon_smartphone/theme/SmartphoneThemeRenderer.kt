package com.nbp.cobblemon_smartphone.theme

import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.client.gui.GuiGraphics

object SmartphoneThemeRenderer {
    const val SCREEN_WIDTH = 131
    const val SCREEN_HEIGHT = 207

    const val LARGE_SCREEN_WIDTH = 211
    const val LARGE_SCREEN_HEIGHT = 207

    fun renderTheme(guiGraphics: GuiGraphics, theme: SmartphoneTheme, screenX: Int, screenY: Int) {
        val matrices = guiGraphics.pose()
        try {
            blitk(
                matrixStack = matrices,
                texture = theme.wallpaper,
                x = screenX,
                y = screenY,
                width = SCREEN_WIDTH,
                height = SCREEN_HEIGHT
            )
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.warn("Failed to render theme wallpaper {}: {}", theme.wallpaper, e.message)
            blitk(
                matrixStack = matrices,
                texture = SmartphoneTheme.DEFAULT_WALLPAPER,
                x = screenX,
                y = screenY,
                width = SCREEN_WIDTH,
                height = SCREEN_HEIGHT
            )
        }
    }

    fun renderLargeTheme(guiGraphics: GuiGraphics, theme: SmartphoneTheme, screenX: Int, screenY: Int) {
        val matrices = guiGraphics.pose()
        val texture = theme.getLargeWallpaperTexture()
        try {
            blitk(
                matrixStack = matrices,
                texture = texture,
                x = screenX,
                y = screenY,
                width = LARGE_SCREEN_WIDTH,
                height = LARGE_SCREEN_HEIGHT
            )
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.warn("Failed to render large theme wallpaper {}: {}", texture, e.message)
            blitk(
                matrixStack = matrices,
                texture = SmartphoneTheme.DEFAULT_LARGE_WALLPAPER,
                x = screenX,
                y = screenY,
                width = LARGE_SCREEN_WIDTH,
                height = LARGE_SCREEN_HEIGHT
            )
        }
    }

    fun renderPreview(guiGraphics: GuiGraphics, theme: SmartphoneTheme, x: Int, y: Int, width: Int, height: Int) {
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(x.toFloat(), y.toFloat(), 0f)
        matrices.scale(width.toFloat() / SCREEN_WIDTH.toFloat(), height.toFloat() / SCREEN_HEIGHT.toFloat(), 1f)
        try {
            blitk(
                matrixStack = matrices,
                texture = theme.wallpaper,
                x = 0,
                y = 0,
                width = SCREEN_WIDTH,
                height = SCREEN_HEIGHT
            )
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.warn("Failed to render theme preview {}: {}", theme.wallpaper, e.message)
            blitk(
                matrixStack = matrices,
                texture = SmartphoneTheme.DEFAULT_WALLPAPER,
                x = 0,
                y = 0,
                width = SCREEN_WIDTH,
                height = SCREEN_HEIGHT
            )
        }
        matrices.popPose()
    }
}
