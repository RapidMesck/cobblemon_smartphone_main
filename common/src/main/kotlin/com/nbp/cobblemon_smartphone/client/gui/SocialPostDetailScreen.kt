package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.systems.RenderSystem
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.social.SocialCommentCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.AddCommentPacket
import com.nbp.cobblemon_smartphone.network.packet.DeleteCommentPacket
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.ReportContentPacket
import com.nbp.cobblemon_smartphone.network.packet.VotePollPacket
import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.social.SocialCommentView
import com.nbp.cobblemon_smartphone.social.SocialPollView
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialTagHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRenderer
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

/**
 * Screen showing a single post expanded, its full list of comments, and a comment input box.
 */
class SocialPostDetailScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack?,
    private var post: SocialPostView
) : Screen(Component.literal("Post Details")) {

    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var draggingScrollbar = false
    private var dragStartMouseY = 0
    private var dragStartScrollY = 0

    private lateinit var input: EditBox
    private val posableState = FloatingState()
    private var cachedRenderable: RenderablePokemon? = null
    private var pokemonPopup: com.nbp.cobblemon_smartphone.social.PokemonAttachment? = null

    private val theme get() = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
    private val NAME_COLOR get() = theme.colors.textPrimary
    private val CONTENT_TEXT get() = theme.colors.textDark
    private val CONTENT_DIM get() = theme.colors.textDarkMuted
    private val MUTED_COLOR get() = theme.colors.textMuted
    private val ACCENT_COLOR get() = theme.colors.primary
    private val BUTTON_BORDER_COLOR get() = theme.colors.primaryBorder
    private val SECTION_CONTENT_BG get() = theme.colors.surface
    private val ROW_HOVER_BG get() = theme.colors.surfaceHover
    private val DANGER_COLOR get() = theme.colors.danger
    private val SHINY_COLOR get() = theme.colors.gold

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        input = EditBox(
            font,
            screenX + CONTENT_X + 2,
            screenY + INPUT_Y + 3,
            INPUT_WIDTH - 4,
            INPUT_HEIGHT - 6,
            Component.empty()
        )
        input.setMaxLength(CobblemonSmartphone.config.social.maxCommentLength)
        input.setBordered(false)
        input.setTextColor(0xFF333333.toInt())
        addRenderableWidget(input)
        setInitialFocus(input)

        SocialCommentCache.registerListener(post.id) {
            // Keep scroll in bounds when new comments arrive
            scrollY = scrollY.coerceIn(0, maxScroll)
        }
        SocialCommentCache.request(post.id)
    }

    override fun removed() {
        SocialCommentCache.unregisterListener(post.id)
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        SocialFeedCache.posts().find { it.id == post.id }?.let { post = it }
        val matrices = guiGraphics.pose()
        SmartphoneThemeRenderer.renderLargeTheme(guiGraphics, theme, screenX, screenY)
        blitk(
            matrixStack = matrices,
            texture = theme.getLargeFrameTexture(color.getLargeScreenTexture()),
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        renderHeader(guiGraphics, mouseX, mouseY)

        val comments = SocialCommentCache.commentsFor(post.id)
        val postHeight = measureCard(post)
        val commentsHeaderHeight = 16
        val commentsHeight = comments.sumOf { measureComment(it) + COMMENT_GAP }
        val totalContentHeight = postHeight + commentsHeaderHeight + commentsHeight + 8

        val viewportHeight = LIST_END_Y - LIST_START_Y
        maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(0, maxScroll)

        guiGraphics.enableScissor(
            screenX + CONTENT_X,
            screenY + LIST_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + LIST_END_Y
        )

        var cursorY = screenY + LIST_START_Y - scrollY

        // 1. Post Card
        renderCard(guiGraphics, post, screenX + CONTENT_X, cursorY, mouseX, mouseY, postHeight)
        cursorY += postHeight + 6

        // 2. Comments Header
        val commentsTitle = "${lang("comments")} (${comments.size})"
        guiGraphics.drawString(font, commentsTitle, screenX + CONTENT_X + 2, cursorY + 2, NAME_COLOR, false)
        cursorY += commentsHeaderHeight

        // 3. Comments List
        if (comments.isEmpty()) {
            val emptyMsg = lang(if (SocialCommentCache.isLoading(post.id)) "loading" else "no_comments")
            val wrapped = font.split(Component.literal(emptyMsg), CONTENT_WIDTH - 8)
            wrapped.forEach { line ->
                guiGraphics.drawString(font, line, screenX + CONTENT_X + 4, cursorY, CONTENT_DIM, false)
                cursorY += font.lineHeight
            }
        } else {
            comments.forEach { comment ->
                val commentH = measureComment(comment)
                renderCommentRow(guiGraphics, comment, screenX + CONTENT_X, cursorY, mouseX, mouseY, commentH)
                cursorY += commentH + COMMENT_GAP
            }
        }

        guiGraphics.disableScissor()

        if (maxScroll > 0) renderScrollbar(guiGraphics)

        // 4. Input Box
        val ix = screenX + CONTENT_X
        val iy = screenY + INPUT_Y
        guiGraphics.fill(ix, iy, ix + INPUT_WIDTH, iy + INPUT_HEIGHT, SECTION_CONTENT_BG)
        guiGraphics.fill(ix, iy, ix + INPUT_WIDTH, iy + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(ix, iy + INPUT_HEIGHT - 1, ix + INPUT_WIDTH, iy + INPUT_HEIGHT, BUTTON_BORDER_COLOR)
        guiGraphics.fill(ix, iy, ix + 1, iy + INPUT_HEIGHT, BUTTON_BORDER_COLOR)
        guiGraphics.fill(ix + INPUT_WIDTH - 1, iy, ix + INPUT_WIDTH, iy + INPUT_HEIGHT, BUTTON_BORDER_COLOR)

        if (input.value.isEmpty() && !input.isFocused) {
            val placeholder = lang("comment_placeholder")
            guiGraphics.drawString(font, placeholder, ix + 4, iy + 4, 0xFF999999.toInt(), false)
        }
        input.render(guiGraphics, mouseX, mouseY, delta)

        // 5. Send Button
        renderSendButton(guiGraphics, mouseX, mouseY)

        // 6. Tooltips
        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        SmartphoneBatteryRenderer.render(guiGraphics, screenX, screenY, isLargeScreen = true, smartphoneStack)
        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + LIST_START_Y, theme.colors.headerNavy)

        val backHovered = isInBack(mouseX, mouseY)
        guiGraphics.drawString(
            font,
            lang("back"),
            screenX + CONTENT_X + 4,
            screenY + TITLE_Y + 2,
            if (backHovered) theme.colors.gold else MUTED_COLOR,
            false
        )

        val title = lang("post_detail")
        guiGraphics.drawString(
            font,
            title,
            screenX + CONTENT_X + (CONTENT_WIDTH - font.width(title)) / 2,
            screenY + TITLE_Y + 2,
            NAME_COLOR,
            false
        )
    }

    private fun renderSendButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val bx = screenX + SEND_BUTTON_X
        val by = screenY + SEND_BUTTON_Y
        val hovered = isInSend(mouseX, mouseY)
        val bg = if (hovered) theme.colors.primaryHover else theme.colors.primary
        guiGraphics.fill(bx, by, bx + SEND_BUTTON_SIZE, by + SEND_BUTTON_SIZE, BUTTON_BORDER_COLOR)
        guiGraphics.fill(bx + 1, by + 1, bx + SEND_BUTTON_SIZE - 1, by + SEND_BUTTON_SIZE - 1, bg)
        SocialUi.drawIcon(
            guiGraphics,
            SocialUi.Icon.SEND,
            bx + (SEND_BUTTON_SIZE - 8) / 2,
            by + (SEND_BUTTON_SIZE - 8) / 2,
            theme.colors.textPrimary
        )
    }

    private fun renderCard(
        guiGraphics: GuiGraphics,
        p: SocialPostView,
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
            PlayerHeads.skinFor(p.authorUuid, p.authorName, p.skinUrl),
            textX,
            cursorY,
            HEAD_SIZE
        )
        guiGraphics.drawString(font, p.authorName, textX + HEAD_SIZE + 4, cursorY + 2, NAME_COLOR, false)

        val age = relativeTime(p.timestamp)
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

        if (p.text.isNotBlank()) {
            wrappedLines(p.text).forEach { line ->
                guiGraphics.drawString(font, line, textX, cursorY, CONTENT_TEXT, false)
                cursorY += font.lineHeight
            }
            cursorY += TEXT_GAP
        }

        p.photo?.let { photo ->
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
                guiGraphics.fill(photoX, cursorY, photoX + photoW, cursorY + photoH, 0xFFE0E0E0.toInt())
                val label = lang(if (SocialPhotoClient.isRequested(photo.id)) "photo_loading" else "photo_load")
                val buttonX = photoX + (photoW - 60) / 2
                val buttonY = cursorY + (photoH - 14) / 2
                guiGraphics.fill(buttonX, buttonY, buttonX + 60, buttonY + 14, BUTTON_BORDER_COLOR)
                guiGraphics.fill(buttonX + 1, buttonY + 1, buttonX + 59, buttonY + 13, SECTION_CONTENT_BG)
                val trimmed = font.plainSubstrByWidth(label, 54)
                guiGraphics.drawString(font, trimmed, buttonX + (60 - font.width(trimmed)) / 2, buttonY + 3, CONTENT_TEXT, false)
            }
            cursorY += photoH + TEXT_GAP
        }

        p.poll?.let { poll ->
            val pollH = measurePoll(poll)
            renderPoll(guiGraphics, p, poll, textX, cursorY, mouseX, mouseY)
            cursorY += pollH + TEXT_GAP
        }

        val footerY = y + height - CARD_PAD - FOOTER_HEIGHT
        guiGraphics.fill(textX, footerY - TEXT_GAP - 1, x + CONTENT_WIDTH - CARD_PAD, footerY - TEXT_GAP, SEPARATOR_COLOR)

        val heartHovered = isInLikeButton(mouseX, mouseY, x, y, height)
        val heartColor = when {
            p.likedByMe -> LIKED_COLOR
            heartHovered -> ACCENT_COLOR
            else -> CONTENT_DIM
        }
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.HEART, textX + 1, footerY + 3, heartColor)
        guiGraphics.drawString(font, p.likeCount.toString(), textX + 12, footerY + 3, heartColor, false)

        if (canDeletePost(p)) {
            val label = lang("delete")
            val hovered = isInDeletePostButton(mouseX, mouseY, x, y, height)
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
            val reportHovered = isInReportPostButton(mouseX, mouseY, x, y, height)
            val flagX = x + CONTENT_WIDTH - CARD_PAD - 12
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.FLAG, flagX, footerY + 2, if (reportHovered) DANGER_COLOR else CONTENT_DIM)
        }
    }

    private fun renderCommentRow(
        guiGraphics: GuiGraphics,
        comment: SocialCommentView,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        height: Int
    ) {
        val hovered = mouseX in x until (x + CONTENT_WIDTH) && mouseY in y until (y + height)
        guiGraphics.fill(x, y, x + CONTENT_WIDTH, y + height, if (hovered) ROW_HOVER_BG else SECTION_CONTENT_BG)
        guiGraphics.fill(x, y, x + CONTENT_WIDTH, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y + height - 1, x + CONTENT_WIDTH, y + height, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y, x + 1, y + height, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x + CONTENT_WIDTH - 1, y, x + CONTENT_WIDTH, y + height, BUTTON_BORDER_COLOR)

        val headX = x + 3
        val headY = y + 3
        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(comment.authorUuid, comment.authorName, comment.skinUrl),
            headX,
            headY,
            HEAD_SIZE
        )

        guiGraphics.drawString(font, comment.authorName, headX + HEAD_SIZE + 3, y + 2, NAME_COLOR, false)
        val age = relativeTime(comment.timestamp)
        guiGraphics.drawString(font, age, x + CONTENT_WIDTH - 4 - font.width(age), y + 2, CONTENT_DIM, false)

        var textY = y + HEADER_HEIGHT
        val lines = font.split(formatPostText(comment.text), CONTENT_WIDTH - 8)
        lines.forEach { line ->
            guiGraphics.drawString(font, line, x + 4, textY, CONTENT_TEXT, false)
            textY += font.lineHeight
        }

        if (canDeleteComment(comment)) {
            val trashHovered = isInDeleteComment(mouseX, mouseY, x, y, height)
            SocialUi.drawIcon(
                guiGraphics,
                SocialUi.Icon.TRASH,
                x + CONTENT_WIDTH - 12,
                y + height - 11,
                if (trashHovered) DANGER_COLOR else CONTENT_DIM
            )
        } else {
            val flagHovered = isInReportComment(mouseX, mouseY, x, y, height)
            SocialUi.drawIcon(
                guiGraphics,
                SocialUi.Icon.FLAG,
                x + CONTENT_WIDTH - 12,
                y + height - 11,
                if (flagHovered) DANGER_COLOR else CONTENT_DIM
            )
        }
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics) {
        val barX = screenX + CONTENT_X + CONTENT_WIDTH + 1
        val barY = screenY + LIST_START_Y
        val barH = LIST_END_Y - LIST_START_Y
        guiGraphics.fill(barX, barY, barX + 3, barY + barH, BUTTON_BORDER_COLOR)

        val thumbH = ((barH.toFloat() / (barH + maxScroll)) * barH).toInt().coerceAtLeast(10)
        val thumbY = barY + ((scrollY.toFloat() / maxScroll) * (barH - thumbH)).toInt()
        guiGraphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, ACCENT_COLOR)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInBack(mx, my)) {
            playClickSound()
            minecraft?.setScreen(SocialScreen(color, smartphoneStack))
            return true
        }

        if (isInSend(mx, my)) {
            trySendComment()
            return true
        }

        // Like button on card
        val postH = measureCard(post)
        val cardY = screenY + LIST_START_Y - scrollY
        val authorHitW = HEAD_SIZE + 4 + font.width(post.authorName)
        if (mx in (screenX + CONTENT_X + CARD_PAD)..(screenX + CONTENT_X + CARD_PAD + authorHitW) &&
            my in (cardY + CARD_PAD)..(cardY + CARD_PAD + HEADER_HEIGHT)) {
            playClickSound()
            minecraft?.setScreen(SocialProfileScreen(color, smartphoneStack, post.authorUuid, post.authorName))
            return true
        }
        if (isInLikeButton(mx, my, screenX + CONTENT_X, cardY, postH)) {
            playClickSound()
            val newLiked = !post.likedByMe
            post = post.copy(
                likedByMe = newLiked,
                likeCount = (post.likeCount + if (newLiked) 1 else -1).coerceAtLeast(0)
            )
            SocialFeedCache.toggleLikeLocally(post.id)
            LikePostPacket(post.id, newLiked).sendToServer()
            return true
        }

        val clickedPollOption = findClickedPollOption(post, mx, my, screenX + CONTENT_X, cardY)
        if (clickedPollOption != null) {
            playClickSound()
            VotePollPacket(post.id, clickedPollOption).sendToServer()
            return true
        }

        // Delete or report post
        if (canDeletePost(post) && isInDeletePostButton(mx, my, screenX + CONTENT_X, cardY, postH)) {
            playClickSound()
            DeletePostPacket(post.id).sendToServer()
            minecraft?.setScreen(SocialScreen(color, smartphoneStack))
            return true
        }
        if (!canDeletePost(post) && isInReportPostButton(mx, my, screenX + CONTENT_X, cardY, postH)) {
            playClickSound()
            ReportContentPacket(ReportTargetType.POST, post.id, "Inappropriate content").sendToServer()
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.nbp.social.report_sent"),
                false
            )
            return true
        }
        val clickedTag = findClickedHashtag(post.text, mx, my, screenX + CONTENT_X, cardY)
        if (clickedTag != null) {
            playClickSound()
            SocialFeedCache.setHashtag(clickedTag)
            minecraft?.setScreen(SocialScreen(color, smartphoneStack))
            return true
        }

        // Photo load click
        post.photo?.let { photo ->
            val photoY = cardY + CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1 +
                (if (post.text.isNotBlank()) wrappedLines(post.text).size * font.lineHeight + TEXT_GAP else 0)
            val (photoW, photoH) = photoDisplaySize(photo.width, photo.height)
            val photoX = screenX + CONTENT_X + CARD_PAD + ((CONTENT_WIDTH - CARD_PAD * 2) - photoW) / 2
            if (mx in photoX..(photoX + photoW) && my in photoY..(photoY + photoH)) {
                if (SocialPhotoClient.texture(photo.id) == null) {
                    playClickSound()
                    SocialPhotoClient.request(photo.id)
                    return true
                }
            }
        }

        // Delete comment click
        val comments = SocialCommentCache.commentsFor(post.id)
        var cursorY = screenY + LIST_START_Y - scrollY + postH + 16
        comments.forEach { comment ->
            val commentH = measureComment(comment)
            val cAuthorHitW = HEAD_SIZE + 4 + font.width(comment.authorName)
            if (mx in (screenX + CONTENT_X + 3)..(screenX + CONTENT_X + 3 + cAuthorHitW) &&
                my in cursorY..(cursorY + HEADER_HEIGHT)) {
                playClickSound()
                minecraft?.setScreen(SocialProfileScreen(color, smartphoneStack, comment.authorUuid, comment.authorName))
                return true
            }
            if (canDeleteComment(comment) && isInDeleteComment(mx, my, screenX + CONTENT_X, cursorY, commentH)) {
                playClickSound()
                DeleteCommentPacket(post.id, comment.id).sendToServer()
                return true
            }
            if (!canDeleteComment(comment) && isInReportComment(mx, my, screenX + CONTENT_X, cursorY, commentH)) {
                playClickSound()
                ReportContentPacket(ReportTargetType.COMMENT, comment.id, "Inappropriate content").sendToServer()
                Minecraft.getInstance().player?.displayClientMessage(
                    Component.translatable("message.nbp.social.report_sent"),
                    false
                )
                return true
            }
            cursorY += commentH + COMMENT_GAP
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) { // Escape
            minecraft?.setScreen(SocialScreen(color, smartphoneStack))
            return true
        }
        if (keyCode == 257 || keyCode == 335) { // Enter or Keypad Enter
            if (input.isFocused && input.value.isNotBlank()) {
                trySendComment()
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (maxScroll > 0) {
            scrollY = (scrollY - (verticalAmount * 14).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun trySendComment() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        playClickSound()
        AddCommentPacket(post.id, text).sendToServer()
        input.value = ""
    }

    private fun measureCard(p: SocialPostView): Int {
        var h = CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        if (p.text.isNotBlank()) h += wrappedLines(p.text).size * font.lineHeight + TEXT_GAP
        if (p.photo != null) h += photoDisplaySize(p.photo.width, p.photo.height).second + TEXT_GAP
        if (p.poll != null) h += measurePoll(p.poll) + TEXT_GAP
        h += TEXT_GAP + 1 + FOOTER_HEIGHT + CARD_PAD
        return h
    }

    private fun measurePoll(poll: SocialPollView): Int {
        val qLines = font.split(Component.literal(poll.question), CONTENT_WIDTH - CARD_PAD * 2 - 8).size
        val qH = qLines * font.lineHeight + 4
        val optionsH = poll.options.size * 17
        val footH = 10
        return 6 + qH + optionsH + footH
    }

    private fun renderPoll(
        guiGraphics: GuiGraphics,
        p: SocialPostView,
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

    private fun findClickedPollOption(p: SocialPostView, mx: Int, my: Int, cardX: Int, cardY: Int): Int? {
        val poll = p.poll ?: return null
        val w = CONTENT_WIDTH - CARD_PAD * 2
        var cursorY = cardY + CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        if (p.text.isNotBlank()) cursorY += wrappedLines(p.text).size * font.lineHeight + TEXT_GAP
        if (p.photo != null) cursorY += photoDisplaySize(p.photo.width, p.photo.height).second + TEXT_GAP

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

    private fun findClickedHashtag(text: String, mx: Int, my: Int, cardX: Int, cardY: Int): String? {
        if (!text.contains('#')) return null
        val textX = cardX + CARD_PAD
        var cursorY = cardY + CARD_PAD + HEADER_HEIGHT + TEXT_GAP + 1
        val lines = wrappedLines(text)
        val hashtags = SocialTagHelper.extractHashtags(text)
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

    private fun measureComment(c: SocialCommentView): Int {
        val lines = font.split(formatPostText(c.text), CONTENT_WIDTH - 8)
        return HEADER_HEIGHT + lines.size * font.lineHeight + 5
    }

    private fun wrappedLines(text: String) = font.split(formatPostText(text), CONTENT_WIDTH - CARD_PAD * 2)

    private fun isInBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + CONTENT_X + 2)..(screenX + CONTENT_X + 36) && mouseY in (screenY + TITLE_Y)..(screenY + TITLE_Y + 12)

    private fun isInSend(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + SEND_BUTTON_X)..(screenX + SEND_BUTTON_X + SEND_BUTTON_SIZE) &&
            mouseY in (screenY + SEND_BUTTON_Y)..(screenY + SEND_BUTTON_Y + SEND_BUTTON_SIZE)

    private fun isInLikeButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, cardHeight: Int): Boolean {
        val footerY = cardY + cardHeight - CARD_PAD - FOOTER_HEIGHT
        return mouseX in (cardX + CARD_PAD)..(cardX + CARD_PAD + 28) && mouseY in (footerY + 1)..(footerY + FOOTER_HEIGHT)
    }

    private fun isInDeletePostButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, cardHeight: Int): Boolean {
        val footerY = cardY + cardHeight - CARD_PAD - FOOTER_HEIGHT
        val delW = font.width(lang("delete")) + 12
        return mouseX in (cardX + CONTENT_WIDTH - CARD_PAD - delW)..(cardX + CONTENT_WIDTH - CARD_PAD) &&
            mouseY in (footerY + 1)..(footerY + FOOTER_HEIGHT)
    }

    private fun isInReportPostButton(mouseX: Int, mouseY: Int, cardX: Int, cardY: Int, cardHeight: Int): Boolean {
        val footerY = cardY + cardHeight - CARD_PAD - FOOTER_HEIGHT
        val flagX = cardX + CONTENT_WIDTH - CARD_PAD - 14
        return mouseX in flagX..(cardX + CONTENT_WIDTH - CARD_PAD) && mouseY in (footerY - 1)..(footerY + FOOTER_HEIGHT + 1)
    }

    private fun isInDeleteComment(mouseX: Int, mouseY: Int, commentX: Int, commentY: Int, commentH: Int): Boolean =
        mouseX in (commentX + CONTENT_WIDTH - 15)..(commentX + CONTENT_WIDTH - 2) &&
            mouseY in (commentY + commentH - 14)..(commentY + commentH)

    private fun isInReportComment(mouseX: Int, mouseY: Int, commentX: Int, commentY: Int, commentH: Int): Boolean =
        mouseX in (commentX + CONTENT_WIDTH - 15)..(commentX + CONTENT_WIDTH - 2) &&
            mouseY in (commentY + commentH - 14)..(commentY + commentH)

    private fun canDeletePost(p: SocialPostView): Boolean {
        val me = Minecraft.getInstance().player ?: return false
        return p.authorUuid == me.uuid || me.hasPermissions(2)
    }

    private fun canDeleteComment(c: SocialCommentView): Boolean {
        val me = Minecraft.getInstance().player ?: return false
        return c.authorUuid == me.uuid || post.authorUuid == me.uuid || me.hasPermissions(2)
    }

    private fun photoDisplaySize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val maxWidth = CONTENT_WIDTH - CARD_PAD * 2
        val maxHeight = 80
        val aspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        return if (aspect >= maxWidth.toFloat() / maxHeight.toFloat()) {
            maxWidth to (maxWidth / aspect).toInt().coerceAtLeast(1)
        } else {
            (maxHeight * aspect).toInt().coerceAtLeast(1) to maxHeight
        }
    }

    private fun relativeTime(timestamp: Long): String {
        val diffSec = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
        return when {
            diffSec < 60 -> lang("now")
            diffSec < 3600 -> "${diffSec / 60}m"
            diffSec < 86400 -> "${diffSec / 3600}h"
            diffSec < 172800 -> lang("one_day_ago")
            else -> lang("days_ago").format(diffSec / 86400)
        }
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (isInSend(mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable("cobblemon_smartphone.tooltip.social_send_comment"), mouseX, mouseY)
            return
        }
        val cardY = screenY + LIST_START_Y - scrollY
        val postH = measureCard(post)
        if (canDeletePost(post)) {
            if (isInDeletePostButton(mouseX, mouseY, screenX + CONTENT_X, cardY, postH)) {
                guiGraphics.renderTooltip(font, Component.translatable("cobblemon_smartphone.tooltip.social_delete"), mouseX, mouseY)
                return
            }
        } else {
            if (isInReportPostButton(mouseX, mouseY, screenX + CONTENT_X, cardY, postH)) {
                guiGraphics.renderTooltip(font, Component.translatable("cobblemon_smartphone.tooltip.social_report"), mouseX, mouseY)
                return
            }
        }
        val comments = SocialCommentCache.commentsFor(post.id)
        var cursorY = screenY + LIST_START_Y - scrollY + postH + 16
        comments.forEach { comment ->
            val commentH = measureComment(comment)
            if (canDeleteComment(comment)) {
                if (isInDeleteComment(mouseX, mouseY, screenX + CONTENT_X, cursorY, commentH)) {
                    guiGraphics.renderTooltip(font, Component.translatable("cobblemon_smartphone.tooltip.social_delete"), mouseX, mouseY)
                    return
                }
            } else {
                if (isInReportComment(mouseX, mouseY, screenX + CONTENT_X, cursorY, commentH)) {
                    guiGraphics.renderTooltip(font, Component.translatable("cobblemon_smartphone.tooltip.social_report"), mouseX, mouseY)
                    return
                }
            }
            cursorY += commentH + COMMENT_GAP
        }
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.GUI_CLICK, 0.5f, 1.0f)
    }

    companion object {
        const val GUI_WIDTH = 211
        const val GUI_HEIGHT = 207
        const val CONTENT_X = 27
        const val CONTENT_WIDTH = 157
        const val TITLE_Y = 28
        const val LIST_START_Y = 46
        const val LIST_END_Y = 173

        const val INPUT_Y = 176
        const val INPUT_WIDTH = 138
        const val INPUT_HEIGHT = 16

        const val SEND_BUTTON_X = CONTENT_X + 141
        const val SEND_BUTTON_Y = 176
        const val SEND_BUTTON_SIZE = 16

        const val CARD_PAD = 4
        const val HEADER_HEIGHT = 12
        const val HEAD_SIZE = 8
        const val TEXT_GAP = 3
        const val FOOTER_HEIGHT = 12
        const val COMMENT_GAP = 3

        const val SEPARATOR_COLOR = 0x22000000
        const val LIKED_COLOR = 0xFFD84A4A.toInt()
    }
}
