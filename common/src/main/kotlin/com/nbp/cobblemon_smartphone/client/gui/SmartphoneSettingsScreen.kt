package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.api.QuickActionBindings
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.api.SmartphoneHiddenActions
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveHiddenActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveQuickActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveThemePacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRegistry
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRenderer
import com.nbp.cobblemon_smartphone.theme.setTheme
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class SmartphoneSettingsScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null,
    initialTab: SettingsTab = SettingsTab.APPS
) : Screen(Component.translatable("cobblemon_smartphone.screen.settings")) {

    private val orderedIds = SmartphoneActionOrder.apply(SmartphoneActionRegistry.getEnabledActions())
        .map { it.id }
        .toMutableList()

    private val slotBindings = QuickActionBindings.currentBindings().toMutableMap()

    private val hiddenIds = SmartphoneHiddenActions.currentHidden().toMutableSet()

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

    enum class SettingsTab {
        APPS,
        THEMES
    }

    private var currentTab = initialTab
    private var currentThemePage = 0
    private var selectedThemeId: String = SmartphoneTheme.DEFAULT_ID

    private val themes: List<SmartphoneTheme>
        get() = SmartphoneThemeRegistry.getThemes()

    private val maxThemePage: Int
        get() = if (themes.isEmpty()) 0 else (themes.size - 1) / THEMES_PER_PAGE

    private fun pagedThemes(): List<SmartphoneTheme> {
        val from = currentThemePage * THEMES_PER_PAGE
        return themes.drop(from).take(THEMES_PER_PAGE)
    }

    private val maxPage get() = if (orderedIds.isEmpty()) 0 else (orderedIds.size - 1) / ACTIONS_PER_PAGE

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        minecraft?.resourceManager?.let { SmartphoneThemeRegistry.ensureLoaded(it) }
        SmartphoneHelper.contextSmartphone = smartphoneStack
        selectedThemeId = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack).id
    }

    override fun removed() {
        SmartphoneActionOrder.setOrder(orderedIds)
        SaveActionOrderPacket(orderedIds).sendToServer()
        QuickActionBindings.setBindings(slotBindings)
        SaveQuickActionsPacket(slotBindings).sendToServer()
        SmartphoneHiddenActions.setHidden(hiddenIds.toList())
        SaveHiddenActionsPacket(hiddenIds.toList()).sendToServer()
        SaveThemePacket(selectedThemeId).sendToServer()
        SmartphoneHelper.contextSmartphone = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        // Active theme resolution
        val currentTheme = SmartphoneThemeRegistry.getTheme(selectedThemeId)
        SmartphoneThemeRenderer.renderTheme(guiGraphics, currentTheme, screenX, screenY)

        // Smartphone frame background
        blitk(
            matrixStack = matrices,
            texture = currentTheme.getFrameTexture(color.getSmallScreenTexture()),
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        renderBackButton(guiGraphics, mouseX, mouseY, currentTheme)
        renderTabs(guiGraphics, mouseX, mouseY, currentTheme)
        SmartphoneBatteryRenderer.render(guiGraphics, screenX, screenY, isLargeScreen = false, smartphoneStack)

        if (currentTab == SettingsTab.APPS) {
            renderHint(guiGraphics, currentTheme)
            pagedIds().forEachIndexed { index, actionId ->
                renderGridItem(guiGraphics, mouseX, mouseY, index, actionId, currentTheme)
            }
            renderPageDots(guiGraphics, currentTheme)
            renderSelector(guiGraphics, mouseX, mouseY, currentTheme)
            renderHoveredTooltip(guiGraphics, mouseX, mouseY)
        } else {
            renderThemeHint(guiGraphics, currentTheme)
            pagedThemes().forEachIndexed { index, theme ->
                renderThemeCard(guiGraphics, mouseX, mouseY, index, theme, currentTheme)
            }
            renderThemePageDots(guiGraphics, currentTheme)
            renderThemeHoveredTooltip(guiGraphics, mouseX, mouseY)
        }

        renderFooterButtons(guiGraphics, mouseX, mouseY, currentTheme)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (selectorActionId != null) {
            handleSelectorClick(mx, my)
            return true
        }

        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }

        // Tab switching
        if (isInAppsTab(mx, my) && currentTab != SettingsTab.APPS) {
            playClickSound()
            currentTab = SettingsTab.APPS
            return true
        }
        if (isInThemesTab(mx, my) && currentTab != SettingsTab.THEMES) {
            playClickSound()
            currentTab = SettingsTab.THEMES
            return true
        }

        if (isInFooterButton(mx, my, FOOTER_PREV_X)) {
            playClickSound()
            if (currentTab == SettingsTab.APPS) changePage(-1) else changeThemePage(-1)
            return true
        }
        if (isInFooterButton(mx, my, FOOTER_HOME_X)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }
        if (isInFooterButton(mx, my, FOOTER_NEXT_X)) {
            playClickSound()
            if (currentTab == SettingsTab.APPS) changePage(1) else changeThemePage(1)
            return true
        }

        if (currentTab == SettingsTab.THEMES) {
            pagedThemes().forEachIndexed { index, theme ->
                if (isInThemeCard(mx, my, index)) {
                    playClickSound()
                    selectedThemeId = theme.id
                    smartphoneStack?.setTheme(theme.id)
                    Minecraft.getInstance().player?.let { p ->
                        SmartphoneHelper.getSmartphone(p)?.setTheme(theme.id)
                    }
                    SmartphoneThemeHelper.clientPreferredTheme = theme.id
                    SaveThemePacket(theme.id).sendToServer()
                    return true
                }
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        pagedIds().forEachIndexed { index, actionId ->
            if (isInViewSelect(mx, my, index)) {
                playClickSound()
                toggleHidden(actionId)
                return true
            }
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
        if (currentTab != SettingsTab.APPS || draggingIndex == -1) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)

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

        if (my > screenY + GRID_START_Y + (GRID_ROWS - 1) * BUTTON_SPACING_Y + BUTTON_HEIGHT && currentPage < maxPage) {
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
        val amount = if (verticalAmount < 0.0) 1 else -1

        return if (currentTab == SettingsTab.APPS) {
            if (maxPage == 0 || verticalAmount == 0.0) {
                super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            } else {
                changePage(amount)
            }
        } else {
            if (maxThemePage == 0 || verticalAmount == 0.0) {
                super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            } else {
                changeThemePage(amount)
            }
        }
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

    private fun changeThemePage(offset: Int): Boolean {
        val nextPage = (currentThemePage + offset).coerceIn(0, maxThemePage)
        if (nextPage == currentThemePage) {
            return false
        }

        currentThemePage = nextPage
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
        return GRID_START_X + col * BUTTON_SPACING_X to GRID_START_Y + row * BUTTON_SPACING_Y
    }

    private fun dragHandlePosition(index: Int): Pair<Int, Int> {
        val (gx, gy) = gridPosition(index)
        return gx + BUTTON_WIDTH - DRAG_WIDTH - 4 to gy + 4
    }

    private fun gridCellAt(mouseX: Int, mouseY: Int): Int {
        val relX = mouseX - screenX - GRID_START_X
        val relY = mouseY - screenY - GRID_START_Y
        val col = relX / BUTTON_SPACING_X
        val row = relY / BUTTON_SPACING_Y

        if (col !in 0 until GRID_COLUMNS || row !in 0 until GRID_ROWS) return -1

        val (gx, gy) = gridPosition(row * GRID_COLUMNS + col)
        val inCell = mouseX >= screenX + gx && mouseX <= screenX + gx + BUTTON_WIDTH &&
                mouseY >= screenY + gy && mouseY <= screenY + gy + BUTTON_HEIGHT

        if (!inCell) return -1

        return currentPage * ACTIONS_PER_PAGE + row * GRID_COLUMNS + col
    }

    private fun renderGridItem(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        actionId: String,
        theme: SmartphoneTheme
    ) {
        val action = actionById(actionId) ?: return
        val matrices = guiGraphics.pose()
        val (gx, gy) = gridPosition(index)
        val globalIndex = currentPage * ACTIONS_PER_PAGE + index
        val isDragging = globalIndex == draggingIndex

        val texture = if (isDragging) theme.getAppHoverIcon(action.id, action.hoverTexture) else theme.getAppIcon(action.id, action.texture)

        blitk(
            matrixStack = matrices,
            texture = texture,
            x = screenX + gx,
            y = screenY + gy,
            width = BUTTON_WIDTH,
            height = BUTTON_HEIGHT
        )

        renderSlotBadge(guiGraphics, action.id, gx, gy, theme)
        renderDragHandle(guiGraphics, mouseX, mouseY, index, isDragging, theme)
        renderViewSelect(guiGraphics, action.id, index, theme)
    }

    private fun renderSlotBadge(guiGraphics: GuiGraphics, actionId: String, gx: Int, gy: Int, theme: SmartphoneTheme) {
        val slot = slotForAction(actionId) ?: return
        val label = (slot + 1).toString()
        val badgeWidth = (font.width(label) * SLOT_BADGE_SCALE).toInt() + SLOT_BADGE_PADDING * 2
        val badgeHeight = (font.lineHeight * SLOT_BADGE_SCALE).toInt() + SLOT_BADGE_PADDING
        val badgeX = screenX + gx
        val badgeY = screenY + gy

        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, theme.colors.primary)
        drawScaled(
            guiGraphics,
            label,
            badgeX + SLOT_BADGE_PADDING,
            badgeY + SLOT_BADGE_PADDING / 2,
            theme.colors.textPrimary,
            SLOT_BADGE_SCALE
        )
    }

    private fun renderDragHandle(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        isDragging: Boolean,
        theme: SmartphoneTheme
    ) {
        val (dx, dy) = dragHandlePosition(index)
        val hovered = isInDragHandle(mouseX, mouseY, index) || isDragging
        val textureY = if (hovered) DRAG_HEIGHT else 0
        guiGraphics.blit(
            theme.getDragHandleTexture(),
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

    private fun viewSelectPosition(index: Int): Pair<Int, Int> {
        val (gx, gy) = gridPosition(index)
        return gx + BUTTON_WIDTH - VIEW_WIDTH - 4 to gy + BUTTON_HEIGHT - VIEW_HEIGHT - 4
    }

    private fun renderViewSelect(guiGraphics: GuiGraphics, actionId: String, index: Int, theme: SmartphoneTheme) {
        val (vx, vy) = viewSelectPosition(index)
        val textureY = if (hiddenIds.contains(actionId)) VIEW_HEIGHT else 0
        guiGraphics.blit(
            theme.getViewSelectTexture(),
            screenX + vx,
            screenY + vy,
            0f,
            textureY.toFloat(),
            VIEW_WIDTH,
            VIEW_HEIGHT,
            VIEW_WIDTH,
            VIEW_HEIGHT * 2
        )
    }

    private fun isInViewSelect(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val (vx, vy) = viewSelectPosition(index)
        return mouseX >= screenX + vx && mouseX <= screenX + vx + VIEW_WIDTH &&
                mouseY >= screenY + vy && mouseY <= screenY + vy + VIEW_HEIGHT
    }

    private fun toggleHidden(actionId: String) {
        if (!hiddenIds.add(actionId)) {
            hiddenIds.remove(actionId)
        }
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

    private fun renderHint(guiGraphics: GuiGraphics, theme: SmartphoneTheme) {
        val hint = Component.translatable("cobblemon_smartphone.quick_actions.hint").string
        val width = (font.width(hint) * HINT_SCALE).toInt()
        drawScaled(guiGraphics, hint, screenX + (GUI_WIDTH - width) / 2, screenY + HINT_Y, theme.colors.textSecondary, HINT_SCALE)
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

    private fun renderSelector(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, theme: SmartphoneTheme) {
        val actionId = selectorActionId ?: return
        val px = screenX + selectorX
        val py = screenY + selectorY
        val w = SELECTOR_WIDTH
        val h = selectorHeight()

        guiGraphics.fill(px - 1, py - 1, px + w + 1, py + h + 1, theme.colors.primaryBorder)
        guiGraphics.fill(px, py, px + w, py + h, theme.colors.surface)

        val currentSlot = slotForAction(actionId)
        for (row in 0 until selectorRowCount()) {
            val slot = row - 1
            val ry = py + row * SELECTOR_ROW_HEIGHT
            val hovered = mouseX >= px && mouseX <= px + w && mouseY >= ry && mouseY <= ry + SELECTOR_ROW_HEIGHT
            val isCurrent = if (slot < 0) currentSlot == null else currentSlot == slot

            when {
                isCurrent -> guiGraphics.fill(px, ry, px + w, ry + SELECTOR_ROW_HEIGHT, theme.colors.primary)
                hovered -> guiGraphics.fill(px, ry, px + w, ry + SELECTOR_ROW_HEIGHT, theme.colors.surfaceHover)
            }

            val label = selectorRowLabel(slot)
            val textColor = if (isCurrent) theme.colors.textPrimary else theme.colors.textDark
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

    private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= screenX + BACK_X && mouseX <= screenX + BACK_X + 30 &&
                mouseY >= screenY + BACK_Y - 2 && mouseY <= screenY + BACK_Y + 10
    }

    private fun renderBackButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, theme: SmartphoneTheme) {
        val backHover = isInBackButton(mouseX, mouseY)
        val backColor = if (backHover) theme.colors.gold else theme.colors.textPrimary
        drawScaled(
            guiGraphics,
            Component.translatable("cobblemon_smartphone.pokeinfo.back").string,
            screenX + BACK_X,
            screenY + BACK_Y,
            backColor,
            BACK_SCALE
        )
    }

    private fun isInAppsTab(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in (screenX + TAB_APPS_X)..(screenX + TAB_APPS_X + TAB_APPS_WIDTH) &&
                mouseY in (screenY + TAB_Y)..(screenY + TAB_Y + TAB_HEIGHT)
    }

    private fun isInThemesTab(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in (screenX + TAB_THEMES_X)..(screenX + TAB_THEMES_X + TAB_THEMES_WIDTH) &&
                mouseY in (screenY + TAB_Y)..(screenY + TAB_Y + TAB_HEIGHT)
    }

    private fun renderTabs(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, theme: SmartphoneTheme) {
        val isAppsActive = currentTab == SettingsTab.APPS
        val isThemesActive = currentTab == SettingsTab.THEMES

        val appsHover = isInAppsTab(mouseX, mouseY)
        val themesHover = isInThemesTab(mouseX, mouseY)

        val activeBg = 0x44000000 or (theme.colors.primary and 0x00FFFFFF)

        // Pill indicator background
        if (isAppsActive) {
            guiGraphics.fill(screenX + TAB_APPS_X, screenY + TAB_Y, screenX + TAB_APPS_X + TAB_APPS_WIDTH, screenY + TAB_Y + TAB_HEIGHT, activeBg)
            guiGraphics.fill(screenX + TAB_APPS_X, screenY + TAB_Y + TAB_HEIGHT - 1, screenX + TAB_APPS_X + TAB_APPS_WIDTH, screenY + TAB_Y + TAB_HEIGHT, theme.colors.primary)
        } else if (appsHover) {
            guiGraphics.fill(screenX + TAB_APPS_X, screenY + TAB_Y, screenX + TAB_APPS_X + TAB_APPS_WIDTH, screenY + TAB_Y + TAB_HEIGHT, 0x22FFFFFF.toInt())
        }

        if (isThemesActive) {
            guiGraphics.fill(screenX + TAB_THEMES_X, screenY + TAB_Y, screenX + TAB_THEMES_X + TAB_THEMES_WIDTH, screenY + TAB_Y + TAB_HEIGHT, activeBg)
            guiGraphics.fill(screenX + TAB_THEMES_X, screenY + TAB_Y + TAB_HEIGHT - 1, screenX + TAB_THEMES_X + TAB_THEMES_WIDTH, screenY + TAB_Y + TAB_HEIGHT, theme.colors.primary)
        } else if (themesHover) {
            guiGraphics.fill(screenX + TAB_THEMES_X, screenY + TAB_Y, screenX + TAB_THEMES_X + TAB_THEMES_WIDTH, screenY + TAB_Y + TAB_HEIGHT, 0x22FFFFFF.toInt())
        }

        val appsColor = if (isAppsActive) theme.colors.textPrimary else if (appsHover) 0xDDFFFFFF.toInt() else theme.colors.textMuted
        val themesColor = if (isThemesActive) theme.colors.textPrimary else if (themesHover) 0xDDFFFFFF.toInt() else theme.colors.textMuted

        val appsText = Component.translatable("cobblemon_smartphone.settings.tab.apps").string
        val themesText = Component.translatable("cobblemon_smartphone.settings.tab.themes").string

        val appsW = (font.width(appsText) * 0.5f).toInt()
        val themesW = (font.width(themesText) * 0.5f).toInt()

        drawScaled(guiGraphics, appsText, screenX + TAB_APPS_X + (TAB_APPS_WIDTH - appsW) / 2, screenY + TAB_Y + 2, appsColor, 0.5f)
        drawScaled(guiGraphics, themesText, screenX + TAB_THEMES_X + (TAB_THEMES_WIDTH - themesW) / 2, screenY + TAB_Y + 2, themesColor, 0.5f)
    }

    private fun themeCardPosition(index: Int): Pair<Int, Int> {
        val col = index % CARD_COLUMNS
        val row = index / CARD_COLUMNS
        return CARD_START_X + col * CARD_SPACING_X to CARD_START_Y + row * CARD_SPACING_Y
    }

    private fun isInThemeCard(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val (cx, cy) = themeCardPosition(index)
        return mouseX in (screenX + cx)..(screenX + cx + CARD_WIDTH) &&
                mouseY in (screenY + cy)..(screenY + cy + CARD_HEIGHT)
    }

    private fun renderThemeCard(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        targetTheme: SmartphoneTheme,
        activeTheme: SmartphoneTheme
    ) {
        val (cx, cy) = themeCardPosition(index)
        val px = screenX + cx
        val py = screenY + cy
        val isSelected = targetTheme.id == selectedThemeId
        val isHovered = mouseX in px..(px + CARD_WIDTH) && mouseY in py..(py + CARD_HEIGHT)

        val borderColor = when {
            isSelected -> activeTheme.colors.gold
            isHovered -> activeTheme.colors.primary
            else -> 0x66000000.toInt()
        }
        guiGraphics.fill(px - 1, py - 1, px + CARD_WIDTH + 1, py + CARD_HEIGHT + 1, borderColor)
        guiGraphics.fill(px, py, px + CARD_WIDTH, py + CARD_HEIGHT, activeTheme.colors.headerNavy)

        SmartphoneThemeRenderer.renderPreview(guiGraphics, targetTheme, px + 2, py + 2, 34, 40)

        if (isSelected) {
            guiGraphics.fill(px + 23, py + 2, px + 36, py + 12, 0xCC000000.toInt())
            drawScaled(guiGraphics, "✓", px + 26, py + 3, activeTheme.colors.gold, 0.7f)
        }

        val nameText = targetTheme.name.string
        val nameW = (font.width(nameText) * 0.45f).toInt()
        val textX = px + (CARD_WIDTH - nameW) / 2
        drawScaled(guiGraphics, nameText, textX, py + 44, if (isSelected) activeTheme.colors.gold else activeTheme.colors.textPrimary, 0.45f)
    }

    private fun renderThemeHint(guiGraphics: GuiGraphics, theme: SmartphoneTheme) {
        val hint = Component.translatable("cobblemon_smartphone.theme.hint").string
        val width = (font.width(hint) * HINT_SCALE).toInt()
        drawScaled(guiGraphics, hint, screenX + (GUI_WIDTH - width) / 2, screenY + HINT_Y, theme.colors.textSecondary, HINT_SCALE)
    }

    private fun renderThemePageDots(guiGraphics: GuiGraphics, theme: SmartphoneTheme) {
        val totalDots = (maxThemePage + 1).coerceAtMost(MAX_VISIBLE_DOTS)
        if (totalDots <= 1) return

        val totalWidth = totalDots * DOT_SIZE + (totalDots - 1) * DOT_SPACING
        val startX = DOT_CENTER_X - totalWidth / 2

        for (i in 0 until totalDots) {
            val active = i == currentThemePage
            val yOffset = if (active) 0 else DOT_INACTIVE_Y_OFFSET
            PageDotRenderer.draw(
                guiGraphics = guiGraphics,
                x = screenX + startX + i * (DOT_SIZE + DOT_SPACING),
                y = screenY + DOT_Y + yOffset,
                active = active,
                activeColor = theme.colors.primary
            )
        }
    }

    private fun renderThemeHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (SmartphoneBatteryRenderer.renderTooltip(font, guiGraphics, screenX, screenY, mouseX, mouseY, isLargeScreen = false, smartphoneStack)) {
            return
        }

        if (isInBackButton(mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                font,
                Component.translatable("cobblemon_smartphone.tooltip.back_to_phone"),
                mouseX,
                mouseY
            )
            return
        }

        pagedThemes().forEachIndexed { index, theme ->
            if (isInThemeCard(mouseX, mouseY, index)) {
                guiGraphics.renderTooltip(font, theme.name, mouseX, mouseY)
            }
        }
    }

    private fun renderFooterButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, theme: SmartphoneTheme) {
        renderFooterButton(guiGraphics, theme.getPrevButtonTexture(), FOOTER_PREV_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, theme.getHomeButtonTexture(), FOOTER_HOME_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, theme.getNextButtonTexture(), FOOTER_NEXT_X, mouseX, mouseY)
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (selectorActionId != null || draggingIndex != -1) return

        if (SmartphoneBatteryRenderer.renderTooltip(font, guiGraphics, screenX, screenY, mouseX, mouseY, isLargeScreen = false, smartphoneStack)) {
            return
        }

        if (isInBackButton(mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                font,
                Component.translatable("cobblemon_smartphone.tooltip.back_to_phone"),
                mouseX,
                mouseY
            )
            return
        }

        pagedIds().forEachIndexed { index, actionId ->
            val action = actionById(actionId) ?: return@forEachIndexed
            val (gx, gy) = gridPosition(index)
            if (isInViewSelect(mouseX, mouseY, index)) {
                val key = if (hiddenIds.contains(actionId)) "show_app" else "hide_app"
                guiGraphics.renderTooltip(
                    font,
                    Component.translatable("cobblemon_smartphone.tooltip.$key"),
                    mouseX,
                    mouseY
                )
                return
            }
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

    private fun renderPageDots(guiGraphics: GuiGraphics, theme: SmartphoneTheme) {
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
            val yOffset = if (active) 0 else DOT_INACTIVE_Y_OFFSET
            PageDotRenderer.draw(
                guiGraphics,
                screenX + startX + index * (DOT_SIZE + DOT_SPACING),
                screenY + DOT_Y + yOffset,
                active,
                activeColor = theme.colors.primary
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

        private const val BACK_X = 20
        private const val BACK_Y = 20
        private const val BACK_SCALE = 0.5f

        private const val TAB_Y = 26
        private const val TAB_HEIGHT = 10
        private const val TAB_APPS_X = 22
        private const val TAB_APPS_WIDTH = 40
        private const val TAB_THEMES_X = 66
        private const val TAB_THEMES_WIDTH = 42

        private const val GRID_COLUMNS = 2
        private const val GRID_ROWS = 3
        private const val ACTIONS_PER_PAGE = GRID_COLUMNS * GRID_ROWS

        private const val GRID_START_X = 26
        private const val GRID_START_Y = 46
        private const val BUTTON_SPACING_X = 43
        private const val BUTTON_SPACING_Y = 41
        private const val BUTTON_WIDTH = 36
        private const val BUTTON_HEIGHT = 36

        private const val DRAG_WIDTH = 8
        private const val DRAG_HEIGHT = 8

        private const val VIEW_WIDTH = 8
        private const val VIEW_HEIGHT = 8

        private const val HINT_Y = 38
        private const val HINT_SCALE = 0.5f

        private const val SLOT_BADGE_SCALE = 0.5f
        private const val SLOT_BADGE_PADDING = 2
        private const val SLOT_BADGE_BG = 0xC03A96B6.toInt()
        private const val SLOT_BADGE_TEXT = 0xFFFFFFFF.toInt()

        // Bounds the popup is clamped inside, matching where the app grid renders (which is
        // known to sit within the phone's lit screen). Derived from the grid/footer layout:
        //   LEFT   = GRID_START_X (26)
        //   RIGHT  = GRID_START_X + BUTTON_SPACING_X + BUTTON_WIDTH (26 + 43 + 36 = 105)
        //   TOP    = GRID_START_Y (46)
        //   BOTTOM = FOOTER_BUTTON_Y + FOOTER_BUTTON_SIZE (187 + 7 = 194)
        // Nudge these if the popup still touches the frame on any side.
        private const val SCREEN_CONTENT_LEFT = 26
        private const val SCREEN_CONTENT_RIGHT = 105
        private const val SCREEN_CONTENT_TOP = 46
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

        private const val THEMES_PER_PAGE = 4
        private const val CARD_COLUMNS = 2
        private const val CARD_ROWS = 2
        private const val CARD_WIDTH = 38
        private const val CARD_HEIGHT = 52
        private const val CARD_START_X = 24
        private const val CARD_START_Y = 48
        private const val CARD_SPACING_X = 44
        private const val CARD_SPACING_Y = 56

        private const val FOOTER_PREV_X = 36
        private const val FOOTER_HOME_X = 62
        private const val FOOTER_NEXT_X = 88
        private const val FOOTER_BUTTON_Y = 187
        private const val FOOTER_BUTTON_SIZE = 7
        private const val FOOTER_BUTTON_TEXTURE_HEIGHT = 14

        private const val MAX_VISIBLE_DOTS = 3
        private const val DOT_CENTER_X = GUI_WIDTH / 2
        private const val DOT_Y = 171
        private const val DOT_SIZE = 9
        private const val DOT_SPACING = 2
        private const val DOT_INACTIVE_Y_OFFSET = 1
    }
}
