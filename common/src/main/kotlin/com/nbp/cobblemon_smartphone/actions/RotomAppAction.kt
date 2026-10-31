package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.client.gui.RotomAppScreen
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object RotomAppAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:rotom"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/rotom.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/rotom_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val color = SmartphoneHelper.contextColor ?: return
        Minecraft.getInstance().setScreen(RotomAppScreen(color, SmartphoneHelper.contextSmartphone))
    }

    override fun isEnabled(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val phone = SmartphoneHelper.contextSmartphone ?: SmartphoneHelper.getSmartphone(player) ?: return false
        return phone.isRotomPhone()
    }
}
