package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object OpenPcAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:pc"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/pc.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/pc_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenPCPacket().sendToServer()
        Minecraft.getInstance().setScreen(null) // Closes the smartphone screen
    }

    override fun isEnabled(): Boolean {
        // Optionally check config or any other condition
        return CobblemonSmartphone.config.features.enablePC
    }
}