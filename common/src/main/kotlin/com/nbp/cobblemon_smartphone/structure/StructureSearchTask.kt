package com.nbp.cobblemon_smartphone.structure

import com.nbp.cobblemon_smartphone.gps.SearchConcurrencyLimiter
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Looks up the nearest instance of a structure using the same vanilla lookup that backs
 * `/locate structure` and treasure/explorer maps (`ChunkGenerator#findNearestMapStructure`).
 *
 * Must run on the server thread: `findNearestMapStructure` accesses `ServerChunkCache` to resolve
 * structure starts, which is not thread-safe and can deadlock or crash off-thread.
 */
class StructureSearchTask(
    private val level: ServerLevel,
    private val origin: BlockPos,
    private val targetKey: ResourceKey<Structure>,
    private val searchRadiusChunks: Int,
    private val skipKnownStructures: Boolean,
    private val onFound: (BlockPos) -> Unit,
    private val onFinished: (Boolean) -> Unit
) {

    fun runOnServerThread() {
        val holder = level.registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .getHolder(targetKey)
            .orElse(null)
        if (holder == null) {
            onFinished(false)
            return
        }

        val result = try {
            level.chunkSource.generator.findNearestMapStructure(
                level, HolderSet.direct(holder), origin, searchRadiusChunks, skipKnownStructures
            )
        } catch (_: Exception) {
            null
        }

        val pos = result?.first
        if (pos != null) onFound(pos) else onFinished(false)
    }
}
