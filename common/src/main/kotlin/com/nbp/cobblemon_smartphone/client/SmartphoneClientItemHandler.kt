package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SetRotomScanPriorityPacket
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object SmartphoneClientItemHandler {
    fun getClientScanRadius(): Double = RotomScannerClientState.clientConfig.scanRadius

    fun isClientManualScanEnabled(): Boolean = RotomScannerClientState.clientConfig.enableManualScanPriority

    fun sendScanPriorityPacket(entityId: Int) {
        SetRotomScanPriorityPacket(entityId).sendToServer()
    }

    fun handleUseOnClient(player: Player, model: SmartphoneColor, stack: ItemStack): Boolean {
        if (SocialPhotoClient.isCameraActive()) {
            return false
        }
        Minecraft.getInstance().setScreen(SmartphoneScreen(model, stack))
        player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.5f, 1f)
        return true
    }
}
