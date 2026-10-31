package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.SetRotomScanPriorityPacket
import com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SetRotomScanPriorityHandler : ServerNetworkPacketHandler<SetRotomScanPriorityPacket> {
    override fun handle(packet: SetRotomScanPriorityPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableScanner) return@execute
            if (!CobblemonSmartphone.config.rotom.enableManualScanPriority) return@execute
            val entity = player.serverLevel().getEntity(packet.entityId) as? PokemonEntity ?: return@execute
            if (!entity.isAlive || player.distanceToSqr(entity) > CobblemonSmartphone.config.rotom.scanRadiusSqr) return@execute
            RotomAutoScannerManager.setPlayerPriorityTarget(player, entity)
        }
    }
}
