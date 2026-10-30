package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.network.packet.VotePollPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object VotePollHandler : ServerNetworkPacketHandler<VotePollPacket> {
    override fun handle(packet: VotePollPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.LIKE)) return@execute

            val data = SocialData.get(server)
            if (data.isPostBanned(player.uuid)) return@execute

            if (data.votePoll(packet.postId, player.uuid, packet.optionIndex)) {
                val post = data.postById(packet.postId) ?: return@execute
                server.playerList.players.forEach { viewer ->
                    FeedPostUpdatePacket(0L, SocialPostView.of(post, viewer.uuid, server)).sendToPlayer(viewer)
                }
            }
        }
    }
}
