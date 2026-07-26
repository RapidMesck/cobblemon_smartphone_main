package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.network.packet.MutePlayerPacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.social.CallStatus
import com.nbp.cobblemon_smartphone.social.DmMessage
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

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
    private lateinit var input: EditBox

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

        input = EditBox(font, screenX + CONTENT_X, screenY + INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, Component.empty())
        input.setMaxLength(CobblemonSmartphone.config.social.maxMessageLength)
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
                screenY + LIST_START_Y + 30,
                MUTED_COLOR,
                false
            )
        }

        guiGraphics.disableScissor()

        input.render(guiGraphics, mouseX, mouseY, delta)
        renderSendButton(guiGraphics, mouseX, mouseY)

        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(
            font,
            lang("back"),
            screenX + CONTENT_X,
            screenY + TITLE_Y,
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
            screenY + TITLE_Y,
            NAME_COLOR,
            false
        )
    }

    private fun renderCallButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val (label, color) = callButtonLabelAndColor()
        val x = callButtonX()
        val y = screenY + TITLE_Y - 3
        val hovered = isInCallButton(mouseX, mouseY)
        guiGraphics.fill(x, y, x + CALL_BUTTON_WIDTH, y + CALL_BUTTON_HEIGHT, if (hovered) color else dimColor(color))
        guiGraphics.drawString(
            font,
            label,
            x + (CALL_BUTTON_WIDTH - font.width(label)) / 2,
            y + 2,
            0xFFFFFFFF.toInt(),
            false
        )
    }

    private fun callButtonLabelAndColor(): Pair<String, Int> = when {
        CallState.status == CallStatus.IN_CALL && CallState.otherUuid == otherUuid ->
            lang("call_end") to DANGER_BUTTON_COLOR
        CallState.status == CallStatus.OUTGOING && CallState.otherUuid == otherUuid ->
            lang("call_calling") to ACCENT_COLOR
        else -> lang("call_start") to ACCENT_COLOR
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

    /** Only offered when this client actually has Simple Voice Chat and the server allows calls. */
    private fun callAvailable(): Boolean =
        VoiceChatBridge.isModPresent && CobblemonSmartphone.config.features.enableCalls

    // Sits just left of the always-present mute bell at the right edge.
    private fun callButtonX(): Int = bellX() - CALL_BELL_GAP - CALL_BUTTON_WIDTH

    private fun isInCallButton(mouseX: Int, mouseY: Int): Boolean {
        val x = callButtonX()
        val y = screenY + TITLE_Y - 3
        return mouseX in x..(x + CALL_BUTTON_WIDTH) && mouseY in y..(y + CALL_BUTTON_HEIGHT)
    }

    /** Per-player Do Not Disturb: a bell (with a red slash when this player is muted). */
    private fun renderMuteBell(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val x = bellX()
        val y = screenY + TITLE_Y - 3
        val muted = MutedPlayers.contains(otherUuid)
        val hovered = isInBellButton(mouseX, mouseY)
        val bg = when {
            muted -> MUTE_ACTIVE_COLOR
            hovered -> ACCENT_COLOR
            else -> ACCENT_DIM_COLOR
        }
        guiGraphics.fill(x, y, x + BELL_WIDTH, y + BELL_HEIGHT, bg)

        val c = 0xFFFFFFFF.toInt()
        guiGraphics.fill(x + 5, y + 2, x + 7, y + 3, c)   // top nub
        guiGraphics.fill(x + 4, y + 3, x + 8, y + 4, c)   // shoulders
        guiGraphics.fill(x + 3, y + 4, x + 9, y + 6, c)   // body
        guiGraphics.fill(x + 2, y + 6, x + 10, y + 7, c)  // rim
        guiGraphics.fill(x + 5, y + 7, x + 7, y + 8, c)   // clapper
        if (muted) {
            guiGraphics.fill(x + 1, y + 4, x + 11, y + 5, MUTE_SLASH_COLOR)
        }
    }

    private fun bellX(): Int = screenX + CONTENT_X + CONTENT_WIDTH - BELL_WIDTH

    private fun isInBellButton(mouseX: Int, mouseY: Int): Boolean {
        val x = bellX()
        val y = screenY + TITLE_Y - 3
        return mouseX in x..(x + BELL_WIDTH) && mouseY in y..(y + BELL_HEIGHT)
    }

    private fun dimColor(color: Int): Int = (color and 0x00FFFFFF) or (0xBB shl 24)

    private fun isOwn(message: DmMessage): Boolean =
        message.senderUuid == Minecraft.getInstance().player?.uuid

    private fun wrapped(message: DmMessage) =
        font.split(Component.literal(message.text), BUBBLE_MAX_WIDTH - BUBBLE_PAD * 2)

    private fun measureBubble(message: DmMessage): Int =
        wrapped(message).size * font.lineHeight + BUBBLE_PAD * 2

    private fun renderBubble(guiGraphics: GuiGraphics, message: DmMessage, y: Int) {
        val lines = wrapped(message)
        val own = isOwn(message)
        val textWidth = lines.maxOfOrNull { font.width(it) } ?: 0
        val bubbleWidth = (textWidth + BUBBLE_PAD * 2).coerceAtMost(BUBBLE_MAX_WIDTH)
        val height = lines.size * font.lineHeight + BUBBLE_PAD * 2

        // Own messages hug the right edge, the counterpart's hug the left.
        val x = if (own) {
            screenX + CONTENT_X + CONTENT_WIDTH - bubbleWidth
        } else {
            screenX + CONTENT_X
        }

        guiGraphics.fill(x, y, x + bubbleWidth, y + height, if (own) OWN_BUBBLE_COLOR else OTHER_BUBBLE_COLOR)

        var lineY = y + BUBBLE_PAD
        lines.forEach { line ->
            guiGraphics.drawString(font, line, x + BUBBLE_PAD, lineY, TEXT_COLOR, false)
            lineY += font.lineHeight
        }
    }

    private fun renderSendButton(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val hovered = isInSend(mouseX, mouseY)
        val enabled = input.value.isNotBlank()
        val bg = when {
            enabled && hovered -> ACCENT_COLOR
            enabled -> ACCENT_DIM_COLOR
            else -> DISABLED_COLOR
        }
        val x = sendX()
        guiGraphics.fill(x, screenY + INPUT_Y, x + SEND_WIDTH, screenY + INPUT_Y + INPUT_HEIGHT, bg)
        val label = lang("send")
        guiGraphics.drawString(
            font,
            label,
            x + (SEND_WIDTH - font.width(label)) / 2,
            screenY + INPUT_Y + (INPUT_HEIGHT - font.lineHeight) / 2 + 1,
            TEXT_COLOR,
            false
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

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
        if (isInSend(mx, my)) {
            send()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
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
        val text = input.value.trim()
        if (text.isEmpty()) return
        playClickSound()
        SendDmPacket(otherUuid, text).sendToServer()
        input.value = ""
        scrollY = 0
    }

    private fun sendX() = screenX + CONTENT_X + CONTENT_WIDTH - SEND_WIDTH

    private fun isInSend(mouseX: Int, mouseY: Int): Boolean =
        mouseX in sendX()..(sendX() + SEND_WIDTH) &&
                mouseY in (screenY + INPUT_Y)..(screenY + INPUT_Y + INPUT_HEIGHT)

    private fun isInBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + CONTENT_X)..(screenX + CONTENT_X + font.width(lang("back"))) &&
                mouseY in (screenY + TITLE_Y)..(screenY + TITLE_Y + font.lineHeight)

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

        private const val BUBBLE_MAX_WIDTH = 130
        private const val BUBBLE_PAD = 4
        private const val BUBBLE_GAP = 3

        private const val SCROLL_SPEED = 12
        private const val LOAD_MORE_THRESHOLD = 20

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val TEXT_COLOR = 0xFFE6FFFF.toInt()
        private const val MUTED_COLOR = 0xFF8AA5AD.toInt()
        private const val OWN_BUBBLE_COLOR = 0xAA3A96B6.toInt()
        private const val OTHER_BUBBLE_COLOR = 0x88000000.toInt()
        private const val ACCENT_COLOR = 0xFF3A96B6.toInt()
        private const val ACCENT_DIM_COLOR = 0xAA3A96B6.toInt()
        private const val DISABLED_COLOR = 0x55000000
        private const val DANGER_BUTTON_COLOR = 0xFFCC3333.toInt()

        private const val CALL_BUTTON_WIDTH = 34
        private const val CALL_BUTTON_HEIGHT = 11
        private const val CALL_BELL_GAP = 3
        private const val BELL_WIDTH = 12
        private const val BELL_HEIGHT = 11
        private const val MUTE_ACTIVE_COLOR = 0xFFAA3333.toInt()
        private const val MUTE_SLASH_COLOR = 0xFFFF3030.toInt()

        private val SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/large_screen.png"
        )
    }
}
