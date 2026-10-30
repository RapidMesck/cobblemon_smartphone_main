package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.systems.RenderSystem
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialMute
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveSocialMutePacket
import com.nbp.cobblemon_smartphone.network.packet.VotePollPacket
import com.nbp.cobblemon_smartphone.network.packet.ReportContentPacket
import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.social.SocialPollView
import com.nbp.cobblemon_smartphone.social.DmThreadSummary
import com.nbp.cobblemon_smartphone.social.SocialNotificationType
import com.nbp.cobblemon_smartphone.social.SocialNotificationView
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRenderer
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

/**
 * The Social feed. Runs on the large screen (211x207) like PokeInfo, since a feed does not fit the
 * 131px phone frame.
 *
 * Post cards have variable height (280-char posts wrap to several lines and an attachment adds a
 * block), so the list measures each card instead of assuming a fixed row height.
 */
class SocialScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null,
    startOnDms: Boolean = false
) : Screen(Component.literal("Social")) {

    private enum class Tab { FEED, DMS, NOTIFICATIONS }

    private var tab = if (startOnDms) Tab.DMS else Tab.FEED
    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0
    private var pokemonPopup: com.nbp.cobblemon_smartphone.social.PokemonAttachment? = null
    private val posableState = FloatingState()

    private val theme get() = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
    private val TITLE_COLOR get() = theme.colors.textPrimary
    private val NAME_COLOR get() = theme.colors.textPrimary
    private val TEXT_COLOR get() = theme.colors.textSecondary
    private val MUTED_COLOR get() = theme.colors.textMuted
    private val SECTION_TITLE_BG get() = theme.colors.primary
    private val SECTION_CONTENT_BG get() = theme.colors.surface
    private val CONTENT_TEXT get() = theme.colors.textDark
    private val CONTENT_DIM get() = theme.colors.textDarkMuted
    private val ROW_HOVER_BG get() = theme.colors.surfaceHover
    private val BUTTON_BORDER_COLOR get() = theme.colors.primaryBorder
    private val UNREAD_BADGE_COLOR get() = theme.colors.danger
    private val ACCENT_COLOR get() = theme.colors.primary
    private val SHINY_COLOR get() = theme.colors.gold
    private val DANGER_COLOR get() = theme.colors.danger

    /** Cached per post id so we don't rebuild a RenderablePokemon every frame. */
    private val renderableCache = mutableMapOf<Long, RenderablePokemon?>()

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color
        if (SocialFeedCache.isEmpty()) {
            SocialFeedCache.refresh()
        }
        if (tab == Tab.DMS) {
            SocialDmCache.refreshThreads()
        }
        if (tab == Tab.NOTIFICATIONS) {
            SocialNotificationCache.refresh()
        }
        SocialNotificationCache.refresh()
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        val theme = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
        SmartphoneThemeRenderer.renderLargeTheme(guiGraphics, theme, screenX, screenY)
        blitk(matrixStack = matrices, texture = theme.getLargeFrameTexture(color.getLargeScreenTexture()), x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)

        renderBackButton(guiGraphics, mouseX, mouseY)
        SmartphoneBatteryRenderer.render(guiGraphics, screenX, screenY, isLargeScreen = true, smartphoneStack)
        renderHeader(guiGraphics, mouseX, mouseY)

        guiGraphics.enableScissor(
            screenX + CONTENT_X,
            screenY + LIST_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + LIST_END_Y
        )

        when (tab) {
            Tab.FEED -> renderFeed(guiGraphics, mouseX, mouseY)
            Tab.DMS -> renderThreadList(guiGraphics, mouseX, mouseY)
            Tab.NOTIFICATIONS -> renderNotifications(guiGraphics, mouseX, mouseY)
        }

        guiGraphics.disableScissor()

        maxScroll = (contentHeight() - (LIST_END_Y - LIST_START_Y)).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(0, maxScroll)
        renderScrollbar(guiGraphics)
        renderBottomNav(guiGraphics, mouseX, mouseY)

        pokemonPopup?.let {
            RenderSystem.disableDepthTest()
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(0f, 0f, POPUP_Z)
            renderPokemonPopup(guiGraphics, it, mouseX, mouseY)
            guiGraphics.pose().popPose()
            RenderSystem.enableDepthTest()
        }

        if (pokemonPopup == null) renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun renderFeed(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        var y = screenY + LIST_START_Y - scrollY
        val posts = SocialFeedCache.posts()
        posts.forEachIndexed { index, post ->
            val height = measureCard(post)
            if (index > 0) {
                val divY = y - 1
                guiGraphics.fill(screenX + CONTENT_X + 4, divY, screenX + CONTENT_X + CONTENT_WIDTH - 4, divY + 1, DIVIDER_COLOR)
            }
            // Skip cards entirely outside the viewport.
            if (y + height >= screenY + LIST_START_Y && y <= screenY + LIST_END_Y) {
                renderCard(guiGraphics, post, screenX + CONTENT_X, y, mouseX, mouseY, height)
            }
            y += height + CARD_GAP
        }

        if (posts.isEmpty()) {
            val emptyMsg = if (SocialFeedCache.followingOnly) lang("empty_following") else lang("empty")
            renderEmptyState(guiGraphics, if (SocialFeedCache.loading) lang("loading") else emptyMsg, SocialFeedCache.loading)
        }
    }

    private fun renderThreadList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val threads = SocialDmCache.threads()
        if (threads.isEmpty()) {
            renderEmptyState(guiGraphics, if (SocialDmCache.loading) lang("loading") else lang("no_threads"), SocialDmCache.loading)
            return
        }

        var y = screenY + LIST_START_Y - scrollY
        threads.forEach { thread ->
            if (y + THREAD_ROW_HEIGHT >= screenY + LIST_START_Y && y <= screenY + LIST_END_Y) {
                renderThreadRow(guiGraphics, thread, y, mouseX, mouseY)
            }
            y += THREAD_ROW_HEIGHT + CARD_GAP
        }
    }

    private fun renderThreadRow(guiGraphics: GuiGraphics, thread: DmThreadSummary, y: Int, mouseX: Int, mouseY: Int) {
        val x = screenX + CONTENT_X
        val hovered = isInThreadRow(mouseX, mouseY, y)
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, THREAD_ROW_HEIGHT, hovered, colors = theme.colors)

        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(thread.otherUuid, thread.otherName, thread.skinUrl),
            x + CARD_PAD,
            y + CARD_PAD,
            HEAD_SIZE
        )

        val textX = x + CARD_PAD + HEAD_SIZE + 4
        guiGraphics.drawString(font, thread.otherName, textX, y + CARD_PAD, CONTENT_TEXT, false)
        val preview = font.plainSubstrByWidth(thread.preview, CONTENT_WIDTH - (textX - x) - CARD_PAD - 24)
        guiGraphics.drawString(font, preview, textX, y + CARD_PAD + 11, CONTENT_DIM, false)

        if (thread.unreadCount > 0) {
            val label = thread.unreadCount.toString()
            val badgeW = font.width(label) + 4
            val badgeX = x + CONTENT_WIDTH - CARD_PAD - badgeW
            guiGraphics.fill(badgeX, y + CARD_PAD, badgeX + badgeW, y + CARD_PAD + 10, UNREAD_BADGE_COLOR)
            guiGraphics.drawString(font, label, badgeX + 2, y + CARD_PAD + 1, NAME_COLOR, false)
        } else {
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.MESSAGE, x + CONTENT_WIDTH - 14, y + 10, MUTED_COLOR)
        }
    }

    private fun renderNotifications(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val notifications = SocialNotificationCache.notifications()
        if (notifications.isEmpty()) {
            renderEmptyState(guiGraphics, if (SocialNotificationCache.loading) lang("loading") else lang("no_notifications"), SocialNotificationCache.loading)
            return
        }

        var y = screenY + LIST_START_Y - scrollY
        val topH = 14
        if (y + topH >= screenY + LIST_START_Y && y <= screenY + LIST_END_Y) {
            val unread = SocialNotificationCache.unreadCount
            val summary = if (unread > 0) "$unread ${lang("new_alerts")}" else lang("notifications")
            guiGraphics.drawString(font, summary, screenX + CONTENT_X + 2, y + 3, CONTENT_DIM, false)

            if (unread > 0) {
                val markLabel = lang("mark_all_read")
                val markW = font.width(markLabel) + 6
                val markX = screenX + CONTENT_X + CONTENT_WIDTH - markW - 2
                val hovered = isInMarkAllRead(mouseX, mouseY, y)
                guiGraphics.fill(markX, y, markX + markW, y + 12, if (hovered) theme.colors.primaryHover else theme.colors.surface)
                guiGraphics.fill(markX, y, markX + markW, y + 1, BUTTON_BORDER_COLOR)
                guiGraphics.fill(markX, y + 11, markX + markW, y + 12, BUTTON_BORDER_COLOR)
                guiGraphics.fill(markX, y, markX + 1, y + 12, BUTTON_BORDER_COLOR)
                guiGraphics.fill(markX + markW - 1, y, markX + markW, y + 12, BUTTON_BORDER_COLOR)
                guiGraphics.drawString(font, markLabel, markX + 3, y + 2, if (hovered) NAME_COLOR else CONTENT_TEXT, false)
            }
        }
        y += 16

        notifications.forEach { notif ->
            if (y + NOTIF_ROW_HEIGHT >= screenY + LIST_START_Y && y <= screenY + LIST_END_Y) {
                renderNotificationRow(guiGraphics, notif, y, mouseX, mouseY)
            }
            y += NOTIF_ROW_HEIGHT + CARD_GAP
        }
    }

    private fun renderNotificationRow(guiGraphics: GuiGraphics, notif: SocialNotificationView, y: Int, mouseX: Int, mouseY: Int) {
        val x = screenX + CONTENT_X
        val hovered = isInNotificationRow(mouseX, mouseY, y)
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, NOTIF_ROW_HEIGHT, hovered, colors = theme.colors)

        if (!notif.read) {
            guiGraphics.fill(x + 1, y + 3, x + 3, y + NOTIF_ROW_HEIGHT - 3, ACCENT_COLOR)
        }

        val faceX = x + 5
        val faceY = y + 6
        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(notif.actorUuid, notif.actorName, notif.skinUrl),
            faceX,
            faceY,
            16
        )

        val badgeX = faceX + 11
        val badgeY = faceY + 11
        guiGraphics.fill(badgeX - 1, badgeY - 1, badgeX + 7, badgeY + 7, theme.colors.surface)
        val icon = when (notif.type) {
            SocialNotificationType.LIKE -> SocialUi.Icon.HEART
            SocialNotificationType.COMMENT -> SocialUi.Icon.MESSAGE
            SocialNotificationType.FOLLOW -> SocialUi.Icon.USER
            SocialNotificationType.MENTION -> SocialUi.Icon.BELL
        }
        val iconColor = when (notif.type) {
            SocialNotificationType.LIKE -> LIKED_COLOR
            SocialNotificationType.COMMENT -> ACCENT_COLOR
            SocialNotificationType.FOLLOW -> theme.colors.gold
            SocialNotificationType.MENTION -> ACCENT_COLOR
        }
        SocialUi.drawIcon(guiGraphics, icon, badgeX, badgeY, iconColor)

        val textX = x + 25
        val age = relativeTime(notif.timestamp)
        val ageW = font.width(age)
        val maxNameW = CONTENT_WIDTH - 25 - ageW - 8
        val trimmedName = font.plainSubstrByWidth(notif.actorName, maxNameW)

        guiGraphics.drawString(font, trimmedName, textX, y + 5, CONTENT_TEXT, false)
        guiGraphics.drawString(font, age, x + CONTENT_WIDTH - ageW - 5, y + 5, CONTENT_DIM, false)

        val desc = when (notif.type) {
            SocialNotificationType.LIKE -> lang("notif_liked_desc")
            SocialNotificationType.COMMENT -> {
                val snippet = if (notif.message.length > 25) notif.message.take(23) + "..." else notif.message
                "\"$snippet\""
            }
            SocialNotificationType.FOLLOW -> lang("notif_followed_desc")
            SocialNotificationType.MENTION -> {
                val snippet = if (notif.message.length > 25) notif.message.take(23) + "..." else notif.message
                "${lang("notif_mentioned_desc")}: \"$snippet\""
            }
        }
        val maxDescW = CONTENT_WIDTH - 25 - 6
        val trimmedDesc = font.plainSubstrByWidth(desc, maxDescW)
        guiGraphics.drawString(font, trimmedDesc, textX, y + 15, CONTENT_DIM, false)
    }

    private fun renderEmptyState(guiGraphics: GuiGraphics, message: String, loading: Boolean = false) {
        val color = if (loading) LOADING_COLOR else MUTED_COLOR
        val icon = when (tab) {
            Tab.FEED -> SocialUi.Icon.FEED
            Tab.DMS -> SocialUi.Icon.MESSAGE
            Tab.NOTIFICATIONS -> SocialUi.Icon.BELL
        }
        SocialUi.drawIcon(
            guiGraphics,
            icon,
            screenX + CONTENT_X + CONTENT_WIDTH / 2 - 4,
            screenY + LIST_START_Y + (LIST_END_Y - LIST_START_Y) / 2 - 15,
            MUTED_COLOR
        )
        guiGraphics.drawString(
            font,
            message,
            screenX + CONTENT_X + (CONTENT_WIDTH - font.width(message)) / 2,
            screenY + LIST_START_Y + (LIST_END_Y - LIST_START_Y) / 2 + 3,
            color,
            false
        )
    }

    // --- Layout ---

    private fun contentHeight(): Int = when (tab) {
        Tab.FEED -> SocialFeedCache.posts().sumOf { measureCard(it) + CARD_GAP }
        Tab.DMS -> SocialDmCache.threads().size * (THREAD_ROW_HEIGHT + CARD_GAP)
        Tab.NOTIFICATIONS -> {
            val notifs = SocialNotificationCache.notifications()
            if (notifs.isEmpty()) 0
            else 16 + notifs.size * (NOTIF_ROW_HEIGHT + CARD_GAP)
        }
    }

    private fun measureCard(post: SocialPostView): Int {
        var height = CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        if (post.text.isNotBlank()) {
            height += wrappedLines(post.text).size * font.lineHeight + TEXT_GAP
        }
        if (post.attachment != null) {
            height += attachmentHeight(post.attachment) + TEXT_GAP
        }
        if (post.photo != null) {
            height += photoDisplaySize(post.photo.width, post.photo.height).second + TEXT_GAP
        }
        if (post.poll != null) {
            height += measurePoll(post.poll) + TEXT_GAP
        }
        height += TEXT_GAP + 1
        height += FOOTER_HEIGHT + CARD_PAD
        return height
    }

    private fun measurePoll(poll: SocialPollView): Int {
        val qLines = font.split(Component.literal(poll.question), CONTENT_WIDTH - CARD_PAD * 2 - 8).size
        val qH = qLines * font.lineHeight + 4
        val optionsH = poll.options.size * 17
        val footH = 10
        return 6 + qH + optionsH + footH
    }

    private fun formatPostText(text: String): Component {
        if (!text.contains('@') && !text.contains('#')) return Component.literal(text)
        val component = net.minecraft.network.chat.MutableComponent.create(net.minecraft.network.chat.contents.PlainTextContents.EMPTY)
        val regex = Regex("""(?<=^|\s)([@#])([a-zA-Z0-9_]{1,24})\b""")
        var lastIdx = 0
        regex.findAll(text).forEach { match ->
            val start = match.range.first
            if (start > lastIdx) {
                component.append(Component.literal(text.substring(lastIdx, start)))
            }
            val prefix = match.groupValues[1]
            val color = if (prefix == "@") ACCENT_COLOR else theme.colors.gold
            component.append(Component.literal(match.value).withStyle { it.withColor(color).withBold(true) })
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            component.append(Component.literal(text.substring(lastIdx)))
        }
        return component
    }

    private fun findClickedHashtag(post: SocialPostView, mx: Int, my: Int, cardX: Int, cardY: Int): String? {
        if (!post.text.contains('#')) return null
        val textX = cardX + CARD_PAD
        var cursorY = cardY + CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        val lines = wrappedLines(post.text)
        val hashtags = com.nbp.cobblemon_smartphone.social.SocialTagHelper.extractHashtags(post.text)
        if (hashtags.isEmpty()) return null

        lines.forEach { line ->
            if (my in cursorY..(cursorY + font.lineHeight) && mx in textX..(textX + CONTENT_WIDTH)) {
                val sb = StringBuilder()
                line.accept { _, _, cp -> sb.append(cp.toChar()); true }
                val lineStr = sb.toString()
                for (tag in hashtags) {
                    if (lineStr.contains("#$tag", ignoreCase = true)) {
                        return tag
                    }
                }
            }
            cursorY += font.lineHeight
        }
        return null
    }

    private fun wrappedLines(text: String) =
        font.split(formatPostText(text), CONTENT_WIDTH - CARD_PAD * 2)

    // --- Rendering ---

    private fun renderBackButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val color = if (isInBackButton(mouseX, mouseY)) theme.colors.gold else theme.colors.textPrimary
        guiGraphics.drawString(font, lang("back"), screenX + BACK_X, screenY + BACK_Y, color, false)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val headerY = screenY + SCREEN_TOP
        val headerH = LIST_START_Y - SCREEN_TOP
        val headerW = SCREEN_RIGHT - SCREEN_LEFT
        val leftX = screenX + SCREEN_LEFT

        guiGraphics.fill(leftX, headerY, leftX + headerW, headerY + headerH, theme.colors.headerNavy)
        guiGraphics.fill(leftX, headerY + headerH - 1, leftX + headerW, headerY + headerH, SEPARATOR_COLOR)

        when (tab) {
            Tab.FEED -> renderFeedHeader(guiGraphics, mouseX, mouseY)
            Tab.DMS -> renderDmsHeader(guiGraphics, mouseX, mouseY)
            Tab.NOTIFICATIONS -> renderNotificationsHeader(guiGraphics, mouseX, mouseY)
        }
    }

    private fun renderFeedHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val y = screenY + SCREEN_TOP + 2
        val followingActive = SocialFeedCache.followingOnly

        // Global Pill
        val (gx, _) = globalFilterPos()
        val gw = globalFilterWidth()
        val gHovered = isInGlobalFilter(mouseX, mouseY)
        val gBg = when {
            !followingActive -> theme.colors.primary
            gHovered -> theme.colors.surfaceHover
            else -> theme.colors.surface
        }
        val gTc = if (!followingActive) NAME_COLOR else CONTENT_DIM
        guiGraphics.fill(gx, y, gx + gw, y + 12, gBg)
        guiGraphics.fill(gx, y, gx + gw, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(gx, y + 11, gx + gw, y + 12, BUTTON_BORDER_COLOR)
        guiGraphics.fill(gx, y, gx + 1, y + 12, BUTTON_BORDER_COLOR)
        guiGraphics.fill(gx + gw - 1, y, gx + gw, y + 12, BUTTON_BORDER_COLOR)
        guiGraphics.drawString(font, lang("feed_global"), gx + 5, y + 2, gTc, false)
        if (!followingActive) guiGraphics.fill(gx + 2, y + 10, gx + gw - 2, y + 11, theme.colors.gold)

        // Following Pill
        val (fx, _) = followingFilterPos()
        val fw = followingFilterWidth()
        val fHovered = isInFollowingFilter(mouseX, mouseY)
        val fBg = when {
            followingActive -> theme.colors.primary
            fHovered -> theme.colors.surfaceHover
            else -> theme.colors.surface
        }
        val fTc = if (followingActive) NAME_COLOR else CONTENT_DIM
        guiGraphics.fill(fx, y, fx + fw, y + 12, fBg)
        guiGraphics.fill(fx, y, fx + fw, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(fx, y + 11, fx + fw, y + 12, BUTTON_BORDER_COLOR)
        guiGraphics.fill(fx, y, fx + 1, y + 12, BUTTON_BORDER_COLOR)
        guiGraphics.fill(fx + fw - 1, y, fx + fw, y + 12, BUTTON_BORDER_COLOR)
        guiGraphics.drawString(font, lang("feed_following"), fx + 5, y + 2, fTc, false)
        if (followingActive) guiGraphics.fill(fx + 2, y + 10, fx + fw - 2, y + 11, theme.colors.gold)

        // Active Hashtag Pill
        val activeTag = SocialFeedCache.hashtag
        val (mx, _) = mutePos()
        if (activeTag != null) {
            val tagLabel = "#$activeTag ✕"
            val tw = font.width(tagLabel) + 8
            val tx = fx + fw + 3
            if (tx + tw < mx - 3) {
                val tHovered = isInHashtagFilter(mouseX, mouseY)
                guiGraphics.fill(tx, y, tx + tw, y + 12, if (tHovered) theme.colors.danger else theme.colors.gold)
                guiGraphics.fill(tx, y, tx + tw, y + 1, BUTTON_BORDER_COLOR)
                guiGraphics.fill(tx, y + 11, tx + tw, y + 12, BUTTON_BORDER_COLOR)
                guiGraphics.fill(tx, y, tx + 1, y + 12, BUTTON_BORDER_COLOR)
                guiGraphics.fill(tx + tw - 1, y, tx + tw, y + 12, BUTTON_BORDER_COLOR)
                guiGraphics.drawString(font, tagLabel, tx + 4, y + 2, 0xFF000000.toInt(), false)
            }
        }

        renderMuteButton(guiGraphics, mouseX, mouseY)
        renderComposeButton(guiGraphics, mouseX, mouseY)
    }

    private fun renderDmsHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val y = screenY + SCREEN_TOP + 2
        val iconX = screenX + SCREEN_LEFT + 4
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.MESSAGE, iconX, y + 2, theme.colors.gold)
        guiGraphics.drawString(font, lang("tab_dms"), iconX + 13, y + 2, NAME_COLOR, false)

        renderMuteButton(guiGraphics, mouseX, mouseY)
        renderComposeButton(guiGraphics, mouseX, mouseY)
    }

    private fun renderNotificationsHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val y = screenY + SCREEN_TOP + 2
        val iconX = screenX + SCREEN_LEFT + 4
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.BELL, iconX, y + 2, theme.colors.gold)
        val unread = SocialNotificationCache.unreadCount
        val title = if (unread > 0) "$unread ${lang("new_alerts")}" else lang("tab_notifications")
        guiGraphics.drawString(font, title, iconX + 13, y + 2, NAME_COLOR, false)

        renderMuteButton(guiGraphics, mouseX, mouseY)
    }

    private fun renderComposeButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val hovered = isInComposeButton(mouseX, mouseY)
        val (bx, by) = composeButtonPos()
        SocialUi.iconButton(guiGraphics, SocialUi.Icon.PLUS, bx, by, COMPOSE_SIZE, hovered, colors = theme.colors)
    }

    /** Do Not Disturb toggle: a bell that gains a red slash when muted. */
    private fun renderMuteButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val (x, y) = mutePos()
        val hovered = isInMuteButton(mouseX, mouseY)
        SocialUi.iconButton(guiGraphics, SocialUi.Icon.BELL, x, y, COMPOSE_SIZE, hovered, SocialMute.muted, colors = theme.colors)
        if (SocialMute.muted) {
            guiGraphics.fill(x + 1, y + 5, x + 11, y + 6, MUTE_SLASH_COLOR)
        }
    }

    private fun renderBottomNav(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val barY = screenY + SCREEN_BOTTOM - BOTTOM_BAR_HEIGHT
        val barX = screenX + SCREEN_LEFT
        val barW = SCREEN_RIGHT - SCREEN_LEFT

        // Dock background and top border
        guiGraphics.fill(barX, barY, barX + barW, barY + BOTTOM_BAR_HEIGHT, theme.colors.headerNavy)
        guiGraphics.fill(barX, barY, barX + barW, barY + 1, BUTTON_BORDER_COLOR)

        val slotW = barW / 5

        for (i in 0..4) {
            val sx = barX + i * slotW
            val sw = if (i == 4) (barX + barW) - sx else slotW
            val hovered = mouseX in sx..(sx + sw) && mouseY in barY..(barY + BOTTOM_BAR_HEIGHT)
            val active = when (i) {
                0 -> tab == Tab.FEED
                2 -> tab == Tab.DMS
                3 -> tab == Tab.NOTIFICATIONS
                else -> false
            }

            if (hovered) {
                guiGraphics.fill(sx, barY + 1, sx + sw, barY + BOTTOM_BAR_HEIGHT, theme.colors.headerNavyLight)
            }

            val icon = when (i) {
                0 -> SocialUi.Icon.FEED
                1 -> SocialUi.Icon.SEARCH
                2 -> SocialUi.Icon.MESSAGE
                3 -> SocialUi.Icon.BELL
                4 -> SocialUi.Icon.USER
                else -> SocialUi.Icon.FEED
            }

            val iconColor = when {
                active -> theme.colors.gold
                hovered -> NAME_COLOR
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
                guiGraphics.fill(iconX + 6, iconY - 1, iconX + 10, iconY + 3, UNREAD_BADGE_COLOR)
                guiGraphics.fill(iconX + 7, iconY, iconX + 9, iconY + 2, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun renderCard(
        guiGraphics: GuiGraphics,
        post: SocialPostView,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        height: Int
    ) {
        val headerH = CARD_PAD + HEADER_HEIGHT
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, height, colors = theme.colors)
        guiGraphics.fill(x + 1, y + 1, x + CONTENT_WIDTH - 1, y + headerH, theme.colors.headerNavyLight)

        var cursorY = y + CARD_PAD
        val textX = x + CARD_PAD

        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(post.authorUuid, post.authorName, post.skinUrl),
            textX,
            cursorY,
            HEAD_SIZE
        )
        guiGraphics.drawString(font, post.authorName, textX + HEAD_SIZE + 4, cursorY + 2, NAME_COLOR, false)

        val age = relativeTime(post.timestamp)
        guiGraphics.drawString(
            font,
            age,
            x + CONTENT_WIDTH - CARD_PAD - font.width(age),
            cursorY + 2,
            NAME_COLOR,
            false
        )
        cursorY += HEADER_HEIGHT

        guiGraphics.fill(textX, cursorY, x + CONTENT_WIDTH - CARD_PAD, cursorY + 1, SEPARATOR_COLOR)
        cursorY += TEXT_GAP + 1

        if (post.text.isNotBlank()) {
            wrappedLines(post.text).forEach { line ->
                guiGraphics.drawString(font, line, textX, cursorY, CONTENT_TEXT, false)
                cursorY += font.lineHeight
            }
            cursorY += TEXT_GAP
        }

        post.attachment?.let { attachment ->
            val attW = CONTENT_WIDTH - CARD_PAD * 2
            val attH = attachmentHeight(attachment)
            guiGraphics.fill(textX, cursorY, textX + attW, cursorY + attH, ATTACHMENT_BG_COLOR)
            guiGraphics.fill(textX, cursorY, textX + attW, cursorY + 1, BUTTON_BORDER_COLOR)
            guiGraphics.fill(textX, cursorY + attH - 1, textX + attW, cursorY + attH, BUTTON_BORDER_COLOR)
            guiGraphics.fill(textX, cursorY, textX + 1, cursorY + attH, BUTTON_BORDER_COLOR)
            guiGraphics.fill(textX + attW - 1, cursorY, textX + attW, cursorY + attH, BUTTON_BORDER_COLOR)
            renderAttachment(guiGraphics, post, attachment, textX, cursorY)
            cursorY += attH + TEXT_GAP
        }

        post.photo?.let { photo ->
            val (photoW, photoH) = photoDisplaySize(photo.width, photo.height)
            val photoX = textX + ((CONTENT_WIDTH - CARD_PAD * 2) - photoW) / 2
            guiGraphics.fill(photoX - 1, cursorY - 1, photoX + photoW + 1, cursorY + photoH + 1, BUTTON_BORDER_COLOR)
            val texture = SocialPhotoClient.texture(photo.id)
            if (texture != null) {
                val textureSize = SocialPhotoClient.dimensions(photo.id) ?: (photo.width to photo.height)
                guiGraphics.blit(
                    texture, photoX, cursorY, photoW, photoH, 0f, 0f,
                    textureSize.first, textureSize.second, textureSize.first, textureSize.second
                )
            } else {
                guiGraphics.fill(photoX, cursorY, photoX + photoW, cursorY + photoH, ATTACHMENT_BG_COLOR)
                val label = lang(if (SocialPhotoClient.isRequested(photo.id)) "photo_loading" else "photo_load")
                val buttonX = photoX + (photoW - PHOTO_LOAD_BUTTON_WIDTH) / 2
                val buttonY = cursorY + (photoH - PHOTO_LOAD_BUTTON_HEIGHT) / 2
                guiGraphics.fill(buttonX, buttonY, buttonX + PHOTO_LOAD_BUTTON_WIDTH, buttonY + PHOTO_LOAD_BUTTON_HEIGHT, BUTTON_BORDER_COLOR)
                guiGraphics.fill(buttonX + 1, buttonY + 1, buttonX + PHOTO_LOAD_BUTTON_WIDTH - 1, buttonY + PHOTO_LOAD_BUTTON_HEIGHT - 1, SECTION_CONTENT_BG)
                val trimmed = font.plainSubstrByWidth(label, PHOTO_LOAD_BUTTON_WIDTH - 6)
                guiGraphics.drawString(font, trimmed, buttonX + (PHOTO_LOAD_BUTTON_WIDTH - font.width(trimmed)) / 2, buttonY + 3, CONTENT_TEXT, false)
            }
            cursorY += photoH + TEXT_GAP
        }

        post.poll?.let { poll ->
            val pollH = measurePoll(poll)
            renderPoll(guiGraphics, post, poll, textX, cursorY, mouseX, mouseY)
            cursorY += pollH + TEXT_GAP
        }

        val footerY = y + height - CARD_PAD - FOOTER_HEIGHT
        guiGraphics.fill(textX, footerY - TEXT_GAP - 1, x + CONTENT_WIDTH - CARD_PAD, footerY - TEXT_GAP, SEPARATOR_COLOR)

        val heartHovered = isInLikeButton(mouseX, mouseY, x, y, height)
        val heartColor = when {
            post.likedByMe -> LIKED_COLOR
            heartHovered -> ACCENT_COLOR
            else -> CONTENT_DIM
        }
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.HEART, textX + 1, footerY + 3, heartColor)
        guiGraphics.drawString(font, post.likeCount.toString(), textX + 12, footerY + 3, heartColor, false)

        val likeW = 12 + font.width(post.likeCount.toString())
        val commentX = textX + likeW + 8
        val commentHovered = isInCommentButton(mouseX, mouseY, x, y, height, likeW)
        val commentColor = if (commentHovered) ACCENT_COLOR else CONTENT_DIM
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.MESSAGE, commentX, footerY + 3, commentColor)
        guiGraphics.drawString(font, post.commentCount.toString(), commentX + 11, footerY + 3, commentColor, false)

        if (canDelete(post)) {
            val label = lang("delete")
            val hovered = isInDeleteButton(mouseX, mouseY, x, y, height)
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.TRASH, x + CONTENT_WIDTH - CARD_PAD - font.width(label) - 10, footerY + 1, if (hovered) DANGER_COLOR else CONTENT_DIM)
            guiGraphics.drawString(
                font,
                label,
                x + CONTENT_WIDTH - CARD_PAD - font.width(label),
                footerY + 3,
                if (hovered) DANGER_COLOR else CONTENT_DIM,
                false
            )
        } else {
            val reportHovered = isInReportButton(mouseX, mouseY, x, y, height)
            val flagX = x + CONTENT_WIDTH - CARD_PAD - 12
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.FLAG, flagX, footerY + 2, if (reportHovered) DANGER_COLOR else CONTENT_DIM)
        }
    }

    private fun renderPoll(
        guiGraphics: GuiGraphics,
        post: SocialPostView,
        poll: SocialPollView,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val w = CONTENT_WIDTH - CARD_PAD * 2
        val h = measurePoll(poll)
        guiGraphics.fill(x, y, x + w, y + h, SECTION_CONTENT_BG)
        guiGraphics.fill(x, y, x + w, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y + h - 1, x + w, y + h, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y + 1, x + 1, y + h, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x + w - 1, y, x + w, y + h, BUTTON_BORDER_COLOR)

        var curY = y + 4
        val qLines = font.split(Component.literal(poll.question), w - 8)
        qLines.forEach { line ->
            guiGraphics.drawString(font, line, x + 4, curY, NAME_COLOR, false)
            curY += font.lineHeight
        }
        curY += 4

        val hasVoted = poll.userVotedOption != null
        val total = poll.totalVotes
        val optW = w - 8
        val optH = 14

        poll.options.forEachIndexed { idx, optText ->
            val optX = x + 4
            val optY = curY
            val isMyVote = poll.userVotedOption == idx
            val count = poll.voteCounts.getOrElse(idx) { 0 }
            val pct = if (total > 0) count.toFloat() / total else 0f
            val pctStr = "${(pct * 100).toInt()}%"

            val hovered = mouseX in optX..(optX + optW) && mouseY in optY..(optY + optH)

            if (!hasVoted) {
                // Interactive button state
                val bg = if (hovered) theme.colors.primaryHover else SECTION_CONTENT_BG
                guiGraphics.fill(optX, optY, optX + optW, optY + optH, bg)
                guiGraphics.fill(optX, optY, optX + optW, optY + 1, BUTTON_BORDER_COLOR)
                guiGraphics.fill(optX, optY + optH - 1, optX + optW, optY + optH, BUTTON_BORDER_COLOR)
                guiGraphics.fill(optX, optY, optX + 1, optY + optH, BUTTON_BORDER_COLOR)
                guiGraphics.fill(optX + optW - 1, optY, optX + optW, optY + optH, BUTTON_BORDER_COLOR)

                val label = "○ $optText"
                val trimmed = font.plainSubstrByWidth(label, optW - 8)
                guiGraphics.drawString(font, trimmed, optX + 4, optY + 3, if (hovered) NAME_COLOR else CONTENT_TEXT, false)
            } else {
                // Result bar state
                guiGraphics.fill(optX, optY, optX + optW, optY + optH, SECTION_CONTENT_BG)
                val fillW = ((optW - 2) * pct).toInt()
                if (fillW > 0) {
                    val barColor = if (isMyVote) theme.colors.primary else 0x33FFFFFF
                    guiGraphics.fill(optX + 1, optY + 1, optX + 1 + fillW, optY + optH - 1, barColor)
                }
                val borderColor = if (isMyVote) theme.colors.gold else BUTTON_BORDER_COLOR
                guiGraphics.fill(optX, optY, optX + optW, optY + 1, borderColor)
                guiGraphics.fill(optX, optY + optH - 1, optX + optW, optY + optH, borderColor)
                guiGraphics.fill(optX, optY, optX + 1, optY + optH, borderColor)
                guiGraphics.fill(optX + optW - 1, optY, optX + optW, optY + optH, borderColor)

                val prefix = if (isMyVote) "● " else "○ "
                val rightText = "$pctStr ($count)"
                val rightW = font.width(rightText)
                val maxOptW = optW - rightW - 12
                val trimmedOpt = font.plainSubstrByWidth(prefix + optText, maxOptW)

                guiGraphics.drawString(font, trimmedOpt, optX + 4, optY + 3, if (isMyVote) NAME_COLOR else CONTENT_TEXT, false)
                guiGraphics.drawString(font, rightText, optX + optW - rightW - 4, optY + 3, if (isMyVote) theme.colors.gold else CONTENT_DIM, false)
            }

            curY += 17
        }

        val totalVotesText = "${poll.totalVotes} ${lang("poll_votes")}"
        guiGraphics.drawString(font, totalVotesText, x + 4, curY + 1, CONTENT_DIM, false)
    }

    private fun findClickedPollOption(post: SocialPostView, mx: Int, my: Int, cardX: Int, cardY: Int): Int? {
        val poll = post.poll ?: return null
        val w = CONTENT_WIDTH - CARD_PAD * 2
        var cursorY = cardY + CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        if (post.text.isNotBlank()) {
            cursorY += wrappedLines(post.text).size * font.lineHeight + TEXT_GAP
        }
        if (post.attachment != null) {
            cursorY += attachmentHeight(post.attachment) + TEXT_GAP
        }
        if (post.photo != null) {
            cursorY += photoDisplaySize(post.photo.width, post.photo.height).second + TEXT_GAP
        }

        val qLines = font.split(Component.literal(poll.question), w - 8).size
        val optStartY = cursorY + 4 + qLines * font.lineHeight + 4
        val optX = cardX + CARD_PAD + 4
        val optW = w - 8
        val optH = 14

        poll.options.indices.forEach { idx ->
            val optY = optStartY + idx * 17
            if (mx in optX..(optX + optW) && my in optY..(optY + optH)) {
                return idx
            }
        }
        return null
    }

    private fun renderAttachment(
        guiGraphics: GuiGraphics,
        post: SocialPostView,
        attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment,
        x: Int,
        y: Int
    ) {
        val renderable = renderableCache.getOrPut(post.id) {
            val species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(attachment.species))
            species?.let { RenderablePokemon(it, attachment.aspects) }
        }

        val modelBoxX = x + MODEL_BOX_X
        val modelBoxY = y + MODEL_BOX_Y
        renderPokeballWatermark(guiGraphics, modelBoxX, modelBoxY)

        // Entity models can submit geometry outside the regular GUI depth order. Do not submit
        // them while the modal is visible, which guarantees no body part can cross the popup.
        if (renderable != null && pokemonPopup == null) {
            guiGraphics.enableScissor(
                modelBoxX + 1,
                modelBoxY + 1,
                modelBoxX + MODEL_BOX_WIDTH - 1,
                modelBoxY + MODEL_BOX_HEIGHT - 1
            )
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate((x + MODEL_X).toDouble(), (y + MODEL_Y).toDouble(), 0.0)
            drawProfilePokemon(
                renderable, matrices, Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                PoseType.PROFILE, posableState, 0f, MODEL_SCALE
            )
            matrices.popPose()
            guiGraphics.disableScissor()
        }

        if (attachment.aspects.contains(SHINY_ASPECT)) {
            guiGraphics.drawString(font, SHINY_ICON, modelBoxX + 2, modelBoxY + 2, SHINY_COLOR, false)
        }

        val speciesLabel = speciesName(attachment.species)
        val label = attachment.nickname?.let { "$it ($speciesLabel)" } ?: speciesLabel
        val infoX = x + ATTACHMENT_TEXT_X
        val compactLabel = font.plainSubstrByWidth(label, (ATTACHMENT_TEXT_WIDTH / ATTACHMENT_TEXT_SCALE).toInt())
        drawAttachmentText(guiGraphics, compactLabel, infoX.toFloat(), (y + 5).toFloat(), CONTENT_TEXT)
        drawAttachmentText(guiGraphics, "Lv. ${attachment.level}", infoX.toFloat(), (y + 15).toFloat(), CONTENT_DIM)
        renderAttachmentStatusIcons(guiGraphics, attachment, x, y)
        renderTrainingInfoButton(guiGraphics, attachment, x, y)
    }

    private fun renderTrainingInfoButton(
        guiGraphics: GuiGraphics,
        attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment,
        x: Int,
        y: Int
    ) {
        if (!hasPokemonDetails(attachment)) return
        val bx = x + INFO_BUTTON_X
        val by = y + INFO_BUTTON_Y
        guiGraphics.fill(bx, by, bx + INFO_BUTTON_WIDTH, by + INFO_BUTTON_HEIGHT, BUTTON_BORDER_COLOR)
        guiGraphics.fill(bx + 1, by + 1, bx + INFO_BUTTON_WIDTH - 1, by + INFO_BUTTON_HEIGHT - 1, SECTION_CONTENT_BG)
        val label = lang("show_more")
        drawCenteredAttachmentText(
            guiGraphics,
            label,
            (bx + INFO_BUTTON_WIDTH / 2f),
            (by + 2).toFloat(),
            CONTENT_TEXT
        )
    }

    private fun renderPokeballWatermark(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val frame = ((System.currentTimeMillis() / POKEBALL_FRAME_TIME_MS) % POKEBALL_FRAME_COUNT).toInt()
        RenderSystem.setShaderColor(POKEBALL_TINT_R, POKEBALL_TINT_G, POKEBALL_TINT_B, POKEBALL_TINT_ALPHA)
        guiGraphics.blit(
            theme.getSocialPokeballTexture(),
            x + 1,
            y + 1,
            MODEL_BOX_WIDTH - 2,
            MODEL_BOX_HEIGHT - 2,
            0f,
            (frame * POKEBALL_FRAME_SIZE).toFloat(),
            POKEBALL_FRAME_SIZE,
            POKEBALL_FRAME_SIZE,
            POKEBALL_TEXTURE_WIDTH,
            POKEBALL_TEXTURE_HEIGHT
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    private fun drawAttachmentText(guiGraphics: GuiGraphics, text: String, x: Float, y: Float, color: Int) {
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(x, y, 0f)
        matrices.scale(ATTACHMENT_TEXT_SCALE, ATTACHMENT_TEXT_SCALE, 1f)
        guiGraphics.drawString(font, text, 0, 0, color, false)
        matrices.popPose()
    }

    private fun drawCenteredAttachmentText(guiGraphics: GuiGraphics, text: String, centerX: Float, y: Float, color: Int) {
        val scaledWidth = font.width(text) * ATTACHMENT_TEXT_SCALE
        drawAttachmentText(guiGraphics, text, centerX - scaledWidth / 2f, y, color)
    }

    private fun renderAttachmentStatusIcons(
        guiGraphics: GuiGraphics,
        attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment,
        x: Int,
        y: Int
    ) {
        var right = x + ATTACHMENT_WIDTH - ATTACHMENT_INNER_PAD
        val genderIcon = when (attachment.gender?.uppercase()) {
            "MALE" -> MALE_ICON to GENDER_MALE_COLOR
            "FEMALE" -> FEMALE_ICON to GENDER_FEMALE_COLOR
            else -> null
        }
        genderIcon?.let { (icon, color) ->
            right -= font.width(icon)
            guiGraphics.drawString(font, icon, right, y + 4, color, false)
        }
    }

    private fun attachmentHeight(attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment): Int =
        ATTACHMENT_HEIGHT

    private fun hasPokemonDetails(attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment): Boolean =
        attachment.ivs.size == STAT_COUNT ||
                attachment.evs.size == STAT_COUNT ||
                attachment.ability != null ||
                attachment.nature != null ||
                attachment.types.isNotEmpty()

    private fun renderPokemonPopup(
        guiGraphics: GuiGraphics,
        attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment,
        mouseX: Int,
        mouseY: Int
    ) {
        guiGraphics.fill(
            screenX + SCREEN_LEFT,
            screenY + SCREEN_TOP,
            screenX + SCREEN_RIGHT,
            screenY + SCREEN_BOTTOM,
            POPUP_OVERLAY_COLOR
        )
        val px = popupX()
        val py = popupY()
        val popupHeight = popupHeight(attachment)
        SocialUi.surface(guiGraphics, px, py, POPUP_WIDTH, popupHeight, colors = theme.colors)
        guiGraphics.fill(px + 1, py + 1, px + POPUP_WIDTH - 1, py + POPUP_HEADER_HEIGHT, theme.colors.headerNavyLight)

        val title = lang("pokemon_details")
        guiGraphics.drawString(font, title, px + POPUP_PAD, py + 5, NAME_COLOR, false)
        val closeColor = if (isInPopupClose(mouseX, mouseY)) LIKED_COLOR else NAME_COLOR
        guiGraphics.drawString(font, "×", px + POPUP_WIDTH - POPUP_PAD - font.width("×"), py + 5, closeColor, false)

        val speciesLabel = speciesName(attachment.species)
        val pokemonLabel = attachment.nickname?.let { "$it ($speciesLabel)" } ?: speciesLabel
        drawAttachmentText(guiGraphics, pokemonLabel, (px + POPUP_PAD).toFloat(), (py + 21).toFloat(), CONTENT_TEXT)

        val allOptionalDetails = attachment.ivs.size == STAT_COUNT &&
                attachment.evs.size == STAT_COUNT &&
                attachment.ability != null &&
                attachment.nature != null
        var leftInfoY = py + 32

        if (attachment.types.isNotEmpty()) {
            val typeNames = attachment.types.joinToString("/") { type ->
                Component.translatable("cobblemon.type.${type.lowercase()}").string
            }
            val types = "${lang("types")}: $typeNames"
            val width = if (allOptionalDetails) POPUP_LEFT_INFO_WIDTH else POPUP_FULL_INFO_WIDTH
            val trimmed = font.plainSubstrByWidth(types, (width / ATTACHMENT_TEXT_SCALE).toInt())
            drawAttachmentText(guiGraphics, trimmed, (px + POPUP_PAD).toFloat(), leftInfoY.toFloat(), CONTENT_TEXT)
            leftInfoY += POPUP_INFO_LINE_HEIGHT
        }

        attachment.ability?.let { abilityKey ->
            val ability = "${lang("ability")}: ${Component.translatable(abilityKey).string}"
            val width = if (allOptionalDetails) POPUP_LEFT_INFO_WIDTH else POPUP_FULL_INFO_WIDTH
            val trimmed = font.plainSubstrByWidth(ability, (width / ATTACHMENT_TEXT_SCALE).toInt())
            drawAttachmentText(guiGraphics, trimmed, (px + POPUP_PAD).toFloat(), leftInfoY.toFloat(), CONTENT_TEXT)
            leftInfoY += POPUP_INFO_LINE_HEIGHT
        }

        attachment.nature?.let { natureKey ->
            val nature = "${lang("nature")}: ${Component.translatable(natureKey).string}"
            if (allOptionalDetails) {
                val trimmed = font.plainSubstrByWidth(nature, (POPUP_RIGHT_INFO_WIDTH / ATTACHMENT_TEXT_SCALE).toInt())
                drawAttachmentText(guiGraphics, trimmed, (px + POPUP_RIGHT_INFO_X).toFloat(), (py + 32).toFloat(), CONTENT_TEXT)
            } else {
                val trimmed = font.plainSubstrByWidth(nature, (POPUP_FULL_INFO_WIDTH / ATTACHMENT_TEXT_SCALE).toInt())
                drawAttachmentText(guiGraphics, trimmed, (px + POPUP_PAD).toFloat(), leftInfoY.toFloat(), CONTENT_TEXT)
            }
        }

        val hasIvs = attachment.ivs.size == STAT_COUNT
        val hasEvs = attachment.evs.size == STAT_COUNT
        val infoBottomY = py + POPUP_INFO_START_Y + popupInfoRows(attachment) * POPUP_INFO_LINE_HEIGHT
        val statsHeaderY = infoBottomY + POPUP_STATS_GAP
        if (hasIvs || hasEvs) {
            val statStartX = px + POPUP_STAT_VALUES_X
            STAT_LABELS.forEachIndexed { index, stat ->
                drawCenteredAttachmentText(
                    guiGraphics,
                    stat,
                    (statStartX + index * POPUP_STAT_COLUMN_WIDTH).toFloat(),
                    statsHeaderY.toFloat(),
                    STAT_COLORS[index]
                )
            }
        }
        var rowY = statsHeaderY + POPUP_STAT_HEADER_GAP
        if (hasIvs) {
            drawPopupTrainingRow(guiGraphics, "IV", attachment.ivs, px, rowY)
            rowY += POPUP_STAT_ROW_HEIGHT
        }
        if (hasEvs) {
            drawPopupTrainingRow(guiGraphics, "EV", attachment.evs, px, rowY)
        }
    }

    private fun drawPopupTrainingRow(guiGraphics: GuiGraphics, label: String, values: List<Int>, x: Int, y: Int) {
        drawAttachmentText(guiGraphics, label, (x + POPUP_PAD).toFloat(), y.toFloat(), CONTENT_TEXT)
        val statStartX = x + POPUP_STAT_VALUES_X
        values.take(STAT_COUNT).forEachIndexed { index, value ->
            drawCenteredAttachmentText(
                guiGraphics,
                value.toString(),
                (statStartX + index * POPUP_STAT_COLUMN_WIDTH).toFloat(),
                y.toFloat(),
                STAT_COLORS[index]
            )
        }
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics) {
        if (maxScroll <= 0) return
        val trackX = scrollbarX()
        val trackY = screenY + LIST_START_Y
        val trackH = LIST_END_Y - LIST_START_Y
        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0x20FFFFFF)

        val handleH = (trackH * trackH / (trackH + maxScroll)).coerceAtLeast(10)
        val handleY = trackY + (trackH - handleH) * scrollY / maxScroll
        guiGraphics.fill(
            trackX,
            handleY,
            trackX + SCROLLBAR_WIDTH,
            handleY + handleH,
            if (draggingScrollbar) 0x90FFFFFF.toInt() else 0x60FFFFFF
        )
    }

    // --- Input ---

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (pokemonPopup != null) {
            if (isInPopupClose(mx, my) || !isInPopup(mx, my)) {
                playClickSound()
                pokemonPopup = null
            }
            return true
        }

        if (isInBackButton(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SmartphoneScreen(color, smartphoneStack))
            return true
        }

        val navIdx = isInBottomNav(mx, my)
        if (navIdx != null) {
            playClickSound()
            when (navIdx) {
                0 -> {
                    switchTab(Tab.FEED)
                    scrollY = 0
                }
                1 -> {
                    Minecraft.getInstance().setScreen(DmNewScreen(color, smartphoneStack))
                }
                2 -> {
                    switchTab(Tab.DMS)
                    scrollY = 0
                }
                3 -> {
                    switchTab(Tab.NOTIFICATIONS)
                    scrollY = 0
                }
                4 -> {
                    val me = Minecraft.getInstance().player
                    if (me != null) {
                        Minecraft.getInstance().setScreen(SocialProfileScreen(color, smartphoneStack, me.uuid, me.gameProfile.name))
                    }
                }
            }
            return true
        }

        // Do Not Disturb toggle: mute/unmute all social alerts, rings and incoming calls.
        if (isInMuteButton(mx, my)) {
            playClickSound()
            SaveSocialMutePacket(SocialMute.toggle()).sendToServer()
            return true
        }

        // The "+" means "new post" on the feed and "new conversation" on DMs.
        if (isInComposeButton(mx, my)) {
            playClickSound()
            val next = if (tab == Tab.FEED) {
                SocialComposeScreen(color, smartphoneStack)
            } else {
                DmNewScreen(color, smartphoneStack)
            }
            Minecraft.getInstance().setScreen(next)
            return true
        }

        if (tab == Tab.FEED) {
            if (isInGlobalFilter(mx, my)) {
                playClickSound()
                SocialFeedCache.setFilter(false)
                scrollY = 0
                return true
            }
            if (isInFollowingFilter(mx, my)) {
                playClickSound()
                SocialFeedCache.setFilter(true)
                scrollY = 0
                return true
            }
            if (isInHashtagFilter(mx, my)) {
                playClickSound()
                SocialFeedCache.clearHashtag()
                scrollY = 0
                return true
            }
        }

        if (tab == Tab.DMS && my in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) {
            var y = screenY + LIST_START_Y - scrollY
            SocialDmCache.threads().forEach { thread ->
                if (isInThreadRow(mx, my, y)) {
                    playClickSound()
                    Minecraft.getInstance().setScreen(
                        DmThreadScreen(color, smartphoneStack, thread.otherUuid, thread.otherName)
                    )
                    return true
                }
                y += THREAD_ROW_HEIGHT + CARD_GAP
            }
            return true
        }

        if (tab == Tab.NOTIFICATIONS && my in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) {
            var y = screenY + LIST_START_Y - scrollY
            if (isInMarkAllRead(mx, my, y)) {
                playClickSound()
                SocialNotificationCache.markAllRead()
                return true
            }
            y += 16
            SocialNotificationCache.notifications().forEach { notif ->
                if (isInNotificationRow(mx, my, y)) {
                    playClickSound()
                    SocialNotificationCache.markRead(notif.id)
                    when (notif.type) {
                        SocialNotificationType.FOLLOW -> {
                            Minecraft.getInstance().setScreen(
                                SocialProfileScreen(color, smartphoneStack, notif.actorUuid, notif.actorName)
                            )
                        }
                        SocialNotificationType.LIKE,
                        SocialNotificationType.COMMENT,
                        SocialNotificationType.MENTION -> {
                            val targetPost = SocialFeedCache.posts().find { it.id == notif.referenceId }
                            if (targetPost != null) {
                                Minecraft.getInstance().setScreen(
                                    SocialPostDetailScreen(color, smartphoneStack, targetPost)
                                )
                            } else {
                                Minecraft.getInstance().setScreen(
                                    SocialProfileScreen(color, smartphoneStack, notif.actorUuid, notif.actorName)
                                )
                            }
                        }
                    }
                    return true
                }
                y += NOTIF_ROW_HEIGHT + CARD_GAP
            }
            return true
        }

        if (isInScrollbar(mx, my)) {
            draggingScrollbar = true
            dragStartMouseY = my
            dragStartScrollY = scrollY
            return true
        }

        if (tab == Tab.FEED && my in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) {
            var y = screenY + LIST_START_Y - scrollY
            SocialFeedCache.posts().forEach { post ->
                val height = measureCard(post)
                val authorHitW = HEAD_SIZE + 4 + font.width(post.authorName)
                if (mx in (screenX + CONTENT_X + CARD_PAD)..(screenX + CONTENT_X + CARD_PAD + authorHitW) &&
                    my in (y + CARD_PAD)..(y + CARD_PAD + HEADER_HEIGHT)) {
                    playClickSound()
                    Minecraft.getInstance().setScreen(SocialProfileScreen(color, smartphoneStack, post.authorUuid, post.authorName))
                    return true
                }
                if (isInPokemonInfoButton(post, mx, my, screenX + CONTENT_X, y)) {
                    playClickSound()
                    pokemonPopup = post.attachment
                    return true
                }
                if (isInPhotoLoadButton(post, mx, my, screenX + CONTENT_X, y)) {
                    playClickSound()
                    post.photo?.let { SocialPhotoClient.request(it.id) }
                    return true
                }
                if (isInLikeButton(mx, my, screenX + CONTENT_X, y, height)) {
                    playClickSound()
                    val liked = SocialFeedCache.toggleLikeLocally(post.id)
                    if (liked != null) LikePostPacket(post.id, liked).sendToServer()
                    return true
                }
                if (canDelete(post) && isInDeleteButton(mx, my, screenX + CONTENT_X, y, height)) {
                    playClickSound()
                    DeletePostPacket(post.id).sendToServer()
                    return true
                }
                if (!canDelete(post) && isInReportButton(mx, my, screenX + CONTENT_X, y, height)) {
                    playClickSound()
                    ReportContentPacket(ReportTargetType.POST, post.id, "Inappropriate content").sendToServer()
                    Minecraft.getInstance().player?.displayClientMessage(
                        Component.translatable("message.nbp.social.report_sent"),
                        false
                    )
                    return true
                }
                val likeW = 12 + font.width(post.likeCount.toString())
                if (isInCommentButton(mx, my, screenX + CONTENT_X, y, height, likeW)) {
                    playClickSound()
                    Minecraft.getInstance().setScreen(SocialPostDetailScreen(color, smartphoneStack, post))
                    return true
                }
                val clickedOption = findClickedPollOption(post, mx, my, screenX + CONTENT_X, y)
                if (clickedOption != null) {
                    playClickSound()
                    VotePollPacket(post.id, clickedOption).sendToServer()
                    return true
                }
                val clickedTag = findClickedHashtag(post, mx, my, screenX + CONTENT_X, y)
                if (clickedTag != null) {
                    playClickSound()
                    SocialFeedCache.setHashtag(clickedTag)
                    scrollY = 0
                    return true
                }
                if (mx in (screenX + CONTENT_X)..(screenX + CONTENT_X + CONTENT_WIDTH) && my in y..(y + height)) {
                    playClickSound()
                    Minecraft.getInstance().setScreen(SocialPostDetailScreen(color, smartphoneStack, post))
                    return true
                }
                y += height + CARD_GAP
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == ESCAPE_KEY && pokemonPopup != null) {
            pokemonPopup = null
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (!draggingScrollbar) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val trackH = LIST_END_Y - LIST_START_Y
        if (maxScroll > 0 && trackH > 0) {
            val delta = (mouseY.toInt() - dragStartMouseY) * maxScroll / trackH
            scrollY = (dragStartScrollY + delta).coerceIn(0, maxScroll)
        }
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        scrollY = (scrollY - (verticalAmount * SCROLL_SPEED).toInt()).coerceIn(0, maxScroll)
        // Pull the next server-owned page once the player reaches the bottom.
        if (tab == Tab.FEED && scrollY >= maxScroll - LOAD_MORE_THRESHOLD) {
            SocialFeedCache.loadMore()
        } else if (tab == Tab.DMS && scrollY >= maxScroll - LOAD_MORE_THRESHOLD) {
            SocialDmCache.loadMoreThreads()
        }
        return true
    }

    // --- Helpers ---

    private fun canDelete(post: SocialPostView): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        // Mirrors the server check; the server re-validates regardless.
        return post.authorUuid == player.uuid || player.hasPermissions(OP_PERMISSION_LEVEL)
    }

    private fun switchTab(next: Tab) {
        if (tab == next) return
        tab = next
        scrollY = 0
        if (next == Tab.DMS) SocialDmCache.refreshThreads()
        if (next == Tab.NOTIFICATIONS) SocialNotificationCache.refresh()
    }

    private fun isInBottomNav(mouseX: Int, mouseY: Int): Int? {
        val barY = screenY + SCREEN_BOTTOM - BOTTOM_BAR_HEIGHT
        val barX = screenX + SCREEN_LEFT
        val barW = SCREEN_RIGHT - SCREEN_LEFT
        if (mouseY !in barY..(barY + BOTTOM_BAR_HEIGHT) || mouseX !in barX..(barX + barW)) return null
        val slotW = barW / 5
        return ((mouseX - barX) / slotW).coerceIn(0, 4)
    }

    private fun isInThreadRow(mouseX: Int, mouseY: Int, rowY: Int): Boolean =
        mouseX in (screenX + CONTENT_X)..(screenX + CONTENT_X + CONTENT_WIDTH) &&
                mouseY in rowY..(rowY + THREAD_ROW_HEIGHT) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)

    private fun isInMarkAllRead(mouseX: Int, mouseY: Int, topY: Int): Boolean {
        if (SocialNotificationCache.unreadCount <= 0) return false
        val markLabel = lang("mark_all_read")
        val markW = font.width(markLabel) + 6
        val markX = screenX + CONTENT_X + CONTENT_WIDTH - markW - 2
        return mouseX in markX..(markX + markW) &&
                mouseY in topY..(topY + 12) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)
    }

    private fun isInNotificationRow(mouseX: Int, mouseY: Int, rowY: Int): Boolean =
        mouseX in (screenX + CONTENT_X)..(screenX + CONTENT_X + CONTENT_WIDTH) &&
                mouseY in rowY..(rowY + NOTIF_ROW_HEIGHT) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)

    private fun feedFilterY(): Int = screenY + SCREEN_TOP + 2

    private fun globalFilterPos(): Pair<Int, Int> =
        screenX + SCREEN_LEFT + 3 to feedFilterY()

    private fun globalFilterWidth(): Int =
        font.width(lang("feed_global")) + 10

    private fun followingFilterPos(): Pair<Int, Int> =
        globalFilterPos().first + globalFilterWidth() + 3 to feedFilterY()

    private fun followingFilterWidth(): Int =
        font.width(lang("feed_following")) + 10

    private fun isInGlobalFilter(mouseX: Int, mouseY: Int): Boolean {
        if (tab != Tab.FEED) return false
        val (x, y) = globalFilterPos()
        val w = globalFilterWidth()
        return mouseX in x..(x + w) && mouseY in y..(y + 12)
    }

    private fun isInFollowingFilter(mouseX: Int, mouseY: Int): Boolean {
        if (tab != Tab.FEED) return false
        val (x, y) = followingFilterPos()
        val w = followingFilterWidth()
        return mouseX in x..(x + w) && mouseY in y..(y + 12)
    }

    private fun isInHashtagFilter(mouseX: Int, mouseY: Int): Boolean {
        if (tab != Tab.FEED) return false
        val tag = SocialFeedCache.hashtag ?: return false
        val (fx, y) = followingFilterPos()
        val fw = followingFilterWidth()
        val tagLabel = "#$tag ✕"
        val tw = font.width(tagLabel) + 8
        val tx = fx + fw + 3
        return mouseX in tx..(tx + tw) && mouseY in y..(y + 12)
    }

    private fun isInReportButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, cardHeight: Int): Boolean {
        val footerY = cardY + cardHeight - CARD_PAD - FOOTER_HEIGHT
        val flagX = cardX + CONTENT_WIDTH - CARD_PAD - 14
        return mouseX in flagX..(cardX + CONTENT_WIDTH - CARD_PAD) && mouseY in (footerY - 1)..(footerY + FOOTER_HEIGHT + 1)
    }

    private fun composeButtonPos(): Pair<Int, Int> =
        screenX + SCREEN_RIGHT - COMPOSE_SIZE - 2 to screenY + SCREEN_TOP + 2

    private fun isInComposeButton(mouseX: Int, mouseY: Int): Boolean {
        if (tab == Tab.NOTIFICATIONS) return false
        val (bx, by) = composeButtonPos()
        return mouseX in bx..(bx + COMPOSE_SIZE) && mouseY in by..(by + COMPOSE_SIZE)
    }

    private fun mutePos(): Pair<Int, Int> {
        val (cx, cy) = composeButtonPos()
        return if (tab == Tab.NOTIFICATIONS) cx to cy else cx - MUTE_GAP - COMPOSE_SIZE to cy
    }

    private fun isInMuteButton(mouseX: Int, mouseY: Int): Boolean {
        val (x, y) = mutePos()
        return mouseX in x..(x + COMPOSE_SIZE) && mouseY in y..(y + COMPOSE_SIZE)
    }

    private fun isInLikeButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, height: Int): Boolean {
        val footerY = cardY + height - CARD_PAD - FOOTER_HEIGHT
        return mouseX in (cardX + CARD_PAD)..(cardX + CARD_PAD + LIKE_HIT_WIDTH) &&
                mouseY in (footerY + 3)..(footerY + 3 + FOOTER_HEIGHT)
    }

    private fun isInCommentButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, height: Int, likeWidth: Int): Boolean {
        val footerY = cardY + height - CARD_PAD - FOOTER_HEIGHT
        val startX = cardX + CARD_PAD + likeWidth + 8
        return mouseX in startX..(startX + 28) &&
                mouseY in (footerY + 1)..(footerY + 3 + FOOTER_HEIGHT)
    }

    private fun isInDeleteButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, height: Int): Boolean {
        val footerY = cardY + height - CARD_PAD - FOOTER_HEIGHT
        val right = cardX + CONTENT_WIDTH - CARD_PAD
        return mouseX in (right - font.width(lang("delete")) - 11)..right &&
                mouseY in (footerY + 3)..(footerY + 3 + FOOTER_HEIGHT)
    }

    private fun attachmentY(post: SocialPostView, cardY: Int): Int {
        var y = cardY + CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        if (post.text.isNotBlank()) {
            y += wrappedLines(post.text).size * font.lineHeight + TEXT_GAP
        }
        return y
    }

    private fun photoY(post: SocialPostView, cardY: Int): Int {
        var y = attachmentY(post, cardY)
        post.attachment?.let { y += attachmentHeight(it) + TEXT_GAP }
        return y
    }

    private fun isInPhotoLoadButton(post: SocialPostView, mouseX: Int, mouseY: Int, cardX: Int, cardY: Int): Boolean {
        val photo = post.photo ?: return false
        if (SocialPhotoClient.texture(photo.id) != null) return false
        val (photoW, photoH) = photoDisplaySize(photo.width, photo.height)
        val photoX = cardX + CARD_PAD + ((CONTENT_WIDTH - CARD_PAD * 2) - photoW) / 2
        val y = photoY(post, cardY)
        val buttonX = photoX + (photoW - PHOTO_LOAD_BUTTON_WIDTH) / 2
        val buttonY = y + (photoH - PHOTO_LOAD_BUTTON_HEIGHT) / 2
        return mouseX in buttonX..(buttonX + PHOTO_LOAD_BUTTON_WIDTH) &&
            mouseY in buttonY..(buttonY + PHOTO_LOAD_BUTTON_HEIGHT)
    }

    private fun isInPokemonInfoButton(post: SocialPostView, mouseX: Int, mouseY: Int, cardX: Int, cardY: Int): Boolean {
        val attachment = post.attachment ?: return false
        if (!hasPokemonDetails(attachment)) return false
        val attachmentX = cardX + CARD_PAD
        val attachmentY = attachmentY(post, cardY)
        val x = attachmentX + INFO_BUTTON_X
        val y = attachmentY + INFO_BUTTON_Y
        return mouseX in x..(x + INFO_BUTTON_WIDTH) && mouseY in y..(y + INFO_BUTTON_HEIGHT)
    }

    private fun popupX(): Int = screenX + CONTENT_X + (CONTENT_WIDTH - POPUP_WIDTH) / 2
    private fun popupY(): Int = screenY + POPUP_Y

    private fun popupInfoRows(attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment): Int {
        val allOptionalDetails = attachment.ivs.size == STAT_COUNT &&
                attachment.evs.size == STAT_COUNT &&
                attachment.ability != null &&
                attachment.nature != null
        var leftRows = 0
        if (attachment.types.isNotEmpty()) leftRows++
        if (attachment.ability != null) leftRows++
        if (attachment.nature != null && !allOptionalDetails) leftRows++
        val rightRows = if (attachment.nature != null && allOptionalDetails) 1 else 0
        return maxOf(leftRows, rightRows)
    }

    private fun popupHeight(attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment): Int {
        val infoBottom = POPUP_INFO_START_Y + popupInfoRows(attachment) * POPUP_INFO_LINE_HEIGHT
        val statRows = (if (attachment.ivs.size == STAT_COUNT) 1 else 0) +
                (if (attachment.evs.size == STAT_COUNT) 1 else 0)
        return if (statRows == 0) {
            (infoBottom + POPUP_BOTTOM_PAD).coerceAtLeast(POPUP_MIN_HEIGHT)
        } else {
            infoBottom + POPUP_STATS_GAP + POPUP_STAT_HEADER_GAP +
                    (statRows - 1) * POPUP_STAT_ROW_HEIGHT + POPUP_STAT_TEXT_HEIGHT + POPUP_BOTTOM_PAD
        }
    }

    private fun isInPopup(mouseX: Int, mouseY: Int): Boolean =
        mouseX in popupX()..(popupX() + POPUP_WIDTH) &&
                mouseY in popupY()..(popupY() + (pokemonPopup?.let(::popupHeight) ?: 0))

    private fun isInPopupClose(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (popupX() + POPUP_WIDTH - 18)..(popupX() + POPUP_WIDTH) &&
                mouseY in popupY()..(popupY() + POPUP_HEADER_HEIGHT)

    /** Sits flush against SCREEN_RIGHT: CONTENT_X + CONTENT_WIDTH + gap + width == SCREEN_RIGHT. */
    private fun scrollbarX(): Int = screenX + CONTENT_X + CONTENT_WIDTH + SCROLLBAR_GAP

    private fun isInScrollbar(mouseX: Int, mouseY: Int): Boolean {
        val trackX = scrollbarX()
        return maxScroll > 0 && mouseX in trackX..(trackX + SCROLLBAR_WIDTH) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)
    }

    private fun speciesName(id: String): String =
        PokemonSpecies.getByIdentifier(ResourceLocation.parse(id))?.name ?: id.substringAfter(':')

    private fun relativeTime(timestamp: Long): String {
        val seconds = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> lang("now")
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3600}h"
            else -> "${seconds / 86_400}d"
        }
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (SmartphoneBatteryRenderer.renderTooltip(font, guiGraphics, screenX, screenY, mouseX, mouseY, isLargeScreen = true, smartphoneStack)) return

        val navIdx = isInBottomNav(mouseX, mouseY)
        if (navIdx != null) {
            val key = when (navIdx) {
                0 -> "cobblemon_smartphone.social.tab_feed"
                1 -> "cobblemon_smartphone.tooltip.social_explore"
                2 -> "cobblemon_smartphone.social.tab_dms"
                3 -> "cobblemon_smartphone.social.tab_notifications"
                4 -> "cobblemon_smartphone.tooltip.social_my_profile"
                else -> ""
            }
            guiGraphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY)
            return
        }

        val tooltip = when {
            isInBackButton(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.back_to_phone")
            isInMuteButton(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_mute")
            isInComposeButton(mouseX, mouseY) -> {
                val key = if (tab == Tab.FEED) "social_new_post" else "social_new_message"
                Component.translatable("cobblemon_smartphone.tooltip.$key")
            }
            tab == Tab.FEED && isInGlobalFilter(mouseX, mouseY) ->
                Component.translatable("cobblemon_smartphone.tooltip.social_feed_global")
            tab == Tab.FEED && isInFollowingFilter(mouseX, mouseY) ->
                Component.translatable("cobblemon_smartphone.tooltip.social_feed_following")
            tab == Tab.FEED && isInHashtagFilter(mouseX, mouseY) ->
                Component.translatable("cobblemon_smartphone.tooltip.social_clear_hashtag")
            tab == Tab.DMS -> findHoveredThread(mouseX, mouseY)?.let {
                Component.translatable("cobblemon_smartphone.tooltip.social_open_conversation")
            }
            tab == Tab.NOTIFICATIONS -> findHoveredNotification(mouseX, mouseY)?.let { notif ->
                val key = when (notif.type) {
                    SocialNotificationType.FOLLOW -> "social_view_profile"
                    SocialNotificationType.LIKE,
                    SocialNotificationType.COMMENT,
                    SocialNotificationType.MENTION -> "social_view_post"
                }
                Component.translatable("cobblemon_smartphone.tooltip.$key")
            }
            tab == Tab.FEED -> findHoveredCardTooltip(mouseX, mouseY)
            else -> null
        } ?: return
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun findHoveredNotification(mouseX: Int, mouseY: Int): SocialNotificationView? {
        if (mouseY !in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) return null
        var y = screenY + LIST_START_Y - scrollY + 16
        for (notif in SocialNotificationCache.notifications()) {
            if (isInNotificationRow(mouseX, mouseY, y)) return notif
            y += NOTIF_ROW_HEIGHT + CARD_GAP
        }
        return null
    }

    private fun findHoveredThread(mouseX: Int, mouseY: Int): DmThreadSummary? {
        if (mouseY !in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) return null
        var y = screenY + LIST_START_Y - scrollY
        for (thread in SocialDmCache.threads()) {
            if (isInThreadRow(mouseX, mouseY, y)) return thread
            y += THREAD_ROW_HEIGHT + CARD_GAP
        }
        return null
    }

    private fun findHoveredCardTooltip(mouseX: Int, mouseY: Int): Component? {
        if (mouseY !in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) return null
        var y = screenY + LIST_START_Y - scrollY
        for (post in SocialFeedCache.posts()) {
            val height = measureCard(post)
            val cardX = screenX + CONTENT_X
            if (isInLikeButton(mouseX, mouseY, cardX, y, height)) {
                val key = if (post.likedByMe) "social_unlike" else "social_like"
                return Component.translatable("cobblemon_smartphone.tooltip.$key")
            }
            val likeW = 12 + font.width(post.likeCount.toString())
            if (isInCommentButton(mouseX, mouseY, cardX, y, height, likeW)) {
                return Component.translatable("cobblemon_smartphone.tooltip.social_comments")
            }
            if (canDelete(post)) {
                if (isInDeleteButton(mouseX, mouseY, cardX, y, height)) {
                    return Component.translatable("cobblemon_smartphone.tooltip.social_delete")
                }
            } else {
                if (isInReportButton(mouseX, mouseY, cardX, y, height)) {
                    return Component.translatable("cobblemon_smartphone.tooltip.social_report")
                }
            }
            y += height + CARD_GAP
        }
        return null
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun photoDisplaySize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return FEED_PHOTO_MAX_WIDTH to FEED_PHOTO_MAX_HEIGHT
        val scale = minOf(FEED_PHOTO_MAX_WIDTH.toDouble() / sourceWidth, FEED_PHOTO_MAX_HEIGHT.toDouble() / sourceHeight)
        return (sourceWidth * scale).toInt().coerceAtLeast(1) to (sourceHeight * scale).toInt().coerceAtLeast(1)
    }

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    private fun isInBackButton(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + BACK_X)..(screenX + BACK_X + 30) &&
                mouseY in (screenY + BACK_Y - 2)..(screenY + BACK_Y + 10)

    companion object {
        private const val ESCAPE_KEY = 256
        private const val OP_PERMISSION_LEVEL = 2
        private const val SHINY_ASPECT = "shiny"

        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207
        private const val BACK_X = 20
        private const val BACK_Y = 14

        // The 211x207 texture is the whole phone including the bezel. The lit screen is only
        // x 20..191 and y 24..194 — anything drawn outside this lands on the frame.
        private const val SCREEN_LEFT = 20
        private const val SCREEN_RIGHT = 191
        private const val SCREEN_TOP = 24
        private const val SCREEN_BOTTOM = 194

        private const val CONTENT_X = SCREEN_LEFT
        // Leaves SCROLLBAR_GAP + SCROLLBAR_WIDTH to the right, landing flush on SCREEN_RIGHT.
        private const val CONTENT_WIDTH = 165
        private const val LIST_START_Y = 40
        private const val BOTTOM_BAR_HEIGHT = 16
        private const val LIST_END_Y = SCREEN_BOTTOM - BOTTOM_BAR_HEIGHT - 2

        private const val COMPOSE_SIZE = 12
        private const val MUTE_GAP = 3
        private const val MUTE_ACTIVE_COLOR = 0xFFAA3333.toInt()
        private const val MUTE_SLASH_COLOR = 0xFFFF3030.toInt()

        private const val THREAD_ROW_HEIGHT = 28
        private const val NOTIF_ROW_HEIGHT = 28
        private const val CARD_PAD = 5
        private const val CARD_GAP = 3
        private const val HEAD_SIZE = 10
        private const val HEADER_HEIGHT = 13
        private const val FOOTER_HEIGHT = 9
        private const val TEXT_GAP = 3
        private const val LIKE_HIT_WIDTH = 24

        private const val ATTACHMENT_HEIGHT = 44
        private const val FEED_PHOTO_MAX_WIDTH = CONTENT_WIDTH - CARD_PAD * 2
        private const val FEED_PHOTO_MAX_HEIGHT = 110
        private const val PHOTO_LOAD_BUTTON_WIDTH = 72
        private const val PHOTO_LOAD_BUTTON_HEIGHT = 14
        private const val ATTACHMENT_WIDTH = CONTENT_WIDTH - CARD_PAD * 2
        private const val ATTACHMENT_INNER_PAD = 5
        private const val ATTACHMENT_TEXT_X = 52
        private const val ATTACHMENT_TEXT_WIDTH = ATTACHMENT_WIDTH - ATTACHMENT_TEXT_X - ATTACHMENT_INNER_PAD - 16
        private const val ATTACHMENT_TEXT_SCALE = 0.75f
        private const val MODEL_BOX_X = 3
        private const val MODEL_BOX_Y = 2
        private const val MODEL_BOX_WIDTH = 40
        private const val MODEL_BOX_HEIGHT = 40
        private const val MODEL_X = 25
        private const val MODEL_Y = 5
        private const val MODEL_SCALE = 18f
        private const val STAT_COUNT = 6
        private const val INFO_BUTTON_X = 52
        private const val INFO_BUTTON_Y = 28
        private const val INFO_BUTTON_WIDTH = 42
        private const val INFO_BUTTON_HEIGHT = 11
        private const val MALE_ICON = "♂"
        private const val FEMALE_ICON = "♀"
        private const val SHINY_ICON = "★"

        private const val POPUP_Y = 62
        private const val POPUP_WIDTH = 151
        private const val POPUP_HEADER_HEIGHT = 16
        private const val POPUP_PAD = 7
        private const val POPUP_INFO_START_Y = 32
        private const val POPUP_LEFT_INFO_WIDTH = 68
        private const val POPUP_FULL_INFO_WIDTH = POPUP_WIDTH - POPUP_PAD * 2
        private const val POPUP_RIGHT_INFO_X = 78
        private const val POPUP_RIGHT_INFO_WIDTH = 66
        private const val POPUP_INFO_LINE_HEIGHT = 10
        private const val POPUP_STATS_GAP = 3
        private const val POPUP_STAT_HEADER_GAP = 11
        private const val POPUP_STAT_ROW_HEIGHT = 10
        private const val POPUP_STAT_TEXT_HEIGHT = 7
        private const val POPUP_BOTTOM_PAD = 7
        private const val POPUP_MIN_HEIGHT = 50
        private const val POPUP_STAT_VALUES_X = 31
        private const val POPUP_STAT_COLUMN_WIDTH = 20

        private const val SCROLLBAR_GAP = 2
        private const val SCROLLBAR_WIDTH = 4
        private const val SCROLL_SPEED = 12
        private const val LOAD_MORE_THRESHOLD = 20

        private const val LOADING_COLOR = 0xFFAAAAAA.toInt()
        private const val SEPARATOR_COLOR = 0x30FFFFFF.toInt()
        private const val DIVIDER_COLOR = 0x15FFFFFF.toInt()
        private const val BUTTON_DISABLED_BG = 0xFFCCCCCC.toInt()
        private const val ATTACHMENT_BG_COLOR = 0x553A96B6
        private const val POPUP_OVERLAY_COLOR = 0x88071322.toInt()
        private const val POPUP_Z = 1_000f
        private const val POKEBALL_FRAME_COUNT = 16L
        private const val POKEBALL_FRAME_SIZE = 109
        private const val POKEBALL_TEXTURE_WIDTH = 109
        private const val POKEBALL_TEXTURE_HEIGHT = POKEBALL_FRAME_SIZE * POKEBALL_FRAME_COUNT.toInt()
        private const val POKEBALL_FRAME_TIME_MS = 280L
        private const val POKEBALL_TINT_R = 1.5f
        private const val POKEBALL_TINT_G = 3.9f
        private const val POKEBALL_TINT_B = 4.7f
        private const val POKEBALL_TINT_ALPHA = 0.12f
        private const val LIKED_COLOR = 0xFFFF5C9A.toInt()
        private const val GENDER_MALE_COLOR = 0xFF408CFF.toInt()
        private const val GENDER_FEMALE_COLOR = 0xFFFF5C9A.toInt()

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
        private val POKEBALL_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/social/background_poke_ball.png"
        )
        private val STAT_LABELS = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        private val STAT_COLORS = listOf(
            0xFF2EAD5B.toInt(), // HP: green
            0xFFE34B4B.toInt(), // Attack: red
            0xFF82C91E.toInt(), // Defence: lime
            0xFFFF8C32.toInt(), // Special Attack: orange
            0xFFE0B928.toInt(), // Special Defence: yellow
            0xFF3F7FE5.toInt()  // Speed: blue
        )
    }
}
