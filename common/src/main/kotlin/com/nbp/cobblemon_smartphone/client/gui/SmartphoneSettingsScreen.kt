package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.api.QuickActionBindings
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveQuickActionsPacket
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
) : Screen(Component.translatable("cobblemon_smartphone.screen.settings")) {

    private val orderedIds = SmartphoneActionOrder.apply(SmartphoneActionRegistry.getEnabledActions())
        .map { it.id }
        .toMutableList()

    private val slotBindings = QuickActionBindings.currentBindings().toMutableMap()

    private val frameTexture = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone",
        "textures/gui/smartphone_${color.modelName}.png"
    )
    private var screenX = 0
    private var screenY = 0
    private var currentPage = 0
    private var draggingIndex = -1
    private var lastPageFlipTime = 0L

    // Quick action slot selector popup (opened by right-clicking an app)
    private var selectorActionId: String? = null
    private var selectorX = 0
    private var selectorY = 0

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
        QuickActionBindings.setBindings(slotBindings)
        SaveQuickActionsPacket(slotBindings).sendToServer()
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

        renderHint(guiGraphics)

        pagedIds().forEachIndexed { index, actionId ->
            renderGridItem(guiGraphics, mouseX, mouseY, index, actionId)
        }

        renderPageDots(guiGraphics)
        renderFooterButtons(guiGraphics, mouseX, mouseY)
        renderSelector(guiGraphics, mouseX, mouseY)
        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (selectorActionId != null) {
            handleSelectorClick(mx, my)
            return true
        }

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

        pagedIds().forEachIndexed { index, actionId ->
            val (gx, gy) = gridPosition(index)
            if (isInDragHandle(mx, my, index) || isInCell(mx, my, gx, gy)) {
                playClickSound()
                if (button == RIGHT_BUTTON) {
                    openSelector(actionId, index)
                } else {
                    draggingIndex = currentPage * ACTIONS_PER_PAGE + index
                }
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
        selectorActionId = null
        val nextPage = (currentPage + offset).coerceIn(0, maxPage)
        if (nextPage == currentPage) {
            return false
        }

        currentPage = nextPage
        return true
    }

    private fun actionById(id: String): SmartphoneAction? =
        SmartphoneActionRegistry.getEnabledActions().firstOrNull { it.id == id }

    private fun slotForAction(actionId: String): Int? =
        slotBindings.entries.firstOrNull { it.value == actionId }?.key

    private fun assignSlot(actionId: String, slot: Int?) {
        // Each app maps to at most one slot, and each slot to at most one app.
        slotBindings.values.removeIf { it == actionId }
        if (slot != null) {
            slotBindings[slot] = actionId
        }
    }

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

        renderSlotBadge(guiGraphics, action.id, gx, gy)
        renderDragHandle(guiGraphics, mouseX, mouseY, index, isDragging)
    }

    private fun renderSlotBadge(guiGraphics: GuiGraphics, actionId: String, gx: Int, gy: Int) {
        val slot = slotForAction(actionId) ?: return
        val label = (slot + 1).toString()
        val badgeWidth = (font.width(label) * SLOT_BADGE_SCALE).toInt() + SLOT_BADGE_PADDING * 2
        val badgeHeight = (font.lineHeight * SLOT_BADGE_SCALE).toInt() + SLOT_BADGE_PADDING
        val badgeX = screenX + gx
        val badgeY = screenY + gy

        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, SLOT_BADGE_BG)
        drawScaled(
            guiGraphics,
            label,
            badgeX + SLOT_BADGE_PADDING,
            badgeY + SLOT_BADGE_PADDING / 2,
            SLOT_BADGE_TEXT,
            SLOT_BADGE_SCALE
        )
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

    private fun renderHint(guiGraphics: GuiGraphics) {
        val hint = Component.translatable("cobblemon_smartphone.quick_actions.hint").string
        val width = (font.width(hint) * HINT_SCALE).toInt()
        drawScaled(guiGraphics, hint, screenX + (GUI_WIDTH - width) / 2, screenY + HINT_Y, HINT_COLOR, HINT_SCALE)
    }

    private fun openSelector(actionId: String, index: Int) {
        selectorActionId = actionId
        val (gx, gy) = gridPosition(index)
        val minX = SCREEN_CONTENT_LEFT + SELECTOR_MARGIN
        val maxX = SCREEN_CONTENT_RIGHT - SELECTOR_WIDTH - SELECTOR_MARGIN
        val minY = SCREEN_CONTENT_TOP + SELECTOR_MARGIN
        val maxY = SCREEN_CONTENT_BOTTOM - selectorHeight() - SELECTOR_MARGIN
        selectorX = (gx + BUTTON_WIDTH / 2 - SELECTOR_WIDTH / 2).coerceIn(minX, maxX)
        selectorY = gy.coerceIn(minY, maxY)
    }

    private fun handleSelectorClick(mouseX: Int, mouseY: Int) {
        val actionId = selectorActionId ?: return
        val px = screenX + selectorX
        val py = screenY + selectorY
        if (mouseX in px..(px + SELECTOR_WIDTH) && mouseY in py..(py + selectorHeight())) {
            val row = (mouseY - py) / SELECTOR_ROW_HEIGHT
            if (row in 0 until selectorRowCount()) {
                playClickSound()
                val slot = row - 1
                assignSlot(actionId, if (slot < 0) null else slot)
            }
        }
        selectorActionId = null
    }

    private fun renderSelector(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val actionId = selectorActionId ?: return
        val px = screenX + selectorX
        val py = screenY + selectorY
        val w = SELECTOR_WIDTH
        val h = selectorHeight()

        guiGraphics.fill(px - 1, py - 1, px + w + 1, py + h + 1, SELECTOR_BORDER)
        guiGraphics.fill(px, py, px + w, py + h, SELECTOR_BG)

        val currentSlot = slotForAction(actionId)
        for (row in 0 until selectorRowCount()) {
            val slot = row - 1
            val ry = py + row * SELECTOR_ROW_HEIGHT
            val hovered = mouseX >= px && mouseX <= px + w && mouseY >= ry && mouseY <= ry + SELECTOR_ROW_HEIGHT
            val isCurrent = if (slot < 0) currentSlot == null else currentSlot == slot

            when {
                isCurrent -> guiGraphics.fill(px, ry, px + w, ry + SELECTOR_ROW_HEIGHT, SELECTOR_ROW_CURRENT)
                hovered -> guiGraphics.fill(px, ry, px + w, ry + SELECTOR_ROW_HEIGHT, SELECTOR_ROW_HOVER)
            }

            val label = selectorRowLabel(slot)
            val textColor = if (isCurrent) SELECTOR_TEXT_CURRENT else SELECTOR_TEXT_COLOR
            drawScaled(guiGraphics, label, px + SELECTOR_TEXT_X, ry + SELECTOR_TEXT_Y, textColor, SELECTOR_TEXT_SCALE)
        }
    }

    private fun selectorRowLabel(slot: Int): String {
        if (slot < 0) return Component.translatable("cobblemon_smartphone.quick_actions.none").string
        val keyMapping = SmartphoneKeybinds.QUICK_ACTION_SLOTS[slot]
        val number = (slot + 1).toString()
        return if (keyMapping.isUnbound) number else "$number  ${keyMapping.translatedKeyMessage.string}"
    }

    private fun selectorRowCount(): Int = SmartphoneKeybinds.QUICK_ACTION_SLOT_COUNT + 1

    private fun selectorHeight(): Int = selectorRowCount() * SELECTOR_ROW_HEIGHT

    private fun drawScaled(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, scale: Float) {
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        matrices.scale(scale, scale, 1f)
        guiGraphics.drawString(font, text, 0, 0, color, false)
        matrices.popPose()
    }

    private fun renderFooterButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        renderFooterButton(guiGraphics, PREV_BUTTON_TEXTURE, FOOTER_PREV_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, HOME_BUTTON_TEXTURE, FOOTER_HOME_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, NEXT_BUTTON_TEXTURE, FOOTER_NEXT_X, mouseX, mouseY)
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (selectorActionId != null || draggingIndex != -1) return

        pagedIds().forEachIndexed { index, actionId ->
            val action = actionById(actionId) ?: return@forEachIndexed
            val (gx, gy) = gridPosition(index)
            if (isInDragHandle(mouseX, mouseY, index)) {
                guiGraphics.renderTooltip(
                    font,
                    Component.translatable("cobblemon_smartphone.tooltip.reorder_app"),
                    mouseX,
                    mouseY
                )
                return
            }
            if (isInCell(mouseX, mouseY, gx, gy)) {
                guiGraphics.renderTooltip(font, action.displayName, mouseX, mouseY)
                return
            }
        }

        val key = when {
            isInFooterButton(mouseX, mouseY, FOOTER_PREV_X) -> "previous_page"
            isInFooterButton(mouseX, mouseY, FOOTER_HOME_X) -> "back_to_phone"
            isInFooterButton(mouseX, mouseY, FOOTER_NEXT_X) -> "next_page"
            else -> null
        } ?: return
        guiGraphics.renderTooltip(
            font,
            Component.translatable("cobblemon_smartphone.tooltip.$key"),
            mouseX,
            mouseY
        )
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
        private const val RIGHT_BUTTON = 1

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

        private const val HINT_Y = 30
        private const val HINT_SCALE = 0.5f
        private const val HINT_COLOR = 0xE6FFFF

        private const val SLOT_BADGE_SCALE = 0.5f
        private const val SLOT_BADGE_PADDING = 2
        private const val SLOT_BADGE_BG = 0xC03A96B6.toInt()
        private const val SLOT_BADGE_TEXT = 0xFFFFFFFF.toInt()

        // Bounds the popup is clamped inside, matching where the app grid renders (which is
        // known to sit within the phone's lit screen). Derived from the grid/footer layout:
        //   LEFT   = GRID_START_X (26)
        //   RIGHT  = GRID_START_X + BUTTON_SPACING + BUTTON_WIDTH (26 + 43 + 36 = 105)
        //   TOP    = GRID_START_Y (37)
        //   BOTTOM = FOOTER_BUTTON_Y + FOOTER_BUTTON_SIZE (187 + 7 = 194)
        // Nudge these if the popup still touches the frame on any side.
        private const val SCREEN_CONTENT_LEFT = 26
        private const val SCREEN_CONTENT_RIGHT = 105
        private const val SCREEN_CONTENT_TOP = 37
        private const val SCREEN_CONTENT_BOTTOM = 194

        // Extra gap (px) kept between the popup and the content edge. Includes room for the 1px border.
        private const val SELECTOR_MARGIN = 1

        private const val SELECTOR_WIDTH = 58
        private const val SELECTOR_ROW_HEIGHT = 11
        private const val SELECTOR_TEXT_SCALE = 0.6f
        private const val SELECTOR_TEXT_X = 4
        private const val SELECTOR_TEXT_Y = 3
        private const val SELECTOR_BG = 0xF0EFFDFF.toInt()
        private const val SELECTOR_BORDER = 0xFF3A96B6.toInt()
        private const val SELECTOR_ROW_CURRENT = 0xFF3A96B6.toInt()
        private const val SELECTOR_ROW_HOVER = 0x553A96B6.toInt()
        private const val SELECTOR_TEXT_COLOR = 0xFF303030.toInt()
        private const val SELECTOR_TEXT_CURRENT = 0xFFFFFFFF.toInt()

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
