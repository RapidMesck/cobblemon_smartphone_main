package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialMute
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveSocialMutePacket
import com.nbp.cobblemon_smartphone.social.DmThreadSummary
import com.nbp.cobblemon_smartphone.social.SocialPostView
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

    private enum class Tab { FEED, DMS }

    private var tab = if (startOnDms) Tab.DMS else Tab.FEED
    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0
    private val posableState = FloatingState()

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
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()

        blitk(matrixStack = matrices, texture = frameTexture, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)
        blitk(matrixStack = matrices, texture = SCREEN_TEXTURE, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)

        renderHeader(guiGraphics, mouseX, mouseY)

        guiGraphics.enableScissor(
            screenX + CONTENT_X,
            screenY + LIST_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + LIST_END_Y
        )

        if (tab == Tab.FEED) renderFeed(guiGraphics, mouseX, mouseY) else renderThreadList(guiGraphics, mouseX, mouseY)

        guiGraphics.disableScissor()

        maxScroll = (contentHeight() - (LIST_END_Y - LIST_START_Y)).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(0, maxScroll)
        renderScrollbar(guiGraphics)

        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
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
            renderEmptyState(guiGraphics, if (SocialFeedCache.loading) lang("loading") else lang("empty"), SocialFeedCache.loading)
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
        guiGraphics.fill(x, y, x + CONTENT_WIDTH, y + THREAD_ROW_HEIGHT, if (hovered) ROW_HOVER_BG else SECTION_CONTENT_BG)

        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(thread.otherUuid, thread.otherName),
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
        }
    }

    private fun renderEmptyState(guiGraphics: GuiGraphics, message: String, loading: Boolean = false) {
        val color = if (loading) LOADING_COLOR else MUTED_COLOR
        guiGraphics.drawString(
            font,
            message,
            screenX + CONTENT_X + (CONTENT_WIDTH - font.width(message)) / 2,
            screenY + LIST_START_Y + (LIST_END_Y - LIST_START_Y) / 2 - font.lineHeight / 2,
            color,
            false
        )
    }

    // --- Layout ---

    private fun contentHeight(): Int = when (tab) {
        Tab.FEED -> SocialFeedCache.posts().sumOf { measureCard(it) + CARD_GAP }
        Tab.DMS -> SocialDmCache.threads().size * (THREAD_ROW_HEIGHT + CARD_GAP)
    }

    private fun measureCard(post: SocialPostView): Int {
        var height = CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        if (post.text.isNotBlank()) {
            height += wrappedLines(post.text).size * font.lineHeight + TEXT_GAP
        }
        if (post.attachment != null) {
            height += ATTACHMENT_HEIGHT + TEXT_GAP
        }
        height += TEXT_GAP + 1
        height += FOOTER_HEIGHT + CARD_PAD
        return height
    }

    private fun wrappedLines(text: String) =
        font.split(Component.literal(text), CONTENT_WIDTH - CARD_PAD * 2)

    // --- Rendering ---

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        var tx = screenX + CONTENT_X
        val btnY = screenY + TITLE_Y - TAB_BUTTON_PAD_Y
        val btnH = font.lineHeight + TAB_BUTTON_PAD_Y * 2

        Tab.entries.forEachIndexed { index, entry ->
            val active = tab == entry
            val hovered = isInTab(mouseX, mouseY, index)
            val label = lang(if (entry == Tab.FEED) "tab_feed" else "tab_dms")
            val text = if (entry == Tab.DMS && SocialDmCache.unreadTotal > 0) "$label (${SocialDmCache.unreadTotal})" else label
            val textW = font.width(text)
            val btnW = textW + TAB_PAD * 2
            val bg = when {
                active -> SECTION_TITLE_BG
                hovered -> 0xFF4AA6C2.toInt()
                else -> 0xFF5BA8C8.toInt()
            }
            val tc = when {
                active -> NAME_COLOR
                hovered -> NAME_COLOR
                else -> 0xFFD5E8EF.toInt()
            }
            guiGraphics.fill(tx, btnY, tx + btnW, btnY + btnH, bg)
            guiGraphics.drawString(font, text, tx + TAB_PAD, screenY + TITLE_Y, tc, false)
            tx += btnW + TAB_GAP
        }

        renderMuteButton(guiGraphics, mouseX, mouseY)

        val hovered = isInComposeButton(mouseX, mouseY)
        val (bx, by) = composeButtonPos()
        guiGraphics.fill(bx, by, bx + COMPOSE_SIZE, by + COMPOSE_SIZE, BUTTON_BORDER_COLOR)
        guiGraphics.fill(bx + 1, by + 1, bx + COMPOSE_SIZE - 1, by + COMPOSE_SIZE - 1, if (hovered) ACCENT_COLOR else SECTION_CONTENT_BG)
        val plus = "+"
        guiGraphics.drawString(
            font,
            plus,
            bx + (COMPOSE_SIZE - font.width(plus)) / 2,
            by + (COMPOSE_SIZE - font.lineHeight) / 2 + 1,
            if (hovered) 0xFFFFFFFF.toInt() else CONTENT_TEXT,
            false
        )
    }

    /** Do Not Disturb toggle: a bell that gains a red slash when muted. */
    private fun renderMuteButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val (x, y) = mutePos()
        val hovered = isInMuteButton(mouseX, mouseY)
        val bg = when {
            SocialMute.muted -> MUTE_ACTIVE_COLOR
            hovered -> ACCENT_COLOR
            else -> SECTION_CONTENT_BG
        }
        guiGraphics.fill(x, y, x + COMPOSE_SIZE, y + COMPOSE_SIZE, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x + 1, y + 1, x + COMPOSE_SIZE - 1, y + COMPOSE_SIZE - 1, bg)

        val c = CONTENT_TEXT
        guiGraphics.fill(x + 5, y + 2, x + 7, y + 3, c)   // top nub
        guiGraphics.fill(x + 4, y + 3, x + 8, y + 4, c)   // shoulders
        guiGraphics.fill(x + 3, y + 4, x + 9, y + 7, c)   // body
        guiGraphics.fill(x + 2, y + 7, x + 10, y + 8, c)  // rim
        guiGraphics.fill(x + 5, y + 8, x + 7, y + 9, c)   // clapper
        if (SocialMute.muted) {
            guiGraphics.fill(x + 1, y + 5, x + 11, y + 6, MUTE_SLASH_COLOR)
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
        guiGraphics.fill(x, y, x + CONTENT_WIDTH, y + headerH, SECTION_TITLE_BG)
        guiGraphics.fill(x, y + headerH, x + CONTENT_WIDTH, y + height, SECTION_CONTENT_BG)

        var cursorY = y + CARD_PAD
        val textX = x + CARD_PAD

        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(post.authorUuid, post.authorName),
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
            val attH = ATTACHMENT_HEIGHT
            guiGraphics.fill(textX, cursorY, textX + attW, cursorY + attH, ATTACHMENT_BG_COLOR)
            guiGraphics.fill(textX, cursorY, textX + attW, cursorY + 1, BUTTON_BORDER_COLOR)
            guiGraphics.fill(textX, cursorY + attH - 1, textX + attW, cursorY + attH, BUTTON_BORDER_COLOR)
            guiGraphics.fill(textX, cursorY, textX + 1, cursorY + attH, BUTTON_BORDER_COLOR)
            guiGraphics.fill(textX + attW - 1, cursorY, textX + attW, cursorY + attH, BUTTON_BORDER_COLOR)
            renderAttachment(guiGraphics, post, attachment, textX, cursorY)
            cursorY += ATTACHMENT_HEIGHT + TEXT_GAP
        }

        val footerY = y + height - CARD_PAD - FOOTER_HEIGHT
        guiGraphics.fill(textX, footerY - TEXT_GAP - 1, x + CONTENT_WIDTH - CARD_PAD, footerY - TEXT_GAP, SEPARATOR_COLOR)

        val heart = if (post.likedByMe) "♥" else "♡"
        val heartHovered = isInLikeButton(mouseX, mouseY, x, y, height)
        val heartColor = when {
            post.likedByMe -> LIKED_COLOR
            heartHovered -> ACCENT_COLOR
            else -> CONTENT_DIM
        }
        guiGraphics.drawString(font, "$heart ${post.likeCount}", textX, footerY, heartColor, false)

        if (canDelete(post)) {
            val label = lang("delete")
            val hovered = isInDeleteButton(mouseX, mouseY, x, y, height)
            guiGraphics.drawString(
                font,
                label,
                x + CONTENT_WIDTH - CARD_PAD - font.width(label),
                footerY,
                if (hovered) DANGER_COLOR else CONTENT_DIM,
                false
            )
        }
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

        if (renderable != null) {
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate((x + MODEL_X).toDouble(), (y + MODEL_Y).toDouble(), 0.0)
            drawProfilePokemon(
                renderable, matrices, Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                PoseType.PROFILE, posableState, 0f, MODEL_SCALE
            )
            matrices.popPose()
        }

        val label = attachment.nickname ?: speciesName(attachment.species)
        val infoX = x + ATTACHMENT_TEXT_X
        guiGraphics.drawString(font, label, infoX, y + 8, CONTENT_TEXT, false)
        guiGraphics.drawString(font, "Lv. ${attachment.level}", infoX, y + 20, CONTENT_DIM, false)
        if (attachment.aspects.contains(SHINY_ASPECT)) {
            guiGraphics.drawString(font, lang("shiny"), infoX, y + 32, LIKED_COLOR, false)
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

        Tab.entries.forEachIndexed { index, entry ->
            if (isInTab(mx, my, index)) {
                playClickSound()
                switchTab(entry)
                return true
            }
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
                y += height + CARD_GAP
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
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
        // Pull the next page once the player reaches the bottom. The thread list is not paginated.
        if (tab == Tab.FEED && scrollY >= maxScroll - LOAD_MORE_THRESHOLD) {
            SocialFeedCache.loadMore()
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
    }

    private fun isInTab(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val baseX = screenX + CONTENT_X
        val btnY = screenY + TITLE_Y - TAB_BUTTON_PAD_Y
        val btnH = font.lineHeight + TAB_BUTTON_PAD_Y * 2
        if (mouseY !in btnY..(btnY + btnH)) return false

        var tx = baseX
        for (i in 0..index) {
            val label = lang(if (Tab.entries[i] == Tab.FEED) "tab_feed" else "tab_dms")
            val text = if (Tab.entries[i] == Tab.DMS && SocialDmCache.unreadTotal > 0) "$label (${SocialDmCache.unreadTotal})" else label
            val btnW = font.width(text) + TAB_PAD * 2
            if (i == index) return mouseX in tx..(tx + btnW)
            tx += btnW + TAB_GAP
        }
        return false
    }

    private fun isInThreadRow(mouseX: Int, mouseY: Int, rowY: Int): Boolean =
        mouseX in (screenX + CONTENT_X)..(screenX + CONTENT_X + CONTENT_WIDTH) &&
                mouseY in rowY..(rowY + THREAD_ROW_HEIGHT) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)

    private fun composeButtonPos(): Pair<Int, Int> =
        screenX + CONTENT_X + CONTENT_WIDTH - COMPOSE_SIZE to screenY + COMPOSE_Y

    private fun isInComposeButton(mouseX: Int, mouseY: Int): Boolean {
        val (bx, by) = composeButtonPos()
        return mouseX in bx..(bx + COMPOSE_SIZE) && mouseY in by..(by + COMPOSE_SIZE)
    }

    private fun mutePos(): Pair<Int, Int> {
        val (cx, cy) = composeButtonPos()
        return cx - MUTE_GAP - COMPOSE_SIZE to cy
    }

    private fun isInMuteButton(mouseX: Int, mouseY: Int): Boolean {
        val (x, y) = mutePos()
        return mouseX in x..(x + COMPOSE_SIZE) && mouseY in y..(y + COMPOSE_SIZE)
    }

    private fun isInLikeButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, height: Int): Boolean {
        val footerY = cardY + height - CARD_PAD - FOOTER_HEIGHT
        return mouseX in (cardX + CARD_PAD)..(cardX + CARD_PAD + LIKE_HIT_WIDTH) &&
                mouseY in footerY..(footerY + FOOTER_HEIGHT)
    }

    private fun isInDeleteButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, height: Int): Boolean {
        val footerY = cardY + height - CARD_PAD - FOOTER_HEIGHT
        val right = cardX + CONTENT_WIDTH - CARD_PAD
        return mouseX in (right - font.width(lang("delete")))..right &&
                mouseY in footerY..(footerY + FOOTER_HEIGHT)
    }

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
        val tooltip = when {
            isInMuteButton(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_mute")
            isInComposeButton(mouseX, mouseY) -> {
                val key = if (tab == Tab.FEED) "social_new_post" else "social_new_message"
                Component.translatable("cobblemon_smartphone.tooltip.$key")
            }
            tab == Tab.DMS -> findHoveredThread(mouseX, mouseY)?.let {
                Component.translatable("cobblemon_smartphone.tooltip.social_open_conversation")
            }
            tab == Tab.FEED -> findHoveredCardTooltip(mouseX, mouseY)
            else -> null
        } ?: return
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
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
            if (canDelete(post) && isInDeleteButton(mouseX, mouseY, cardX, y, height)) {
                return Component.translatable("cobblemon_smartphone.tooltip.social_delete")
            }
            y += height + CARD_GAP
        }
        return null
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    companion object {
        private const val OP_PERMISSION_LEVEL = 2
        private const val SHINY_ASPECT = "shiny"

        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        // The 211x207 texture is the whole phone including the bezel. The lit screen is only
        // x 20..191 and y 24..194 — anything drawn outside this lands on the frame.
        private const val SCREEN_LEFT = 20
        private const val SCREEN_RIGHT = 191
        private const val SCREEN_TOP = 24
        private const val SCREEN_BOTTOM = 194

        private const val CONTENT_X = SCREEN_LEFT
        // Leaves SCROLLBAR_GAP + SCROLLBAR_WIDTH to the right, landing flush on SCREEN_RIGHT.
        private const val CONTENT_WIDTH = 165
        private const val TITLE_Y = 27
        private const val LIST_START_Y = 42
        private const val LIST_END_Y = SCREEN_BOTTOM - 2

        private const val COMPOSE_Y = 25
        private const val COMPOSE_SIZE = 12
        private const val MUTE_GAP = 3
        private const val MUTE_ACTIVE_COLOR = 0xFFAA3333.toInt()
        private const val MUTE_SLASH_COLOR = 0xFFFF3030.toInt()

        private const val TAB_PAD = 4
        private const val TAB_BUTTON_PAD_Y = 2
        private const val TAB_GAP = 3

        private const val THREAD_ROW_HEIGHT = 28
        private const val CARD_PAD = 5
        private const val CARD_GAP = 3
        private const val HEAD_SIZE = 10
        private const val HEADER_HEIGHT = 13
        private const val FOOTER_HEIGHT = 9
        private const val TEXT_GAP = 3
        private const val LIKE_HIT_WIDTH = 24

        private const val ATTACHMENT_HEIGHT = 44
        private const val ATTACHMENT_TEXT_X = 52
        private const val MODEL_X = 24
        private const val MODEL_Y = 40
        private const val MODEL_SCALE = 18f

        private const val SCROLLBAR_GAP = 2
        private const val SCROLLBAR_WIDTH = 4
        private const val SCROLL_SPEED = 12
        private const val LOAD_MORE_THRESHOLD = 20

        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val TEXT_COLOR = 0xFFE6FFFF.toInt()
        private const val MUTED_COLOR = 0xFF8AA5AD.toInt()
        private const val SECTION_TITLE_BG = 0xFF3A96B6.toInt()
        private const val SECTION_CONTENT_BG = 0xFFEFFDFF.toInt()
        private const val CONTENT_TEXT = 0xFF1A1A2E.toInt()
        private const val CONTENT_DIM = 0xFF555555.toInt()
        private const val ROW_HOVER_BG = 0xFFD0E8F5.toInt()
        private const val LOADING_COLOR = 0xFFAAAAAA.toInt()
        private const val SEPARATOR_COLOR = 0x30FFFFFF.toInt()
        private const val DIVIDER_COLOR = 0x15FFFFFF.toInt()
        private const val BUTTON_BORDER_COLOR = 0xFF3A96B6.toInt()
        private const val BUTTON_DISABLED_BG = 0xFFCCCCCC.toInt()
        private const val UNREAD_BADGE_COLOR = 0xFFD03030.toInt()
        private const val ATTACHMENT_BG_COLOR = 0x553A96B6
        private const val ACCENT_COLOR = 0xFF3A96B6.toInt()
        private const val LIKED_COLOR = 0xFFFFD700.toInt()
        private const val DANGER_COLOR = 0xFFFD0100.toInt()

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
    }
}
