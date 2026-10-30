package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.FeedPagePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestFeedPagePacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestFeedPageHandler : ServerNetworkPacketHandler<RequestFeedPagePacket> {
    override fun handle(packet: RequestFeedPagePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.FEED_PAGE)) {
                sendPage(server, player, packet.beforeId, packet.followingOnly)
            }
        }
    }

    /**
     * Sends one page of the feed. Must already be on the server thread — other handlers reuse this
     * to refresh a client after a mutation.
     *
     * [beforeId] of 0 means the newest page; otherwise posts strictly older than it are returned.
     * [followingOnly] filters posts to only those by authors the player follows (and their own).
     * Page size comes from the server config, never from the client.
     */
    fun sendPage(server: MinecraftServer, player: ServerPlayer, beforeId: Long, followingOnly: Boolean = false) {
        if (!CobblemonSmartphone.config.features.enableSocial) return

        val pageSize = CobblemonSmartphone.config.social.feedPageSize.coerceIn(1, 100)
        val data = SocialData.get(server)
        val feed = data.feed()

        val candidates = if (followingOnly) {
            val profile = data.profileFor(player.uuid)
            feed.filter { it.authorUuid in profile.following || it.authorUuid == player.uuid }
        } else {
            feed
        }

        val filtered = if (beforeId <= 0L) candidates else candidates.filter { it.id < beforeId }
        val page = filtered.take(pageSize)

        FeedPagePacket(
            posts = page.map { SocialPostView.of(it, player.uuid, server) },
            hasMore = filtered.size > page.size,
            append = beforeId > 0L
        ).sendToPlayer(player)
    }
}
