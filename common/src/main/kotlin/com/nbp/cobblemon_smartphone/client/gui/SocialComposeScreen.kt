package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonClient
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.client.social.SocialMutationState
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CreatePostPacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import java.util.UUID

/**
 * Compose screen for a new post.
 *
 * Uses [MultiLineEditBox] rather than the single-line EditBox the search screens use: 280
 * characters on one scrolling line would be unusable.
 *
 * The attachment picker only ever yields a party *slot index*, which is what gets sent — the
 * server reads its own copy of the party and builds the snapshot, so the attachment can't be forged.
 */
class SocialComposeScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack? = null,
    private val initialText: String = "",
    private var photoId: UUID? = null,
    private val dmTargetUuid: UUID? = null,
    private val dmTargetName: String? = null
) : Screen(Component.literal("New Post")) {
    private enum class AttachmentMode { NONE, POKEMON, PHOTO }

    private var pendingRequestId: Long? = null

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var selectedSlot = -1
    private var attachmentMode = if (photoId != null) AttachmentMode.PHOTO else AttachmentMode.NONE
    private var showDetails = false
    private var showIvs = false
    private var showEvs = false
    private var showAbility = false
    private var showNature = false
    private val partyModels = mutableMapOf<UUID, RenderablePokemon>()
    private val partyModelState = FloatingState()
    private lateinit var editBox: MultiLineEditBox

    private val isDmCompose get() = dmTargetUuid != null
    private val maxLength get() = if (isDmCompose) SocialClientSession.capabilities.maxMessageLength else SocialClientSession.capabilities.maxPostLength

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        editBox = object : MultiLineEditBox(
            font,
            screenX + CONTENT_X,
            screenY + EDIT_Y,
            CONTENT_WIDTH,
            EDIT_HEIGHT,
            Component.translatable("cobblemon_smartphone.social.placeholder"),
            Component.translatable("cobblemon_smartphone.social.compose_title")
        ) {
            override fun renderDecorations(guiGraphics: GuiGraphics) {
                val counter = "${value.length}/$maxLength"
                guiGraphics.drawString(
                    this@SocialComposeScreen.font,
                    counter,
                    screenX + CONTENT_X + CONTENT_WIDTH - this@SocialComposeScreen.font.width(counter) - 4,
                    screenY + EDIT_Y + EDIT_HEIGHT - this@SocialComposeScreen.font.lineHeight - 2,
                    if (value.length >= maxLength) DANGER_COLOR else SocialUi.TEXT,
                    false
                )
            }
        }
        editBox.setCharacterLimit(maxLength)
        editBox.setValue(initialText.take(maxLength))
        addRenderableWidget(editBox)
        setInitialFocus(editBox)
    }

    override fun removed() {
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        consumePostResult()
        val matrices = guiGraphics.pose()

        blitk(matrixStack = matrices, texture = frameTexture, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)
        blitk(matrixStack = matrices, texture = SCREEN_TEXTURE, x = screenX, y = screenY, width = GUI_WIDTH, height = GUI_HEIGHT)

        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + 38, SocialUi.NAVY)
        SocialUi.drawIcon(guiGraphics, if (isDmCompose) SocialUi.Icon.MESSAGE else SocialUi.Icon.FEED, screenX + CONTENT_X + 5, screenY + TITLE_Y - 1, SocialUi.GOLD)
        guiGraphics.drawString(font, lang(if (isDmCompose) "new_message" else "compose_title"), screenX + CONTENT_X + 15, screenY + TITLE_Y, TITLE_COLOR, false)

        // MultiLineEditBox exposes render() publicly; renderContents() is protected.
        val ex = screenX + CONTENT_X
        val ey = screenY + EDIT_Y
        SocialUi.surface(guiGraphics, ex, ey, CONTENT_WIDTH, EDIT_HEIGHT)
        guiGraphics.fill(ex, ey, ex + CONTENT_WIDTH, ey + 1, ACCENT_COLOR)
        guiGraphics.fill(ex, ey + EDIT_HEIGHT - 1, ex + CONTENT_WIDTH, ey + EDIT_HEIGHT, ACCENT_COLOR)
        guiGraphics.fill(ex, ey, ex + 1, ey + EDIT_HEIGHT, ACCENT_COLOR)
        guiGraphics.fill(ex + CONTENT_WIDTH - 1, ey, ex + CONTENT_WIDTH, ey + EDIT_HEIGHT, ACCENT_COLOR)
        editBox.render(guiGraphics, mouseX, mouseY, delta)

        renderAttachmentButtons(guiGraphics, mouseX, mouseY)
        when (attachmentMode) {
            AttachmentMode.POKEMON -> {
                renderPartyPicker(guiGraphics, mouseX, mouseY)
                renderPrivacyOptions(guiGraphics)
            }
            AttachmentMode.PHOTO -> renderPhotoPreview(guiGraphics)
            AttachmentMode.NONE -> Unit
        }
        renderFooterButtons(guiGraphics, mouseX, mouseY)

        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun partySlots() = CobblemonClient.storage.party.slots

    private fun renderAttachmentButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        drawAttachmentButton(guiGraphics, lang("attach_pokemon"), attachPokemonX(), attachmentMode == AttachmentMode.POKEMON, isInAttachPokemon(mouseX, mouseY))
        drawAttachmentButton(guiGraphics, lang("photo"), photoButtonX(), attachmentMode == AttachmentMode.PHOTO, isInPhotoButton(mouseX, mouseY))
    }

    private fun drawAttachmentButton(guiGraphics: GuiGraphics, label: String, x: Int, active: Boolean, hovered: Boolean) {
        val y = screenY + ATTACH_BUTTON_Y
        SocialUi.surface(guiGraphics, x, y, ATTACH_BUTTON_WIDTH, ATTACH_BUTTON_HEIGHT, hovered)
        if (active) guiGraphics.fill(x + 1, y + 1, x + ATTACH_BUTTON_WIDTH - 1, y + ATTACH_BUTTON_HEIGHT - 1, ACCENT_COLOR)
        val trimmed = font.plainSubstrByWidth(label, ATTACH_BUTTON_WIDTH - 6)
        guiGraphics.drawString(font, trimmed, x + (ATTACH_BUTTON_WIDTH - font.width(trimmed)) / 2, y + 2, if (active) TITLE_COLOR else CONTENT_TEXT, false)
    }

    private fun renderPhotoPreview(guiGraphics: GuiGraphics) {
        val id = photoId ?: return
        val x = screenX + CONTENT_X + (CONTENT_WIDTH - PHOTO_PREVIEW_WIDTH) / 2
        val y = screenY + PHOTO_PREVIEW_Y
        guiGraphics.fill(x - 1, y - 1, x + PHOTO_PREVIEW_WIDTH + 1, y + PHOTO_PREVIEW_HEIGHT + 1, ACCENT_COLOR)
        SocialPhotoClient.texture(id)?.let {
            val dimensions = SocialPhotoClient.dimensions(id) ?: (PHOTO_PREVIEW_WIDTH to PHOTO_PREVIEW_HEIGHT)
            guiGraphics.fill(x, y, x + PHOTO_PREVIEW_WIDTH, y + PHOTO_PREVIEW_HEIGHT, SLOT_BG_COLOR)
            guiGraphics.blit(
                it, x, y, PHOTO_PREVIEW_WIDTH, PHOTO_PREVIEW_HEIGHT, 0f, 0f,
                dimensions.first, dimensions.second, dimensions.first, dimensions.second
            )
        }
        val status = when {
            SocialPhotoClient.didUploadFail(id) -> lang("photo_failed")
            SocialPhotoClient.isUploadReady(id) -> null
            else -> lang("photo_uploading")
        }
        status?.let {
            guiGraphics.drawString(font, it, screenX + CONTENT_X + (CONTENT_WIDTH - font.width(it)) / 2, screenY + PHOTO_STATUS_Y, if (SocialPhotoClient.didUploadFail(id)) DANGER_COLOR else CONTENT_DIM, false)
        }
    }

    private fun renderPartyPicker(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        partySlots().forEachIndexed { index, pokemon ->
            val (x, y) = slotPos(index)
            val hovered = isInSlot(mouseX, mouseY, index)
            val selected = selectedSlot == index
            SocialUi.surface(guiGraphics, x, y, SLOT_WIDTH, SLOT_HEIGHT, hovered && pokemon != null)

            pokemon?.let {
                val model = partyModels.getOrPut(it.uuid) { it.asRenderablePokemon() }
                renderPartyModel(guiGraphics, model, x, y)
            }

            val label = pokemon?.let { it.nickname?.string ?: it.species.name } ?: "-"
            val trimmed = font.plainSubstrByWidth(label, SLOT_TEXT_WIDTH)
            guiGraphics.drawString(
                font,
                trimmed,
                x + 2,
                y + 2,
                when {
                    pokemon == null -> CONTENT_DIM
                    else -> CONTENT_TEXT
                },
                false
            )
            pokemon?.let {
                guiGraphics.drawString(font, "Lv${it.level}", x + 2, y + 10, CONTENT_DIM, false)
            }
            if (selected) {
                guiGraphics.fill(x, y, x + SLOT_WIDTH, y + 1, SocialUi.GOLD)
                guiGraphics.fill(x, y + SLOT_HEIGHT - 1, x + SLOT_WIDTH, y + SLOT_HEIGHT, SocialUi.GOLD)
                guiGraphics.fill(x, y, x + 1, y + SLOT_HEIGHT, SocialUi.GOLD)
                guiGraphics.fill(x + SLOT_WIDTH - 1, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, SocialUi.GOLD)
            }
        }
    }

    private fun renderPartyModel(guiGraphics: GuiGraphics, model: RenderablePokemon, x: Int, y: Int) {
        guiGraphics.enableScissor(x + 1, y + 1, x + SLOT_WIDTH - 1, y + SLOT_HEIGHT - 1)
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate((x + SLOT_MODEL_X).toFloat(), (y + SLOT_MODEL_Y).toFloat(), 0f)
        drawProfilePokemon(
            model,
            matrices,
            Quaternionf().rotateY(Math.toRadians(25.0).toFloat()),
            PoseType.PROFILE,
            partyModelState,
            0f,
            SLOT_MODEL_SCALE
        )
        matrices.popPose()
        guiGraphics.disableScissor()
    }

    private fun renderFooterButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        drawButton(guiGraphics, lang("cancel"), cancelX(), screenY + BUTTON_Y, isInCancel(mouseX, mouseY), false)
        val postLabel = if (!isDmCompose && isOnPostCooldown()) "${remainingPostCooldownSec()}s" else lang(if (isDmCompose) "send" else "post")
        drawButton(guiGraphics, postLabel, postX(), screenY + BUTTON_Y, isInPost(mouseX, mouseY), canPost())
    }

    private fun renderPrivacyOptions(guiGraphics: GuiGraphics) {
        val available = selectedSlot >= 0
        drawCheckbox(guiGraphics, OPTION_SHOW_MORE_X, OPTIONS_Y, lang("show_more"), showDetails, available)
        drawCheckbox(guiGraphics, OPTION_IVS_X, OPTIONS_DETAIL_Y, "IVs", showIvs, available && showDetails)
        drawCheckbox(guiGraphics, OPTION_EVS_X, OPTIONS_DETAIL_Y, "EVs", showEvs, available && showDetails)
        drawCheckbox(guiGraphics, OPTION_ABILITY_X, OPTIONS_DETAIL_Y, lang("ability"), showAbility, available && showDetails)
        drawCheckbox(guiGraphics, OPTION_NATURE_X, OPTIONS_DETAIL_Y, lang("nature"), showNature, available && showDetails)
    }

    private fun drawCheckbox(guiGraphics: GuiGraphics, relativeX: Int, relativeY: Int, label: String, checked: Boolean, enabled: Boolean) {
        val x = screenX + CONTENT_X + relativeX
        val y = screenY + relativeY
        val border = if (enabled) ACCENT_COLOR else CONTENT_DIM
        guiGraphics.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, border)
        guiGraphics.fill(x + 1, y + 1, x + CHECKBOX_SIZE - 1, y + CHECKBOX_SIZE - 1, SLOT_BG_COLOR)
        if (checked && enabled) {
            guiGraphics.fill(x + 2, y + 3, x + 4, y + 5, ACCENT_COLOR)
            guiGraphics.fill(x + 4, y + 2, x + 6, y + 4, ACCENT_COLOR)
        }
        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate((x + CHECKBOX_SIZE + 3).toFloat(), (y + 1).toFloat(), 0f)
        val textScale = if (relativeY == OPTIONS_DETAIL_Y) OPTION_DETAIL_TEXT_SCALE else OPTION_TEXT_SCALE
        matrices.scale(textScale, textScale, 1f)
        guiGraphics.drawString(font, label, 0, 0, if (enabled) CONTENT_TEXT else CONTENT_DIM, false)
        matrices.popPose()
    }

    private fun drawButton(guiGraphics: GuiGraphics, label: String, x: Int, y: Int, hovered: Boolean, primary: Boolean) {
        val bg = when {
            primary && hovered -> ACCENT_COLOR
            primary -> ACCENT_COLOR
            hovered -> SLOT_HOVER_COLOR
            else -> SLOT_BG_COLOR
        }
        SocialUi.surface(guiGraphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, hovered)
        if (primary) guiGraphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, bg)
        guiGraphics.drawString(
            font,
            label,
            x + (BUTTON_WIDTH - font.width(label)) / 2,
            y + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1,
            if (primary) TITLE_COLOR else CONTENT_TEXT,
            false
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (isInCancel(mx, my)) {
            playClickSound()
            back()
            return true
        }
        if (isInPost(mx, my)) {
            if (!canPost()) return true
            playClickSound()
            val requestId = SocialMutationState.nextRequestId()
            pendingRequestId = requestId
            val slot = selectedSlot.takeIf { attachmentMode == AttachmentMode.POKEMON } ?: -1
            val readyPhoto = photoId?.takeIf { attachmentMode == AttachmentMode.PHOTO && SocialPhotoClient.isUploadReady(it) }
            if (dmTargetUuid != null) {
                SendDmPacket(
                    requestId, dmTargetUuid, editBox.value.trim(), slot, showDetails,
                    showDetails && showIvs, showDetails && showEvs, showDetails && showAbility,
                    showDetails && showNature, readyPhoto
                ).sendToServer()
            } else {
                CreatePostPacket(
                    requestId, editBox.value.trim(), slot, showDetails,
                    showDetails && showIvs, showDetails && showEvs, showDetails && showAbility,
                    showDetails && showNature, readyPhoto
                ).sendToServer()
            }
            return true
        }

        if (isInAttachPokemon(mx, my)) {
            playClickSound()
            photoId?.let(SocialPhotoClient::removeDraft)
            photoId = null
            attachmentMode = AttachmentMode.POKEMON
            return true
        }
        if (isInPhotoButton(mx, my)) {
            if (pendingRequestId != null) return true
            playClickSound()
            selectedSlot = -1
            photoId?.let(SocialPhotoClient::removeDraft)
            photoId = null
            SocialPhotoClient.beginCapture(color, smartphoneStack, editBox.value, dmTargetUuid, dmTargetName)
            return true
        }

        if (selectedSlot >= 0 && isInOption(mx, my, OPTION_SHOW_MORE_X, OPTIONS_Y, OPTION_SHOW_MORE_WIDTH)) {
            playClickSound()
            showDetails = !showDetails
            return true
        }
        if (selectedSlot >= 0 && showDetails) {
            when {
                isInOption(mx, my, OPTION_IVS_X, OPTIONS_DETAIL_Y, OPTION_IVS_WIDTH) -> showIvs = !showIvs
                isInOption(mx, my, OPTION_EVS_X, OPTIONS_DETAIL_Y, OPTION_EVS_WIDTH) -> showEvs = !showEvs
                isInOption(mx, my, OPTION_ABILITY_X, OPTIONS_DETAIL_Y, OPTION_ABILITY_WIDTH) -> showAbility = !showAbility
                isInOption(mx, my, OPTION_NATURE_X, OPTIONS_DETAIL_Y, OPTION_NATURE_WIDTH) -> showNature = !showNature
                else -> null
            }?.let {
                playClickSound()
                return true
            }
        }

        if (attachmentMode == AttachmentMode.POKEMON) partySlots().forEachIndexed { index, pokemon ->
            if (pokemon != null && isInSlot(mx, my, index)) {
                playClickSound()
                // Clicking the selected slot clears the attachment.
                selectedSlot = if (selectedSlot == index) -1 else index
                showDetails = false
                showIvs = false
                showEvs = false
                showAbility = false
                showNature = false
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun canPost(): Boolean {
        val hasPokemon = attachmentMode == AttachmentMode.POKEMON && selectedSlot >= 0
        val hasReadyPhoto = attachmentMode == AttachmentMode.PHOTO && photoId?.let(SocialPhotoClient::isUploadReady) == true
        return pendingRequestId == null && (isDmCompose || !isOnPostCooldown()) && (editBox.value.isNotBlank() || hasPokemon || hasReadyPhoto) &&
            (attachmentMode != AttachmentMode.PHOTO || photoId == null || hasReadyPhoto)
    }

    private fun consumePostResult() {
        val requestId = pendingRequestId ?: return
        val status = SocialMutationState.consume(requestId) ?: return
        pendingRequestId = null
        if (status == SocialMutationResultPacket.Status.SUCCESS) {
            if (!isDmCompose) lastPostTime = System.currentTimeMillis()
            photoId?.let(SocialPhotoClient::removeDraft)
            back()
        } else {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.nbp.social.result.${status.name.lowercase()}"), true
            )
        }
    }

    private fun back() {
        val target = dmTargetUuid
        Minecraft.getInstance().setScreen(
            if (target != null) DmThreadScreen(color, smartphoneStack, target, dmTargetName ?: target.toString().take(8))
            else SocialScreen(color, smartphoneStack)
        )
    }

    // --- Hit testing ---

    private fun slotPos(index: Int): Pair<Int, Int> {
        val col = index % PICKER_COLUMNS
        val row = index / PICKER_COLUMNS
        return screenX + CONTENT_X + col * (SLOT_WIDTH + SLOT_GAP) to screenY + PICKER_Y + row * (SLOT_HEIGHT + SLOT_GAP)
    }

    private fun isInSlot(mouseX: Int, mouseY: Int, index: Int): Boolean {
        val (x, y) = slotPos(index)
        return mouseX in x..(x + SLOT_WIDTH) && mouseY in y..(y + SLOT_HEIGHT)
    }

    private fun isInOption(mouseX: Int, mouseY: Int, relativeX: Int, relativeY: Int, width: Int): Boolean {
        val x = screenX + CONTENT_X + relativeX
        val y = screenY + relativeY
        return mouseX in x..(x + width) && mouseY in (y - 1)..(y + CHECKBOX_SIZE + 1)
    }

    private fun cancelX() = screenX + CONTENT_X
    private fun postX() = screenX + CONTENT_X + CONTENT_WIDTH - BUTTON_WIDTH

    private fun isInCancel(mouseX: Int, mouseY: Int) = inButton(mouseX, mouseY, cancelX())
    private fun isInPost(mouseX: Int, mouseY: Int) = inButton(mouseX, mouseY, postX())

    private fun attachPokemonX() = screenX + CONTENT_X
    private fun photoButtonX() = screenX + CONTENT_X + CONTENT_WIDTH - ATTACH_BUTTON_WIDTH
    private fun isInAttachPokemon(mouseX: Int, mouseY: Int) = inAttachButton(mouseX, mouseY, attachPokemonX())
    private fun isInPhotoButton(mouseX: Int, mouseY: Int) = inAttachButton(mouseX, mouseY, photoButtonX())
    private fun inAttachButton(mouseX: Int, mouseY: Int, x: Int) =
        mouseX in x..(x + ATTACH_BUTTON_WIDTH) && mouseY in (screenY + ATTACH_BUTTON_Y)..(screenY + ATTACH_BUTTON_Y + ATTACH_BUTTON_HEIGHT)

    private fun inButton(mouseX: Int, mouseY: Int, x: Int): Boolean =
        mouseX in x..(x + BUTTON_WIDTH) && mouseY in (screenY + BUTTON_Y)..(screenY + BUTTON_Y + BUTTON_HEIGHT)

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltip = when {
            isInCancel(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_cancel")
            isInPost(mouseX, mouseY) -> Component.translatable("cobblemon_smartphone.tooltip.social_create_post")
            findHoveredSlot(mouseX, mouseY) != null -> Component.translatable("cobblemon_smartphone.tooltip.social_select_pokemon")
            else -> null
        } ?: return
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun findHoveredSlot(mouseX: Int, mouseY: Int): Int? =
        partySlots().indices.firstOrNull { index ->
            partySlots()[index] != null && isInSlot(mouseX, mouseY, index)
        }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    companion object {
        var lastPostTime = 0L
        private val postCooldownSec get() = SocialClientSession.capabilities.postCooldownSeconds

        fun isOnPostCooldown(): Boolean = remainingPostCooldownMs() > 0

        private fun remainingPostCooldownMs(): Long =
            ((postCooldownSec * 1000L) - (System.currentTimeMillis() - lastPostTime)).coerceAtLeast(0)

        fun remainingPostCooldownSec(): Int = ((remainingPostCooldownMs() + 999) / 1000).toInt()

        private const val GUI_WIDTH = 211
        private const val GUI_HEIGHT = 207

        // The 211x207 texture is the whole phone including the bezel. The lit screen is only
        // x 20..191 and y 24..194 — anything drawn outside this lands on the frame, so every Y
        // below stays within 24..194 and the picker row math must not overflow the bottom.
        private const val SCREEN_LEFT = 20

        private const val CONTENT_X = SCREEN_LEFT
        private const val CONTENT_WIDTH = 165

        private const val TITLE_Y = 27
        private const val EDIT_Y = 40
        private const val EDIT_HEIGHT = 48
        private const val COUNTER_Y = 98
        private const val ATTACH_BUTTON_Y = 92
        private const val ATTACH_BUTTON_WIDTH = 80
        private const val ATTACH_BUTTON_HEIGHT = 10
        private const val PICKER_Y = 104
        private const val PHOTO_PREVIEW_Y = 107
        private const val PHOTO_PREVIEW_WIDTH = 106
        private const val PHOTO_PREVIEW_HEIGHT = 59
        private const val PHOTO_STATUS_Y = 166

        // 3 * SLOT_WIDTH + 2 * SLOT_GAP == CONTENT_WIDTH
        private const val PICKER_COLUMNS = 3
        private const val SLOT_WIDTH = 53
        private const val SLOT_HEIGHT = 20
        private const val SLOT_GAP = 2
        private const val SLOT_TEXT_WIDTH = 32
        private const val SLOT_MODEL_X = 43
        private const val SLOT_MODEL_Y = 4
        private const val SLOT_MODEL_SCALE = 7f

        private const val OPTIONS_Y = 149
        private const val OPTIONS_DETAIL_Y = 161
        private const val CHECKBOX_SIZE = 7
        private const val OPTION_SHOW_MORE_X = 0
        private const val OPTION_IVS_X = 0
        private const val OPTION_EVS_X = 41
        private const val OPTION_ABILITY_X = 82
        private const val OPTION_NATURE_X = 123
        private const val OPTION_SHOW_MORE_WIDTH = 66
        private const val OPTION_IVS_WIDTH = 38
        private const val OPTION_EVS_WIDTH = 38
        private const val OPTION_ABILITY_WIDTH = 38
        private const val OPTION_NATURE_WIDTH = 42
        private const val OPTION_TEXT_SCALE = 0.75f
        private const val OPTION_DETAIL_TEXT_SCALE = 0.58f

        private const val BUTTON_Y = 176
        private const val BUTTON_WIDTH = 52
        private const val BUTTON_HEIGHT = 14

        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val TEXT_COLOR = 0xFFE6FFFF.toInt()
        private const val MUTED_COLOR = 0xFF8AA5AD.toInt()
        private const val CONTENT_TEXT = 0xFF1A1A2E.toInt()
        private const val CONTENT_DIM = 0xFF555555.toInt()
        private const val SECTION_CONTENT_BG = 0xFFEFFDFF.toInt()
        private const val SLOT_BG_COLOR = 0xFFEFFDFF.toInt()
        private const val SLOT_HOVER_COLOR = 0xFFD0E8F5.toInt()
        private const val ACCENT_COLOR = 0xFF3A96B6.toInt()
        private const val DANGER_COLOR = 0xFFFD0100.toInt()

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
    }
}
