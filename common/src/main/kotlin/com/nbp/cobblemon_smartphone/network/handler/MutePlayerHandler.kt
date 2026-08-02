package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.network.packet.MutePlayerPacket
import com.nbp.cobblemon_smartphone.util.MutedPlayersStorage
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object MutePlayerHandler : ServerNetworkPacketHandler<MutePlayerPacket> {
    override fun handle(packet: MutePlayerPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.MUTE)) return@execute
            if (packet.targetUuid == player.uuid) return@execute
            MutedPlayersStorage.setMuted(player, packet.targetUuid, packet.muted)
        }
    }
}
