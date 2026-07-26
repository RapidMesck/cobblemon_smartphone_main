package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.network.packet.SyncMutedPlayersPacket
import net.minecraft.client.Minecraft

object SyncMutedPlayersHandler : ClientNetworkPacketHandler<SyncMutedPlayersPacket> {
    override fun handle(packet: SyncMutedPlayersPacket, client: Minecraft) {
        MutedPlayers.set(packet.players)
    }
}
