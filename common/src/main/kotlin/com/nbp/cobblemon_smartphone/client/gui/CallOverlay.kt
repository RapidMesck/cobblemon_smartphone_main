package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.social.CallStatus
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.network.chat.Component

/**
 * Small right-edge call widget drawn over the HUD (so it shows while the phone is closed) and
 * clicked through [MouseHandlerMixin]. Replaces the old full-screen incoming-call popup: an
 * incoming ring, an outgoing "Calling…", and an active call with a hang-up button all live here.
 */
object CallOverlay {

    private data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        fun has(mx: Int, my: Int) = mx in x1..x2 && my in y1..y2
    }

    private data class CallButton(
        val rect: Rect,
        val label: String,
        val color: Int,
        val action: CallActionPacket.Action,
        val key: KeyMapping
    )

    // Also require a player so a call state left stale by a mid-call disconnect can't draw the
    // widget over the title/menu screens.
    private fun visible(): Boolean = !CallState.isIdle() && Minecraft.getInstance().player != null

    fun render(guiGraphics: GuiGraphics) {
        if (!visible()) return
        val minecraft = Minecraft.getInstance()
        // Rendered both over the HUD (in-world) and over open screens (via ScreenRenderMixin), so a
        // ringing call is visible while the player is in chat, inventory, or the phone itself.
        val window = minecraft.window
        val panel = panelRect(window.guiScaledWidth, window.guiScaledHeight)
        val font = minecraft.font

        guiGraphics.fill(panel.x1 - 1, panel.y1 - 1, panel.x2 + 1, panel.y2 + 1, BORDER_COLOR)
        guiGraphics.fill(panel.x1, panel.y1, panel.x2, panel.y2, PANEL_COLOR)

        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(CallState.otherUuid ?: return, CallState.otherName),
            panel.x1 + PAD,
            panel.y1 + PAD,
            HEAD_SIZE
        )
        val textX = panel.x1 + PAD + HEAD_SIZE + 4
        guiGraphics.drawString(font, CallState.otherName, textX, panel.y1 + PAD, NAME_COLOR, false)
        guiGraphics.drawString(font, statusLine(), textX, panel.y1 + PAD + 10, MUTED_COLOR, false)

        val mouse = guiMouse()
        buttons(panel).forEach { button ->
            val hovered = mouse != null && button.rect.has(mouse.first, mouse.second)
            guiGraphics.fill(button.rect.x1, button.rect.y1, button.rect.x2, button.rect.y2, if (hovered) button.color else dim(button.color))
            guiGraphics.drawString(
                font,
                button.label,
                button.rect.x1 + (button.rect.x2 - button.rect.x1 - font.width(button.label)) / 2,
                button.rect.y1 + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1,
                0xFFFFFFFF.toInt(),
                false
            )
            renderKeyBadge(guiGraphics, button)
        }
    }

    /**
     * The bound hotkey shown in the button's top-left corner. When the action has no key assigned
     * yet, shows "not bound" so the player knows to set one in Controls (needed to answer in-world,
     * where the grabbed cursor can't click the button).
     */
    private fun renderKeyBadge(guiGraphics: GuiGraphics, button: CallButton) {
        val font = Minecraft.getInstance().font
        val label = if (button.key.isUnbound) lang("key_unbound") else button.key.translatedKeyMessage.string
        val badgeWidth = (font.width(label) * KEY_SCALE).toInt() + KEY_PAD * 2
        val badgeHeight = (font.lineHeight * KEY_SCALE).toInt() + KEY_PAD
        val bx = button.rect.x1
        val by = button.rect.y1

        guiGraphics.fill(bx, by, bx + badgeWidth, by + badgeHeight, KEY_BG_COLOR)

        val matrices = guiGraphics.pose()
        matrices.pushPose()
        matrices.translate((bx + KEY_PAD).toDouble(), (by + KEY_PAD / 2.0), 0.0)
        matrices.scale(KEY_SCALE, KEY_SCALE, 1f)
        guiGraphics.drawString(font, label, 0, 0, 0xFFFFFFFF.toInt(), false)
        matrices.popPose()
    }

    /** Answer hotkey: only meaningful while a call is ringing in. */
    fun pressAnswer() {
        if (CallState.status != CallStatus.INCOMING) return
        val other = CallState.otherUuid ?: return
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        CallActionPacket(CallActionPacket.Action.ACCEPT, other).sendToServer()
    }

    /** Decline hotkey: rejects a ring, cancels an outgoing call, or hangs up an active one. */
    fun pressHangup() {
        if (CallState.isIdle()) return
        val other = CallState.otherUuid ?: return
        val action = if (CallState.status == CallStatus.INCOMING) {
            CallActionPacket.Action.DECLINE
        } else {
            CallActionPacket.Action.HANGUP
        }
        Minecraft.getInstance().player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        CallActionPacket(action, other).sendToServer()
        CallState.reset()
    }

    /**
     * Called from the mouse mixin on a left-press; returns true only when the click actually lands
     * on a button. Works whether or not a screen is open: in-world the cursor is grabbed so this
     * rarely hits (keybinds cover that case), but over a screen the free cursor can click it — and
     * because we only consume button hits, clicks anywhere else still reach the screen.
     */
    fun handleClick(): Boolean {
        if (!visible()) return false
        val minecraft = Minecraft.getInstance()
        val (mx, my) = guiMouse() ?: return false
        val other = CallState.otherUuid ?: return false

        val panel = panelRect(minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight)
        val hit = buttons(panel).firstOrNull { it.rect.has(mx, my) } ?: return false

        minecraft.player?.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        CallActionPacket(hit.action, other).sendToServer()
        // Decline/hang-up won't get an IN_CALL echo, so clear locally for a snappy dismiss; the
        // server's IDLE push reconciles. Accept keeps ringing state until the IN_CALL push lands.
        if (hit.action != CallActionPacket.Action.ACCEPT) {
            CallState.reset()
        }
        return true
    }

    // --- Layout ---

    private fun panelRect(guiW: Int, guiH: Int): Rect {
        val x2 = guiW - MARGIN
        val x1 = x2 - PANEL_WIDTH
        val y1 = (guiH - PANEL_HEIGHT) / 2
        return Rect(x1, y1, x2, y1 + PANEL_HEIGHT)
    }

    private fun buttons(panel: Rect): List<CallButton> {
        val by1 = panel.y2 - PAD - BUTTON_HEIGHT
        val by2 = panel.y2 - PAD
        return when (CallState.status) {
            CallStatus.INCOMING -> {
                val gap = 3
                val halfW = (PANEL_WIDTH - PAD * 2 - gap) / 2
                val ax1 = panel.x1 + PAD
                val dx1 = ax1 + halfW + gap
                listOf(
                    CallButton(Rect(ax1, by1, ax1 + halfW, by2), lang("accept"), ACCEPT_COLOR, CallActionPacket.Action.ACCEPT, SmartphoneKeybinds.ANSWER_CALL),
                    CallButton(Rect(dx1, by1, dx1 + halfW, by2), lang("decline"), DECLINE_COLOR, CallActionPacket.Action.DECLINE, SmartphoneKeybinds.DECLINE_CALL)
                )
            }
            CallStatus.OUTGOING, CallStatus.IN_CALL -> {
                val label = if (CallState.status == CallStatus.IN_CALL) lang("call_end") else lang("cancel")
                listOf(CallButton(Rect(panel.x1 + PAD, by1, panel.x2 - PAD, by2), label, DECLINE_COLOR, CallActionPacket.Action.HANGUP, SmartphoneKeybinds.DECLINE_CALL))
            }
            CallStatus.IDLE -> emptyList()
        }
    }

    private fun statusLine(): String = when (CallState.status) {
        CallStatus.INCOMING -> lang("incoming_call")
        CallStatus.OUTGOING -> lang("call_calling")
        CallStatus.IN_CALL -> lang("in_call")
        CallStatus.IDLE -> ""
    }

    /** Current cursor in GUI-scaled coordinates, or null if unavailable. */
    private fun guiMouse(): Pair<Int, Int>? {
        val window = Minecraft.getInstance().window
        val handler = Minecraft.getInstance().mouseHandler
        if (window.screenWidth == 0 || window.screenHeight == 0) return null
        val mx = handler.xpos() * window.guiScaledWidth / window.screenWidth
        val my = handler.ypos() * window.guiScaledHeight / window.screenHeight
        return mx.toInt() to my.toInt()
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun dim(color: Int): Int = (color and 0x00FFFFFF) or (0xBB shl 24)

    private const val MARGIN = 6
    private const val PAD = 5
    private const val PANEL_WIDTH = 96
    private const val PANEL_HEIGHT = 48
    private const val HEAD_SIZE = 16
    private const val BUTTON_HEIGHT = 13
    private const val KEY_SCALE = 0.5f
    private const val KEY_PAD = 2

    private const val PANEL_COLOR = 0xE01A1A1A.toInt()
    private const val BORDER_COLOR = 0xFF3A96B6.toInt()
    private const val NAME_COLOR = 0xFFFFFFFF.toInt()
    private const val MUTED_COLOR = 0xFF8AA5AD.toInt()
    private const val ACCEPT_COLOR = 0xFF3AAA55.toInt()
    private const val DECLINE_COLOR = 0xFFCC3333.toInt()
    private const val KEY_BG_COLOR = 0xCC000000.toInt()
}
