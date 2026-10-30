package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobblemon_smartphone.client.social.FakePlayerFilter
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRenderer
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Directory of trainers on the server. Allows searching online players in real-time,
 * opening direct message conversations, or viewing their full Social profile.
 */
class DmNewScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack?
) : Screen(Component.literal("Trainers")) {

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private lateinit var searchBox: EditBox

    private val theme get() = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
    private val NAME_COLOR get() = theme.colors.textPrimary
    private val MUTED_COLOR get() = theme.colors.textMuted
    private val ACCENT_COLOR get() = theme.colors.primary
    private val CONTENT_TEXT get() = theme.colors.textDark
    private val CONTENT_DIM get() = theme.colors.textDarkMuted
    private val ROW_BG_COLOR get() = theme.colors.surface
    private val ROW_HOVER_COLOR get() = theme.colors.surfaceHover

    override fun isPauseScreen(): Boolean = false

    private var lastCandidatesUpdate = 0L
    private var cachedCandidates: List<PlayerInfo> = emptyList()

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color
        lastCandidatesUpdate = 0L

        searchBox = EditBox(font, screenX + CONTENT_X + 4, screenY + 41, CONTENT_WIDTH - 8, 12, Component.literal(""))
        searchBox.setHint(Component.translatable("cobblemon_smartphone.social.search_trainers"))
        searchBox.setMaxLength(32)
        searchBox.setBordered(false)
        searchBox.setTextColor(theme.colors.textDark)
        addRenderableWidget(searchBox)
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
        val selfName = minecraft.player?.gameProfile?.name
        val connection = minecraft.connection ?: return emptyList()

        val allPlayers = connection.onlinePlayers
        val hasTabFakePlayers = allPlayers.any { FakePlayerFilter.isFakePlayer(it) }

        val source = if (hasTabFakePlayers) allPlayers else connection.listedOnlinePlayers

        val result = source
            .filter {
                it.profile.id != selfId &&
                (selfName == null || !it.profile.name.equals(selfName, ignoreCase = true)) &&
                !FakePlayerFilter.isFakePlayer(it)
            }
            .distinctBy { it.profile.id }
            .sortedBy { it.profile.name.lowercase() }

        cachedCandidates = result
        return result
    }

    private fun filteredCandidates(): List<PlayerInfo> {
        val query = if (::searchBox.isInitialized) searchBox.value.trim() else ""
        val all = candidates()
        if (query.isEmpty()) return all
        return all.filter { it.profile.name.contains(query, ignoreCase = true) }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        val theme = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
        SmartphoneThemeRenderer.renderLargeTheme(guiGraphics, theme, screenX, screenY)
        blitk(matrixStack = matrices, texture = theme.getLargeFrameTexture(color.getLargeScreenTexture()), x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)

        SmartphoneBatteryRenderer.render(guiGraphics, screenX, screenY, isLargeScreen = true, smartphoneStack)

        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + 37, theme.colors.headerNavy)
        val back = lang("back")
        guiGraphics.drawString(
            font,
            back,
            screenX + CONTENT_X + 2,
            screenY + TITLE_Y,
            if (isInBack(mouseX, mouseY)) theme.colors.gold else MUTED_COLOR,
            false
        )
        val title = lang("explore_trainers")
        guiGraphics.drawString(
            font,
            title,
            screenX + CONTENT_X + CONTENT_WIDTH - font.width(title) - 3,
            screenY + TITLE_Y,
            NAME_COLOR,
            false
        )

        val sx = screenX + CONTENT_X + 1
        val sy = screenY + 39
        val sw = CONTENT_WIDTH - 2
        val sh = 16
        guiGraphics.fill(sx, sy, sx + sw, sy + sh, theme.colors.surface)
        guiGraphics.fill(sx, sy, sx + sw, sy + 1, theme.colors.primaryBorder)
        guiGraphics.fill(sx, sy + sh - 1, sx + sw, sy + sh, theme.colors.primaryBorder)
        guiGraphics.fill(sx, sy + 1, sx + 1, sy + sh, theme.colors.primaryBorder)
        guiGraphics.fill(sx + sw - 1, sy, sx + sw, sy + sh, theme.colors.primaryBorder)
        searchBox.render(guiGraphics, mouseX, mouseY, delta)

        guiGraphics.enableScissor(
            screenX + CONTENT_X,
            screenY + LIST_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + LIST_END_Y
        )

        val players = filteredCandidates()
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
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.USER, screenX + CONTENT_X + CONTENT_WIDTH / 2 - 4, screenY + 108, MUTED_COLOR)
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

        renderBottomNav(guiGraphics, mouseX, mouseY)
        renderHoveredTooltip(guiGraphics, mouseX, mouseY, players)
    }

    private fun renderBottomNav(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val barY = screenY + SCREEN_BOTTOM - BOTTOM_BAR_HEIGHT
        val barX = screenX + CONTENT_X
        val barW = CONTENT_WIDTH

        // Dock background and top border
        guiGraphics.fill(barX, barY, barX + barW, barY + BOTTOM_BAR_HEIGHT, theme.colors.headerNavy)
        guiGraphics.fill(barX, barY, barX + barW, barY + 1, theme.colors.primaryBorder)

        val slotW = barW / 4

        for (i in 0..3) {
            val sx = barX + i * slotW
            val sw = if (i == 3) (barX + barW) - sx else slotW
            val hovered = mouseX in sx..(sx + sw) && mouseY in barY..(barY + BOTTOM_BAR_HEIGHT)
            val active = (i == 1) // Discovery is slot 1

            if (hovered) {
                guiGraphics.fill(sx, barY + 1, sx + sw, barY + BOTTOM_BAR_HEIGHT, theme.colors.headerNavyLight)
            }

            val icon = when (i) {
                0 -> SocialUi.Icon.FEED
                1 -> SocialUi.Icon.SEARCH
                2 -> SocialUi.Icon.MESSAGE
                3 -> SocialUi.Icon.BELL
                else -> SocialUi.Icon.FEED
            }

            val iconColor = when {
                active -> theme.colors.gold
                hovered -> theme.colors.textPrimary
                else -> theme.colors.textMuted
            }

            val iconX = sx + (sw - 8) / 2
            val iconY = barY + (BOTTOM_BAR_HEIGHT - 8) / 2 + 1
            SocialUi.drawIcon(guiGraphics, icon, iconX, iconY, iconColor)

            if (active) {
                guiGraphics.fill(sx + 6, barY + BOTTOM_BAR_HEIGHT - 2, sx + sw - 6, barY + BOTTOM_BAR_HEIGHT, theme.colors.gold)
            }

            val hasUnread = when (i) {
                2 -> SocialDmCache.unreadTotal > 0
                3 -> SocialNotificationCache.unreadCount > 0
                else -> false
            }
            if (hasUnread) {
                guiGraphics.fill(iconX + 6, iconY - 1, iconX + 10, iconY + 3, theme.colors.danger)
                guiGraphics.fill(iconX + 7, iconY, iconX + 9, iconY + 2, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun isInBottomNav(mouseX: Int, mouseY: Int): Int? {
        val barY = screenY + SCREEN_BOTTOM - BOTTOM_BAR_HEIGHT
        val barX = screenX + CONTENT_X
        val barW = CONTENT_WIDTH
        if (mouseY !in barY..(barY + BOTTOM_BAR_HEIGHT) || mouseX !in barX..(barX + barW)) return null
        val slotW = barW / 4
        val relX = mouseX - barX
        return (relX / slotW).coerceIn(0, 3)
    }

    private fun renderRow(guiGraphics: GuiGraphics, info: PlayerInfo, y: Int, mouseX: Int, mouseY: Int) {
        val x = screenX + CONTENT_X
        val hovered = isInRow(mouseX, mouseY, y)
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, ROW_HEIGHT, hovered, colors = theme.colors)
        PlayerFaceRenderer.draw(guiGraphics, info.skin, x + ROW_PAD, y + ROW_PAD, HEAD_SIZE)
        guiGraphics.drawString(
            font,
            info.profile.name,
            x + ROW_PAD + HEAD_SIZE + 5,
            y + ROW_PAD + 1,
            CONTENT_TEXT,
            false
        )

        // Online status dot
        guiGraphics.fill(x + CONTENT_WIDTH - 14, y + 7, x + CONTENT_WIDTH - 10, y + 11, theme.colors.success)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInBack(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
            return true
        }

        val navIdx = isInBottomNav(mx, my)
        if (navIdx != null) {
            playClickSound()
            when (navIdx) {
                0 -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
                1 -> scrollY = 0
                2 -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.DMS))
                3 -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.NOTIFICATIONS))
            }
            return true
        }

        if (my in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) {
            var y = screenY + LIST_START_Y - scrollY
            filteredCandidates().forEach { info ->
                if (isInRow(mx, my, y)) {
                    val selfId = Minecraft.getInstance().player?.uuid
                    val selfName = Minecraft.getInstance().player?.gameProfile?.name
                    if (info.profile.id != selfId && (selfName == null || !info.profile.name.equals(selfName, ignoreCase = true))) {
                        playClickSound()
                        Minecraft.getInstance().setScreen(
                            DmThreadScreen(color, smartphoneStack, info.profile.id, info.profile.name)
                        )
                    }
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
        if (SmartphoneBatteryRenderer.renderTooltip(font, guiGraphics, screenX, screenY, mouseX, mouseY, isLargeScreen = true, smartphoneStack)) return

        val navIdx = isInBottomNav(mouseX, mouseY)
        if (navIdx != null) {
            val key = when (navIdx) {
                0 -> "cobblemon_smartphone.social.tab_feed"
                1 -> "cobblemon_smartphone.tooltip.social_explore"
                2 -> "cobblemon_smartphone.social.tab_dms"
                3 -> "cobblemon_smartphone.social.tab_notifications"
                else -> ""
            }
            guiGraphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY)
            return
        }

        val hoveredPlayer = findHoveredPlayer(mouseX, mouseY, players)
        val tooltip = when {
            isInBack(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_back_to_social")
            hoveredPlayer != null -> Component.translatable("cobblemon_smartphone.tooltip.social_start_conversation")
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
        private const val SCREEN_BOTTOM = 194
        private const val BOTTOM_BAR_HEIGHT = 16
        private const val LIST_START_Y = 58
        private const val LIST_END_Y = SCREEN_BOTTOM - BOTTOM_BAR_HEIGHT - 2 // 176

        private const val ROW_HEIGHT = 18
        private const val ROW_GAP = 2
        private const val ROW_PAD = 4
        private const val HEAD_SIZE = 10
        private const val SCROLL_SPEED = 12

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
    }
}
