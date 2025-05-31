package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class SmartphoneScreen(private val color: SmartphoneColor) : Screen(Component.literal("Smartphone")) {
    private val actions = SmartphoneActionRegistry.getEnabledActions()
    private var screenX = 0
    private var screenY = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        val backgroundTexture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/smartphone_${color.modelName}.png"
        )
        guiGraphics.blit(backgroundTexture, screenX, screenY, 0f, 0f, GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT)

        // Render actions as buttons
        actions.forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            val texture = if (isHovered(mouseX, mouseY, x, y)) action.hoverTexture else action.texture
            guiGraphics.blit(
                texture, screenX + x, screenY + y,
                0f, 0f, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        actions.forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            if (isHovered(mouseX.toInt(), mouseY.toInt(), x, y)) {
                action.onClick()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun isHovered(mouseX: Int, mouseY: Int, x: Int, y: Int): Boolean {
        return mouseX >= screenX + x && mouseX <= screenX + x + BUTTON_WIDTH &&
                mouseY >= screenY + y && mouseY <= screenY + y + BUTTON_HEIGHT
    }

    private fun getButtonPosition(index: Int): Pair<Int, Int> {
        val gridX = (index % GRID_COLUMNS) * BUTTON_SPACING + GRID_START_X
        val gridY = (index / GRID_COLUMNS) * BUTTON_SPACING + GRID_START_Y
        return Pair(gridX, gridY)
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val GRID_START_X = 26
        private const val GRID_START_Y = 37
        private const val BUTTON_SPACING = 43
        private const val GUI_WIDTH = 131
        private const val GUI_HEIGHT = 207
        private const val BUTTON_WIDTH = 36
        private const val BUTTON_HEIGHT = 36
    }
}