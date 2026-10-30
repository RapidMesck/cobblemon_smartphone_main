package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialCommentCache
import com.nbp.cobblemon_smartphone.network.packet.CommentsListPacket
import net.minecraft.client.Minecraft

object CommentsListHandler : ClientNetworkPacketHandler<CommentsListPacket> {
    override fun handle(packet: CommentsListPacket, client: Minecraft) {
        SocialCommentCache.accept(packet.postId, packet.comments)
    }
}
