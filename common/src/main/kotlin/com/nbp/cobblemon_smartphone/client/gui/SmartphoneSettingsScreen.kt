package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class SmartphoneSettingsScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null
) : Screen(Component.literal("Smartphone Settings")) {

    private val orderedIds = SmartphoneActionOrder.apply(SmartphoneActionRegistry.getEnabledActions())
        .map { it.id }
        .toMutableList()

    private val frameTexture = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone",
        "textures/gui/smartphone_${color.modelName}.png"
    )
    private var screenX = 0
    private var screenY = 0
    private var currentPage = 0
    private var draggingIndex = -1
    private var lastPageFlipTime = 0L

    private val maxPage get() = if (orderedIds.isEmpty()) 0 else (orderedIds.size - 1) / ACTIONS_PER_PAGE

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
    }

    override fun removed() {
        SmartphoneActionOrder.setOrder(orderedIds)
        SaveActionOrderPacket(orderedIds).sendToServer()
        SmartphoneHelper.contextSmartphone = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        blitk(
            matrixStack = matrices,
            texture = frameTexture,
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )
        blitk(
            matrixStack = matrices,
            texture = HOME_SCREEN_TEXTURE,
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        pagedIds().forEachIndexed { index, actionId ->
            renderGridItem(guiGraphics, mouseX, mouseY, index, actionId)
        }

        renderPageDots(guiGraphics)
        renderFooterButtons(guiGraphics, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInFooterButton(mx, my, FOOTER_PREV_X)) {
            playClickSound()
            changePage(-1)
            return true
        }
        if (isInFooterButton(mx, my, FOOTER_HOME_X)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }
        if (isInFooterButton(mx, my, FOOTER_NEXT_X)) {
            playClickSound()
            changePage(1)
            return true
        }

        pagedIds().forEachIndexed { index, _ ->
            val (gx, gy) = gridPosition(index)
            if (isInDragHandle(mx, my, index) || isInCell(mx, my, gx, gy)) {
                val globalIndex = currentPage * ACTIONS_PER_PAGE + index
                playClickSound()
                draggingIndex = globalIndex
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingIndex == -1) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)

        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (my < screenY + GRID_START_Y && currentPage > 0) {
            val now = System.currentTimeMillis()
            if (now - lastPageFlipTime > 500) {
                val boundaryIndex = currentPage * ACTIONS_PER_PAGE - 1
                if (boundaryIndex >= 0 && draggingIndex != boundaryIndex) {
                    moveAction(draggingIndex, boundaryIndex)
                    draggingIndex = boundaryIndex
                }
                if (changePage(-1)) {
                    lastPageFlipTime = now
                    playClickSound()
                }
            }
            return true
        }

        if (my > screenY + GRID_START_Y + (GRID_ROWS - 1) * BUTTON_SPACING + BUTTON_HEIGHT && currentPage < maxPage) {
            val now = System.currentTimeMillis()
            if (now - lastPageFlipTime > 500) {
                val boundaryIndex = (currentPage + 1) * ACTIONS_PER_PAGE
                if (boundaryIndex < orderedIds.size && draggingIndex != boundaryIndex) {
                    moveAction(draggingIndex, boundaryIndex)
                    draggingIndex = boundaryIndex
                }
                if (changePage(1)) {
                    lastPageFlipTime = now
                    playClickSound()
                }
            }
            return true
        }

        val targetGlobal = gridCellAt(mx, my)
        if (targetGlobal >= 0 && targetGlobal != draggingIndex && targetGlobal in orderedIds.indices) {
            moveAction(draggingIndex, targetGlobal)
            draggingIndex = targetGlobal
        }

        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingIndex != -1) {
            draggingIndex = -1
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (draggingIndex != -1) return true
        if (maxPage == 0 || verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        return changePage(if (verticalAmount < 0.0) 1 else -1)
    }

    private fun moveAction(fromIndex: Int, toIndex: Int) {
        val id = orderedIds.removeAt(fromIndex)
        orderedIds.add(toIndex, id)
    }

    private fun changePage(offset: Int): Boolean {
        val nextPage = (currentPage + offset).coerceIn(0, maxPage)
        if (nextPage == currentPage) {
            return false
        }

        currentPage = nextPage
        return true
    }

    private fun actionById(id: String): SmartphoneAction? =
        SmartphoneActionRegistry.getEnabledActions().firstOrNull { it.id == id }

    private fun gridPosition(index: Int): Pair<Int, Int> {
        val col = index % GRID_COLUMNS
        val row = index / GRID_COLUMNS
        return GRID_START_X + col * BUTTON_SPACING to GRID_START_Y + row * BUTTON_SPACING
    }

    private fun dragHandlePosition(index: Int): Pair<Int, Int> {
        val (gx, gy) = gridPosition(index)
        return gx + BUTTON_WIDTH - DRAG_WIDTH - 4 to gy + 4
    }

    private fun gridCellAt(mouseX: Int, mouseY: Int): Int {
        val relX = mouseX - screenX - GRID_START_X
        val relY = mouseY - screenY - GRID_START_Y
        val col = relX / BUTTON_SPACING
        val row = relY / BUTTON_SPACING

        if (col !in 0 until GRID_COLUMNS || row !in 0 until GRID_ROWS) return -1

        val (gx, gy) = gridPosition(row * GRID_COLUMNS + col)
        val inCell = mouseX >= screenX + gx && mouseX <= screenX + gx + BUTTON_WIDTH &&
                mouseY >= screenY + gy && mouseY <= screenY + gy + BUTTON_HEIGHT

        if (!inCell) return -1

        return currentPage * ACTIONS_PER_PAGE + row * GRID_COLUMNS + col
    }

    private fun renderGridItem(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, index: Int, actionId: String) {
        val action = actionById(actionId) ?: return
        val matrices = guiGraphics.pose()
        val (gx, gy) = gridPosition(index)
        val globalIndex = currentPage * ACTIONS_PER_PAGE + index
        val isDragging = globalIndex == draggingIndex

        val texture = if (isDragging) action.hoverTexture else action.texture

        blitk(
            matrixStack = matrices,
            texture = texture,
            x = screenX + gx,
            y = screenY + gy,
            width = BUTTON_WIDTH,
            height = BUTTON_HEIGHT
        )

        renderDragHandle(guiGraphics, mouseX, mouseY, index, isDragging)
    }

    private fun renderDragHandle(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        isDragging: Boolean
    ) {
        val (dx, dy) = dragHandlePosition(index)
        val hovered = isInDragHandle(mouseX, mouseY, index) || isDragging
        val textureY = if (hovered) DRAG_HEIGHT else 0
        guiGraphics.blit(
            DRAG_HANDLE_TEXTURE,
            screenX + dx,
            screenY + dy,
            0f,
            textureY.toFloat(),
            DRAG_WIDTH,
            DRAG_HEIGHT,
            DRAG_WIDTH,
            DRAG_HEIGHT * 2
        )
    }

    private fun isInDragHandle(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val (dx, dy) = dragHandlePosition(index)
        return mouseX >= screenX + dx && mouseX <= screenX + dx + DRAG_WIDTH &&
                mouseY >= screenY + dy && mouseY <= screenY + dy + DRAG_HEIGHT
    }

    private fun isInCell(mouseX: Int, mouseY: Int, gx: Int, gy: Int): Boolean {
        return mouseX >= screenX + gx && mouseX <= screenX + gx + BUTTON_WIDTH &&
                mouseY >= screenY + gy && mouseY <= screenY + gy + BUTTON_HEIGHT
    }

    private fun pagedIds(): List<String> {
        val from = currentPage * ACTIONS_PER_PAGE
        return orderedIds.drop(from).take(ACTIONS_PER_PAGE)
    }

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    private fun renderFooterButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        renderFooterButton(guiGraphics, PREV_BUTTON_TEXTURE, FOOTER_PREV_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, HOME_BUTTON_TEXTURE, FOOTER_HOME_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, NEXT_BUTTON_TEXTURE, FOOTER_NEXT_X, mouseX, mouseY)
    }

    private fun renderFooterButton(
        guiGraphics: GuiGraphics,
        texture: ResourceLocation,
        x: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = isInFooterButton(mouseX, mouseY, x)
        val textureY = if (hovered) FOOTER_BUTTON_SIZE else 0
        guiGraphics.blit(
            texture,
            screenX + x,
            screenY + FOOTER_BUTTON_Y,
            0f,
            textureY.toFloat(),
            FOOTER_BUTTON_SIZE,
            FOOTER_BUTTON_SIZE,
            FOOTER_BUTTON_SIZE,
            FOOTER_BUTTON_TEXTURE_HEIGHT
        )
    }

    private fun renderPageDots(guiGraphics: GuiGraphics) {
        val totalPages = maxPage + 1
        if (totalPages <= 1) {
            return
        }

        val dotCount = totalPages.coerceAtMost(MAX_VISIBLE_DOTS)
        val activeDot = when {
            totalPages <= MAX_VISIBLE_DOTS -> currentPage
            currentPage == 0 -> 0
            currentPage == maxPage -> dotCount - 1
            else -> 1
        }
        val startX = DOT_CENTER_X - ((dotCount * DOT_SIZE + (dotCount - 1) * DOT_SPACING) / 2)

        repeat(dotCount) { index ->
            val active = index == activeDot
            val texture = if (active) PAGE_DOT_ON_TEXTURE else PAGE_DOT_OFF_TEXTURE
            val yOffset = if (active) 0 else DOT_INACTIVE_Y_OFFSET
            guiGraphics.blit(
                texture,
                screenX + startX + index * (DOT_SIZE + DOT_SPACING),
                screenY + DOT_Y + yOffset,
                0f,
                0f,
                DOT_SIZE,
                DOT_SIZE,
                DOT_SIZE,
                DOT_SIZE
            )
        }
    }

    private fun isInFooterButton(mouseX: Int, mouseY: Int, x: Int): Boolean {
        return mouseX >= screenX + x && mouseX <= screenX + x + FOOTER_BUTTON_SIZE &&
                mouseY >= screenY + FOOTER_BUTTON_Y && mouseY <= screenY + FOOTER_BUTTON_Y + FOOTER_BUTTON_SIZE
    }

    companion object {
        private const val GUI_WIDTH = 131
        private const val GUI_HEIGHT = 207

        private const val GRID_COLUMNS = 2
        private const val GRID_ROWS = 3
        private const val ACTIONS_PER_PAGE = GRID_COLUMNS * GRID_ROWS

        private const val GRID_START_X = 26
        private const val GRID_START_Y = 37
        private const val BUTTON_SPACING = 43
        private const val BUTTON_WIDTH = 36
        private const val BUTTON_HEIGHT = 36

        private const val DRAG_WIDTH = 8
        private const val DRAG_HEIGHT = 8

        private const val FOOTER_PREV_X = 36
        private const val FOOTER_HOME_X = 62
        private const val FOOTER_NEXT_X = 88
        private const val FOOTER_BUTTON_Y = 187
        private const val FOOTER_BUTTON_SIZE = 7
        private const val FOOTER_BUTTON_TEXTURE_HEIGHT = 14

        private const val MAX_VISIBLE_DOTS = 3
        private const val DOT_CENTER_X = GUI_WIDTH / 2
        private const val DOT_Y = 169
        private const val DOT_SIZE = 9
        private const val DOT_SPACING = 2
        private const val DOT_INACTIVE_Y_OFFSET = 1

        private val HOME_SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/home_screen.png"
        )
        private val PREV_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/prev_button.png"
        )
        private val HOME_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/home_button.png"
        )
        private val NEXT_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/next_button.png"
        )
        private val PAGE_DOT_ON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/page_dot_on.png"
        )
        private val PAGE_DOT_OFF_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/page_dot_off.png"
        )
        private val DRAG_HANDLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/drag_handle.png"
        )
    }
}
