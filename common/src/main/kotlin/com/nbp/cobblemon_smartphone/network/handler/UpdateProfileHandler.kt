package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.ProfileResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.UpdateProfilePacket
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpdateProfileHandler : ServerNetworkPacketHandler<UpdateProfilePacket> {
    override fun handle(packet: UpdateProfilePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.UPDATE_PROFILE)) return@execute

            val data = SocialData.get(server)
            val bio = packet.bio.trim().take(150)
            data.updateBio(player.uuid, player.gameProfile.name, bio)

            if (packet.favoritePartySlot in 0..5) {
                val pokemon = player.party().get(packet.favoritePartySlot)
                if (pokemon != null) {
                    data.setFavoritePokemon(
                        player.uuid,
                        player.gameProfile.name,
                        pokemon.species.resourceIdentifier.toString(),
                        pokemon.aspects
                    )
                }
            } else if (packet.favoritePartySlot == -2) {
                data.setFavoritePokemon(player.uuid, player.gameProfile.name, null, emptySet())
            }

            val profile = data.profileFor(player.uuid, player.gameProfile.name)
            val userPosts = data.feed().filter { it.authorUuid == player.uuid }
            val postViews = userPosts.map { SocialPostView.of(it, player.uuid, server) }
            val profileView = SocialProfileView.of(profile, player.uuid, userPosts.size, true, server)

            ProfileResponsePacket(profileView, postViews).sendToPlayer(player)
        }
    }
}
