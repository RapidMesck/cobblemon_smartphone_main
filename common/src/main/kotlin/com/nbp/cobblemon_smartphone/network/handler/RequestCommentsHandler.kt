package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.CommentsListPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestCommentsPacket
import com.nbp.cobblemon_smartphone.social.SocialCommentView
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestCommentsHandler : ServerNetworkPacketHandler<RequestCommentsPacket> {
    override fun handle(packet: RequestCommentsPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.REQUEST_COMMENTS)) return@execute

            val data = SocialData.get(server)
            val post = data.postById(packet.postId)
            val comments = post?.comments?.map { SocialCommentView.of(it, server) } ?: emptyList()
            CommentsListPacket(packet.postId, comments).sendToPlayer(player)
        }
    }
}
