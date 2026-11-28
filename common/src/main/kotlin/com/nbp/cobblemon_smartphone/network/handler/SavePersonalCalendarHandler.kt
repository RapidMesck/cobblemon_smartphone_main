package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.calendar.CalendarPersonalStorage
import com.nbp.cobblemon_smartphone.network.packet.SavePersonalCalendarPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SavePersonalCalendarHandler : ServerNetworkPacketHandler<SavePersonalCalendarPacket> {
    override fun handle(packet: SavePersonalCalendarPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableCalendar) return@execute
            CalendarPersonalStorage.write(player, packet.data)
        }
    }
}
