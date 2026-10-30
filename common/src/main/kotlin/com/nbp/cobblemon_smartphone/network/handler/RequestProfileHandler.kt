package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.ProfileResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestProfilePacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestProfileHandler : ServerNetworkPacketHandler<RequestProfilePacket> {
    override fun handle(packet: RequestProfilePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.REQUEST_PROFILE)) return@execute

            val data = SocialData.get(server)
            val isOnline = server.playerList.getPlayer(packet.targetUuid) != null
            val profile = data.profileFor(packet.targetUuid)
            val userPosts = data.feed().filter { it.authorUuid == packet.targetUuid }
            val postViews = userPosts.map { SocialPostView.of(it, player.uuid, server) }
            val profileView = SocialProfileView.of(profile, player.uuid, userPosts.size, isOnline, server)

            ProfileResponsePacket(profileView, postViews).sendToPlayer(player)
        }
    }
}
