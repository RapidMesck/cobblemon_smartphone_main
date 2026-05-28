package com.nbp.cobblemon_smartphone.api

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackActionPacket
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

class DatapackAction(private val definition: DatapackActionDefinition) : SmartphoneAction {
    override val id: String
        get() = definition.id

    override val texture: ResourceLocation
        get() {
            val parts = definition.texture.split(":", limit = 2)
            return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1])
        }

    override val hoverTexture: ResourceLocation
        get() {
            val parts = definition.hoverTexture.split(":", limit = 2)
            return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1])
        }

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        ExecuteDatapackActionPacket(id).sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        return true
    }
}
