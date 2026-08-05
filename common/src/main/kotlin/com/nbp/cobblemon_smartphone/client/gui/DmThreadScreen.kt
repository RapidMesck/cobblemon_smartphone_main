package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.mojang.blaze3d.systems.RenderSystem
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.client.social.SocialMutationState
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.network.packet.MutePlayerPacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.social.CallStatus
import com.nbp.cobblemon_smartphone.social.DmMessage
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.joml.Quaternionf

/**
 * A 1:1 conversation. Messages are stored oldest-first and rendered bottom-up so the newest sits
 * at the bottom, like any chat.
 *
 * The header deliberately leaves room to the left of the back button for a future call button
 * (voice chat, phase 3).
 */
class DmThreadScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack?,
    private val otherUuid: UUID,
    private val otherName: String
) : Screen(Component.literal("Direct Message")) {

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var lastSendTime = 0L
    private lateinit var input: EditBox
    private var pendingRequestId: Long? = null
    private var callNoticeUntil = 0L
    private val pokemonModels = mutableMapOf<Long, RenderablePokemon?>()
    private val pokemonModelState = FloatingState()
    private data class PendingPokemonModel(val pokemon: RenderablePokemon, val x: Int, val y: Int)
    private val pendingPokemonModels = mutableListOf<PendingPokemonModel>()
    private var pokemonPopup: com.nbp.cobblemon_smartphone.social.PokemonAttachment? = null

    private val messageCooldownSec get() = SocialClientSession.capabilities.messageCooldownSeconds

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        // Opening the thread requests its newest page and marks it read.
        if (SocialDmCache.openThreadWith != otherUuid) {
            SocialDmCache.openThread(otherUuid, otherName)
        }

        input = EditBox(
            font,
            screenX + CONTENT_X + INPUT_TEXT_OFFSET_X,
            screenY + INPUT_Y + INPUT_TEXT_OFFSET_Y,
            INPUT_WIDTH - INPUT_TEXT_OFFSET_X - 2,
            INPUT_HEIGHT - INPUT_TEXT_OFFSET_Y,
            Component.empty()
        )
        input.setMaxLength(SocialClientSession.capabilities.maxMessageLength)
        input.setBordered(false)
        input.setTextColor(0xFF888888.toInt())
        addRenderableWidget(input)
        setInitialFocus(input)
    }

    override fun removed() {
        SocialDmCache.closeThread()
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        consumeSendResult()
        pendingPokemonModels.clear()
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

        val messages = SocialDmCache.messages()
        val total = messages.sumOf { measureBubble(it) + BUBBLE_GAP }
        maxScroll = (total - (LIST_END_Y - LIST_START_Y)).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(0, maxScroll)

        // Anchor to the bottom: with less content than the viewport, start at the top instead.
        var y = screenY + LIST_END_Y - total + scrollY
        if (total < LIST_END_Y - LIST_START_Y) y = screenY + LIST_START_Y

        messages.forEach { message ->
            val height = measureBubble(message)
            if (y + height >= screenY + LIST_START_Y && y <= screenY + LIST_END_Y) {
                renderBubble(guiGraphics, message, y)
            }
            y += height + BUBBLE_GAP
        }

        if (messages.isEmpty()) {
            val text = lang("no_messages")
            guiGraphics.drawString(
                font,
                text,
                screenX + CONTENT_X + (CONTENT_WIDTH - font.width(text)) / 2,
                screenY + LIST_START_Y + (LIST_END_Y - LIST_START_Y) / 2 - font.lineHeight / 2,
                MUTED_COLOR,
                false
            )
        }

        guiGraphics.disableScissor()
        renderPendingPokemonModels(guiGraphics)

        renderCallNotice(guiGraphics)

        val ix = screenX + CONTENT_X
        val iy = screenY + INPUT_Y
        guiGraphics.fill(ix, iy, ix + INPUT_WIDTH, iy + INPUT_HEIGHT, SECTION_CONTENT_BG)
        guiGraphics.fill(ix, iy, ix + INPUT_WIDTH, iy + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(ix, iy + INPUT_HEIGHT - 1, ix + INPUT_WIDTH, iy + INPUT_HEIGHT, BUTTON_BORDER_COLOR)
        guiGraphics.fill(ix, iy, ix + 1, iy + INPUT_HEIGHT, BUTTON_BORDER_COLOR)
        guiGraphics.fill(ix + INPUT_WIDTH - 1, iy, ix + INPUT_WIDTH, iy + INPUT_HEIGHT, BUTTON_BORDER_COLOR)

        input.render(guiGraphics, mouseX, mouseY, delta)
        renderAttachButton(guiGraphics, mouseX, mouseY)
        renderSendButton(guiGraphics, mouseX, mouseY)

        if (pokemonPopup == null) renderHoveredTooltip(guiGraphics, mouseX, mouseY)
        pokemonPopup?.let {
            RenderSystem.disableDepthTest()
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(0f, 0f, POPUP_Z)
            renderPokemonPopup(guiGraphics, it, mouseX, mouseY)
            guiGraphics.pose().popPose()
            RenderSystem.enableDepthTest()
        }
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + LIST_START_Y, SocialUi.NAVY)
        guiGraphics.drawString(
            font,
            lang("back"),
            screenX + CONTENT_X + 2,
            screenY + TITLE_Y + 2,
            if (isInBack(mouseX, mouseY)) ACCENT_COLOR else MUTED_COLOR,
            false
        )

        renderMuteBell(guiGraphics, mouseX, mouseY)
        if (callAvailable()) {
            renderCallButton(guiGraphics, mouseX, mouseY)
        }

        // Name centered between the back link and the call button.
        val name = SocialDmCache.currentThreadName.ifBlank { otherName }
        guiGraphics.drawString(
            font,
            name,
            screenX + CONTENT_X + (CONTENT_WIDTH - font.width(name)) / 2,
            screenY + TITLE_Y + 2,
            NAME_COLOR,
            false
        )
    }

    private fun renderCallButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val x = callButtonX()
        val y = bellY()
        val hovered = isInCallButton(mouseX, mouseY)
        val active = CallState.isBusyWith(otherUuid)
        val background = when {
            active -> SocialUi.CYAN
            hovered -> SocialUi.CYAN_HOVER
            else -> SocialUi.SURFACE
        }
        guiGraphics.fill(x, y, x + CALL_BUTTON_WIDTH, y + CALL_BUTTON_HEIGHT, SocialUi.BORDER)
        guiGraphics.fill(x + 1, y + 1, x + CALL_BUTTON_WIDTH - 1, y + CALL_BUTTON_HEIGHT - 1, background)
        guiGraphics.drawString(
            font,
            CALL_BUTTON_TEXT,
            x + (CALL_BUTTON_WIDTH - font.width(CALL_BUTTON_TEXT)) / 2,
            y + (CALL_BUTTON_HEIGHT - font.lineHeight) / 2 + 1,
            if (active) SocialUi.WHITE else SocialUi.TEXT,
            false
        )
    }

    private fun onCallButton() {
        when {
            CallState.isBusyWith(otherUuid) ->
                CallActionPacket(CallActionPacket.Action.HANGUP, otherUuid).sendToServer()
            CallState.isIdle() ->
                CallActionPacket(CallActionPacket.Action.START, otherUuid).sendToServer()
            // Busy with someone else: ignore rather than yank them out of another call.
        }
    }

    fun showCallOffline(targetUuid: UUID) {
        if (targetUuid == otherUuid) {
            callNoticeUntil = System.currentTimeMillis() + CALL_NOTICE_DURATION_MS
        }
    }

    private fun renderCallNotice(guiGraphics: GuiGraphics) {
        if (System.currentTimeMillis() >= callNoticeUntil) return
        val message = Component.translatable("message.nbp.social.call_offline").string
        val boxWidth = (font.width(message) + CALL_NOTICE_PADDING_X * 2).coerceAtMost(CONTENT_WIDTH - 12)
        val x = screenX + CONTENT_X + (CONTENT_WIDTH - boxWidth) / 2
        val y = screenY + CALL_NOTICE_Y
        guiGraphics.fill(x, y, x + boxWidth, y + CALL_NOTICE_HEIGHT, SocialUi.BORDER)
        guiGraphics.fill(x + 1, y + 1, x + boxWidth - 1, y + CALL_NOTICE_HEIGHT - 1, CALL_NOTICE_BG)
        guiGraphics.drawCenteredString(font, message, x + boxWidth / 2, y + 4, SocialUi.WHITE)
    }

    /** Only offered when this client actually has Simple Voice Chat and the server allows calls. */
    private fun callAvailable(): Boolean {
        if (!VoiceChatBridge.isModPresent || !SocialClientSession.capabilities.callsEnabled) return false
        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.getSmartphone(player) != null
    }

    // Sits just left of the always-present mute bell at the right edge.
    private fun callButtonX(): Int = bellX() - CALL_BELL_GAP - CALL_BUTTON_WIDTH

    private fun isInCallButton(mouseX: Int, mouseY: Int): Boolean {
        val x = callButtonX()
        val y = bellY()
        return mouseX in x..(x + CALL_BUTTON_WIDTH) && mouseY in y..(y + CALL_BUTTON_HEIGHT)
    }

    /** Per-player Do Not Disturb: a bell (with a red slash when this player is muted). */
    private fun renderMuteBell(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val x = bellX()
        val y = bellY()
        val muted = MutedPlayers.contains(otherUuid)
        val hovered = isInBellButton(mouseX, mouseY)
        val bg = when {
            muted -> MUTE_ACTIVE_COLOR
            hovered -> ACCENT_COLOR
            else -> SECTION_CONTENT_BG
        }
        SocialUi.iconButton(guiGraphics, SocialUi.Icon.BELL, x, y, BELL_WIDTH, hovered, muted)
        if (muted) {
            guiGraphics.fill(x + 1, y + 4, x + 11, y + 5, MUTE_SLASH_COLOR)
        }
    }

    private fun bellX(): Int = screenX + CONTENT_X + CONTENT_WIDTH - BELL_WIDTH - 4
    private fun bellY(): Int = screenY + TITLE_Y - 1

    private fun isInBellButton(mouseX: Int, mouseY: Int): Boolean {
        val x = bellX()
        val y = bellY()
        return mouseX in x..(x + BELL_WIDTH) && mouseY in y..(y + BELL_HEIGHT)
    }

    private fun isOwn(message: DmMessage): Boolean =
        message.senderUuid == Minecraft.getInstance().player?.uuid

    private fun wrapped(message: DmMessage) =
        if (message.text.isBlank()) emptyList() else font.split(Component.literal(message.text), BUBBLE_MAX_WIDTH - BUBBLE_PAD * 2)

    private fun measureBubble(message: DmMessage): Int {
        var content = wrapped(message).size * font.lineHeight
        if (message.attachment != null) content += (if (content > 0) BUBBLE_CONTENT_GAP else 0) + DM_POKEMON_HEIGHT
        if (message.photo != null) content += (if (content > 0) BUBBLE_CONTENT_GAP else 0) + DM_PHOTO_HEIGHT
        return content + BUBBLE_PAD * 2 + TIMESTAMP_HEIGHT
    }

    private fun renderBubble(guiGraphics: GuiGraphics, message: DmMessage, y: Int) {
        val lines = wrapped(message)
        val own = isOwn(message)
        val textWidth = lines.maxOfOrNull { font.width(it) } ?: 0
        val timestamp = messageTimestamp(message.timestamp)
        val bubbleWidth = bubbleWidth(message, textWidth, timestamp)
        val height = measureBubble(message)

        // Own messages hug the right edge, the counterpart's hug the left.
        val x = if (own) {
            screenX + CONTENT_X + CONTENT_WIDTH - bubbleWidth
        } else {
            screenX + CONTENT_X
        }

        guiGraphics.fill(x, y, x + bubbleWidth, y + height, if (own) OWN_BUBBLE_COLOR else OTHER_BUBBLE_COLOR)

        var lineY = y + BUBBLE_PAD
        val textColor = if (own) TEXT_COLOR else CONTENT_TEXT
        lines.forEach { line ->
            guiGraphics.drawString(font, line, x + BUBBLE_PAD, lineY, textColor, false)
            lineY += font.lineHeight
        }
        if (lines.isNotEmpty() && (message.attachment != null || message.photo != null)) lineY += BUBBLE_CONTENT_GAP
        message.attachment?.let {
            renderDmPokemon(guiGraphics, message, it, x + BUBBLE_PAD, lineY)
            lineY += DM_POKEMON_HEIGHT
            if (message.photo != null) lineY += BUBBLE_CONTENT_GAP
        }
        message.photo?.let { photo ->
            renderDmPhoto(guiGraphics, photo, x + BUBBLE_PAD, lineY)
        }
        guiGraphics.drawString(
            font,
            timestamp,
            x + bubbleWidth - BUBBLE_PAD - font.width(timestamp),
            y + height - font.lineHeight,
            if (own) 0xFFBFE5EF.toInt() else SocialUi.MUTED,
            false
        )
    }

    private fun bubbleWidth(message: DmMessage, textWidth: Int = 0, timestamp: String = messageTimestamp(message.timestamp)): Int =
        if (message.attachment != null || message.photo != null) BUBBLE_MAX_WIDTH
        else (maxOf(textWidth, font.width(timestamp)) + BUBBLE_PAD * 2).coerceAtMost(BUBBLE_MAX_WIDTH)

    private fun bubbleX(message: DmMessage): Int =
        if (isOwn(message)) screenX + CONTENT_X + CONTENT_WIDTH - bubbleWidth(message) else screenX + CONTENT_X

    private fun renderDmPokemon(
        guiGraphics: GuiGraphics,
        message: DmMessage,
        attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment,
        x: Int,
        y: Int
    ) {
        val own = isOwn(message)
        val cardBackground = if (own) OWN_BUBBLE_COLOR else DM_ATTACHMENT_BG
        val cardBorder = if (own) OWN_ATTACHMENT_BORDER else BUTTON_BORDER_COLOR
        val primaryText = if (own) TEXT_COLOR else CONTENT_TEXT
        val secondaryText = if (own) OWN_ATTACHMENT_MUTED else MUTED_COLOR
        guiGraphics.fill(x, y, x + DM_CONTENT_WIDTH, y + DM_POKEMON_HEIGHT, cardBackground)
        guiGraphics.fill(x, y, x + DM_CONTENT_WIDTH, y + 1, cardBorder)
        guiGraphics.fill(x, y + DM_POKEMON_HEIGHT - 1, x + DM_CONTENT_WIDTH, y + DM_POKEMON_HEIGHT, cardBorder)
        guiGraphics.fill(x, y, x + 1, y + DM_POKEMON_HEIGHT, cardBorder)
        guiGraphics.fill(x + DM_CONTENT_WIDTH - 1, y, x + DM_CONTENT_WIDTH, y + DM_POKEMON_HEIGHT, cardBorder)
        val modelBoxX = x + DM_MODEL_BOX_X
        val modelBoxY = y + DM_MODEL_BOX_Y
        renderPokeballWatermark(guiGraphics, modelBoxX, modelBoxY, own)
        val renderable = pokemonModels.getOrPut(message.id) {
            PokemonSpecies.getByIdentifier(ResourceLocation.parse(attachment.species))?.let { RenderablePokemon(it, attachment.aspects) }
        }
        if (renderable != null && pokemonPopup == null) {
            pendingPokemonModels += PendingPokemonModel(renderable, x, y)
        }
        val species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(attachment.species))?.name ?: attachment.species.substringAfter(':')
        val name = attachment.nickname?.let { "$it ($species)" } ?: species
        val textX = x + DM_ATTACHMENT_TEXT_X
        val textWidth = ((DM_CONTENT_WIDTH - DM_ATTACHMENT_TEXT_X - 5) / DM_ATTACHMENT_TEXT_SCALE).toInt()
        drawDmAttachmentText(guiGraphics, font.plainSubstrByWidth(name, textWidth), textX.toFloat(), (y + 5).toFloat(), primaryText)
        drawDmAttachmentText(guiGraphics, "Lv. ${attachment.level}", textX.toFloat(), (y + 15).toFloat(), secondaryText)

        if (attachment.aspects.contains("shiny")) {
            guiGraphics.drawString(font, "★", modelBoxX + 2, modelBoxY + 2, SHINY_COLOR, false)
        }
        val gender = when (attachment.gender?.uppercase()) {
            "MALE" -> "♂" to GENDER_MALE_COLOR
            "FEMALE" -> "♀" to GENDER_FEMALE_COLOR
            else -> null
        }
        gender?.let { (icon, color) ->
            guiGraphics.drawString(font, icon, x + DM_CONTENT_WIDTH - font.width(icon) - 5, y + 4, color, false)
        }

        val details = dmPokemonDetailLines(attachment)
        if (details.isNotEmpty()) {
            val bx = x + DM_MORE_X
            val by = y + DM_MORE_Y
            guiGraphics.fill(bx, by, bx + DM_MORE_BUTTON_WIDTH, by + DM_MORE_BUTTON_HEIGHT, cardBorder)
            guiGraphics.fill(
                bx + 1, by + 1, bx + DM_MORE_BUTTON_WIDTH - 1, by + DM_MORE_BUTTON_HEIGHT - 1,
                if (own) OWN_BUBBLE_COLOR else SECTION_CONTENT_BG
            )
            val label = lang("show_more")
            drawCenteredDmAttachmentText(guiGraphics, label, bx + DM_MORE_BUTTON_WIDTH / 2f, (by + 2).toFloat(), primaryText)
        }
    }

    private fun drawDmAttachmentText(guiGraphics: GuiGraphics, text: String, x: Float, y: Float, color: Int) {
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(x, y, 0f)
        matrices.scale(DM_ATTACHMENT_TEXT_SCALE, DM_ATTACHMENT_TEXT_SCALE, 1f)
        guiGraphics.drawString(font, text, 0, 0, color, false)
        matrices.popPose()
    }

    private fun drawCenteredDmAttachmentText(guiGraphics: GuiGraphics, text: String, centerX: Float, y: Float, color: Int) =
        drawDmAttachmentText(guiGraphics, text, centerX - font.width(text) * DM_ATTACHMENT_TEXT_SCALE / 2f, y, color)

    private fun renderPendingPokemonModels(guiGraphics: GuiGraphics) {
        pendingPokemonModels.forEach { pending ->
            val left = maxOf(pending.x + DM_MODEL_BOX_X + 1, screenX + CONTENT_X)
            val top = maxOf(pending.y + DM_MODEL_BOX_Y + 1, screenY + LIST_START_Y)
            val right = minOf(pending.x + DM_MODEL_BOX_X + DM_MODEL_WIDTH - 1, screenX + CONTENT_X + CONTENT_WIDTH)
            val bottom = minOf(pending.y + DM_MODEL_BOX_Y + DM_MODEL_HEIGHT - 1, screenY + LIST_END_Y)
            if (left >= right || top >= bottom) return@forEach
            guiGraphics.enableScissor(left, top, right, bottom)
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate((pending.x + DM_MODEL_ANCHOR_X).toFloat(), (pending.y + DM_MODEL_ANCHOR_Y).toFloat(), 0f)
            drawProfilePokemon(
                pending.pokemon, matrices, Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                PoseType.PROFILE, pokemonModelState, 0f, DM_MODEL_SCALE
            )
            matrices.popPose()
            guiGraphics.disableScissor()
        }
    }

    private fun dmPokemonDetailLines(attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment): List<String> = buildList {
        if (attachment.types.isNotEmpty()) add("${lang("types")}: ${attachment.types.joinToString("/")}")
        attachment.ability?.let { add("${lang("ability")}: ${Component.translatable(it).string}") }
        attachment.nature?.let { add("${lang("nature")}: ${Component.translatable(it).string}") }
        if (attachment.ivs.isNotEmpty()) add("IVs: ${attachment.ivs.joinToString("/")}")
        if (attachment.evs.isNotEmpty()) add("EVs: ${attachment.evs.joinToString("/")}")
    }

    private fun renderPokeballWatermark(guiGraphics: GuiGraphics, x: Int, y: Int, own: Boolean) {
        val frame = ((System.currentTimeMillis() / POKEBALL_FRAME_TIME_MS) % POKEBALL_FRAME_COUNT).toInt()
        if (own) {
            RenderSystem.setShaderColor(1f, 1f, 1f, OWN_POKEBALL_ALPHA)
        } else {
            RenderSystem.setShaderColor(POKEBALL_TINT_R, POKEBALL_TINT_G, POKEBALL_TINT_B, POKEBALL_TINT_ALPHA)
        }
        guiGraphics.blit(
            if (own) POKEBALL_BACKGROUND_WHITE_TEXTURE else POKEBALL_BACKGROUND_TEXTURE,
            x + 1, y + 1, DM_MODEL_WIDTH - 2, DM_MODEL_HEIGHT - 2,
            0f, (frame * POKEBALL_FRAME_SIZE).toFloat(), POKEBALL_FRAME_SIZE, POKEBALL_FRAME_SIZE,
            POKEBALL_TEXTURE_WIDTH, POKEBALL_TEXTURE_HEIGHT
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    private fun renderPokemonPopup(
        guiGraphics: GuiGraphics,
        attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment,
        mouseX: Int,
        mouseY: Int
    ) {
        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + 194, POPUP_OVERLAY_COLOR)
        val px = popupX()
        val py = popupY()
        val height = popupHeight(attachment)
        SocialUi.surface(guiGraphics, px, py, POPUP_WIDTH, height)
        guiGraphics.fill(px + 1, py + 1, px + POPUP_WIDTH - 1, py + POPUP_HEADER_HEIGHT, SocialUi.NAVY_LIGHT)
        guiGraphics.drawString(font, lang("pokemon_details"), px + POPUP_PAD, py + 5, NAME_COLOR, false)
        guiGraphics.drawString(
            font, "×", px + POPUP_WIDTH - POPUP_PAD - font.width("×"), py + 5,
            if (isInPopupClose(mouseX, mouseY)) 0xFFFF5C9A.toInt() else NAME_COLOR, false
        )

        val species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(attachment.species))?.name ?: attachment.species.substringAfter(':')
        val name = attachment.nickname?.let { "$it ($species)" } ?: species
        drawPopupText(guiGraphics, name, (px + POPUP_PAD).toFloat(), (py + 21).toFloat(), CONTENT_TEXT)

        var rowY = py + POPUP_INFO_START_Y
        val infoLines = buildList {
            if (attachment.types.isNotEmpty()) add("${lang("types")}: ${attachment.types.joinToString("/")}")
            attachment.ability?.let { add("${lang("ability")}: ${Component.translatable(it).string}") }
            attachment.nature?.let { add("${lang("nature")}: ${Component.translatable(it).string}") }
        }
        infoLines.forEach { line ->
            drawPopupText(guiGraphics, font.plainSubstrByWidth(line, (POPUP_INNER_WIDTH / POPUP_TEXT_SCALE).toInt()), (px + POPUP_PAD).toFloat(), rowY.toFloat(), CONTENT_TEXT)
            rowY += POPUP_INFO_LINE_HEIGHT
        }

        val hasIvs = attachment.ivs.size == STAT_COUNT
        val hasEvs = attachment.evs.size == STAT_COUNT
        if (hasIvs || hasEvs) {
            rowY += POPUP_STATS_GAP
            STAT_LABELS.forEachIndexed { index, label ->
                drawCenteredPopupText(guiGraphics, label, (px + POPUP_STAT_VALUES_X + index * POPUP_STAT_COLUMN_WIDTH).toFloat(), rowY.toFloat(), STAT_COLORS[index])
            }
            rowY += POPUP_STAT_HEADER_GAP
            if (hasIvs) {
                drawPopupStatRow(guiGraphics, "IV", attachment.ivs, px, rowY)
                rowY += POPUP_STAT_ROW_HEIGHT
            }
            if (hasEvs) drawPopupStatRow(guiGraphics, "EV", attachment.evs, px, rowY)
        }
    }

    private fun drawPopupStatRow(guiGraphics: GuiGraphics, label: String, values: List<Int>, x: Int, y: Int) {
        drawPopupText(guiGraphics, label, (x + POPUP_PAD).toFloat(), y.toFloat(), CONTENT_TEXT)
        values.take(STAT_COUNT).forEachIndexed { index, value ->
            drawCenteredPopupText(guiGraphics, value.toString(), (x + POPUP_STAT_VALUES_X + index * POPUP_STAT_COLUMN_WIDTH).toFloat(), y.toFloat(), STAT_COLORS[index])
        }
    }

    private fun drawPopupText(guiGraphics: GuiGraphics, text: String, x: Float, y: Float, color: Int) {
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate(x, y, 0f)
        matrices.scale(POPUP_TEXT_SCALE, POPUP_TEXT_SCALE, 1f)
        guiGraphics.drawString(font, text, 0, 0, color, false)
        matrices.popPose()
    }

    private fun drawCenteredPopupText(guiGraphics: GuiGraphics, text: String, x: Float, y: Float, color: Int) =
        drawPopupText(guiGraphics, text, x - font.width(text) * POPUP_TEXT_SCALE / 2f, y, color)

    private fun popupHeight(attachment: com.nbp.cobblemon_smartphone.social.PokemonAttachment): Int {
        val infoRows = (if (attachment.types.isNotEmpty()) 1 else 0) +
            (if (attachment.ability != null) 1 else 0) + (if (attachment.nature != null) 1 else 0)
        val statRows = (if (attachment.ivs.size == STAT_COUNT) 1 else 0) + (if (attachment.evs.size == STAT_COUNT) 1 else 0)
        val infoBottom = POPUP_INFO_START_Y + infoRows * POPUP_INFO_LINE_HEIGHT
        return if (statRows == 0) (infoBottom + POPUP_BOTTOM_PAD).coerceAtLeast(POPUP_MIN_HEIGHT)
        else infoBottom + POPUP_STATS_GAP + POPUP_STAT_HEADER_GAP + statRows * POPUP_STAT_ROW_HEIGHT + POPUP_BOTTOM_PAD
    }

    private fun renderDmPhoto(guiGraphics: GuiGraphics, photo: com.nbp.cobblemon_smartphone.social.SocialPhotoRef, x: Int, y: Int) {
        guiGraphics.fill(x, y, x + DM_CONTENT_WIDTH, y + DM_PHOTO_HEIGHT, DM_ATTACHMENT_BG)
        val texture = SocialPhotoClient.texture(photo.id)
        if (texture != null) {
            val size = SocialPhotoClient.dimensions(photo.id) ?: (photo.width to photo.height)
            guiGraphics.blit(texture, x, y, DM_CONTENT_WIDTH, DM_PHOTO_HEIGHT, 0f, 0f, size.first, size.second, size.first, size.second)
        } else {
            val label = lang(if (SocialPhotoClient.isRequested(photo.id)) "photo_loading" else "photo_load")
            val bx = x + (DM_CONTENT_WIDTH - DM_PHOTO_BUTTON_WIDTH) / 2
            val by = y + (DM_PHOTO_HEIGHT - DM_PHOTO_BUTTON_HEIGHT) / 2
            guiGraphics.fill(bx, by, bx + DM_PHOTO_BUTTON_WIDTH, by + DM_PHOTO_BUTTON_HEIGHT, BUTTON_BORDER_COLOR)
            guiGraphics.fill(bx + 1, by + 1, bx + DM_PHOTO_BUTTON_WIDTH - 1, by + DM_PHOTO_BUTTON_HEIGHT - 1, SECTION_CONTENT_BG)
            val trimmed = font.plainSubstrByWidth(label, DM_PHOTO_BUTTON_WIDTH - 4)
            guiGraphics.drawString(font, trimmed, bx + (DM_PHOTO_BUTTON_WIDTH - font.width(trimmed)) / 2, by + 3, CONTENT_TEXT, false)
        }
    }

    private fun messageTimestamp(timestamp: Long): String {
        val zone = ZoneId.systemDefault()
        val messageDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val elapsedDays = ChronoUnit.DAYS.between(messageDate, LocalDate.now(zone)).coerceAtLeast(0)
        return when (elapsedDays) {
            0L -> TIME_FORMAT.format(Instant.ofEpochMilli(timestamp))
            1L -> lang("one_day_ago")
            else -> Component.translatable("cobblemon_smartphone.social.days_ago", elapsedDays).string
        }
    }

    private fun renderSendButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val hovered = isInSend(mouseX, mouseY)
        val onCooldown = isOnCooldown()
        val enabled = input.value.isNotBlank() && !onCooldown && pendingRequestId == null
        val bg = when {
            onCooldown -> DISABLED_COLOR
            enabled && hovered -> ACCENT_COLOR
            enabled -> SECTION_CONTENT_BG
            else -> DISABLED_COLOR
        }
        val x = sendX()
        guiGraphics.fill(x, screenY + INPUT_Y, x + SEND_WIDTH, screenY + INPUT_Y + INPUT_HEIGHT, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x + 1, screenY + INPUT_Y + 1, x + SEND_WIDTH - 1, screenY + INPUT_Y + INPUT_HEIGHT - 1, bg)
        val label = if (onCooldown) "${remainingCooldownSec()}s" else ""
        val textColor = if (onCooldown) MUTED_COLOR else TEXT_COLOR
        if (onCooldown) {
            guiGraphics.drawString(
                font,
                label,
                x + (SEND_WIDTH - font.width(label)) / 2,
                screenY + INPUT_Y + (INPUT_HEIGHT - font.lineHeight) / 2 + 1,
                textColor,
                false
            )
        } else {
            SocialUi.drawIcon(
                guiGraphics,
                SocialUi.Icon.SEND,
                x + (SEND_WIDTH - 8) / 2,
                screenY + INPUT_Y + (INPUT_HEIGHT - 8) / 2,
                if (enabled) SocialUi.TEXT else SocialUi.MUTED
            )
        }
    }

    private fun renderAttachButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val x = screenX + CONTENT_X + 2
        val y = screenY + INPUT_Y + 3
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.PLUS, x, y, if (isInAttach(mouseX, mouseY)) ACCENT_COLOR else SocialUi.MUTED)
    }

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

        if (isInBack(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, startOnDms = true))
            return true
        }
        if (isInBellButton(mx, my)) {
            playClickSound()
            MutePlayerPacket(otherUuid, MutedPlayers.toggle(otherUuid)).sendToServer()
            return true
        }
        if (callAvailable() && isInCallButton(mx, my)) {
            playClickSound()
            onCallButton()
            return true
        }
        if (my in (screenY + LIST_START_Y)..(screenY + LIST_END_Y)) {
            val messages = SocialDmCache.messages()
            val total = messages.sumOf { measureBubble(it) + BUBBLE_GAP }
            var y = screenY + LIST_END_Y - total + scrollY
            if (total < LIST_END_Y - LIST_START_Y) y = screenY + LIST_START_Y
            messages.forEach { message ->
                if (isInDmPokemonMore(message, mx, my, y)) {
                    playClickSound()
                    pokemonPopup = message.attachment
                    return true
                }
                if (isInDmPhotoButton(message, mx, my, y)) {
                    playClickSound()
                    message.photo?.let { SocialPhotoClient.request(it.id) }
                    return true
                }
                y += measureBubble(message) + BUBBLE_GAP
            }
        }
        if (isInSend(mx, my)) {
            send()
            return true
        }
        if (isInAttach(mx, my)) {
            playClickSound()
            Minecraft.getInstance().setScreen(
                SocialComposeScreen(color, smartphoneStack, input.value, dmTargetUuid = otherUuid, dmTargetName = otherName)
            )
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == ESCAPE_KEY && pokemonPopup != null) {
            pokemonPopup = null
            return true
        }
        // Enter sends, matching every chat app.
        if (keyCode == ENTER_KEY || keyCode == NUMPAD_ENTER_KEY) {
            send()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        scrollY = (scrollY + (verticalAmount * SCROLL_SPEED).toInt()).coerceIn(0, maxScroll)
        if (scrollY >= maxScroll - LOAD_MORE_THRESHOLD) {
            SocialDmCache.loadOlder()
        }
        return true
    }

    private fun send() {
        if (isOnCooldown() || pendingRequestId != null) return
        val text = input.value.trim()
        if (text.isEmpty()) return
        playClickSound()
        val requestId = SocialMutationState.nextRequestId()
        pendingRequestId = requestId
        SendDmPacket(requestId, otherUuid, text).sendToServer()
    }

    private fun consumeSendResult() {
        val requestId = pendingRequestId ?: return
        val status = SocialMutationState.consume(requestId) ?: return
        pendingRequestId = null
        if (status == SocialMutationResultPacket.Status.SUCCESS) {
            input.value = ""
            lastSendTime = System.currentTimeMillis()
            scrollY = 0
        } else {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.nbp.social.result.${status.name.lowercase()}"), true
            )
        }
    }

    private fun isOnCooldown(): Boolean = remainingCooldownMs() > 0

    private fun remainingCooldownMs(): Long =
        ((messageCooldownSec * 1000L) - (System.currentTimeMillis() - lastSendTime)).coerceAtLeast(0)

    private fun remainingCooldownSec(): Int = ((remainingCooldownMs() + 999) / 1000).toInt()

    private fun sendX() = screenX + CONTENT_X + CONTENT_WIDTH - SEND_WIDTH

    private fun isInSend(mouseX: Int, mouseY: Int): Boolean =
        mouseX in sendX()..(sendX() + SEND_WIDTH) &&
                mouseY in (screenY + INPUT_Y)..(screenY + INPUT_Y + INPUT_HEIGHT)

    private fun isInAttach(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + CONTENT_X + 1)..(screenX + CONTENT_X + 14) &&
            mouseY in (screenY + INPUT_Y)..(screenY + INPUT_Y + INPUT_HEIGHT)

    private fun isInDmPhotoButton(message: DmMessage, mouseX: Int, mouseY: Int, bubbleY: Int): Boolean {
        val photo = message.photo ?: return false
        if (SocialPhotoClient.texture(photo.id) != null) return false
        var contentY = bubbleY + BUBBLE_PAD + wrapped(message).size * font.lineHeight
        if (message.text.isNotBlank()) contentY += BUBBLE_CONTENT_GAP
        if (message.attachment != null) contentY += DM_POKEMON_HEIGHT + BUBBLE_CONTENT_GAP
        val contentX = bubbleX(message) + BUBBLE_PAD
        val bx = contentX + (DM_CONTENT_WIDTH - DM_PHOTO_BUTTON_WIDTH) / 2
        val by = contentY + (DM_PHOTO_HEIGHT - DM_PHOTO_BUTTON_HEIGHT) / 2
        return mouseX in bx..(bx + DM_PHOTO_BUTTON_WIDTH) && mouseY in by..(by + DM_PHOTO_BUTTON_HEIGHT)
    }

    private fun isInDmPokemonMore(message: DmMessage, mouseX: Int, mouseY: Int, bubbleY: Int): Boolean {
        val attachment = message.attachment ?: return false
        if (dmPokemonDetailLines(attachment).isEmpty()) return false
        var contentY = bubbleY + BUBBLE_PAD + wrapped(message).size * font.lineHeight
        if (message.text.isNotBlank()) contentY += BUBBLE_CONTENT_GAP
        val x = bubbleX(message) + BUBBLE_PAD + DM_MORE_X
        val y = contentY + DM_MORE_Y
        return mouseX in x..(x + DM_MORE_BUTTON_WIDTH) && mouseY in y..(y + DM_MORE_BUTTON_HEIGHT)
    }

    private fun popupX() = screenX + CONTENT_X + (CONTENT_WIDTH - POPUP_WIDTH) / 2
    private fun popupY() = screenY + POPUP_Y
    private fun isInPopup(mouseX: Int, mouseY: Int): Boolean =
        mouseX in popupX()..(popupX() + POPUP_WIDTH) &&
            mouseY in popupY()..(popupY() + (pokemonPopup?.let(::popupHeight) ?: 0))
    private fun isInPopupClose(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (popupX() + POPUP_WIDTH - 18)..(popupX() + POPUP_WIDTH) &&
            mouseY in popupY()..(popupY() + POPUP_HEADER_HEIGHT)

    private fun isInBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + CONTENT_X + 2)..(screenX + CONTENT_X + 2 + font.width(lang("back"))) &&
                mouseY in (screenY + TITLE_Y + 2)..(screenY + TITLE_Y + 2 + font.lineHeight)

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltip = when {
            isInBack(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_back_to_social")
            isInBellButton(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_mute_player")
            callAvailable() && isInCallButton(mouseX, mouseY) -> {
                val key = when {
                    CallState.isBusyWith(otherUuid) -> "social_call_end"
                    CallState.status == CallStatus.OUTGOING && CallState.otherUuid == otherUuid -> "social_call_end"
                    else -> "social_call_start"
                }
                Component.translatable("cobblemon_smartphone.tooltip.$key")
            }
            isInSend(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_send")
            else -> null
        } ?: return
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    companion object {
        private const val ENTER_KEY = 257
        private const val NUMPAD_ENTER_KEY = 335
        private const val ESCAPE_KEY = 256

        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        // Lit screen is x 20..191, y 24..194 — everything below stays inside it.
        private const val CONTENT_X = 20
        private const val CONTENT_WIDTH = 171
        private const val TITLE_Y = 27
        private const val LIST_START_Y = 41
        private const val LIST_END_Y = 170

        private const val INPUT_Y = 175
        private const val INPUT_HEIGHT = 14
        private const val SEND_WIDTH = 34
        private const val INPUT_WIDTH = CONTENT_WIDTH - SEND_WIDTH - 3
        private const val INPUT_TEXT_OFFSET_X = 17
        private const val INPUT_TEXT_OFFSET_Y = 3

        private const val BUBBLE_MAX_WIDTH = 130
        private const val BUBBLE_PAD = 4
        private const val BUBBLE_GAP = 3
        private const val TIMESTAMP_HEIGHT = 8
        private const val BUBBLE_CONTENT_GAP = 3
        private const val DM_CONTENT_WIDTH = BUBBLE_MAX_WIDTH - BUBBLE_PAD * 2
        private const val DM_POKEMON_HEIGHT = 44
        private const val DM_MODEL_BOX_X = 3
        private const val DM_MODEL_BOX_Y = 2
        private const val DM_MODEL_WIDTH = 40
        private const val DM_MODEL_HEIGHT = 40
        private const val DM_MODEL_ANCHOR_X = 25
        private const val DM_MODEL_ANCHOR_Y = 5
        private const val DM_MODEL_SCALE = 18f
        private const val DM_ATTACHMENT_TEXT_X = 45
        private const val DM_ATTACHMENT_TEXT_SCALE = 0.75f
        private const val DM_MORE_X = 45
        private const val DM_MORE_Y = 28
        private const val DM_MORE_BUTTON_WIDTH = 42
        private const val DM_MORE_BUTTON_HEIGHT = 11
        private const val DM_PHOTO_HEIGHT = 69
        private const val DM_PHOTO_BUTTON_WIDTH = 72
        private const val DM_PHOTO_BUTTON_HEIGHT = 14

        private const val SCROLL_SPEED = 12
        private const val LOAD_MORE_THRESHOLD = 20

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val TEXT_COLOR = 0xFFE6FFFF.toInt()
        private const val MUTED_COLOR = 0xFF8AA5AD.toInt()
        private const val CONTENT_TEXT = 0xFF1A1A2E.toInt()
        private const val SECTION_CONTENT_BG = 0xFFEFFDFF.toInt()
        private const val OWN_BUBBLE_COLOR = 0xFF3A96B6.toInt()
        private const val OTHER_BUBBLE_COLOR = 0xFFE0E8EC.toInt()
        private const val ACCENT_COLOR = 0xFF3A96B6.toInt()
        private const val DISABLED_COLOR = 0xFFCCCCCC.toInt()
        private const val DANGER_BUTTON_COLOR = 0xFFCC3333.toInt()
        private const val BUTTON_BORDER_COLOR = 0xFF3A96B6.toInt()
        private const val DM_ATTACHMENT_BG = 0xFFEAF6F8.toInt()
        private const val OWN_ATTACHMENT_BORDER = 0xFFFFFFFF.toInt()
        private const val OWN_ATTACHMENT_MUTED = 0xFFBFE5EF.toInt()
        private const val GENDER_MALE_COLOR = 0xFF408CFF.toInt()
        private const val GENDER_FEMALE_COLOR = 0xFFFF5C9A.toInt()
        private const val SHINY_COLOR = 0xFFFFD84A.toInt()
        private const val POPUP_OVERLAY_COLOR = 0x88071322.toInt()
        private const val POPUP_Z = 1_000f
        private const val POPUP_Y = 62
        private const val POPUP_WIDTH = 151
        private const val POPUP_HEADER_HEIGHT = 16
        private const val POPUP_PAD = 7
        private const val POPUP_INNER_WIDTH = POPUP_WIDTH - POPUP_PAD * 2
        private const val POPUP_INFO_START_Y = 32
        private const val POPUP_INFO_LINE_HEIGHT = 10
        private const val POPUP_STATS_GAP = 3
        private const val POPUP_STAT_HEADER_GAP = 11
        private const val POPUP_STAT_ROW_HEIGHT = 10
        private const val POPUP_BOTTOM_PAD = 7
        private const val POPUP_MIN_HEIGHT = 50
        private const val POPUP_STAT_VALUES_X = 31
        private const val POPUP_STAT_COLUMN_WIDTH = 20
        private const val POPUP_TEXT_SCALE = 0.75f
        private const val STAT_COUNT = 6
        private const val POKEBALL_FRAME_COUNT = 16L
        private const val POKEBALL_FRAME_SIZE = 109
        private const val POKEBALL_TEXTURE_WIDTH = 109
        private const val POKEBALL_TEXTURE_HEIGHT = POKEBALL_FRAME_SIZE * POKEBALL_FRAME_COUNT.toInt()
        private const val POKEBALL_FRAME_TIME_MS = 280L
        private const val POKEBALL_TINT_R = 1.5f
        private const val POKEBALL_TINT_G = 3.9f
        private const val POKEBALL_TINT_B = 4.7f
        private const val POKEBALL_TINT_ALPHA = 0.12f
        private const val OWN_POKEBALL_ALPHA = 0.18f

        private const val CALL_BUTTON_WIDTH = 30
        private const val CALL_BUTTON_HEIGHT = 12
        private const val CALL_BUTTON_TEXT = "CALL"
        private const val CALL_BELL_GAP = 3
        private const val CALL_NOTICE_DURATION_MS = 3_000L
        private const val CALL_NOTICE_Y = 92
        private const val CALL_NOTICE_HEIGHT = 17
        private const val CALL_NOTICE_PADDING_X = 6
        private const val CALL_NOTICE_BG = 0xE62B3548.toInt()
        private const val BELL_WIDTH = 12
        private const val BELL_HEIGHT = 11
        private const val MUTE_ACTIVE_COLOR = 0xFFAA3333.toInt()
        private const val MUTE_SLASH_COLOR = 0xFFFF3030.toInt()

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
        private val POKEBALL_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone", "textures/gui/social/background_poke_ball.png"
        )
        private val POKEBALL_BACKGROUND_WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone", "textures/gui/social/background_poke_ball_white.png"
        )
        private val STAT_LABELS = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        private val STAT_COLORS = listOf(
            0xFF2EAD5B.toInt(), 0xFFE34B4B.toInt(), 0xFF82C91E.toInt(),
            0xFFFF8C32.toInt(), 0xFFE0B928.toInt(), 0xFF3F7FE5.toInt()
        )
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }
}
