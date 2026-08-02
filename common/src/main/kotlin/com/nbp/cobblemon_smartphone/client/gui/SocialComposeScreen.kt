package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonClient
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CreatePostPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

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
    private val smartphoneStack: ItemStack? = null
) : Screen(Component.literal("New Post")) {

    private val frameTexture get() = color.getLargeScreenTexture()
    private var screenX = 0
    private var screenY = 0
    private var selectedSlot = -1
    private lateinit var editBox: MultiLineEditBox

    private val maxLength get() = CobblemonSmartphone.config.social.maxPostLength

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        editBox = MultiLineEditBox(
            font,
            screenX + CONTENT_X,
            screenY + EDIT_Y,
            CONTENT_WIDTH,
            EDIT_HEIGHT,
            Component.translatable("cobblemon_smartphone.social.placeholder"),
            Component.translatable("cobblemon_smartphone.social.compose_title")
        )
        editBox.setCharacterLimit(maxLength)
        addRenderableWidget(editBox)
        setInitialFocus(editBox)
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

        guiGraphics.drawString(font, lang("compose_title"), screenX + CONTENT_X, screenY + TITLE_Y, TITLE_COLOR, false)

        // MultiLineEditBox exposes render() publicly; renderContents() is protected.
        val ex = screenX + CONTENT_X
        val ey = screenY + EDIT_Y
        guiGraphics.fill(ex, ey, ex + CONTENT_WIDTH, ey + EDIT_HEIGHT, SECTION_CONTENT_BG)
        guiGraphics.fill(ex, ey, ex + CONTENT_WIDTH, ey + 1, ACCENT_COLOR)
        guiGraphics.fill(ex, ey + EDIT_HEIGHT - 1, ex + CONTENT_WIDTH, ey + EDIT_HEIGHT, ACCENT_COLOR)
        guiGraphics.fill(ex, ey, ex + 1, ey + EDIT_HEIGHT, ACCENT_COLOR)
        guiGraphics.fill(ex + CONTENT_WIDTH - 1, ey, ex + CONTENT_WIDTH, ey + EDIT_HEIGHT, ACCENT_COLOR)
        editBox.render(guiGraphics, mouseX, mouseY, delta)

        val counter = "${editBox.value.length}/$maxLength"
        guiGraphics.drawString(
            font,
            counter,
            screenX + CONTENT_X + CONTENT_WIDTH - font.width(counter),
            screenY + COUNTER_Y,
            if (editBox.value.length >= maxLength) DANGER_COLOR else MUTED_COLOR,
            false
        )

        guiGraphics.drawString(font, lang("attach"), screenX + CONTENT_X, screenY + ATTACH_LABEL_Y, MUTED_COLOR, false)
        renderPartyPicker(guiGraphics, mouseX, mouseY)
        renderFooterButtons(guiGraphics, mouseX, mouseY)

        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun partySlots() = CobblemonClient.storage.party.slots

    private fun renderPartyPicker(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        partySlots().forEachIndexed { index, pokemon ->
            val (x, y) = slotPos(index)
            val hovered = isInSlot(mouseX, mouseY, index)
            val selected = selectedSlot == index
            val bg = when {
                selected -> ACCENT_COLOR
                hovered && pokemon != null -> SLOT_HOVER_COLOR
                else -> SLOT_BG_COLOR
            }
            guiGraphics.fill(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, bg)

            val label = pokemon?.let { it.nickname?.string ?: it.species.name } ?: "-"
            val trimmed = font.plainSubstrByWidth(label, SLOT_WIDTH - 4)
            guiGraphics.drawString(
                font,
                trimmed,
                x + 2,
                y + 2,
                when {
                    selected -> TITLE_COLOR
                    pokemon == null -> CONTENT_DIM
                    else -> CONTENT_TEXT
                },
                false
            )
            pokemon?.let {
                guiGraphics.drawString(font, "Lv${it.level}", x + 2, y + 12, CONTENT_DIM, false)
            }
        }
    }

    private fun renderFooterButtons(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        drawButton(guiGraphics, lang("cancel"), cancelX(), screenY + BUTTON_Y, isInCancel(mouseX, mouseY), false)
        val postLabel = if (isOnPostCooldown()) "${remainingPostCooldownSec()}s" else lang("post")
        drawButton(guiGraphics, postLabel, postX(), screenY + BUTTON_Y, isInPost(mouseX, mouseY), canPost())
    }

    private fun drawButton(guiGraphics: GuiGraphics, label: String, x: Int, y: Int, hovered: Boolean, primary: Boolean) {
        val bg = when {
            primary && hovered -> ACCENT_COLOR
            primary -> ACCENT_COLOR
            hovered -> SLOT_HOVER_COLOR
            else -> SLOT_BG_COLOR
        }
        guiGraphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, bg)
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
            CreatePostPacket(editBox.value.trim(), selectedSlot).sendToServer()
            lastPostTime = System.currentTimeMillis()
            back()
            return true
        }

        partySlots().forEachIndexed { index, pokemon ->
            if (pokemon != null && isInSlot(mx, my, index)) {
                playClickSound()
                // Clicking the selected slot clears the attachment.
                selectedSlot = if (selectedSlot == index) -1 else index
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun canPost(): Boolean = !isOnPostCooldown() && (editBox.value.isNotBlank() || selectedSlot >= 0)

    private fun back() {
        Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack))
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

    private fun cancelX() = screenX + CONTENT_X
    private fun postX() = screenX + CONTENT_X + CONTENT_WIDTH - BUTTON_WIDTH

    private fun isInCancel(mouseX: Int, mouseY: Int) = inButton(mouseX, mouseY, cancelX())
    private fun isInPost(mouseX: Int, mouseY: Int) = inButton(mouseX, mouseY, postX())

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
        private val postCooldownSec get() = CobblemonSmartphone.config.cooldowns.socialPost

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
        private const val EDIT_HEIGHT = 56
        private const val COUNTER_Y = 98
        private const val ATTACH_LABEL_Y = 110
        private const val PICKER_Y = 121

        // 3 * SLOT_WIDTH + 2 * SLOT_GAP == CONTENT_WIDTH
        private const val PICKER_COLUMNS = 3
        private const val SLOT_WIDTH = 53
        private const val SLOT_HEIGHT = 24
        private const val SLOT_GAP = 3

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
