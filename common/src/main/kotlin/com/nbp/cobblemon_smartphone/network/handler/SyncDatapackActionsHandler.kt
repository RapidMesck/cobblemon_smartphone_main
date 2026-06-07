package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.api.DatapackAction
import com.nbp.cobblemon_smartphone.api.DatapackActionDefinition
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackActionsPacket
import net.minecraft.client.Minecraft

object SyncDatapackActionsHandler : ClientNetworkPacketHandler<SyncDatapackActionsPacket> {
    override fun handle(packet: SyncDatapackActionsPacket, client: Minecraft) {
        SmartphoneActionRegistry.clearDatapackActions()

        for (actionData in packet.actions) {
            val definition = DatapackActionDefinition(
                id = actionData.id,
                texture = actionData.texture,
                hoverTexture = actionData.hoverTexture,
                requireUpgrade = actionData.requireUpgrade,
                requireMod = actionData.requireMod,
                cooldownSeconds = actionData.cooldownSeconds
            )
            SmartphoneActionRegistry.registerDatapackAction(DatapackAction(definition))
        }
    }
}
