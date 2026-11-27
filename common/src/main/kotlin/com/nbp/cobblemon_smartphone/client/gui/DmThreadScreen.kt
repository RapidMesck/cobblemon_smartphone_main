package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialMutationState
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.social.SocialProfileCache
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
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.network.packet.ChallengeBattlePacket
import com.nbp.cobblemon_smartphone.network.packet.ChallengeTradePacket
import com.nbp.cobblemon_smartphone.network.packet.MutePlayerPacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.network.packet.ToggleFollowPacket
import com.nbp.cobblemon_smartphone.social.CallStatus
import com.nbp.cobblemon_smartphone.social.DmMessage
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.min

/**
 * 1:1 Direct Message conversation screen built on [HdPhoneScreen].
 * Messages are stored oldest-first and anchored to the bottom of the chat scroll area.
 */
class DmThreadScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    private val otherUuid: UUID,
    private val otherName: String
) : HdPhoneScreen(color, smartphoneStack, Component.literal("Direct Message")) {

    private var lastSendTime = 0L
    private var pendingRequestId: Long? = null
    private var callNoticeUntil = 0L
    private val pokemonModelState = FloatingState()

    private val messageCooldownSec get() = SocialClientSession.capabilities.messageCooldownSeconds
    private val isOtherPlayer get() = Minecraft.getInstance().player?.uuid != otherUuid

    override val viewportMode: ViewportMode = ViewportMode.Large

    override fun init() {
        super.init()
        if (SocialDmCache.openThreadWith != otherUuid) {
            SocialDmCache.openThread(otherUuid, otherName)
        }
        if (isOtherPlayer) {
            SocialProfileCache.request(otherUuid)
        }
        stateStore.input("dm_input_${otherUuid}")
    }

    override fun removed() {
        SocialDmCache.closeThread()
        super.removed()
    }

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, startOnDms = true))
    }

    fun showCallOffline(targetUuid: UUID) {
        if (targetUuid == otherUuid) {
            callNoticeUntil = System.currentTimeMillis() + CALL_NOTICE_DURATION_MS
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        consumeSendResult()
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        // Floating Island Header
        val name = SocialDmCache.currentThreadName.ifBlank { otherName }
        builder.appHeader(
            title = name,
            subtitle = lang("tab_dms"),
            icon = HdIconAtlas.Icon.MESSAGE,
            iconColor = HdColorSpec.Token("primary")
        ) { hb ->
            if (callAvailable()) {
                val activeCall = CallState.isBusyWith(otherUuid)
                val callTipKey = when {
                    activeCall -> "social_call_end"
                    CallState.status == CallStatus.OUTGOING && CallState.otherUuid == otherUuid -> "social_call_end"
                    else -> "social_call_start"
                }
                hb.button(
                    label = if (activeCall) "END" else "CALL",
                    icon = HdIconAtlas.Icon.PHONE,
                    variant = if (activeCall) ButtonNode.Variant.DANGER else ButtonNode.Variant.SURFACE,
                    fontSize = 8f,
                    radius = 8f
                ) {
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 3.5f)
                    iconSizeDp = 10f
                    tooltip = Component.translatable("cobblemon_smartphone.tooltip.$callTipKey").string
                    onClick = {
                        playClickSound()
                        onCallButton()
                    }
                }
            }

            val muted = MutedPlayers.contains(otherUuid)
            hb.button(
                icon = HdIconAtlas.Icon.BELL,
                variant = if (muted) ButtonNode.Variant.DANGER else ButtonNode.Variant.SURFACE,
                radius = 9f
            ) {
                padding = HdPadding.all(4.5f)
                iconSizeDp = 11f
                tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_mute_player").string
                onClick = {
                    playClickSound()
                    MutePlayerPacket(otherUuid, MutedPlayers.toggle(otherUuid)).sendToServer()
                }
            }
        }

        // Follow / Battle / Trade action bar
        if (isOtherPlayer) {
            buildActionBar(builder)
        }
        builder.divider(thickness = 0.8f, color = HdColorSpec.Fixed(0x2EFFFFFF))

        // Offline call toast if active
        if (System.currentTimeMillis() < callNoticeUntil) {
            builder.row(
                mainAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f),
                width = HdSizeSpec.Fill,
                background = HdBackground(color = HdColorSpec.Fixed(0xE62B3548.toInt()))
            ) { nb ->
                nb.text(
                    Component.translatable("message.nbp.social.call_offline").string,
                    size = 8.6f,
                    weight = HdTextLayout.Weight.MEDIUM,
                    color = HdColorSpec.Token("text_primary"),
                    align = HdAlign.CENTER
                )
            }
        }

        // Chat scroll area (anchored bottom)
        val messages = SocialDmCache.messages()
        builder.scroll(
            scrollId = "dm_thread_scroll_$otherUuid",
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 6f),
            onNearBottom = { SocialDmCache.loadOlder() }
        ) { sb ->
            if (messages.isEmpty()) {
                sb.column(
                    gap = 8f,
                    crossAlign = HdAlign.CENTER,
                    mainAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(vertical = 64f),
                    width = HdSizeSpec.Fill
                ) { eb ->
                    eb.text(lang("no_messages"), size = 9.8f, color = HdColorSpec.Token("text_primary"), align = HdAlign.CENTER)
                }
            } else {
                messages.forEach { message ->
                    buildMessageBubble(sb, message, theme)
                }
            }
        }.apply {
            anchorBottom = true
        }

        // Floating input bar
        builder.column(
            padding = HdPadding.only(left = 7f, right = 7f, top = 3f, bottom = 7f),
            width = HdSizeSpec.Fill
        ) { wrap ->
            wrap.row(
                gap = 5f,
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
            ) { ib ->
                ib.button(icon = HdIconAtlas.Icon.PLUS, variant = ButtonNode.Variant.SURFACE, radius = 8f) {
                    padding = HdPadding.all(5f)
                    iconSizeDp = 11.5f
                    tooltip = lang("attach_pokemon")
                    onClick = {
                        playClickSound()
                        val currentText = stateStore.getInputText("dm_input_$otherUuid")
                        Minecraft.getInstance().setScreen(
                            SocialComposeScreen(color, smartphoneStack, currentText, dmTargetUuid = otherUuid, dmTargetName = otherName)
                        )
                    }
                }
                ib.textInput(
                    inputId = "dm_input_$otherUuid",
                    placeholder = "Message...",
                    maxLength = SocialClientSession.capabilities.maxMessageLength,
                    onSubmit = { send() }
                ) {
                    width = HdSizeSpec.Weight(1f)
                    fontSizeDp = 9.2f
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f)
                }

                val onCd = isOnCooldown()
                val canSend = !onCd && pendingRequestId == null && stateStore.getInputText("dm_input_$otherUuid").isNotBlank()
                if (onCd) {
                    ib.button("${remainingCooldownSec()}s", variant = ButtonNode.Variant.PRIMARY, fontSize = 8.4f, radius = 8f, enabled = false) {
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f)
                    }
                } else {
                    ib.button(icon = HdIconAtlas.Icon.SEND, variant = ButtonNode.Variant.PRIMARY, radius = 8f, enabled = canSend) {
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f)
                        iconSizeDp = 11.5f
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_send").string
                        onClick = { send() }
                    }
                }
            }
        }
    }

    private fun buildActionBar(builder: HdBuilder) {
        val profile = SocialProfileCache.getProfile(otherUuid)
        val isMutual = profile?.isMutualFollow == true
        val isFollowed = profile?.isFollowedByMe == true

        val canBattle = isMutual && SocialClientSession.capabilities.battleEnabled
        val canTrade = isMutual && SocialClientSession.capabilities.tradeEnabled
        val hasInteractionButtons = canBattle || canTrade

        builder.row(
            gap = 5f,
            crossAlign = HdAlign.CENTER,
            mainAlign = HdAlign.CENTER,
            padding = HdPadding.only(left = 7f, right = 7f, top = 3f, bottom = 2f),
            width = HdSizeSpec.Fill
        ) { ab ->
            val followLabel = if (isFollowed) "✓ ${lang("feed_following")}" else "+ ${lang("follow")}"
            ab.button(
                label = followLabel,
                variant = if (isFollowed) ButtonNode.Variant.SURFACE else ButtonNode.Variant.PRIMARY,
                fontSize = 8.2f,
                radius = 7f
            ) {
                if (hasInteractionButtons) width = HdSizeSpec.Weight(1f)
                padding = HdPadding.symmetric(horizontal = 9f, vertical = 3.5f)
                tooltip = Component.translatable(
                    if (isFollowed) "cobblemon_smartphone.tooltip.social_unfollow" else "cobblemon_smartphone.tooltip.social_follow"
                ).string
                onClick = {
                    playClickSound()
                    ToggleFollowPacket(otherUuid).sendToServer()
                }
            }

            if (canBattle) {
                ab.button(
                    label = lang("battle_action"),
                    icon = HdIconAtlas.Icon.SWORDS,
                    variant = ButtonNode.Variant.SURFACE,
                    fontSize = 8.2f,
                    radius = 7f
                ) {
                    width = HdSizeSpec.Weight(1f)
                    iconSizeDp = 10f
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 3.5f)
                    tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_battle_challenge").string
                    onClick = {
                        playClickSound()
                        ChallengeBattlePacket(otherUuid).sendToServer()
                    }
                }
            }
            if (canTrade) {
                ab.button(
                    label = lang("trade_action"),
                    icon = HdIconAtlas.Icon.TRADE,
                    variant = ButtonNode.Variant.SURFACE,
                    fontSize = 8.2f,
                    radius = 7f
                ) {
                    width = HdSizeSpec.Weight(1f)
                    iconSizeDp = 10f
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 3.5f)
                    tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_trade_challenge").string
                    onClick = {
                        playClickSound()
                        ChallengeTradePacket(otherUuid).sendToServer()
                    }
                }
            }
        }
    }

    private fun buildMessageBubble(sb: HdBuilder, message: DmMessage, theme: SmartphoneTheme) {
        val own = message.senderUuid == Minecraft.getInstance().player?.uuid
        val rowAlign = if (own) HdAlign.END else HdAlign.START

        sb.row(mainAlign = rowAlign, width = HdSizeSpec.Fill) { rb ->
            val bgCol = if (own) HdColorSpec.Token("primary") else HdColorSpec.Token("surface")
            val textCol = if (own) HdColorSpec.Token("text_primary") else HdColorSpec.Token("text_dark")
            val timeCol = if (own) HdColorSpec.Token("text_secondary") else HdColorSpec.Token("text_dark_muted")
            val brdCol = if (own) HdColorSpec.Fixed(0x35FFFFFF) else HdColorSpec.Token("primary_border")

            rb.column(
                gap = 5f,
                padding = HdPadding.symmetric(horizontal = 9f, vertical = 7f),
                background = HdBackground(
                    color = bgCol,
                    radius = 10f,
                    borderWidth = 0.8f,
                    borderColor = brdCol,
                    shadowBlur = 4f,
                    shadowOffsetY = 1f,
                    shadowColor = HdColorSpec.Fixed(0x22000000)
                )
            ) { bb ->
                maxWidth = 248f
                if (message.text.isNotBlank()) {
                    bb.text(message.text, size = 9.4f, color = textCol, wrap = true) {
                        maxWidth = 230f
                    }
                }
                message.attachment?.let { att ->
                    bb.column(width = HdSizeSpec.Fixed(220f)) { ab ->
                        SocialComponents.buildPokemonAttachmentBox(
                            builder = ab,
                            attachment = att,
                            colors = theme.colors,
                            floatingState = pokemonModelState,
                            compact = true
                        )
                    }
                }
                message.photo?.let { photo ->
                    val maxW = 220f
                    val maxH = 110f
                    val s = if (photo.width > 0 && photo.height > 0) {
                        min(maxW / photo.width.toFloat(), maxH / photo.height.toFloat())
                    } else 1f
                    val pw = if (photo.width > 0) (photo.width * s).coerceAtLeast(16f) else maxW
                    val ph = if (photo.height > 0) (photo.height * s).coerceAtLeast(16f) else maxH

                    val tex = SocialPhotoClient.texture(photo.id)
                    if (tex != null) {
                        bb.image(tex, pw, ph, radius = 7f, filter = HdCanvas.TextureFilter.LINEAR)
                    } else {
                        val lbl = lang(if (SocialPhotoClient.isRequested(photo.id)) "photo_loading" else "photo_load")
                        bb.button(lbl, icon = HdIconAtlas.Icon.IMAGE, variant = ButtonNode.Variant.SURFACE, fontSize = 8.4f) {
                            onClick = {
                                playClickSound()
                                SocialPhotoClient.request(photo.id)
                            }
                        }
                    }
                }
                bb.row(mainAlign = HdAlign.END, width = HdSizeSpec.Fill) { tb ->
                    tb.text(messageTimestamp(message.timestamp), size = 7.6f, color = timeCol)
                }
            }
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

    private fun send() {
        if (isOnCooldown() || pendingRequestId != null) return
        val st = stateStore.input("dm_input_$otherUuid")
        val text = st.text.trim()
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
            val st = stateStore.input("dm_input_$otherUuid")
            st.text = ""
            st.cursor = 0
            st.selectionAnchor = null
            st.resetBlink()
            lastSendTime = System.currentTimeMillis()
            stateStore.scroll("dm_thread_scroll_$otherUuid").offset = 0f
        } else {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.nbp.social.result.${status.name.lowercase()}"), true
            )
        }
    }

    private fun onCallButton() {
        when {
            CallState.isBusyWith(otherUuid) ->
                CallActionPacket(CallActionPacket.Action.HANGUP, otherUuid).sendToServer()
            CallState.isIdle() ->
                CallActionPacket(CallActionPacket.Action.START, otherUuid).sendToServer()
        }
    }

    private fun callAvailable(): Boolean {
        if (!VoiceChatBridge.isModPresent || !SocialClientSession.capabilities.callsEnabled) return false
        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.getSmartphone(player) != null
    }

    private fun isOnCooldown(): Boolean = remainingCooldownMs() > 0

    private fun remainingCooldownMs(): Long =
        ((messageCooldownSec * 1000L) - (System.currentTimeMillis() - lastSendTime)).coerceAtLeast(0)

    private fun remainingCooldownSec(): Int = ((remainingCooldownMs() + 999) / 1000).toInt()

    companion object {
        private const val CALL_NOTICE_DURATION_MS = 3_000L
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }
}
