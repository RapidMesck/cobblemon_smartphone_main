package com.nbp.cobblemon_smartphone.social

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatCalls
import com.nbp.cobblemon_smartphone.network.packet.CallStatePacket
import com.nbp.cobblemon_smartphone.util.MutedPlayersStorage
import com.nbp.cobblemon_smartphone.util.SocialMuteStorage
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Server-side ring-and-accept call state machine. Holds no Simple Voice Chat types itself — all
 * voice work goes through [VoiceChatCalls], which is only reached once the call is accepted.
 *
 * Invariant: a player is in at most one session, and both participants map to the *same* [Session]
 * instance, so ending from either side tears the whole thing down exactly once.
 */
object CallManager {
    private const val ERROR_COLOR = 0xfd0100

    private class Session(
        val caller: UUID,
        val callee: UUID,
        val callerName: String,
        val calleeName: String
    ) {
        var accepted = false
        var group: VoiceChatCalls.ActiveGroup? = null
    }

    private val sessions = mutableMapOf<UUID, Session>()

    fun start(server: MinecraftServer, caller: ServerPlayer, calleeUuid: UUID) {
        if (!CobblemonSmartphone.config.features.enableCalls || !VoiceChatBridge.isAvailable()) {
            caller.err("message.nbp.social.call_unavailable")
            return
        }
        if (calleeUuid == caller.uuid) return
        if (sessions.containsKey(caller.uuid)) {
            caller.err("message.nbp.social.call_in_progress")
            return
        }
        val callee = server.playerList.getPlayer(calleeUuid)
        if (callee == null) {
            caller.err("message.nbp.social.call_offline")
            return
        }
        // Do Not Disturb (global) or the callee having muted this caller specifically: block the
        // call at the source so the callee never rings and the caller gets immediate feedback.
        if (SocialMuteStorage.read(callee) || MutedPlayersStorage.isMuted(callee, caller.uuid)) {
            caller.err("message.nbp.social.call_muted")
            return
        }
        if (sessions.containsKey(calleeUuid)) {
            caller.err("message.nbp.social.call_busy")
            return
        }
        // Both sides must have an established voice-chat connection or the group can't hold them.
        if (!VoiceChatCalls.bothConnected(caller, callee)) {
            caller.err("message.nbp.social.call_not_connected")
            return
        }

        val session = Session(caller.uuid, calleeUuid, caller.gameProfile.name, callee.gameProfile.name)
        sessions[caller.uuid] = session
        sessions[calleeUuid] = session

        push(caller, CallStatus.OUTGOING, calleeUuid, session.calleeName)
        push(callee, CallStatus.INCOMING, caller.uuid, session.callerName)
    }

    fun accept(server: MinecraftServer, callee: ServerPlayer) {
        val session = sessions[callee.uuid] ?: return
        // Only the ringing callee can accept, and only once.
        if (session.callee != callee.uuid || session.accepted) return

        val caller = server.playerList.getPlayer(session.caller)
        if (caller == null) {
            endSession(server, session)
            return
        }

        val group = VoiceChatCalls.startGroup(caller, callee)
        if (group == null) {
            caller.err("message.nbp.social.call_not_connected")
            endSession(server, session)
            return
        }

        session.accepted = true
        session.group = group
        push(caller, CallStatus.IN_CALL, callee.uuid, session.calleeName)
        push(callee, CallStatus.IN_CALL, caller.uuid, session.callerName)
    }

    /** Decline an incoming ring, cancel an outgoing ring, or hang up an active call — all the same. */
    fun end(server: MinecraftServer, player: ServerPlayer) {
        val session = sessions[player.uuid] ?: return
        endSession(server, session)
    }

    fun onLogout(server: MinecraftServer, player: ServerPlayer) {
        val session = sessions[player.uuid] ?: return
        endSession(server, session)
    }

    private fun endSession(server: MinecraftServer, session: Session) {
        sessions.remove(session.caller)
        sessions.remove(session.callee)

        val callerPlayer = server.playerList.getPlayer(session.caller)
        val calleePlayer = server.playerList.getPlayer(session.callee)

        session.group?.let { active ->
            VoiceChatCalls.endGroup(active, listOfNotNull(callerPlayer, calleePlayer))
        }

        callerPlayer?.let { push(it, CallStatus.IDLE, session.callee, session.calleeName) }
        calleePlayer?.let { push(it, CallStatus.IDLE, session.caller, session.callerName) }
    }

    private fun push(target: ServerPlayer, status: CallStatus, otherUuid: UUID, otherName: String) {
        CallStatePacket(status, otherUuid, otherName).sendToPlayer(target)
    }

    private fun ServerPlayer.err(key: String) {
        displayClientMessage(Component.translatable(key).withColor(ERROR_COLOR), true)
    }
}
