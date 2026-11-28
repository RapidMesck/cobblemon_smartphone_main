package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.CalendarClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncPersonalCalendarPacket
import net.minecraft.client.Minecraft

object SyncPersonalCalendarHandler : ClientNetworkPacketHandler<SyncPersonalCalendarPacket> {
    override fun handle(packet: SyncPersonalCalendarPacket, client: Minecraft) {
        client.execute {
            CalendarClientState.setPersonalFromServer(packet.data, packet.canManageServerEvents)
        }
    }
}
