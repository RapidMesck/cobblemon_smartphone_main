package com.nbp.cobblemon_smartphone.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket
import net.minecraft.network.chat.Component

object SocialEvents {
    private const val GOLD_COLOR = 0xffd700

    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event: PokemonCapturedEvent ->
            handlePokemonCaptured(event)
        }
    }

    private fun handlePokemonCaptured(event: PokemonCapturedEvent) {
        val player = event.player
        val pokemon = event.pokemon
        val server = player.server ?: return

        if (!CobblemonSmartphone.config.features.enableSocial) return

        val isShiny = pokemon.shiny
        val isLegendary = pokemon.isLegendary()

        if (isShiny || isLegendary) {
            val speciesName = pokemon.species.name
            val text = "★ $speciesName"

            val data = SocialData.get(server)
            val notif = data.addNotification(
                targetUuid = player.uuid,
                actorUuid = player.uuid,
                actorName = player.gameProfile.name,
                type = SocialNotificationType.MENTION,
                referenceId = 0L,
                message = text
            )

            if (notif != null) {
                PushNotificationPacket(SocialNotificationView.of(notif, server)).sendToPlayer(player)
            }

            player.sendSystemMessage(
                Component.translatable(
                    if (isShiny) "message.nbp.social.captured_shiny" else "message.nbp.social.captured_legendary",
                    speciesName
                ).withColor(GOLD_COLOR)
            )
        }
    }
}
