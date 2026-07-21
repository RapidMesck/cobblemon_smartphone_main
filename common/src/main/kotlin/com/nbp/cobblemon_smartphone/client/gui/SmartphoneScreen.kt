package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.actions.PokedexAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionOrder
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class SmartphoneScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null
) : Screen(Component.translatable("cobblemon_smartphone.screen.smartphone")) {
    private val actions get() = SmartphoneActionOrder.apply(SmartphoneActionRegistry.getEnabledActions())
    private val frameTexture = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone",
        "textures/gui/smartphone_${color.modelName}.png"
    )
    private var screenX = 0
    private var screenY = 0
    private var currentPage = 0

    private val actionsPerPage get() = GRID_COLUMNS * GRID_ROWS
    private val maxPage get() = if (actions.isEmpty()) 0 else (actions.size - 1) / actionsPerPage

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        PokedexAction.requestedPokedexType = color.toPokedexType()
        // Set context so isEnabled() checks THIS smartphone's upgrades
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        // Smartphone frame background
        blitk(
            matrixStack = matrices,
            texture = frameTexture,
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        // Screen content
        blitk(
            matrixStack = matrices,
            texture = HOME_SCREEN_TEXTURE,
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        renderWorldTime(guiGraphics)
        renderHeaderSettingsButton(guiGraphics, mouseX, mouseY)

        // Render actions as buttons
        pagedActions().forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            val texture = if (isHovered(mouseX, mouseY, x, y)) action.hoverTexture else action.texture
            blitk(
                matrixStack = matrices,
                texture = texture,
                x = screenX + x,
                y = screenY + y,
                width = BUTTON_WIDTH,
                height = BUTTON_HEIGHT
            )
        }

        renderPageDots(guiGraphics)
        renderFooterButtons(guiGraphics, mouseX, mouseY)
        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isInHeaderSettingsButton(mouseX.toInt(), mouseY.toInt())) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneSettingsScreen(color, smartphoneStack))
            return true
        }

        // Handle button click
        pagedActions().forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            if (isHovered(mouseX.toInt(), mouseY.toInt(), x, y)) {
                action.onClick()
                return true
            }
        }
        // Handle page controls
        if (isInFooterButton(mouseX.toInt(), mouseY.toInt(), FOOTER_PREV_X)) {
            playClickSound()
            changePage(-1)
            return true
        }
        if (isInFooterButton(mouseX.toInt(), mouseY.toInt(), FOOTER_HOME_X)) {
            playClickSound()
            changePageTo(0)
            return true
        }
        if (isInFooterButton(mouseX.toInt(), mouseY.toInt(), FOOTER_NEXT_X)) {
            playClickSound()
            changePage(1)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (maxPage == 0 || verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        return changePage(if (verticalAmount < 0.0) 1 else -1)
    }

    private fun changePage(offset: Int): Boolean {
        val nextPage = (currentPage + offset).coerceIn(0, maxPage)
        if (nextPage == currentPage) {
            return false
        }

        currentPage = nextPage
        return true
    }

    private fun changePageTo(page: Int): Boolean {
        val nextPage = page.coerceIn(0, maxPage)
        if (nextPage == currentPage) {
            return false
        }

        currentPage = nextPage
        return true
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

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    private fun renderWorldTime(guiGraphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        val dayTime = minecraft.level?.dayTime ?: return
        val ticksToday = Math.floorMod(dayTime, TICKS_PER_DAY)
        val totalMinutes = ((ticksToday * MINUTES_PER_DAY / TICKS_PER_DAY) + DAWN_MINUTES) % MINUTES_PER_DAY
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        val timeText = "%02d:%02d".format(hours, minutes)

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(
            (screenX + TIME_X).toDouble(),
            (screenY + TIME_Y).toDouble(),
            0.0
        )
        guiGraphics.pose().scale(TIME_SCALE, TIME_SCALE, 1f)
        guiGraphics.drawString(minecraft.font, timeText, 0, 0, TIME_COLOR, false)
        guiGraphics.pose().popPose()
    }

    private fun renderHeaderSettingsButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val hovered = isInHeaderSettingsButton(mouseX, mouseY)
        val textureY = if (hovered) HEADER_SETTINGS_SIZE else 0
        guiGraphics.blit(
            SETTINGS_BUTTON_TEXTURE,
            screenX + HEADER_SETTINGS_X,
            screenY + HEADER_SETTINGS_Y,
            0f,
            textureY.toFloat(),
            HEADER_SETTINGS_SIZE,
            HEADER_SETTINGS_SIZE,
            HEADER_SETTINGS_SIZE,
            HEADER_SETTINGS_SIZE * 2
        )
    }

    private fun isInHeaderSettingsButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= screenX + HEADER_SETTINGS_X && mouseX <= screenX + HEADER_SETTINGS_X + HEADER_SETTINGS_SIZE &&
                mouseY >= screenY + HEADER_SETTINGS_Y && mouseY <= screenY + HEADER_SETTINGS_Y + HEADER_SETTINGS_SIZE
    }

    private fun renderFooterButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        renderFooterButton(guiGraphics, PREV_BUTTON_TEXTURE, FOOTER_PREV_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, HOME_BUTTON_TEXTURE, FOOTER_HOME_X, mouseX, mouseY)
        renderFooterButton(guiGraphics, NEXT_BUTTON_TEXTURE, FOOTER_NEXT_X, mouseX, mouseY)
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        pagedActions().forEachIndexed { index, action ->
            val (x, y) = getButtonPosition(index)
            if (isHovered(mouseX, mouseY, x, y)) {
                guiGraphics.renderTooltip(font, action.displayName, mouseX, mouseY)
                return
            }
        }

        val key = when {
            isInHeaderSettingsButton(mouseX, mouseY) -> "settings"
            isInFooterButton(mouseX, mouseY, FOOTER_PREV_X) -> "previous_page"
            isInFooterButton(mouseX, mouseY, FOOTER_HOME_X) -> "first_page"
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
        private const val GRID_COLUMNS = 2
        private const val GRID_ROWS = 3
        private const val GRID_START_X = 26
        private const val GRID_START_Y = 37
        private const val BUTTON_SPACING = 43
        private const val GUI_WIDTH = 131
        private const val GUI_HEIGHT = 207
        private const val BUTTON_WIDTH = 36
        private const val BUTTON_HEIGHT = 36

        private const val HEADER_SETTINGS_X = 80
        private const val HEADER_SETTINGS_Y = 17
        private const val HEADER_SETTINGS_SIZE = 9

        private const val TIME_X = 20
        private const val TIME_Y = 20
        private const val TIME_SCALE = 0.6f
        private const val TIME_COLOR = 0xE6FFFF
        private const val TICKS_PER_DAY = 24_000L
        private const val MINUTES_PER_DAY = 1_440L
        private const val MINUTES_PER_HOUR = 60L
        private const val DAWN_MINUTES = 360L

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
        private val SETTINGS_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/elements/settings_button.png"
        )
    }
}
