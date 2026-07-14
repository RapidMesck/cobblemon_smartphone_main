package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.network.packet.OpenCraftingTablePacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object CraftingAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:crafting"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/crafting.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/crafting_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenCraftingTablePacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableCrafting) return false

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, "upgrade_crafting", id)
    }
}
