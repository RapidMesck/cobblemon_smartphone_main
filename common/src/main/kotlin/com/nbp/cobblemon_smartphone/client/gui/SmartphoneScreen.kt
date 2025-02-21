package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.HealPokemonPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenEnderChestPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class SmartphoneScreen(private val color: SmartphoneColor) : Screen(Component.literal("Smartphone")) {
    private val enabledButtons = mutableListOf<SmartphoneButton>()
    private var screenX = 0
    private var screenY = 0

    init {
        //if (CobblemonSmartphone.config.features.enableHeal) {
            enabledButtons.add(SmartphoneButton(
                ButtonType.HEAL, HEAL_BUTTON, HEAL_BUTTON_HOVER, ::executeHealCommand
            ))
        //}
        //if (CobblemonSmartphone.config.features.enablePC) {
            enabledButtons.add(SmartphoneButton(
                ButtonType.PC, PC_BUTTON, PC_BUTTON_HOVER, ::executePCCommand
            ))
        //}
        //if (CobblemonSmartphone.config.features.enableCloud) {
            enabledButtons.add(SmartphoneButton(
                ButtonType.CLOUD, CLOUD_BUTTON, CLOUD_BUTTON_HOVER, ::executeCloudCommand
            ))
        //}
    }

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Desenha a textura de fundo baseada na cor do smartphone
        val backgroundTexture = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/smartphone_${color.modelName}.png")
        guiGraphics.blit(backgroundTexture, screenX, screenY, 0f, 0f, GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT)

        // Desenha os botões
        enabledButtons.forEachIndexed { index, button ->
            val (x, y) = button.getPosition(index)
            val texture = if (isHovered(mouseX, mouseY, x, y)) button.hoverTexture else button.texture
            guiGraphics.blit(
                texture,
                screenX + x,
                screenY + y,
                0f, 0f,  // coordenadas U e V na textura
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                BUTTON_WIDTH,
                BUTTON_HEIGHT
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        enabledButtons.forEachIndexed { index, btn ->
            val (x, y) = btn.getPosition(index)
            if (isHovered(mouseX.toInt(), mouseY.toInt(), x, y)) {
                btn.action()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun executeHealCommand() {
        Minecraft.getInstance().player!!.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        //PokemonHealing.sendHealRequest()
        HealPokemonPacket().sendToServer()
        onClose()
    }

    private fun executePCCommand() {
        Minecraft.getInstance().player!!.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenPCPacket().sendToServer()
        onClose()
    }

    private fun executeCloudCommand() {
        Minecraft.getInstance().player!!.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenEnderChestPacket().sendToServer()
        onClose()
    }

    private fun isHovered(mouseX: Int, mouseY: Int, x: Int, y: Int): Boolean {
        return mouseX >= screenX + x && mouseX <= screenX + x + BUTTON_WIDTH &&
                mouseY >= screenY + y && mouseY <= screenY + y + BUTTON_HEIGHT
    }

    private data class SmartphoneButton(
        val type: ButtonType,
        val texture: ResourceLocation,
        val hoverTexture: ResourceLocation,
        val action: () -> Unit
    ) {
        fun getPosition(index: Int): Pair<Int, Int> {
            val gridX = (index % GRID_COLUMNS) * BUTTON_SPACING + GRID_START_X
            val gridY = (index / GRID_COLUMNS) * BUTTON_SPACING + GRID_START_Y
            return Pair(gridX, gridY)
        }
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val GRID_START_X = 26
        private const val GRID_START_Y = 37
        private const val BUTTON_SPACING = 43
        private const val GUI_WIDTH = 131
        private const val GUI_HEIGHT = 207
        private const val BUTTON_WIDTH = 36
        private const val BUTTON_HEIGHT = 36

        private val HEAL_BUTTON = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/heal.png")
        private val PC_BUTTON = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/pc.png")
        private val CLOUD_BUTTON = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/cloud.png")
        private val HEAL_BUTTON_HOVER = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/heal_hover.png")
        private val PC_BUTTON_HOVER = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/pc_hover.png")
        private val CLOUD_BUTTON_HOVER = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "textures/gui/buttons/cloud_hover.png")
    }

    private enum class ButtonType {
        HEAL, PC, CLOUD
    }
}
