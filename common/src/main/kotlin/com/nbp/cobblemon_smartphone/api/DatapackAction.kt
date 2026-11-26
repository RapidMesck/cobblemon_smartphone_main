package com.nbp.cobblemon_smartphone.api

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.gui.DatapackScreen
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackActionPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

class DatapackAction(private val definition: DatapackActionDefinition) : SmartphoneAction {
    val requireUpgrade: String?
        get() = definition.requireUpgrade

    val requireMod: String?
        get() = definition.requireMod

    override val id: String
        get() = definition.id

    override val texture: ResourceLocation
        get() = parseTextureOrFallback(definition.texture)

    override val hoverTexture: ResourceLocation
        get() = parseTextureOrFallback(definition.hoverTexture)

    private fun parseTextureOrFallback(raw: String?): ResourceLocation {
        if (!raw.isNullOrBlank()) {
            ResourceLocation.tryParse(raw)?.let { return it }
        }
        return FALLBACK_TEXTURE
    }

    companion object {
        private val FALLBACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_smartphone",
            "textures/gui/buttons/app_store.png"
        )
    }

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val screenTarget = definition.openScreen
        if (!screenTarget.isNullOrBlank()) {
            val color = SmartphoneHelper.contextColor ?: SmartphoneColor.RED
            DatapackScreen.openTargetScreen(color, SmartphoneHelper.contextSmartphone, screenTarget)
            return
        }
        ExecuteDatapackActionPacket(id).sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        val requireMod = definition.requireMod
        if (requireMod != null && !isModLoaded(requireMod)) return false
        if (!definition.patchouliBook.isNullOrBlank() && !isModLoaded("patchouli")) return false
        val upgradeKey = definition.requireUpgrade ?: return true
        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.satisfiesUpgradeRequirement(player, upgradeKey, id)
    }
}
