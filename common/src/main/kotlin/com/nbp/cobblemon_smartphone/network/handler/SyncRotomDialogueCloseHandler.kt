package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.RotomDialogueClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomDialogueClosePacket
import net.minecraft.client.Minecraft

object SyncRotomDialogueCloseHandler : ClientNetworkPacketHandler<SyncRotomDialogueClosePacket> {
    override fun handle(packet: SyncRotomDialogueClosePacket, client: Minecraft) {
        client.execute {
            RotomDialogueClientState.dismissDialogue(packet.playerUuid)
        }
    }
}
