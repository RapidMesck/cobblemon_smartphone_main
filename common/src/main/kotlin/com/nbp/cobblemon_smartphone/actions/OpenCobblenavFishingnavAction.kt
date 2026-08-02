package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavFishingnavPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

object OpenCobblenavFishingnavAction : SmartphoneAction {
    private const val COBBLENAV_NAMESPACE = "cobblenav"
    private const val FISHINGNAV_ITEM = "fishingnav_item"

    override val id = "${CobblemonSmartphone.ID}:cobblenav_fishingnav"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/fishingnav.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/fishingnav_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenCobblenavFishingnavPacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableFishingnav) {
            return false
        }

        if (!isModLoaded(COBBLENAV_NAMESPACE)) {
            return false
        }

        if (!isFishingnavAvailable()) {
            return false
        }

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, "upgrade_fishingnav", id)
    }

    private fun isFishingnavAvailable(): Boolean {
        return BuiltInRegistries.ITEM.containsKey(
            ResourceLocation.fromNamespaceAndPath(COBBLENAV_NAMESPACE, FISHINGNAV_ITEM)
        )
    }
}
