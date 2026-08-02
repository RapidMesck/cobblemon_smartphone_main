package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.CreatePostPacket
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.SocialData
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialPhotoManager
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object CreatePostHandler : ServerNetworkPacketHandler<CreatePostPacket> {
    private const val ERROR_COLOR = 0xfd0100
    private val lastPost = mutableMapOf<UUID, Long>()

    override fun handle(packet: CreatePostPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) {
                player.error("message.nbp.social.disabled")
                result(player, packet, SocialMutationResultPacket.Status.DISABLED)
                return@execute
            }

            val data = SocialData.get(server)
            if (data.isPostBanned(player.uuid)) {
                player.error("message.nbp.social.post_banned")
                result(player, packet, SocialMutationResultPacket.Status.BANNED)
                return@execute
            }

            val cooldown = CobblemonSmartphone.config.cooldowns.socialPost
            if (cooldown > 0) {
                val now = System.currentTimeMillis() / 1000
                val elapsed = now - (lastPost[player.uuid] ?: 0)
                if (elapsed < cooldown) {
                    player.displayClientMessage(
                        Component.translatable("message.nbp.social.cooldown", (cooldown - elapsed).toInt())
                            .withColor(ERROR_COLOR),
                        true
                    )
                    result(player, packet, SocialMutationResultPacket.Status.RATE_LIMITED)
                    return@execute
                }
            }

            val maxLength = CobblemonSmartphone.config.social.maxPostLength
            val text = packet.text.trim().take(maxLength)
            val attachment = resolveAttachment(player, packet)
            val photo = SocialPhotoManager.claim(player, packet.photoId)
            if (text.isEmpty() && attachment == null && photo == null) {
                player.error("message.nbp.social.empty_post")
                result(player, packet, SocialMutationResultPacket.Status.EMPTY)
                return@execute
            }

            val post = data.addPost(player.uuid, player.gameProfile.name, text, attachment, photo)
            lastPost[player.uuid] = System.currentTimeMillis() / 1000
            server.playerList.players.forEach { viewer ->
                FeedPostUpdatePacket(0L, SocialPostView.of(post, viewer.uuid)).sendToPlayer(viewer)
            }
            result(player, packet, SocialMutationResultPacket.Status.SUCCESS)
        }
    }

    /**
     * Builds the snapshot from the player's *own* party, so the attachment cannot be forged:
     * the client only ever supplies a slot index.
     */
    private fun resolveAttachment(player: ServerPlayer, packet: CreatePostPacket): PokemonAttachment? {
        if (packet.attachSlot < 0) return null
        val pokemon = player.party().get(packet.attachSlot) ?: return null
        val showDetails = packet.showDetails
        return PokemonAttachment(
            species = pokemon.species.resourceIdentifier.toString(),
            aspects = pokemon.aspects,
            level = pokemon.level,
            nickname = pokemon.nickname?.string,
            ivs = if (showDetails && packet.showIvs) ATTACHMENT_STATS.map { pokemon.ivs[it] ?: 0 } else emptyList(),
            evs = if (showDetails && packet.showEvs) ATTACHMENT_STATS.map { pokemon.evs[it] ?: 0 } else emptyList(),
            gender = pokemon.gender.name,
            ability = if (showDetails && packet.showAbility) pokemon.ability.displayName else null,
            nature = if (showDetails && packet.showNature) pokemon.effectiveNature.displayName else null,
            types = if (showDetails) {
                listOfNotNull(pokemon.form.primaryType.name, pokemon.form.secondaryType?.name)
            } else {
                emptyList()
            }
        )
    }

    private fun ServerPlayer.error(key: String) {
        displayClientMessage(Component.translatable(key).withColor(ERROR_COLOR), true)
    }

    private fun result(player: ServerPlayer, packet: CreatePostPacket, status: SocialMutationResultPacket.Status) {
        SocialMutationResultPacket(packet.requestId, status).sendToPlayer(player)
    }

    private val ATTACHMENT_STATS = listOf(
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED
    )
}
