package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobblemon_smartphone.client.social.FakePlayerFilter
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Picks someone to start a conversation with.
 *
 * Entirely client-side: the online player list is already synced to every client, so no packet is
 * needed. Only online players are offered — you can keep talking to someone who logs off (their
 * thread stays in the list), but starting a brand new conversation with an offline player would
 * need a name lookup the client cannot do.
 */
class DmNewScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack?
) : Screen(Component.literal("New Message")) {

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0

    override fun isPauseScreen(): Boolean = false

    private var lastCandidatesUpdate = 0L
    private var cachedCandidates: List<PlayerInfo> = emptyList()

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color
        lastCandidatesUpdate = 0L
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    private fun candidates(): List<PlayerInfo> {
        val now = System.currentTimeMillis()
        if (now - lastCandidatesUpdate < 500L) {
            return cachedCandidates
        }
        lastCandidatesUpdate = now

        val minecraft = Minecraft.getInstance()
        val selfId = minecraft.player?.uuid ?: return emptyList()
        val connection = minecraft.connection ?: return emptyList()

        val allPlayers = connection.onlinePlayers
        val hasTabFakePlayers = allPlayers.any { FakePlayerFilter.isFakePlayer(it) }

        // When TAB's Layout feature is active, it fills the tablist with fake slots (marked listed=true)
        // and hides real players by setting them to listed=false.
        // When fake players are detected, we inspect onlinePlayers so real players remain visible
        // in contacts even if the layout plugin unlisted them.
        // On normal servers without fake layout players, we use listedOnlinePlayers to respect
        // vanilla unlisted players (e.g. spectator or vanish).
        val source = if (hasTabFakePlayers) allPlayers else connection.listedOnlinePlayers

        val result = source
            .filter { it.profile.id != selfId && !FakePlayerFilter.isFakePlayer(it) }
            .distinctBy { it.profile.id }
            .sortedBy { it.profile.name.lowercase() }

        cachedCandidates = result
        return result
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        blitk(matrixStack = matrices, texture = frameTexture, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)
        blitk(matrixStack = matrices, texture = SCREEN_TEXTURE, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)

        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + LIST_START_Y - 2, SocialUi.NAVY)
        val back = lang("back")
        guiGraphics.drawString(
            font,
            back,
            screenX + CONTENT_X + 2,
            screenY + TITLE_Y,
            if (isInBack(mouseX, mouseY)) ACCENT_COLOR else MUTED_COLOR,
            false
        )
        val title = lang("new_message")
        guiGraphics.drawString(
            font,
            title,
            screenX + CONTENT_X + CONTENT_WIDTH - font.width(title) - 3,
            screenY + TITLE_Y,
            NAME_COLOR,
            false
        )

        guiGraphics.enableScissor(
            screenX + CONTENT_X,
            screenY + LIST_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + LIST_END_Y
        )

        val players = candidates()
        maxScroll = (players.size * (ROW_HEIGHT + ROW_GAP) - (LIST_END_Y - LIST_START_Y)).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(0, maxScroll)

        var y = screenY + LIST_START_Y - scrollY
        players.forEach { info ->
            if (y + ROW_HEIGHT >= screenY + LIST_START_Y && y <= screenY + LIST_END_Y) {
                renderRow(guiGraphics, info, y, mouseX, mouseY)
            }
            y += ROW_HEIGHT + ROW_GAP
        }

        if (players.isEmpty()) {
            val message = lang("nobody_online")
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.USER, screenX + CONTENT_X + CONTENT_WIDTH / 2 - 4, screenY + 103, SocialUi.MUTED)
            guiGraphics.drawString(
                font,
                message,
                screenX + CONTENT_X + (CONTENT_WIDTH - font.width(message)) / 2,
                screenY + LIST_START_Y + (LIST_END_Y - LIST_START_Y) / 2 + 7,
                MUTED_COLOR,
                false
            )
        }

        guiGraphics.disableScissor()

        renderHoveredTooltip(guiGraphics, mouseX, mouseY, players)
    }

    private fun renderRow(guiGraphics: GuiGraphics, info: PlayerInfo, y: Int, mouseX: Int, mouseY: Int) {
        val x = screenX + CONTENT_X
        val hovered = isInRow(mouseX, mouseY, y)
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, ROW_HEIGHT, hovered)
        PlayerFaceRenderer.draw(guiGraphics, info.skin, x + ROW_PAD, y + ROW_PAD, HEAD_SIZE)
        guiGraphics.drawString(
            font,
            info.profile.name,
            x + ROW_PAD + HEAD_SIZE + 5,
            y + ROW_PAD + 1,
            CONTENT_TEXT,
            false
        )
        guiGraphics.fill(x + CONTENT_WIDTH - 8, y + 7, x + CONTENT_WIDTH - 4, y + 11, SocialUi.SUCCESS)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInBack(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, startOnDms = true))
            return true
        }

        if (my in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) {
            var y = screenY + LIST_START_Y - scrollY
            candidates().forEach { info ->
                if (isInRow(mx, my, y)) {
                    playClickSound()
                    Minecraft.getInstance().setScreen(
                        DmThreadScreen(color, smartphoneStack, info.profile.id, info.profile.name)
                    )
                    return true
                }
                y += ROW_HEIGHT + ROW_GAP
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        scrollY = (scrollY - (verticalAmount * SCROLL_SPEED).toInt()).coerceIn(0, maxScroll)
        return true
    }

    private fun isInRow(mouseX: Int, mouseY: Int, rowY: Int): Boolean =
        mouseX in (screenX + CONTENT_X)..(screenX + CONTENT_X + CONTENT_WIDTH) &&
                mouseY in rowY..(rowY + ROW_HEIGHT) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)

    private fun isInBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + CONTENT_X + 2)..(screenX + CONTENT_X + 2 + font.width(lang("back"))) &&
                mouseY in (screenY + TITLE_Y)..(screenY + TITLE_Y + font.lineHeight)

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, players: List<PlayerInfo>) {
        val tooltip = when {
            isInBack(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_back_to_social")
            findHoveredPlayer(mouseX, mouseY, players) != null -> Component.translatable("cobblemon_smartphone.tooltip.social_start_conversation")
            else -> null
        } ?: return
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun findHoveredPlayer(mouseX: Int, mouseY: Int, players: List<PlayerInfo>): PlayerInfo? {
        if (mouseY !in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) return null
        var y = screenY + LIST_START_Y - scrollY
        for (info in players) {
            if (isInRow(mouseX, mouseY, y)) return info
            y += ROW_HEIGHT + ROW_GAP
        }
        return null
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    companion object {
        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        // Lit screen is x 20..191, y 24..194.
        private const val CONTENT_X = 20
        private const val CONTENT_WIDTH = 171
        private const val TITLE_Y = 27
        private const val LIST_START_Y = 41
        private const val LIST_END_Y = 192

        private const val ROW_HEIGHT = 18
        private const val ROW_GAP = 2
        private const val ROW_PAD = 4
        private const val HEAD_SIZE = 10
        private const val SCROLL_SPEED = 12

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val MUTED_COLOR = 0xFF8AA5AD.toInt()
        private const val ACCENT_COLOR = 0xFF3A96B6.toInt()
        private const val CONTENT_TEXT = 0xFF1A1A2E.toInt()
        private const val ROW_BG_COLOR = 0xFFEFFDFF.toInt()
        private const val ROW_HOVER_COLOR = 0xFFD0E8F5.toInt()

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
    }
}
