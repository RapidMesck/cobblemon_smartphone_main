package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.client.social.SocialMute
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.network.packet.PushNotificationPacket
import net.minecraft.client.Minecraft

object PushNotificationHandler : ClientNetworkPacketHandler<PushNotificationPacket> {
    override fun handle(packet: PushNotificationPacket, client: Minecraft) {
        val notif = packet.notification
        val allowed = SocialMute.prefs.allowsNotification(notif.type) && !MutedPlayers.contains(notif.actorUuid)
        if (!allowed) return
        SocialNotificationCache.push(notif)
        if (SocialMute.prefs.allowsSound()) {
            client.player?.playSound(CobblemonSounds.POKEDEX_OPEN, 0.35f, 1.5f)
        }
    }
}
