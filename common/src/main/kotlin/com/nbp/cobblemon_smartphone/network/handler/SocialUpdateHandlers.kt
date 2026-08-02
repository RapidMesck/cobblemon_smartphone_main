package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialMutationState
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadSummaryUpdatePacket
import net.minecraft.client.Minecraft

object SocialMutationResultHandler : ClientNetworkPacketHandler<SocialMutationResultPacket> {
    override fun handle(packet: SocialMutationResultPacket, client: Minecraft) =
        SocialMutationState.accept(packet.requestId, packet.status)
}

object FeedPostUpdateHandler : ClientNetworkPacketHandler<FeedPostUpdatePacket> {
    override fun handle(packet: FeedPostUpdatePacket, client: Minecraft) =
        SocialFeedCache.applyUpdate(packet.deletedId, packet.post)
}

object ThreadSummaryUpdateHandler : ClientNetworkPacketHandler<ThreadSummaryUpdatePacket> {
    override fun handle(packet: ThreadSummaryUpdatePacket, client: Minecraft) =
        SocialDmCache.applySummary(packet.summary)
}
