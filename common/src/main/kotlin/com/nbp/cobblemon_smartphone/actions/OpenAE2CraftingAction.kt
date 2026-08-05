package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenAE2CraftingTerminalPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object OpenAE2CraftingAction : SmartphoneAction {
    const val MOD_ID = "ae2"
    const val UPGRADE_NBT_KEY = "upgrade_ae2_crafting"

    override val id = "${CobblemonSmartphone.ID}:ae2_crafting_terminal"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/ae2.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/ae2_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenAE2CraftingTerminalPacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableAE2) return false
        if (!isModLoaded(MOD_ID)) return false

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, UPGRADE_NBT_KEY, id)
    }
}
