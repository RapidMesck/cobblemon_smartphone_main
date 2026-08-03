package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.client.gui.StructureCompassScreen
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object StructureCompassAction : SmartphoneAction {
    const val UPGRADE_NBT_KEY = "upgrade_structure_compass"

    override val id = "${CobblemonSmartphone.ID}:structure_compass"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/structure_compass.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/structure_compass_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val color = SmartphoneHelper.contextColor ?: return
        Minecraft.getInstance().setScreen(StructureCompassScreen(color, SmartphoneHelper.contextSmartphone))
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableStructureCompass) return false

        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, UPGRADE_NBT_KEY, id)
    }
}
