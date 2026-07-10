package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
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
import org.joml.Quaternionf

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
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0
    private lateinit var searchBox: EditBox
    private var results: List<PokeInfoDataProvider.SpeciesInfo> = emptyList()
    private val modelCache = mutableMapOf<Int, RenderablePokemon?>()

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        searchBox = EditBox(
            font,
            screenX + SEARCH_X + 2,
            screenY + SEARCH_Y + 3,
            SEARCH_WIDTH - 4,
            SEARCH_HEIGHT,
            Component.translatable("cobblemon_smartphone.pokeinfo.search")
        )
        searchBox.setMaxLength(30)
        searchBox.setBordered(false)
        searchBox.setTextColor(0xFF888888.toInt())
        searchBox.setResponder { query -> onSearchChanged(query) }
        addRenderableWidget(searchBox)

        results = PokeInfoDataProvider.all()
        updateMaxScroll()
        scrollY = 0
    }

    override fun removed() {
        modelCache.clear()
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        blitk(matrixStack = matrices, texture = frameTexture, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)
        blitk(matrixStack = matrices, texture = HOME_SCREEN_TEXTURE, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)

        // Back button
        val backHover = isInBackButton(mouseX, mouseY)
        val backColor = if (backHover) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        guiGraphics.drawString(font, "\u00AB Back", screenX + BACK_X, screenY + BACK_Y, backColor, false)

        // Search box background
        val sx = screenX + SEARCH_X
        val sy = screenY + SEARCH_Y
        guiGraphics.fill(sx, sy, sx + SEARCH_WIDTH, sy + SEARCH_HEIGHT, SECTION_CONTENT_BG)
        guiGraphics.fill(sx, sy, sx + SEARCH_WIDTH, sy + 1, SECTION_TITLE_BG)
        guiGraphics.fill(sx, sy + SEARCH_HEIGHT - 1, sx + SEARCH_WIDTH, sy + SEARCH_HEIGHT, SECTION_TITLE_BG)
        guiGraphics.fill(sx, sy, sx + 1, sy + SEARCH_HEIGHT, SECTION_TITLE_BG)
        guiGraphics.fill(sx + SEARCH_WIDTH - 1, sy, sx + SEARCH_WIDTH, sy + SEARCH_HEIGHT, SECTION_TITLE_BG)

        searchBox.renderWidget(guiGraphics, mouseX, mouseY, delta)

        guiGraphics.enableScissor(
            screenX + CONTENT_X, screenY + LIST_START_Y,
            screenX + CONTENT_X + LIST_WIDTH, screenY + LIST_END_Y
        )

        val startIndex = (scrollY / (CARD_HEIGHT + CARD_GAP)).coerceAtLeast(0)
        val endIndex = ((scrollY + LIST_END_Y - LIST_START_Y) / (CARD_HEIGHT + CARD_GAP) + 1).coerceAtMost(results.size)
        for (i in startIndex until endIndex) {
            val cy = LIST_START_Y + i * (CARD_HEIGHT + CARD_GAP) - scrollY
            renderCard(guiGraphics, mouseX, mouseY, i, cy, results[i])
        }

        guiGraphics.disableScissor()
        renderScrollbar(guiGraphics, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Back button
        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }

        val startIndex = (scrollY / (CARD_HEIGHT + CARD_GAP)).coerceAtLeast(0)
        val endIndex = ((scrollY + LIST_END_Y - LIST_START_Y) / (CARD_HEIGHT + CARD_GAP) + 1).coerceAtMost(results.size)
        for (i in startIndex until endIndex) {
            val cy = screenY + LIST_START_Y + i * (CARD_HEIGHT + CARD_GAP) - scrollY
            if (mx >= screenX + CONTENT_X && mx <= screenX + CONTENT_X + LIST_WIDTH &&
                my >= cy && my <= cy + CARD_HEIGHT
            ) {
                playClickSound()
                Minecraft.getInstance().setScreen(PokeInfoDetailScreen(color, smartphoneStack, results[i].dexNumber))
                return true
            }
        }

        // Scrollbar drag
        if (maxScroll > 0) {
            val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
            val trackY = screenY + LIST_START_Y
            val trackH = LIST_END_Y - LIST_START_Y
            val handleH = maxOf(12, (trackH.toFloat() * trackH / (results.size * (CARD_HEIGHT + CARD_GAP)).toFloat()).toInt())
            val handleY = trackY + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()
            if (mx >= trackX && mx <= trackX + SCROLLBAR_WIDTH && my >= handleY && my <= handleY + handleH) {
                draggingScrollbar = true
                dragStartMouseY = my
                dragStartScrollY = scrollY
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return searchBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused && searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        if (v == 0.0 || maxScroll == 0) return super.mouseScrolled(mx, my, h, v)
        scrollY = (scrollY - v.toInt() * SCROLL_SPEED).coerceIn(0, maxScroll)
        return true
    }

    private fun onSearchChanged(query: String) {
        results = PokeInfoDataProvider.search(query)
        updateMaxScroll()
        scrollY = 0
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, mouseY: Int) {
        if (maxScroll <= 0) return
        val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
        val trackY = screenY + LIST_START_Y
        val trackH = LIST_END_Y - LIST_START_Y
        val handleH = maxOf(12, (trackH.toFloat() * trackH / (results.size * (CARD_HEIGHT + CARD_GAP)).toFloat()).toInt())
        val handleY = trackY + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()

        // Handle drag
        if (draggingScrollbar) {
            val dy = mouseY - dragStartMouseY
            val scrollRange = trackH - handleH
            if (scrollRange > 0) {
                scrollY = (dragStartScrollY + (dy.toFloat() / scrollRange * maxScroll).toInt()).coerceIn(0, maxScroll)
            }
        }

        // Track
        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0x20FFFFFF.toInt())
        // Handle
        val color = if (draggingScrollbar) 0x90FFFFFF.toInt() else 0x60FFFFFF.toInt()
        guiGraphics.fill(trackX, handleY, trackX + SCROLLBAR_WIDTH, handleY + handleH, color)
    }

    private fun updateMaxScroll() {
        maxScroll = maxOf(0, results.size * (CARD_HEIGHT + CARD_GAP) - (LIST_END_Y - LIST_START_Y))
    }

    private fun getModel(dexNumber: Int): RenderablePokemon? {
        modelCache[dexNumber]?.let { return it }
        if (modelCache.containsKey(dexNumber)) return null
        val species = PokemonSpecies.getByPokedexNumber(dexNumber)
        val model = if (species != null) RenderablePokemon(species, emptySet()) else null
        modelCache[dexNumber] = model
        return model
    }

    private fun renderCard(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        cy: Int,
        species: PokeInfoDataProvider.SpeciesInfo
    ) {
        val x1 = screenX + CONTENT_X
        val y1 = screenY + cy
        val x2 = screenX + CONTENT_X + LIST_WIDTH
        val y2 = y1 + CARD_HEIGHT
        val hovered = mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2

        // Card background
        val bgColor = if (hovered) 0x703A96B6.toInt() else 0x40FFFFFF.toInt()
        guiGraphics.fill(x1, y1, x2, y2, bgColor)

        // Divider line between cards
        if (index > 0) {
            guiGraphics.fill(x1 + 4, y1, x2 - 4, y1 + 1, 0x15FFFFFF.toInt())
        }

        // Left: dex number + name + type
        val dexAndName = "#${String.format("%03d", species.dexNumber)} ${species.name}"
        val textColor = if (hovered) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        guiGraphics.drawString(font, dexAndName, x1 + 8, y1 + 3, textColor, false)

        // Type line
        val types = listOfNotNull(species.primaryType, species.secondaryType)
        val typeText = types.joinToString(" / ") { it.replaceFirstChar { c -> c.uppercase() } }
        guiGraphics.drawString(font, typeText, x1 + 8, y1 + 15, 0xA0FFFFFF.toInt(), false)

        // Right: mini 3D model
        val model = getModel(species.dexNumber)
        if (model != null) {
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate((x2 - 18).toFloat(), (y1 + CARD_HEIGHT / 2 - 5).toFloat(), 0f)
            drawProfilePokemon(
                model, matrices, Quaternionf().rotateY(Math.toRadians(25.0).toFloat()),
                PoseType.PROFILE, FloatingState(), 0f, 9f
            )
            matrices.popPose()
        }
    }

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= screenX + BACK_X && mouseX <= screenX + BACK_X + 30 &&
                mouseY >= screenY + BACK_Y - 2 && mouseY <= screenY + BACK_Y + 10
    }

    companion object {
        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        private const val BACK_X = 20
        private const val BACK_Y = 14

        private const val SECTION_TITLE_BG = 0xFF3A96B6.toInt()
        private const val SECTION_CONTENT_BG = 0xFFEFFDFF.toInt()

        private const val SEARCH_X = 22
        private const val SEARCH_Y = 28
        private const val SEARCH_WIDTH = 167
        private const val SEARCH_HEIGHT = 14

        private const val CONTENT_X = 20
        private const val CONTENT_WIDTH = 171
        private const val SCROLLBAR_WIDTH = 5
        private const val LIST_WIDTH = CONTENT_WIDTH - SCROLLBAR_WIDTH
        private const val LIST_START_Y = 48
        private const val LIST_END_Y = 195

        private const val CARD_HEIGHT = 28
        private const val CARD_GAP = 2
        private const val SCROLL_SPEED = 15

        private val HOME_SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone", "textures/gui/large_screen.png"
        )
    }
}
