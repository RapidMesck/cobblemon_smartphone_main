package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageLink
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenTomsStorageTerminalPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object OpenTomsStorageAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:toms_storage_terminal"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/toms_storage.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/toms_storage_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenTomsStorageTerminalPacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableTomsStorage) return false
        if (!isModLoaded(TomsStorageLink.MOD_ID)) return false

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, TomsStorageLink.UPGRADE_NBT_KEY, id)
    }
}
