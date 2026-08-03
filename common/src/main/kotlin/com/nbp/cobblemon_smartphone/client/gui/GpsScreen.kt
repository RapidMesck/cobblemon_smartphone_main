package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.BiomeListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestBiomeListPacket
import com.nbp.cobblemon_smartphone.util.GpsDataProvider.BiomeInfo
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class GpsScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null
) : Screen(Component.translatable("cobblemon_smartphone.screen.gps")) {

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0
    private lateinit var searchBox: EditBox
    private var results: List<BiomeInfo> = emptyList()
    private var allBiomes: List<BiomeInfo> = emptyList()
    private var listRequestId = -1L
    private var listRequestStartedAt = 0L
    private var listLoading = true
    private var listErrorKey: String? = null

    override fun isPauseScreen(): Boolean = false

    private fun lang(key: String): String =
        Component.translatable("cobblemon_smartphone.gps.$key").string

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
            Component.translatable("cobblemon_smartphone.gps.search")
        )
        searchBox.setMaxLength(30)
        searchBox.setBordered(false)
        searchBox.setHint(Component.translatable("cobblemon_smartphone.gps.search"))
        searchBox.setTextColor(0xFF888888.toInt())
        searchBox.setResponder { query -> onSearchChanged(query) }
        addRenderableWidget(searchBox)

        val cached = GpsClientState.cachedBiomes
        if (cached != null) {
            allBiomes = cached
            listLoading = false
            listErrorKey = null
            onSearchChanged("")
        } else {
            listRequestId = GpsClientState.nextRequestId()
            GpsClientState.beginListRequest(listRequestId)
            listRequestStartedAt = System.currentTimeMillis()
            listLoading = true
            listErrorKey = null
            RequestBiomeListPacket(listRequestId).sendToServer()
        }
        scrollY = 0
    }

    override fun removed() {
        GpsClientState.cancelListRequest(listRequestId)
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        receiveBiomeList()
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

        // Back button
        val backHover = isInBackButton(mouseX, mouseY)
        val backColor = if (backHover) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        guiGraphics.drawString(font, lang("back"), screenX + BACK_X, screenY + BACK_Y, backColor, false)

        // Stop tracking button, only shown while a search is active
        if (GpsClientState.tracking) {
            val stopText = lang("stop_tracking")
            val stopHover = isInStopButton(mouseX, mouseY)
            val stopColor = if (stopHover) 0xFFFF6B6B.toInt() else 0xFFFFFFFF.toInt()
            guiGraphics.drawString(font, stopText, stopButtonX(stopText), screenY + BACK_Y, stopColor, false)
        }

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
        if (results.isEmpty() && (listLoading || listErrorKey != null)) {
            val message = Component.translatable(
                listErrorKey ?: "cobblemon_smartphone.gps.loading_biomes"
            ).string
            val centerX = screenX + CONTENT_X + LIST_WIDTH / 2
            val centerY = screenY + (LIST_START_Y + LIST_END_Y) / 2
            guiGraphics.drawString(font, message, centerX - font.width(message) / 2, centerY, 0xFFAAAAAA.toInt(), false)
        }
        renderScrollbar(guiGraphics, mouseY)
        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }

        if (isInStopButton(mx, my)) {
            playClickSound()
            GpsClientState.stopTracking()
            return true
        }

        val startIndex = (scrollY / (CARD_HEIGHT + CARD_GAP)).coerceAtLeast(0)
        val endIndex = ((scrollY + LIST_END_Y - LIST_START_Y) / (CARD_HEIGHT + CARD_GAP) + 1).coerceAtMost(results.size)
        for (i in startIndex until endIndex) {
            val biome = results[i]
            if (!isTrackable(biome)) continue
            val cy = screenY + LIST_START_Y + i * (CARD_HEIGHT + CARD_GAP) - scrollY
            if (mx >= screenX + CONTENT_X && mx <= screenX + CONTENT_X + LIST_WIDTH &&
                my >= cy && my <= cy + CARD_HEIGHT
            ) {
                playClickSound()
                GpsClientState.startTracking(biome)
                Minecraft.getInstance().setScreen(null)
                return true
            }
        }

        // Scrollbar drag
        if (maxScroll > 0) {
            val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
            val trackY = screenY + LIST_START_Y
            val trackH = LIST_END_Y - LIST_START_Y
            val handleH =
                maxOf(12, (trackH.toFloat() * trackH / (results.size * (CARD_HEIGHT + CARD_GAP)).toFloat()).toInt())
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
        val normalized = query.trim().lowercase()
        results = if (normalized.isEmpty()) {
            allBiomes
        } else {
            allBiomes.filter { matchesQuery(it, normalized) }
        }
        updateMaxScroll()
        scrollY = 0
    }

    private fun matchesQuery(biome: BiomeInfo, q: String): Boolean {
        val tagQuery = q.removePrefix("#")
        return localizedBiomeName(biome).lowercase().contains(q) ||
            biome.id.lowercase().contains(q) ||
            biome.id.substringBefore(':').lowercase().contains(q) ||
            biome.tags.any { it.lowercase().removePrefix("#").contains(tagQuery) } ||
            biome.dimensions.any { it.lowercase().contains(q) } ||
            biome.sourceMod.lowercase().contains(q)
    }

    private fun receiveBiomeList() {
        if (!listLoading) return
        val response = GpsClientState.consumeListResponse(listRequestId)
        if (response != null) {
            listLoading = false
            when (response.status) {
                BiomeListResponsePacket.Status.SUCCESS -> {
                    allBiomes = response.biomes
                    onSearchChanged(if (::searchBox.isInitialized) searchBox.value else "")
                }
                BiomeListResponsePacket.Status.RATE_LIMITED -> listErrorKey = "cobblemon_smartphone.gps.error.rate_limited"
                BiomeListResponsePacket.Status.ERROR -> listErrorKey = "cobblemon_smartphone.gps.error.server"
            }
        } else if (System.currentTimeMillis() - listRequestStartedAt >= REQUEST_TIMEOUT_MS) {
            listLoading = false
            listErrorKey = "cobblemon_smartphone.gps.error.timeout"
        }
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, mouseY: Int) {
        if (maxScroll <= 0) return
        val trackX = screenX + CONTENT_X + LIST_WIDTH + 1
        val trackY = screenY + LIST_START_Y
        val trackH = LIST_END_Y - LIST_START_Y
        val handleH =
            maxOf(12, (trackH.toFloat() * trackH / (results.size * (CARD_HEIGHT + CARD_GAP)).toFloat()).toInt())
        val handleY = trackY + (scrollY.toFloat() / maxScroll * (trackH - handleH)).toInt()

        if (draggingScrollbar) {
            val dy = mouseY - dragStartMouseY
            val scrollRange = trackH - handleH
            if (scrollRange > 0) {
                scrollY = (dragStartScrollY + (dy.toFloat() / scrollRange * maxScroll).toInt()).coerceIn(0, maxScroll)
            }
        }

        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0x20FFFFFF.toInt())
        val color = if (draggingScrollbar) 0x90FFFFFF.toInt() else 0x60FFFFFF.toInt()
        guiGraphics.fill(trackX, handleY, trackX + SCROLLBAR_WIDTH, handleY + handleH, color)
    }

    private fun updateMaxScroll() {
        maxScroll = maxOf(0, results.size * (CARD_HEIGHT + CARD_GAP) - (LIST_END_Y - LIST_START_Y))
    }

    private fun isTrackable(biome: BiomeInfo): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val dimension = level.dimension().location().toString()
        return biome.dimensions.isEmpty() || biome.dimensions.contains(dimension)
    }

    private fun localizedBiomeName(biome: BiomeInfo): String {
        val split = biome.id.split(':', limit = 2)
        if (split.size != 2) return biome.id
        val key = "biome.${split[0]}.${split[1]}"
        return if (I18n.exists(key)) I18n.get(key) else biome.id
    }

    private fun renderCard(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        index: Int,
        cy: Int,
        biome: BiomeInfo
    ) {
        val x1 = screenX + CONTENT_X
        val y1 = screenY + cy
        val x2 = screenX + CONTENT_X + LIST_WIDTH
        val y2 = y1 + CARD_HEIGHT
        val hovered = mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2
        val trackable = isTrackable(biome)

        // Card background
        val bgColor = when {
            !trackable -> 0x403A96B6.toInt()
            hovered -> 0x4DFFFFFF.toInt()
            else -> 0xBF3A96B6.toInt()
        }
        guiGraphics.fill(x1, y1, x2, y2, bgColor)

        // Divider line between cards
        if (index > 0) {
            guiGraphics.fill(x1 + 4, y1, x2 - 4, y1 + 1, 0x15FFFFFF.toInt())
        }

        val nameColor = if (!trackable) 0x80808080.toInt() else if (hovered) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        val subColor = if (trackable) 0xA0FFFFFF.toInt() else 0x60808080.toInt()

        // Line 1: localized name + id
        val nameAndId = localizedBiomeName(biome)
        guiGraphics.drawString(font, nameAndId, x1 + 8, y1 + 3, nameColor, false)
        val idX = x1 + 8 + font.width(nameAndId) + 6
        guiGraphics.drawString(font, biome.id, idX, y1 + 3, subColor, false)

        // Line 2: dimensions + source mod
        val dims = if (biome.dimensions.isEmpty()) lang("any_dimension") else biome.dimensions.joinToString(", ")
        guiGraphics.drawString(font, "$dims • ${biome.sourceMod}", x1 + 8, y1 + 15, subColor, false)
    }

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltip = when {
            isInBackButton(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.back_to_phone")
            else -> {
                val startIndex = (scrollY / (CARD_HEIGHT + CARD_GAP)).coerceAtLeast(0)
                val endIndex = ((scrollY + LIST_END_Y - LIST_START_Y) / (CARD_HEIGHT + CARD_GAP) + 1)
                    .coerceAtMost(results.size)
                (startIndex until endIndex).firstOrNull { index ->
                    val cy = screenY + LIST_START_Y + index * (CARD_HEIGHT + CARD_GAP) - scrollY
                    mouseX >= screenX + CONTENT_X && mouseX <= screenX + CONTENT_X + LIST_WIDTH &&
                        mouseY >= cy && mouseY <= cy + CARD_HEIGHT
                }?.let { index ->
                    val biome = results[index]
                    if (!isTrackable(biome)) {
                        Component.translatable("cobblemon_smartphone.gps.other_dimension")
                    } else if (biome.tags.isNotEmpty()) {
                        Component.literal(biome.tags.joinToString("  "))
                    } else {
                        Component.translatable("cobblemon_smartphone.gps.select")
                    }
                }
            }
        } ?: return
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= screenX + BACK_X && mouseX <= screenX + BACK_X + 30 &&
            mouseY >= screenY + BACK_Y - 2 && mouseY <= screenY + BACK_Y + 10
    }

    private fun stopButtonX(text: String): Int = screenX + GUI_WIDTH - STOP_RIGHT_MARGIN - font.width(text)

    private fun isInStopButton(mouseX: Int, mouseY: Int): Boolean {
        if (!GpsClientState.tracking) return false
        val text = lang("stop_tracking")
        val x = stopButtonX(text)
        return mouseX >= x - 2 && mouseX <= x + font.width(text) + 2 &&
            mouseY >= screenY + BACK_Y - 2 && mouseY <= screenY + BACK_Y + 10
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        private const val BACK_X = 20
        private const val BACK_Y = 14
        private const val STOP_RIGHT_MARGIN = 20

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
