package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobbledollarsShopPacket
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object OpenCobbledollarsAction : SmartphoneAction {
    private const val COBBLEDOLLARS_MOD_ID = "cobbledollars"

    override val id = "${CobblemonSmartphone.ID}:cobbledollars_shop"
    override val texture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/cobbledollars.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "textures/gui/buttons/cobbledollars_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        OpenCobbledollarsShopPacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        if (!CobblemonSmartphone.config.features.enableCobbleDollars) {
            return false
        }

        return isModLoaded(COBBLEDOLLARS_MOD_ID)
    }
}