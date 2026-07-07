package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.client.gui.PokeInfoScreen
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object PokeInfoAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:pokeinfo"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/pokeinfo.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/pokeinfo_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val color = SmartphoneHelper.contextColor ?: return
        Minecraft.getInstance().setScreen(PokeInfoScreen(color, SmartphoneHelper.contextSmartphone))
    }

    override fun isEnabled(): Boolean {
        return CobblemonSmartphone.config.features.enablePokeInfo
    }
}
