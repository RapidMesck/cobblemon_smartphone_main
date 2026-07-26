package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.CreatePostPacket
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.SocialData
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
                return@execute
            }

            val data = SocialData.get(server)
            if (data.isPostBanned(player.uuid)) {
                player.error("message.nbp.social.post_banned")
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
                    return@execute
                }
            }

            val maxLength = CobblemonSmartphone.config.social.maxPostLength
            val text = packet.text.trim().take(maxLength)
            val attachment = resolveAttachment(player, packet.attachSlot)
            if (text.isEmpty() && attachment == null) {
                player.error("message.nbp.social.empty_post")
                return@execute
            }

            data.addPost(player.uuid, player.gameProfile.name, text, attachment)
            lastPost[player.uuid] = System.currentTimeMillis() / 1000

            // Refresh the author's feed so the new post shows immediately.
            RequestFeedPageHandler.sendPage(server, player, 0L)
        }
    }

    /**
     * Builds the snapshot from the player's *own* party, so the attachment cannot be forged:
     * the client only ever supplies a slot index.
     */
    private fun resolveAttachment(player: ServerPlayer, slot: Int): PokemonAttachment? {
        if (slot < 0) return null
        val pokemon = player.party().get(slot) ?: return null
        return PokemonAttachment(
            species = pokemon.species.resourceIdentifier.toString(),
            aspects = pokemon.aspects,
            level = pokemon.level,
            nickname = pokemon.nickname?.string
        )
    }

    private fun ServerPlayer.error(key: String) {
        displayClientMessage(Component.translatable(key).withColor(ERROR_COLOR), true)
    }
}
