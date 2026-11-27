package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.NotesClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncNotesPacket
import net.minecraft.client.Minecraft

object SyncNotesHandler : ClientNetworkPacketHandler<SyncNotesPacket> {
    override fun handle(packet: SyncNotesPacket, client: Minecraft) {
        client.execute {
            NotesClientState.setFromServer(packet.notes)
        }
    }
}
