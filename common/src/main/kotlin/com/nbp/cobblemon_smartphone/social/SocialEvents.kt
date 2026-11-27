package com.nbp.cobblemon_smartphone.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
import com.cobblemon.mod.common.api.events.pokemon.TradeEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.trade.PlayerTradeParticipant
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket
import net.minecraft.network.chat.Component

object SocialEvents {
    private const val GOLD_COLOR = 0xffd700

    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event: PokemonCapturedEvent ->
            handlePokemonCaptured(event)
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            handleBattleVictory(event)
        }
        CobblemonEvents.TRADE_EVENT_POST.subscribe { event: TradeEvent.Post ->
            handleTradeCompleted(event)
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
            data.recordCapture(player.uuid, player.gameProfile.name, isShiny, isLegendary)

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

    private fun handleBattleVictory(event: BattleVictoryEvent) {
        if (!CobblemonSmartphone.config.features.enableSocial) return
        val winnerPlayers = event.winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }.filter { !it.level().isClientSide }
        val loserPlayers = event.losers.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }.filter { !it.level().isClientSide }
        if (winnerPlayers.isEmpty() || loserPlayers.isEmpty()) return

        val server = winnerPlayers.first().server ?: return
        val data = SocialData.get(server)
        for (winner in winnerPlayers) {
            for (loser in loserPlayers) {
                if (winner.uuid != loser.uuid) {
                    data.recordBattleResult(winner.uuid, winner.gameProfile.name, loser.uuid, loser.gameProfile.name)
                }
            }
        }
    }

    private fun handleTradeCompleted(event: TradeEvent.Post) {
        if (!CobblemonSmartphone.config.features.enableSocial) return
        val p1 = (event.tradeParticipant1 as? PlayerTradeParticipant)?.player ?: return
        val p2 = (event.tradeParticipant2 as? PlayerTradeParticipant)?.player ?: return
        if (p1.level().isClientSide || p2.level().isClientSide) return
        if (p1.uuid == p2.uuid) return
        val server = p1.server ?: return
        SocialData.get(server).recordTrade(p1.uuid, p1.gameProfile.name, p2.uuid, p2.gameProfile.name)
    }
}
