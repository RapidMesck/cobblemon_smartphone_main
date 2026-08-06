package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.PokeInfoRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.RequestStructureSearchPacket
import com.nbp.cobblemon_smartphone.network.packet.StructureSearchResultPacket
import com.nbp.cobblemon_smartphone.structure.StructureSearchTask
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.structure.Structure
import java.util.UUID

/** Runs the structure search server-side (see [StructureSearchTask]) — mirrors
 *  RequestGpsSearchHandler's request/response and single-search-per-player bookkeeping. */
object RequestStructureSearchHandler : ServerNetworkPacketHandler<RequestStructureSearchPacket> {
    private val limiter = PokeInfoRequestLimiter(400L)
    private val activeSearches = mutableMapOf<UUID, StructureSearchTask>()

    override fun handle(packet: RequestStructureSearchPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!CobblemonSmartphone.config.features.enableStructureCompass) return@execute

            synchronized(activeSearches) {
                activeSearches.remove(player.uuid)?.cancel()
            }

            val level = player.serverLevel()

            if (!limiter.tryAcquire(player.uuid)) {
                sendResult(player, packet.requestId, StructureSearchResultPacket.Status.RATE_LIMITED, level)
                return@execute
            }

            val key = runCatching { ResourceLocation.parse(packet.structureId) }
                .getOrNull()
                ?.let { ResourceKey.create(Registries.STRUCTURE, it) }
            val registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
            if (key == null || !registry.containsKey(key)) {
                sendResult(player, packet.requestId, StructureSearchResultPacket.Status.ERROR, level)
                return@execute
            }

            startSearch(server, player, level, key, packet.requestId)
        }
    }

    private fun startSearch(
        server: MinecraftServer,
        player: ServerPlayer,
        level: ServerLevel,
        key: ResourceKey<Structure>,
        requestId: Long
    ) {
        lateinit var task: StructureSearchTask
        task = StructureSearchTask(
            level = level,
            origin = player.blockPosition(),
            targetKey = key,
            searchRadiusChunks = CobblemonSmartphone.config.structureCompass.searchRadiusChunks,
            skipKnownStructures = CobblemonSmartphone.config.structureCompass.skipKnownStructures,
            onFound = { pos ->
                server.execute {
                    completeSearch(player.uuid, task)
                    sendResult(player, requestId, StructureSearchResultPacket.Status.FOUND, level, pos)
                }
            },
            onFinished = { found ->
                if (!found) {
                    server.execute {
                        completeSearch(player.uuid, task)
                        sendResult(player, requestId, StructureSearchResultPacket.Status.NOT_FOUND, level)
                    }
                }
            }
        )
        synchronized(activeSearches) { activeSearches[player.uuid] = task }
        task.start()
    }

    private fun completeSearch(playerId: UUID, task: StructureSearchTask) {
        synchronized(activeSearches) {
            if (activeSearches[playerId] === task) activeSearches.remove(playerId)
        }
    }

    private fun sendResult(
        player: ServerPlayer,
        requestId: Long,
        status: StructureSearchResultPacket.Status,
        level: ServerLevel,
        pos: BlockPos? = null
    ) {
        StructureSearchResultPacket(requestId, status, level.dimension().location().toString(), pos).sendToPlayer(player)
    }
}
