package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.calendar.CalendarServerData
import com.nbp.cobblemon_smartphone.network.packet.DeleteServerCalendarEventPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncServerCalendarPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object DeleteServerCalendarEventHandler : ServerNetworkPacketHandler<DeleteServerCalendarEventPacket> {
    override fun handle(packet: DeleteServerCalendarEventPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableCalendar) return@execute
            if (!player.hasPermissions(CalendarServerData.OP_PERMISSION_LEVEL)) return@execute

            val data = CalendarServerData.get(server)
            if (!data.removeEvent(packet.eventId)) return@execute

            val syncPacket = SyncServerCalendarPacket(data.listEvents())
            server.playerList.players.forEach { target ->
                syncPacket.sendToPlayer(target)
            }
        }
    }
}
