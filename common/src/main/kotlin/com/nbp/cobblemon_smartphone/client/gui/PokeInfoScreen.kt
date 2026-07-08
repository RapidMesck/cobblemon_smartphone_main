package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class PokeInfoScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null
) : Screen(Component.literal("PokeInfo")) {

    private val frameTexture = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone",
        "textures/gui/large_smartphone_red.png"
    )
    private var screenX = 0
    private var screenY = 0
    private var currentPage = 0
    private lateinit var searchBox: EditBox
    private var results: List<PokeInfoDataProvider.SpeciesInfo> = emptyList()

    private val maxPage get() = if (results.isEmpty()) 0 else (results.size - 1) / RESULTS_PER_PAGE

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        searchBox = EditBox(
            font,
            screenX + SEARCH_X,
            screenY + SEARCH_Y,
            SEARCH_WIDTH,
            SEARCH_HEIGHT,
            Component.translatable("cobblemon_smartphone.pokeinfo.search")
        )
        searchBox.setMaxLength(30)
        searchBox.setResponder { query -> onSearchChanged(query) }
        addRenderableWidget(searchBox)

        results = PokeInfoDataProvider.all()
        currentPage = 0
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
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

        searchBox.renderWidget(guiGraphics, mouseX, mouseY, delta)

        pagedResults().forEachIndexed { index, species ->
            renderResult(guiGraphics, mouseX, mouseY, index, species)
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

        pagedResults().forEachIndexed { index, species ->
            if (isInResult(mx, my, index)) {
                playClickSound()
                Minecraft.getInstance().setScreen(PokeInfoDetailScreen(color, smartphoneStack, species.dexNumber))
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return searchBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused && searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
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

    private fun onSearchChanged(query: String) {
        results = PokeInfoDataProvider.search(query)
        currentPage = 0
    }

    private fun changePage(offset: Int): Boolean {
        val nextPage = (currentPage + offset).coerceIn(0, maxPage)
        if (nextPage == currentPage) {
            return false
        }

        currentPage = nextPage
        return true
    }

    private fun pagedResults(): List<PokeInfoDataProvider.SpeciesInfo> {
        val from = currentPage * RESULTS_PER_PAGE
        return results.drop(from).take(RESULTS_PER_PAGE)
    }

    private fun resultY(index: Int): Int = RESULTS_START_Y + index * RESULT_HEIGHT

    private fun isInResult(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val y = resultY(index)
        return mouseX >= screenX + RESULT_X && mouseX <= screenX + RESULT_X + RESULT_WIDTH &&
                mouseY >= screenY + y && mouseY <= screenY + y + RESULT_HEIGHT
    }

    private fun renderResult(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        species: PokeInfoDataProvider.SpeciesInfo
    ) {
        val y = resultY(index)
        val hovered = isInResult(mouseX, mouseY, index)
        val color = if (hovered) RESULT_HOVER_COLOR else RESULT_COLOR

        if (hovered) {
            guiGraphics.fill(
                screenX + RESULT_BG_X,
                screenY + y,
                screenX + RESULT_BG_X + RESULT_BG_WIDTH,
                screenY + y + RESULT_HEIGHT,
                RESULT_BG_HOVER.toInt()
            )
        }

        val text = String.format("#%03d %s", species.dexNumber, species.name)
        guiGraphics.drawString(
            font,
            text,
            screenX + RESULT_TEXT_X,
            screenY + y + RESULT_TEXT_Y_OFFSET,
            color,
            false
        )
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

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    companion object {
        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        private const val SEARCH_X = 22
        private const val SEARCH_Y = 28
        private const val SEARCH_WIDTH = 167
        private const val SEARCH_HEIGHT = 14

        private const val RESULTS_PER_PAGE = 12
        private const val RESULTS_START_Y = 48
        private const val RESULT_HEIGHT = 12

        private const val RESULT_X = 22
        private const val RESULT_WIDTH = 167
        private const val RESULT_TEXT_X = 26
        private const val RESULT_TEXT_Y_OFFSET = 1
        private const val RESULT_BG_X = 20
        private const val RESULT_BG_WIDTH = 171
        private const val RESULT_COLOR = 0xFFFFFFFF.toInt()
        private const val RESULT_HOVER_COLOR = 0xFFFFD700.toInt()
        private const val RESULT_BG_HOVER = 0x30FFFFFF

        private const val FOOTER_PREV_X = 76
        private const val FOOTER_HOME_X = 102
        private const val FOOTER_NEXT_X = 128
        private const val FOOTER_BUTTON_Y = 190
        private const val FOOTER_BUTTON_SIZE = 7
        private const val FOOTER_BUTTON_TEXTURE_HEIGHT = 14

        private const val MAX_VISIBLE_DOTS = 3
        private const val DOT_CENTER_X = GUI_WIDTH / 2
        private const val DOT_Y = 175
        private const val DOT_SIZE = 9
        private const val DOT_SPACING = 2
        private const val DOT_INACTIVE_Y_OFFSET = 1

        private val HOME_SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
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
    }
}
