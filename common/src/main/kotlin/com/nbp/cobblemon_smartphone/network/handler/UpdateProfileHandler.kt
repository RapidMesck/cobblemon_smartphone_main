package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.UpdateProfilePacket
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpdateProfileHandler : ServerNetworkPacketHandler<UpdateProfilePacket> {
    override fun handle(packet: UpdateProfilePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            if (!SocialRequestLimiter.allow(player.uuid, SocialRequestLimiter.Action.UPDATE_PROFILE)) return@execute

            val data = SocialData.get(server)
            val playerName = player.gameProfile.name
            data.updateProfileSettings(
                uuid = player.uuid,
                name = playerName,
                bio = packet.bio,
                status = packet.status,
                bannerType = packet.bannerType,
                showTeam = packet.showTeam,
                acceptInteractions = packet.acceptInteractions,
                dmMutualOnly = packet.dmMutualOnly
            )

            if (packet.favoritePartySlot in 0..5) {
                val pokemon = player.party().get(packet.favoritePartySlot)
                if (pokemon != null) {
                    data.setFavoritePokemon(
                        player.uuid,
                        playerName,
                        pokemon.species.resourceIdentifier.toString(),
                        pokemon.aspects
                    )
                }
            } else if (packet.favoritePartySlot == -2) {
                data.setFavoritePokemon(player.uuid, playerName, null, emptySet())
            }

            if (packet.syncTeamAction == 1) {
                val team = (0..5).mapNotNull { slot ->
                    player.party().get(slot)?.let { PokemonAttachment.fromPokemon(it) }
                }
                data.setTeamShowcase(player.uuid, playerName, team)
            } else if (packet.syncTeamAction == -1) {
                data.setTeamShowcase(player.uuid, playerName, emptyList())
            }

            RequestProfileHandler.sendProfileResponse(server, player, player.uuid)
        }
    }
}
