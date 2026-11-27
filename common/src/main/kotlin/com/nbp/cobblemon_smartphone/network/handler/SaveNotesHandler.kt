package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.SaveNotesPacket
import com.nbp.cobblemon_smartphone.util.NotesStorage
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveNotesHandler : ServerNetworkPacketHandler<SaveNotesPacket> {
    override fun handle(packet: SaveNotesPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableNotes) return@execute
            NotesStorage.write(player, packet.notes)
        }
    }
}
