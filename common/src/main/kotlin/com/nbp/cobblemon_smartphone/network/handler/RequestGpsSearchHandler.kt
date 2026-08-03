package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.gps.GpsBiomeSearchTask
import com.nbp.cobblemon_smartphone.network.PokeInfoRequestLimiter
import com.nbp.cobblemon_smartphone.network.packet.GpsSearchResultPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestGpsSearchPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Runs the GPS biome search server-side (see [GpsBiomeSearchTask]) rather than trusting the
 *  client to search the world on its own, since a client only has biome data for chunks it has
 *  already loaded. At most one search per player is kept alive; a new request cancels the old. */
object RequestGpsSearchHandler : ServerNetworkPacketHandler<RequestGpsSearchPacket> {
    private val limiter = PokeInfoRequestLimiter(400L)
    private val activeSearches = mutableMapOf<UUID, GpsBiomeSearchTask>()

    override fun handle(packet: RequestGpsSearchPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            synchronized(activeSearches) {
                activeSearches.remove(player.uuid)?.cancel()
            }

            val level = player.serverLevel()

            if (!limiter.tryAcquire(player.uuid)) {
                sendResult(player, packet.requestId, GpsSearchResultPacket.Status.RATE_LIMITED, level)
                return@execute
            }

            val key = runCatching { ResourceLocation.parse(packet.biomeId) }
                .getOrNull()
                ?.let { ResourceKey.create(Registries.BIOME, it) }
            val registry = server.registryAccess().registryOrThrow(Registries.BIOME)
            if (key == null || !registry.containsKey(key)) {
                sendResult(player, packet.requestId, GpsSearchResultPacket.Status.ERROR, level)
                return@execute
            }

            startSearch(server, player, level, key, packet.requestId)
        }
    }

    private fun startSearch(
        server: MinecraftServer,
        player: ServerPlayer,
        level: ServerLevel,
        key: ResourceKey<net.minecraft.world.level.biome.Biome>,
        requestId: Long
    ) {
        lateinit var task: GpsBiomeSearchTask
        task = GpsBiomeSearchTask(
            level = level,
            origin = player.blockPosition(),
            targetKey = key,
            maxRadius = CobblemonSmartphone.config.gps.maxSearchRadius,
            onFound = { pos ->
                server.execute {
                    completeSearch(player.uuid, task)
                    sendResult(player, requestId, GpsSearchResultPacket.Status.FOUND, level, pos)
                }
            },
            onFinished = { found ->
                if (!found) {
                    server.execute {
                        completeSearch(player.uuid, task)
                        sendResult(player, requestId, GpsSearchResultPacket.Status.NOT_FOUND, level)
                    }
                }
            }
        )
        synchronized(activeSearches) { activeSearches[player.uuid] = task }
        task.start()
    }

    private fun completeSearch(playerId: UUID, task: GpsBiomeSearchTask) {
        synchronized(activeSearches) {
            if (activeSearches[playerId] === task) activeSearches.remove(playerId)
        }
    }

    private fun sendResult(
        player: ServerPlayer,
        requestId: Long,
        status: GpsSearchResultPacket.Status,
        level: ServerLevel,
        pos: BlockPos? = null
    ) {
        GpsSearchResultPacket(requestId, status, level.dimension().location().toString(), pos).sendToPlayer(player)
    }
}
