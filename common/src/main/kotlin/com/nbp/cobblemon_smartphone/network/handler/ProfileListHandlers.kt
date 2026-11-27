package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.PinPostPacket
import com.nbp.cobblemon_smartphone.network.packet.ProfileListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestProfileListPacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialProfile
import com.nbp.cobblemon_smartphone.social.SocialTrainerSummary
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object PinPostHandler : ServerNetworkPacketHandler<PinPostPacket> {
    override fun handle(packet: PinPostPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.PIN_POST)) return@execute

            val data = SocialData.get(server)
            data.setPinnedPost(player.uuid, player.gameProfile.name, packet.postId)
            RequestProfileHandler.sendProfileResponse(server, player, player.uuid)
        }
    }
}

object RequestProfileListHandler : ServerNetworkPacketHandler<RequestProfileListPacket> {
    private const val MAX_LIST_RESULTS = 200

    override fun handle(packet: RequestProfileListPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.REQUEST_PROFILE_LIST)) return@execute

            val data = SocialData.get(server)
            val onlinePlayers = server.playerList.players.associateBy { it.uuid }

            val summaries = when (packet.listType) {
                RequestProfileListPacket.ListType.FOLLOWERS -> {
                    val target = data.findProfile(packet.targetUuid)
                    (target?.followers ?: emptySet())
                        .mapNotNull { uuid -> buildSummary(server, data, onlinePlayers, uuid, player.uuid) }
                        .sortedWith(compareByDescending<SocialTrainerSummary> { it.isOnline }.thenBy { it.name.lowercase() })
                        .take(MAX_LIST_RESULTS)
                }

                RequestProfileListPacket.ListType.FOLLOWING -> {
                    val target = data.findProfile(packet.targetUuid)
                    (target?.following ?: emptySet())
                        .mapNotNull { uuid -> buildSummary(server, data, onlinePlayers, uuid, player.uuid) }
                        .sortedWith(compareByDescending<SocialTrainerSummary> { it.isOnline }.thenBy { it.name.lowercase() })
                        .take(MAX_LIST_RESULTS)
                }

                RequestProfileListPacket.ListType.DIRECTORY -> {
                    val allUuids = LinkedHashSet<UUID>()
                    onlinePlayers.keys.forEach { if (it != player.uuid) allUuids.add(it) }
                    data.allProfiles().forEach { if (it.uuid != player.uuid && it.name.isNotBlank()) allUuids.add(it.uuid) }
                    allUuids.mapNotNull { uuid -> buildSummary(server, data, onlinePlayers, uuid, player.uuid) }
                        .sortedWith(compareByDescending<SocialTrainerSummary> { it.isOnline }.thenBy { it.name.lowercase() })
                        .take(MAX_LIST_RESULTS)
                }
            }

            ProfileListResponsePacket(packet.targetUuid, packet.listType, summaries).sendToPlayer(player)
        }
    }

    private fun buildSummary(
        server: MinecraftServer,
        data: SocialData,
        onlinePlayers: Map<UUID, ServerPlayer>,
        uuid: UUID,
        viewerUuid: UUID
    ): SocialTrainerSummary? {
        val online = onlinePlayers[uuid]
        val existing = data.findProfile(uuid)
        val resolvedName = online?.gameProfile?.name
            ?: existing?.name?.takeIf { it.isNotBlank() }
            ?: server.profileCache?.get(uuid)?.orElse(null)?.name
            ?: return null

        val profile = existing ?: SocialProfile(uuid = uuid, name = resolvedName)
        if (existing != null && online != null && existing.name != online.gameProfile.name) {
            existing.name = online.gameProfile.name
            data.setDirty()
        }
        return SocialTrainerSummary.of(profile, viewerUuid, online != null, server)
    }
}
