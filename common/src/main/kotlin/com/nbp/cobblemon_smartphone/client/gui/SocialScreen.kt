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
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveSocialMutePacket
import com.nbp.cobblemon_smartphone.social.DmThreadSummary
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
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
    private var pokemonPopup: com.nbp.cobblemon_smartphone.social.PokemonAttachment? = null
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

        renderBackButton(guiGraphics, mouseX, mouseY)
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
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, THREAD_ROW_HEIGHT, hovered)

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
        } else {
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.MESSAGE, x + CONTENT_WIDTH - 14, y + 10, SocialUi.MUTED)
        }
    }

    private fun renderEmptyState(guiGraphics: GuiGraphics, message: String, loading: Boolean = false) {
        val color = if (loading) LOADING_COLOR else MUTED_COLOR
        SocialUi.drawIcon(
            guiGraphics,
            if (tab == Tab.FEED) SocialUi.Icon.FEED else SocialUi.Icon.MESSAGE,
            screenX + CONTENT_X + CONTENT_WIDTH / 2 - 4,
            screenY + LIST_START_Y + (LIST_END_Y - LIST_START_Y) / 2 - 15,
            SocialUi.MUTED
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
        height += TEXT_GAP + 1
        height += FOOTER_HEIGHT + CARD_PAD
        return height
    }

    private fun wrappedLines(text: String) =
        font.split(Component.literal(text), CONTENT_WIDTH - CARD_PAD * 2)

    // --- Rendering ---

    private fun renderBackButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val color = if (isInBackButton(mouseX, mouseY)) SocialUi.GOLD else SocialUi.WHITE
        guiGraphics.drawString(font, lang("back"), screenX + BACK_X, screenY + BACK_Y, color, false)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.fill(screenX + SCREEN_LEFT, screenY + SCREEN_TOP, screenX + SCREEN_RIGHT, screenY + LIST_START_Y - 1, SocialUi.NAVY)
        var tx = screenX + CONTENT_X
        val btnY = screenY + TITLE_Y - TAB_BUTTON_PAD_Y + 1
        val btnH = font.lineHeight + TAB_BUTTON_PAD_Y * 2

        Tab.entries.forEachIndexed { index, entry ->
            val active = tab == entry
            val hovered = isInTab(mouseX, mouseY, index)
            val label = lang(if (entry == Tab.FEED) "tab_feed" else "tab_dms")
            val text = if (entry == Tab.DMS && SocialDmCache.unreadTotal > 0) "$label (${SocialDmCache.unreadTotal})" else label
            val textW = font.width(text)
            val btnW = textW + TAB_PAD * 2 + TAB_ICON_SPACE
            val boxX = tx + if (entry == Tab.FEED) 2 else 0
            val bg = when {
                active -> SocialUi.CYAN
                hovered -> SocialUi.NAVY_LIGHT
                else -> SocialUi.NAVY
            }
            val tc = when {
                active -> NAME_COLOR
                hovered -> NAME_COLOR
                else -> 0xFFD5E8EF.toInt()
            }
            guiGraphics.fill(boxX, btnY, boxX + btnW, btnY + btnH, bg)
            SocialUi.drawIcon(
                guiGraphics,
                if (entry == Tab.FEED) SocialUi.Icon.FEED else SocialUi.Icon.MESSAGE,
                tx + if (entry == Tab.FEED) 6 else 4,
                screenY + TITLE_Y,
                tc
            )
            val labelX = tx + TAB_PAD + TAB_ICON_SPACE + if (entry == Tab.FEED) 1 else 0
            guiGraphics.drawString(font, text, labelX, screenY + TITLE_Y + 1, tc, false)
            if (active) guiGraphics.fill(boxX, btnY + btnH - 1, boxX + btnW, btnY + btnH, SocialUi.GOLD)
            tx += btnW + TAB_GAP
        }

        renderMuteButton(guiGraphics, mouseX, mouseY)

        val hovered = isInComposeButton(mouseX, mouseY)
        val (bx, by) = composeButtonPos()
        SocialUi.iconButton(guiGraphics, SocialUi.Icon.PLUS, bx, by, COMPOSE_SIZE, hovered)
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
        SocialUi.iconButton(guiGraphics, SocialUi.Icon.BELL, x, y, COMPOSE_SIZE, hovered, SocialMute.muted)
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
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, height)
        guiGraphics.fill(x + 1, y + 1, x + CONTENT_WIDTH - 1, y + headerH, SocialUi.NAVY_LIGHT)

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
            POKEBALL_BACKGROUND_TEXTURE,
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
        SocialUi.surface(guiGraphics, px, py, POPUP_WIDTH, popupHeight)
        guiGraphics.fill(px + 1, py + 1, px + POPUP_WIDTH - 1, py + POPUP_HEADER_HEIGHT, SocialUi.NAVY_LIGHT)

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
    }

    private fun isInTab(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val baseX = screenX + CONTENT_X
        val btnY = screenY + TITLE_Y - TAB_BUTTON_PAD_Y + 1
        val btnH = font.lineHeight + TAB_BUTTON_PAD_Y * 2
        if (mouseY !in btnY..(btnY + btnH)) return false

        var tx = baseX
        for (i in 0..index) {
            val label = lang(if (Tab.entries[i] == Tab.FEED) "tab_feed" else "tab_dms")
            val text = if (Tab.entries[i] == Tab.DMS && SocialDmCache.unreadTotal > 0) "$label (${SocialDmCache.unreadTotal})" else label
            val btnW = font.width(text) + TAB_PAD * 2 + TAB_ICON_SPACE
            if (i == index) {
                val boxX = tx + if (Tab.entries[i] == Tab.FEED) 2 else 0
                return mouseX in boxX..(boxX + btnW)
            }
            tx += btnW + TAB_GAP
        }
        return false
    }

    private fun isInThreadRow(mouseX: Int, mouseY: Int, rowY: Int): Boolean =
        mouseX in (screenX + CONTENT_X)..(screenX + CONTENT_X + CONTENT_WIDTH) &&
                mouseY in rowY..(rowY + THREAD_ROW_HEIGHT) &&
                mouseY in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)

    private fun composeButtonPos(): Pair<Int, Int> =
        screenX + CONTENT_X + CONTENT_WIDTH - COMPOSE_SIZE + 2 to screenY + COMPOSE_Y + 1

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
                mouseY in (footerY + 3)..(footerY + 3 + FOOTER_HEIGHT)
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
        val tooltip = when {
            isInBackButton(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.back_to_phone")
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
        private const val TAB_ICON_SPACE = 11

        private const val THREAD_ROW_HEIGHT = 28
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
        private const val ACCENT_COLOR = 0xFF3A96B6.toInt()
        private const val LIKED_COLOR = 0xFFFF5C9A.toInt()
        private const val GENDER_MALE_COLOR = 0xFF408CFF.toInt()
        private const val GENDER_FEMALE_COLOR = 0xFFFF5C9A.toInt()
        private const val SHINY_COLOR = 0xFFFFD84A.toInt()
        private const val DANGER_COLOR = 0xFFFD0100.toInt()

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
