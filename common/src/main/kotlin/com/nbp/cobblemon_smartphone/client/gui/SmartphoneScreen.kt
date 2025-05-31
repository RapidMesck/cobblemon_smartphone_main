package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class SmartphoneScreen(private val color: SmartphoneColor) : Screen(Component.literal("Smartphone")) {
    private val actions = SmartphoneActionRegistry.getEnabledActions()
    private var screenX = 0
    private var screenY = 0
    private var currentPage = 0

    private val actionsPerPage get() = GRID_COLUMNS * GRID_ROWS
    private val maxPage get() = if (actions.isEmpty()) 0 else (actions.size - 1) / actionsPerPage

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        val backgroundTexture = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/smartphone_${color.modelName}.png"
        )
        guiGraphics.blit(backgroundTexture, screenX, screenY, 0f, 0f, GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT)

        // Render actions as buttons
        pagedActions().forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            val texture = if (isHovered(mouseX, mouseY, x, y)) action.hoverTexture else action.texture
            guiGraphics.blit(
                texture, screenX + x, screenY + y,
                0f, 0f, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT
            )
        }

        // Render page controls if needed
        val prevEnabled = currentPage > 0
        val nextEnabled = currentPage < maxPage

        if (prevEnabled) {
            guiGraphics.drawString(
                font, "<", screenX + PAGE_BUTTON_X, screenY + PAGE_BUTTON_Y, 0xFFFFFF, false
            )
        }
        if (nextEnabled) {
            guiGraphics.drawString(
                font, ">", screenX + PAGE_BUTTON_X + 36, screenY + PAGE_BUTTON_Y, 0xFFFFFF, false
            )
        }
        // Page number
        val pageLabel = "${currentPage + 1}/${maxPage + 1}"
        guiGraphics.drawString(
            font, pageLabel, screenX + PAGE_BUTTON_X + PAGE_BUTTON_WIDTH / 2 + 6, screenY + PAGE_BUTTON_Y, 0xFFFFFF, false
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Handle button click
        pagedActions().forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            if (isHovered(mouseX.toInt(), mouseY.toInt(), x, y)) {
                action.onClick()
                return true
            }
        }
        // Handle page controls
        if (isInPrevButton(mouseX.toInt(), mouseY.toInt()) && currentPage > 0) {
            currentPage--
            return true
        }
        if (isInNextButton(mouseX.toInt(), mouseY.toInt()) && currentPage < maxPage) {
            currentPage++
            return true
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

    private fun pagedActions(): List<com.nbp.cobblemon_smartphone.api.SmartphoneAction> {
        val from = currentPage * actionsPerPage
        return actions.drop(from).take(actionsPerPage)
    }

    private fun isInPrevButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= screenX + PAGE_BUTTON_X && mouseX <= screenX + PAGE_BUTTON_X + PAGE_BUTTON_WIDTH &&
                mouseY >= screenY + PAGE_BUTTON_Y && mouseY <= screenY + PAGE_BUTTON_Y + PAGE_BUTTON_HEIGHT
    }

    private fun isInNextButton(mouseX: Int, mouseY: Int): Boolean {
        val x = PAGE_BUTTON_X + PAGE_BUTTON_WIDTH + 16
        return mouseX >= screenX + x && mouseX <= screenX + x + PAGE_BUTTON_WIDTH &&
                mouseY >= screenY + PAGE_BUTTON_Y && mouseY <= screenY + PAGE_BUTTON_Y + PAGE_BUTTON_HEIGHT
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val GRID_ROWS = 3
        private const val GRID_START_X = 26
        private const val GRID_START_Y = 37
        private const val BUTTON_SPACING = 43
        private const val GUI_WIDTH = 131
        private const val GUI_HEIGHT = 207
        private const val BUTTON_WIDTH = 36
        private const val BUTTON_HEIGHT = 36

        // Page button layout (adjust as needed)
        private const val PAGE_BUTTON_X = 45
        private const val PAGE_BUTTON_Y = 175
        private const val PAGE_BUTTON_WIDTH = 12
        private const val PAGE_BUTTON_HEIGHT = 12
    }
}