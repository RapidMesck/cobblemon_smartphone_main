package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.CalendarClientState
import com.nbp.cobblemon_smartphone.network.packet.SyncServerCalendarPacket
import net.minecraft.client.Minecraft

object SyncServerCalendarHandler : ClientNetworkPacketHandler<SyncServerCalendarPacket> {
    override fun handle(packet: SyncServerCalendarPacket, client: Minecraft) {
        client.execute {
            CalendarClientState.setServerEventsFromServer(packet.events)
        }
    }
}
