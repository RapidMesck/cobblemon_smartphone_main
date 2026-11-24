package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.client.social.SocialMutationState
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdCanvas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CreatePostPacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * HD post & DM attachment composer built on [HdPhoneScreen].
 *
 * The attachment picker only ever yields a party slot index; the server reads its own copy of the
 * party to build the snapshot so attachments cannot be forged.
 */
class SocialComposeScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    private val initialText: String = "",
    private var photoId: UUID? = null,
    private val dmTargetUuid: UUID? = null,
    private val dmTargetName: String? = null
) : HdPhoneScreen(color, smartphoneStack, Component.literal("New Post")) {

    private enum class AttachmentMode { NONE, POKEMON, PHOTO, POLL }

    private var pendingRequestId: Long? = null
    private var selectedSlot = -1
    private var attachmentMode = if (photoId != null) AttachmentMode.PHOTO else AttachmentMode.POKEMON
    private var showDetails = false
    private var showIvs = false
    private var showEvs = false
    private var showAbility = false
    private var showNature = false
    private val partyModelState = FloatingState()

    private val isDmCompose get() = dmTargetUuid != null
    private val maxLength get() = if (isDmCompose) SocialClientSession.capabilities.maxMessageLength else SocialClientSession.capabilities.maxPostLength

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelBackButton: Boolean = false

    override fun init() {
        super.init()
        stateStore.input("compose_body", initialText.take(maxLength))
    }

    override fun onBackPressed() {
        val target = dmTargetUuid
        Minecraft.getInstance().setScreen(
            if (target != null) DmThreadScreen(color, smartphoneStack, target, dmTargetName ?: target.toString().take(8))
            else SocialScreen(color, smartphoneStack)
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        consumePostResult()
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        // Floating Island Header
        builder.appHeader(
            title = lang(if (isDmCompose) "new_message" else "compose_title"),
            icon = if (isDmCompose) HdIconAtlas.Icon.MESSAGE else HdIconAtlas.Icon.EDIT,
            iconColor = HdColorSpec.Token("primary")
        )

        builder.scroll(
            scrollId = "compose_scroll",
            gap = 7f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 6f)
        ) { sb ->
            // Multi-line text input
            sb.textInput(
                inputId = "compose_body",
                placeholder = lang("placeholder"),
                initialText = initialText.take(maxLength),
                maxLength = maxLength,
                multiline = true,
                showCounter = true
            ) {
                width = HdSizeSpec.Fill
                height = HdSizeSpec.Fixed(74f)
                fontSizeDp = 9.4f
                padding = HdPadding.all(8f)
            }

            // Attachment mode pills
            sb.row(gap = 6f, width = HdSizeSpec.Fill) { ab ->
                ab.pill(
                    label = lang("attach_pokemon"),
                    icon = HdIconAtlas.Icon.SPARKLES,
                    active = attachmentMode == AttachmentMode.POKEMON,
                    onClick = {
                        playClickSound()
                        photoId?.let(SocialPhotoClient::removeDraft)
                        photoId = null
                        attachmentMode = if (attachmentMode == AttachmentMode.POKEMON) AttachmentMode.NONE else AttachmentMode.POKEMON
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }

                ab.pill(
                    label = lang("photo"),
                    icon = HdIconAtlas.Icon.CAMERA,
                    active = attachmentMode == AttachmentMode.PHOTO,
                    onClick = {
                        if (pendingRequestId != null) return@pill
                        playClickSound()
                        selectedSlot = -1
                        photoId?.let(SocialPhotoClient::removeDraft)
                        photoId = null
                        attachmentMode = AttachmentMode.PHOTO
                        val bodyText = stateStore.getInputText("compose_body")
                        SocialPhotoClient.beginCapture(color, smartphoneStack, bodyText, dmTargetUuid, dmTargetName)
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }

                if (!isDmCompose) {
                    ab.pill(
                        label = lang("poll"),
                        icon = HdIconAtlas.Icon.POLL,
                        active = attachmentMode == AttachmentMode.POLL,
                        onClick = {
                            playClickSound()
                            selectedSlot = -1
                            photoId?.let(SocialPhotoClient::removeDraft)
                            photoId = null
                            attachmentMode = if (attachmentMode == AttachmentMode.POLL) AttachmentMode.NONE else AttachmentMode.POLL
                        }
                    ) {
                        width = HdSizeSpec.Weight(1f)
                    }
                }
            }

            // Attachment section
            when (attachmentMode) {
                AttachmentMode.POKEMON -> buildPokemonPicker(sb)
                AttachmentMode.PHOTO -> buildPhotoPreview(sb)
                AttachmentMode.POLL -> buildPollEditor(sb)
                AttachmentMode.NONE -> Unit
            }
        }

        // Floating footer bar: Cancel / Post
        builder.column(
            padding = HdPadding.only(left = 7f, right = 7f, top = 3f, bottom = 7f),
            width = HdSizeSpec.Fill
        ) { wrap ->
            wrap.row(
                gap = 8f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.all(5f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 12f,
                    borderWidth = 0.85f,
                    borderColor = HdColorSpec.Token("primary_border"),
                    shadowBlur = 6f,
                    shadowOffsetY = 2f,
                    shadowColor = HdColorSpec.Fixed(0x28000000)
                )
            ) { fb ->
                fb.button(lang("cancel"), variant = ButtonNode.Variant.SURFACE, radius = 8f) {
                    width = HdSizeSpec.Weight(1f)
                    tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_cancel").string
                    onClick = {
                        playClickSound()
                        onBackPressed()
                    }
                }
                val postLabel = if (!isDmCompose && isOnPostCooldown()) "${remainingPostCooldownSec()}s" else lang(if (isDmCompose) "send" else "post")
                fb.button(postLabel, icon = HdIconAtlas.Icon.SEND, variant = ButtonNode.Variant.PRIMARY, radius = 8f, enabled = canPost()) {
                    width = HdSizeSpec.Weight(1f)
                    tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_create_post").string
                    onClick = { submitPost() }
                }
            }
        }
    }

    private fun buildPokemonPicker(sb: HdBuilder) {
        val slots = CobblemonClient.storage.party.slots
        sb.grid(columns = 3, colGap = 5f, rowGap = 5f, width = HdSizeSpec.Fill) { gb ->
            for (index in 0 until 6) {
                val pokemon = slots.getOrNull(index)
                val selected = selectedSlot == index
                val borderCol = if (selected) HdColorSpec.Token("gold") else HdColorSpec.Token("primary_border")
                val bgCol = if (selected) HdColorSpec.Token("surface_hover") else HdColorSpec.Token("surface")
                gb.row(
                    gap = 4f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(horizontal = 5f, vertical = 4f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(
                        color = bgCol,
                        radius = 8f,
                        borderWidth = if (selected) 1.3f else 0.8f,
                        borderColor = borderCol
                    )
                ) { slotRow ->
                    this.height = HdSizeSpec.Fixed(38f)
                    if (pokemon != null) {
                        this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_select_pokemon").string
                        this.onClick = {
                            playClickSound()
                            selectedSlot = if (selectedSlot == index) -1 else index
                            showDetails = false
                            showIvs = false
                            showEvs = false
                            showAbility = false
                            showNature = false
                        }
                    }
                    slotRow.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { tb ->
                        val label = pokemon?.let { it.nickname?.string ?: it.species.name } ?: "—"
                        tb.text(label, size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token(if (pokemon != null) "text_dark" else "text_dark_muted"))
                        if (pokemon != null) {
                            tb.text("Lv.${pokemon.level}", size = 7.6f, color = HdColorSpec.Token("text_dark_muted"))
                        }
                    }
                    if (pokemon != null) {
                        slotRow.pokemonView(
                            speciesId = pokemon.species.resourceIdentifier.toString(),
                            aspects = pokemon.aspects,
                            size = 28f,
                            floatingState = partyModelState,
                            showShinyBadge = false
                        )
                    }
                }
            }
        }

        // Privacy options
        val available = selectedSlot >= 0
        sb.card(gap = 4f, padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f), shadowBlur = 3f) { pb ->
            pb.checkbox(lang("show_more"), showDetails, enabled = available, onToggle = {
                playClickSound()
                showDetails = it
            })
            if (available && showDetails) {
                pb.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { dr ->
                    dr.checkbox("IVs", showIvs, enabled = true, fontSize = 8f, onToggle = { playClickSound(); showIvs = it }) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    dr.checkbox("EVs", showEvs, enabled = true, fontSize = 8f, onToggle = { playClickSound(); showEvs = it }) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    dr.checkbox(lang("ability"), showAbility, enabled = true, fontSize = 8f, onToggle = { playClickSound(); showAbility = it }) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    dr.checkbox(lang("nature"), showNature, enabled = true, fontSize = 8f, onToggle = { playClickSound(); showNature = it }) {
                        width = HdSizeSpec.Weight(1f)
                    }
                }
            }
        }
    }

    private fun buildPhotoPreview(sb: HdBuilder) {
        val id = photoId ?: return
        sb.card(gap = 5f, padding = HdPadding.all(8f), shadowBlur = 4f) { pb ->
            val tex = SocialPhotoClient.texture(id)
            if (tex != null) {
                pb.row(mainAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { ir ->
                    ir.image(tex, 200f, 112f, radius = 7f) {
                        borderWidth = 0.8f
                        borderColor = HdColorSpec.Token("primary_border")
                    }
                }
            }
            val status = when {
                SocialPhotoClient.didUploadFail(id) -> lang("photo_failed") to HdColorSpec.Token("danger")
                SocialPhotoClient.isUploadReady(id) -> null
                else -> lang("photo_uploading") to HdColorSpec.Token("text_dark_muted")
            }
            status?.let { (msg, col) ->
                pb.text(msg, size = 8.6f, weight = HdTextLayout.Weight.MEDIUM, color = col, align = HdAlign.CENTER) {
                    width = HdSizeSpec.Fill
                }
            }
        }
    }

    private fun buildPollEditor(sb: HdBuilder) {
        if (isDmCompose) return
        sb.card(gap = 5f, padding = HdPadding.all(8f), shadowBlur = 4f) { pb ->
            pb.textInput("poll_q", placeholder = Component.translatable("cobblemon_smartphone.social.poll_question_hint").string, maxLength = 120) {
                fontSizeDp = 9f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
            }
            pb.textInput("poll_opt1", placeholder = Component.translatable("cobblemon_smartphone.social.poll_opt1_hint").string, maxLength = 50) {
                fontSizeDp = 8.8f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
            }
            pb.textInput("poll_opt2", placeholder = Component.translatable("cobblemon_smartphone.social.poll_opt2_hint").string, maxLength = 50) {
                fontSizeDp = 8.8f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
            }
            pb.textInput("poll_opt3", placeholder = Component.translatable("cobblemon_smartphone.social.poll_opt3_hint").string, maxLength = 50) {
                fontSizeDp = 8.8f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
            }
            pb.textInput("poll_opt4", placeholder = Component.translatable("cobblemon_smartphone.social.poll_opt4_hint").string, maxLength = 50) {
                fontSizeDp = 8.8f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
            }
        }
    }

    private fun canPost(): Boolean {
        val body = stateStore.getInputText("compose_body")
        val hasPokemon = attachmentMode == AttachmentMode.POKEMON && selectedSlot >= 0
        val hasReadyPhoto = attachmentMode == AttachmentMode.PHOTO && photoId?.let(SocialPhotoClient::isUploadReady) == true
        val pollQ = stateStore.getInputText("poll_q")
        val poll1 = stateStore.getInputText("poll_opt1")
        val poll2 = stateStore.getInputText("poll_opt2")
        val hasValidPoll = !isDmCompose && attachmentMode == AttachmentMode.POLL &&
            pollQ.isNotBlank() && poll1.isNotBlank() && poll2.isNotBlank()

        return pendingRequestId == null && (isDmCompose || !isOnPostCooldown()) &&
            (body.isNotBlank() || hasPokemon || hasReadyPhoto || hasValidPoll) &&
            (attachmentMode != AttachmentMode.PHOTO || photoId == null || hasReadyPhoto) &&
            (attachmentMode != AttachmentMode.POLL || hasValidPoll)
    }

    private fun submitPost() {
        if (!canPost()) return
        playClickSound()
        val requestId = SocialMutationState.nextRequestId()
        pendingRequestId = requestId
        val body = stateStore.getInputText("compose_body").trim()
        val slot = selectedSlot.takeIf { attachmentMode == AttachmentMode.POKEMON } ?: -1
        val readyPhoto = photoId?.takeIf { attachmentMode == AttachmentMode.PHOTO && SocialPhotoClient.isUploadReady(it) }
        val pollQ = if (!isDmCompose && attachmentMode == AttachmentMode.POLL) stateStore.getInputText("poll_q").trim() else null
        val pollOpts = if (!isDmCompose && attachmentMode == AttachmentMode.POLL) {
            listOf("poll_opt1", "poll_opt2", "poll_opt3", "poll_opt4")
                .map { stateStore.getInputText(it).trim() }
                .filter { it.isNotBlank() }
        } else emptyList()

        if (dmTargetUuid != null) {
            SendDmPacket(
                requestId, dmTargetUuid, body, slot, showDetails,
                showDetails && showIvs, showDetails && showEvs, showDetails && showAbility,
                showDetails && showNature, readyPhoto
            ).sendToServer()
        } else {
            CreatePostPacket(
                requestId, body, slot, showDetails,
                showDetails && showIvs, showDetails && showEvs, showDetails && showAbility,
                showDetails && showNature, readyPhoto, pollQ, pollOpts
            ).sendToServer()
        }
    }

    private fun consumePostResult() {
        val requestId = pendingRequestId ?: return
        val status = SocialMutationState.consume(requestId) ?: return
        pendingRequestId = null
        if (status == SocialMutationResultPacket.Status.SUCCESS) {
            if (!isDmCompose) lastPostTime = System.currentTimeMillis()
            photoId?.let(SocialPhotoClient::removeDraft)
            onBackPressed()
        } else {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.nbp.social.result.${status.name.lowercase()}"), true
            )
        }
    }

    companion object {
        var lastPostTime = 0L
        private val postCooldownSec get() = SocialClientSession.capabilities.postCooldownSeconds

        fun isOnPostCooldown(): Boolean = remainingPostCooldownMs() > 0

        private fun remainingPostCooldownMs(): Long =
            ((postCooldownSec * 1000L) - (System.currentTimeMillis() - lastPostTime)).coerceAtLeast(0)

        fun remainingPostCooldownSec(): Int = ((remainingPostCooldownMs() + 999) / 1000).toInt()
    }
}
