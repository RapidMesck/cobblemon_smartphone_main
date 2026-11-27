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
import java.util.UUID

object RequestProfileHandler : ServerNetworkPacketHandler<RequestProfilePacket> {
    private const val MAX_PROFILE_POSTS = 100

    override fun handle(packet: RequestProfilePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.REQUEST_PROFILE)) return@execute

            sendProfileResponse(server, player, packet.targetUuid)
        }
    }

    fun sendProfileResponse(server: MinecraftServer, viewer: ServerPlayer, targetUuid: UUID) {
        val data = SocialData.get(server)
        val onlinePlayer = server.playerList.getPlayer(targetUuid)
        val fallbackName = onlinePlayer?.gameProfile?.name
            ?: server.profileCache?.get(targetUuid)?.orElse(null)?.name
            ?: data.feed().firstOrNull { it.authorUuid == targetUuid }?.authorName
            ?: ""

        val existing = data.findProfile(targetUuid)
        val profile = existing ?: com.nbp.cobblemon_smartphone.social.SocialProfile(uuid = targetUuid, name = fallbackName)
        if (existing != null && onlinePlayer != null && existing.name != onlinePlayer.gameProfile.name) {
            existing.name = onlinePlayer.gameProfile.name
            data.setDirty()
        } else if (existing != null && existing.name.isBlank() && fallbackName.isNotBlank()) {
            existing.name = fallbackName
            data.setDirty()
        }

        val allUserPosts = data.feed().filter { it.authorUuid == targetUuid }
        val pinnedId = profile.pinnedPostId
        val orderedPosts = if (pinnedId > 0L) {
            val pinned = allUserPosts.firstOrNull { it.id == pinnedId }
            if (pinned != null) {
                listOf(pinned) + allUserPosts.filter { it.id != pinnedId }
            } else {
                allUserPosts
            }
        } else {
            allUserPosts
        }
        val postViews = orderedPosts.take(MAX_PROFILE_POSTS).map { SocialPostView.of(it, viewer.uuid, server) }
        val profileView = SocialProfileView.of(profile, viewer.uuid, allUserPosts.size, onlinePlayer != null, server)

        ProfileResponsePacket(profileView, postViews).sendToPlayer(viewer)
    }
}
