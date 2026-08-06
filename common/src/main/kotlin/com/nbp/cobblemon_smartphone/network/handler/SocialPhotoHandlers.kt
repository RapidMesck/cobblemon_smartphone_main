package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.network.packet.RequestSocialPhotoPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoChunkPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoUploadResultPacket
import com.nbp.cobblemon_smartphone.network.packet.UploadSocialPhotoPacket
import com.nbp.cobblemon_smartphone.social.SocialPhotoManager
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UploadSocialPhotoHandler : ServerNetworkPacketHandler<UploadSocialPhotoPacket> {
    override fun handle(packet: UploadSocialPhotoPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            SocialPhotoManager.acceptChunk(server, player, packet.photoId, packet.totalBytes, packet.offset, packet.data)
        }
    }
}

object RequestSocialPhotoHandler : ServerNetworkPacketHandler<RequestSocialPhotoPacket> {
    override fun handle(packet: RequestSocialPhotoPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableSocial) return@execute
            SocialPhotoManager.send(server, player, packet.photoId)
        }
    }
}

object SocialPhotoUploadResultHandler : ClientNetworkPacketHandler<SocialPhotoUploadResultPacket> {
    override fun handle(packet: SocialPhotoUploadResultPacket, client: Minecraft) {
        SocialPhotoClient.acceptUploadResult(packet)
    }
}

object SocialPhotoChunkHandler : ClientNetworkPacketHandler<SocialPhotoChunkPacket> {
    override fun handle(packet: SocialPhotoChunkPacket, client: Minecraft) {
        SocialPhotoClient.acceptDownload(packet)
    }
}
