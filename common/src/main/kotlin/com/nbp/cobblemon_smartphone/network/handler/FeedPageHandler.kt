package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.network.packet.FeedPagePacket
import net.minecraft.client.Minecraft

object FeedPageHandler : ClientNetworkPacketHandler<FeedPagePacket> {
    override fun handle(packet: FeedPagePacket, client: Minecraft) {
        SocialFeedCache.accept(packet.posts, packet.hasMore, packet.append)
    }
}
