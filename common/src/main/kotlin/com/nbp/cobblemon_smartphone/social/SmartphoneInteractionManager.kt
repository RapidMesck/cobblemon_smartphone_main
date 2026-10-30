package com.nbp.cobblemon_smartphone.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket
import com.cobblemon.mod.common.trade.ActiveTrade
import com.cobblemon.mod.common.trade.PlayerTradeParticipant
import com.cobblemon.mod.common.trade.TradeManager
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import com.mojang.brigadier.context.CommandContext
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SmartphoneInteractionManager {
    private const val ERROR_COLOR = 0xfd0100
    private const val SUCCESS_COLOR = 0x55ff55
    private const val EXPIRY_SECONDS = 30L

    enum class InteractionType {
        BATTLE,
        TRADE
    }

    data class InteractionRequest(
        val id: UUID = UUID.randomUUID(),
        val type: InteractionType,
        val senderUuid: UUID,
        val senderName: String,
        val targetUuid: UUID,
        val targetName: String,
        val createdAt: Long = System.currentTimeMillis(),
        val expirySeconds: Long = EXPIRY_SECONDS
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > createdAt + (expirySeconds * 1000L)
    }

    private val pendingRequests = ConcurrentHashMap<UUID, InteractionRequest>()

    fun onLogout(player: ServerPlayer) {
        val toRemove = pendingRequests.filter {
            it.value.senderUuid == player.uuid || it.value.targetUuid == player.uuid
        }
        toRemove.forEach { (id, _) -> pendingRequests.remove(id) }
    }

    fun sendRequest(sender: ServerPlayer, targetUuid: UUID, type: InteractionType) {
        val server = sender.server ?: return

        if (sender.uuid == targetUuid) return

        val target = server.playerList.getPlayer(targetUuid)
        if (target == null) {
            sender.displayClientMessage(
                Component.translatable("message.nbp.social.target_offline").withColor(ERROR_COLOR),
                false
            )
            return
        }

        // Mutual follow requirement: both players must follow each other
        val socialData = SocialData.get(server)
        if (!socialData.isMutualFollow(sender.uuid, target.uuid)) {
            sender.displayClientMessage(
                Component.translatable("message.nbp.social.mutual_follow_required").withColor(ERROR_COLOR),
                false
            )
            return
        }

        // Check if either player is already in battle
        if (sender.isInBattle() || target.isInBattle()) {
            sender.displayClientMessage(
                Component.translatable("message.nbp.social.already_in_battle").withColor(ERROR_COLOR),
                false
            )
            return
        }

        // Check if either player is already trading
        if (TradeManager.getActiveTrade(sender.uuid) != null || TradeManager.getActiveTrade(target.uuid) != null) {
            sender.displayClientMessage(
                Component.translatable("message.nbp.social.already_trading").withColor(ERROR_COLOR),
                false
            )
            return
        }

        // Validate party requirements
        if (type == InteractionType.BATTLE) {
            if (sender.party().none { it.currentHealth > 0 }) {
                sender.displayClientMessage(
                    Component.translatable("message.nbp.social.insufficient_pokemon_sender").withColor(ERROR_COLOR),
                    false
                )
                return
            }
            if (target.party().none { it.currentHealth > 0 }) {
                sender.displayClientMessage(
                    Component.translatable("message.nbp.social.insufficient_pokemon_target").withColor(ERROR_COLOR),
                    false
                )
                return
            }
        } else {
            if (sender.party().none()) {
                sender.displayClientMessage(
                    Component.translatable("message.nbp.social.no_pokemon_sender").withColor(ERROR_COLOR),
                    false
                )
                return
            }
            if (target.party().none()) {
                sender.displayClientMessage(
                    Component.translatable("message.nbp.social.no_pokemon_target").withColor(ERROR_COLOR),
                    false
                )
                return
            }
        }

        // Check if sender already sent a pending request of this type to target
        val existingOutbound = pendingRequests.values.firstOrNull {
            it.senderUuid == sender.uuid && it.targetUuid == target.uuid && it.type == type && !it.isExpired()
        }
        if (existingOutbound != null) {
            sender.displayClientMessage(
                Component.translatable("message.nbp.social.request_already_sent").withColor(ERROR_COLOR),
                false
            )
            return
        }

        // Check if target already sent a pending request of this type to sender (mutual request -> instant accept!)
        val existingInbound = pendingRequests.values.firstOrNull {
            it.senderUuid == target.uuid && it.targetUuid == sender.uuid && it.type == type && !it.isExpired()
        }
        if (existingInbound != null) {
            acceptRequest(sender, existingInbound.id)
            return
        }

        // Create new request
        val request = InteractionRequest(
            type = type,
            senderUuid = sender.uuid,
            senderName = sender.gameProfile.name,
            targetUuid = target.uuid,
            targetName = target.gameProfile.name
        )
        pendingRequests[request.id] = request

        // Feedback to sender
        val sentKey = if (type == InteractionType.BATTLE) "message.nbp.social.battle_sent" else "message.nbp.social.trade_sent"
        sender.displayClientMessage(
            Component.translatable(sentKey, target.gameProfile.name).withColor(SUCCESS_COLOR),
            false
        )

        // Send clickable chat invite prompt to target
        sendInvitePrompt(target, request)

        // Schedule expiration cleanup
        afterOnServer(seconds = EXPIRY_SECONDS.toFloat()) {
            val expired = pendingRequests.remove(request.id)
            if (expired != null) {
                val s = server.playerList.getPlayer(request.senderUuid)
                val t = server.playerList.getPlayer(request.targetUuid)
                val expireMsg = Component.translatable("message.nbp.social.request_expired").withStyle(ChatFormatting.GRAY)
                s?.displayClientMessage(expireMsg, false)
                t?.displayClientMessage(expireMsg, false)
            }
        }
    }

    private fun sendInvitePrompt(target: ServerPlayer, request: InteractionRequest) {
        val sound = CobblemonSounds.POKEDEX_OPEN
        try {
            target.playNotifySound(sound, SoundSource.PLAYERS, 0.6f, 1.2f)
        } catch (_: Throwable) {
            target.level().playSound(null, target.x, target.y, target.z, sound, SoundSource.PLAYERS, 0.6f, 1.2f)
        }

        val promptKey = if (request.type == InteractionType.BATTLE) {
            "message.nbp.social.battle_prompt"
        } else {
            "message.nbp.social.trade_prompt"
        }

        val header = Component.literal("\n§b📱 [Smartphone] ")
            .append(Component.translatable(promptKey, request.senderName).withStyle(ChatFormatting.YELLOW))

        val acceptBtn = Component.literal("[ ")
            .append(
                Component.translatable("message.nbp.social.btn_accept")
                    .withStyle { style ->
                        style.withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/social accept ${request.id}"))
                            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.nbp.social.btn_accept_hover", request.senderName)))
                    }
            )
            .append(Component.literal(" ]"))

        val declineBtn = Component.literal("[ ")
            .append(
                Component.translatable("message.nbp.social.btn_decline")
                    .withStyle { style ->
                        style.withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/social decline ${request.id}"))
                            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.nbp.social.btn_decline_hover")))
                    }
            )
            .append(Component.literal(" ]"))

        val timerInfo = Component.literal(" §7(30s)\n")

        val message = header
            .append(Component.literal("\n   "))
            .append(acceptBtn)
            .append(Component.literal("   "))
            .append(declineBtn)
            .append(timerInfo)

        target.displayClientMessage(message, false)
    }

    fun acceptRequest(acceptingPlayer: ServerPlayer, requestId: UUID?): Boolean {
        val server = acceptingPlayer.server ?: return false

        val request = if (requestId != null) {
            pendingRequests.remove(requestId)
        } else {
            val found = pendingRequests.values
                .filter { it.targetUuid == acceptingPlayer.uuid && !it.isExpired() }
                .maxByOrNull { it.createdAt }
            if (found != null) {
                pendingRequests.remove(found.id)
            } else null
        }

        if (request == null || request.isExpired()) {
            acceptingPlayer.displayClientMessage(
                Component.translatable("message.nbp.social.request_expired").withColor(ERROR_COLOR),
                false
            )
            return false
        }

        val sender = server.playerList.getPlayer(request.senderUuid)
        if (sender == null) {
            acceptingPlayer.displayClientMessage(
                Component.translatable("message.nbp.social.target_offline").withColor(ERROR_COLOR),
                false
            )
            return false
        }

        // Mutual follow requirement: both players must still follow each other
        val socialData = SocialData.get(server)
        if (!socialData.isMutualFollow(sender.uuid, acceptingPlayer.uuid)) {
            acceptingPlayer.displayClientMessage(
                Component.translatable("message.nbp.social.mutual_follow_required").withColor(ERROR_COLOR),
                false
            )
            return false
        }

        if (acceptingPlayer.isInBattle() || sender.isInBattle()) {
            val msg = Component.translatable("message.nbp.social.already_in_battle").withColor(ERROR_COLOR)
            acceptingPlayer.displayClientMessage(msg, false)
            sender.displayClientMessage(msg, false)
            return false
        }

        if (TradeManager.getActiveTrade(acceptingPlayer.uuid) != null || TradeManager.getActiveTrade(sender.uuid) != null) {
            val msg = Component.translatable("message.nbp.social.already_trading").withColor(ERROR_COLOR)
            acceptingPlayer.displayClientMessage(msg, false)
            sender.displayClientMessage(msg, false)
            return false
        }

        when (request.type) {
            InteractionType.BATTLE -> {
                if (sender.party().none { it.currentHealth > 0 }) {
                    sender.displayClientMessage(Component.translatable("message.nbp.social.insufficient_pokemon_sender").withColor(ERROR_COLOR), false)
                    acceptingPlayer.displayClientMessage(Component.translatable("message.nbp.social.insufficient_pokemon_target").withColor(ERROR_COLOR), false)
                    return false
                }
                if (acceptingPlayer.party().none { it.currentHealth > 0 }) {
                    acceptingPlayer.displayClientMessage(Component.translatable("message.nbp.social.insufficient_pokemon_sender").withColor(ERROR_COLOR), false)
                    sender.displayClientMessage(Component.translatable("message.nbp.social.insufficient_pokemon_target").withColor(ERROR_COLOR), false)
                    return false
                }

                val result = BattleBuilder.pvp1v1(sender, acceptingPlayer)
                if (result is ErroredBattleStart) {
                    result.sendTo(sender)
                    result.sendTo(acceptingPlayer)
                    return false
                } else {
                    sender.displayClientMessage(
                        Component.translatable("message.nbp.social.battle_started", acceptingPlayer.gameProfile.name).withColor(SUCCESS_COLOR),
                        false
                    )
                    acceptingPlayer.displayClientMessage(
                        Component.translatable("message.nbp.social.battle_started", sender.gameProfile.name).withColor(SUCCESS_COLOR),
                        false
                    )
                    return true
                }
            }
            InteractionType.TRADE -> {
                if (sender.party().none()) {
                    sender.displayClientMessage(Component.translatable("message.nbp.social.no_pokemon_sender").withColor(ERROR_COLOR), false)
                    acceptingPlayer.displayClientMessage(Component.translatable("message.nbp.social.no_pokemon_target").withColor(ERROR_COLOR), false)
                    return false
                }
                if (acceptingPlayer.party().none()) {
                    acceptingPlayer.displayClientMessage(Component.translatable("message.nbp.social.no_pokemon_sender").withColor(ERROR_COLOR), false)
                    sender.displayClientMessage(Component.translatable("message.nbp.social.no_pokemon_target").withColor(ERROR_COLOR), false)
                    return false
                }

                return startRemoteTrade(sender, acceptingPlayer)
            }
        }
    }

    private fun startRemoteTrade(sender: ServerPlayer, receiver: ServerPlayer): Boolean {
        return try {
            val trade = ActiveTrade(PlayerTradeParticipant(receiver), PlayerTradeParticipant(sender))
            val field = TradeManager::class.java.getDeclaredField("activeTrades").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val activeTrades = field.get(TradeManager) as MutableList<ActiveTrade>
            activeTrades.add(trade)

            sender.sendPacket(
                TradeStartedPacket(
                    sender.uuid,
                    receiver.name.plainCopy(),
                    trade.player1.party.mapNullPreserving(TradeStartedPacket::TradeablePokemon)
                )
            )
            receiver.sendPacket(
                TradeStartedPacket(
                    receiver.uuid,
                    sender.name.plainCopy(),
                    trade.player2.party.mapNullPreserving(TradeStartedPacket::TradeablePokemon)
                )
            )

            sender.displayClientMessage(
                Component.translatable("message.nbp.social.trade_started", receiver.gameProfile.name).withColor(SUCCESS_COLOR),
                false
            )
            receiver.displayClientMessage(
                Component.translatable("message.nbp.social.trade_started", sender.gameProfile.name).withColor(SUCCESS_COLOR),
                false
            )
            true
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error("Failed to start remote trade between ${sender.gameProfile.name} and ${receiver.gameProfile.name}", e)
            val errMsg = Component.translatable("message.nbp.social.trade_error").withColor(ERROR_COLOR)
            sender.displayClientMessage(errMsg, false)
            receiver.displayClientMessage(errMsg, false)
            false
        }
    }

    fun declineRequest(decliningPlayer: ServerPlayer, requestId: UUID?): Boolean {
        val server = decliningPlayer.server ?: return false

        val request = if (requestId != null) {
            pendingRequests.remove(requestId)
        } else {
            val found = pendingRequests.values
                .filter { it.targetUuid == decliningPlayer.uuid && !it.isExpired() }
                .maxByOrNull { it.createdAt }
            if (found != null) {
                pendingRequests.remove(found.id)
            } else null
        }

        if (request == null || request.isExpired()) {
            decliningPlayer.displayClientMessage(
                Component.translatable("message.nbp.social.request_expired").withColor(ERROR_COLOR),
                false
            )
            return false
        }

        decliningPlayer.displayClientMessage(
            Component.translatable("message.nbp.social.request_declined_receiver", request.senderName).withStyle(ChatFormatting.GRAY),
            false
        )

        val sender = server.playerList.getPlayer(request.senderUuid)
        sender?.displayClientMessage(
            Component.translatable("message.nbp.social.request_declined_sender", decliningPlayer.gameProfile.name).withStyle(ChatFormatting.RED),
            false
        )
        return true
    }

    fun handleAcceptCommand(context: CommandContext<CommandSourceStack>, requestIdStr: String?): Int {
        val player = context.source.player ?: run {
            context.source.sendFailure(Component.literal("Only players can accept interaction requests."))
            return 0
        }
        val requestId = try {
            requestIdStr?.let { UUID.fromString(it) }
        } catch (_: Exception) {
            null
        }
        val success = acceptRequest(player, requestId)
        return if (success) 1 else 0
    }

    fun handleDeclineCommand(context: CommandContext<CommandSourceStack>, requestIdStr: String?): Int {
        val player = context.source.player ?: run {
            context.source.sendFailure(Component.literal("Only players can decline interaction requests."))
            return 0
        }
        val requestId = try {
            requestIdStr?.let { UUID.fromString(it) }
        } catch (_: Exception) {
            null
        }
        val success = declineRequest(player, requestId)
        return if (success) 1 else 0
    }
}
