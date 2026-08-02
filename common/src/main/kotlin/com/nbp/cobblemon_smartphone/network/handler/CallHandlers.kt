package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.client.gui.DmThreadScreen
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.network.packet.CallOfflinePacket
import com.nbp.cobblemon_smartphone.network.packet.CallStatePacket
import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import com.nbp.cobblemon_smartphone.social.CallManager
import com.nbp.cobblemon_smartphone.social.CallStatus
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object CallActionHandler : ServerNetworkPacketHandler<CallActionPacket> {
    override fun handle(packet: CallActionPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val limitAction = if (packet.action == CallActionPacket.Action.START) {
                SocialRequestLimiter.Action.CALL_START
            } else {
                SocialRequestLimiter.Action.CALL_ACTION
            }
            if (packet.action != CallActionPacket.Action.START &&
                !SocialRequestLimiter.allow(player.uuid, limitAction)
            ) return@execute
            when (packet.action) {
                CallActionPacket.Action.START -> CallManager.start(server, player, packet.targetUuid)
                CallActionPacket.Action.ACCEPT -> CallManager.accept(server, player)
                CallActionPacket.Action.DECLINE, CallActionPacket.Action.HANGUP -> CallManager.end(server, player)
            }
        }
    }
}

/**
 * Applies the server's call state to the client. The visuals live in the HUD [CallOverlay] and the
 * ring/timeout in [com.nbp.cobblemon_smartphone.client.social.CallClientTicker], so this handler
 * only updates the cache and plays one-shot cues on connect/end.
 */
object CallStateHandler : ClientNetworkPacketHandler<CallStatePacket> {
    override fun handle(packet: CallStatePacket, client: Minecraft) {
        val previous = CallState.status
        CallState.update(packet.status, packet.otherUuid, packet.otherName)
        val player = client.player ?: return

        when (packet.status) {
            CallStatus.IN_CALL -> {
                player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.5f, 1.2f)
                player.displayClientMessage(
                    Component.translatable("message.nbp.social.call_connected", packet.otherName),
                    true
                )
            }
            CallStatus.IDLE -> {
                // Only announce the end of something that was actually happening.
                if (previous != CallStatus.IDLE) {
                    player.displayClientMessage(
                        Component.translatable("message.nbp.social.call_ended", packet.otherName),
                        true
                    )
                }
            }
            // INCOMING and OUTGOING are surfaced entirely by the overlay + ticker.
            CallStatus.INCOMING, CallStatus.OUTGOING -> Unit
        }
    }
}

/** Displays server-confirmed offline feedback only inside the matching smartphone conversation. */
object CallOfflineHandler : ClientNetworkPacketHandler<CallOfflinePacket> {
    override fun handle(packet: CallOfflinePacket, client: Minecraft) {
        (client.screen as? DmThreadScreen)?.showCallOffline(packet.targetUuid)
    }
}
