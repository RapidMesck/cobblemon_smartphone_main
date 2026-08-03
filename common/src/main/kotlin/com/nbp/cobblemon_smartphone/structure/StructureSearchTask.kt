package com.nbp.cobblemon_smartphone.structure

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Looks up the nearest instance of a structure using the same vanilla lookup that backs
 * `/locate structure` and treasure/explorer maps (`ChunkGenerator#findNearestMapStructure`), which
 * reads structure placement straight from the generator's structure sets — unlike biomes there's no
 * need for a manual chunk-by-chunk scan (see GpsBiomeSearchTask). Still runs off the main thread
 * since resolving structure references can trigger chunk generation within the search radius.
 */
class StructureSearchTask(
    private val level: ServerLevel,
    private val origin: BlockPos,
    private val targetKey: ResourceKey<Structure>,
    private val searchRadiusChunks: Int,
    private val skipKnownStructures: Boolean,
    private val onFound: (BlockPos) -> Unit,
    private val onFinished: (Boolean) -> Unit
) : Thread("cobblemon-smartphone-structure-search") {

    @Volatile private var active = true

    init {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }

    fun cancel() {
        active = false
    }

    override fun run() {
        if (!active) return

        val holder = level.registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .getHolder(targetKey)
            .orElse(null)
        if (holder == null) {
            if (active) onFinished(false)
            return
        }

        val result = try {
            level.chunkSource.generator.findNearestMapStructure(
                level, HolderSet.direct(holder), origin, searchRadiusChunks, skipKnownStructures
            )
        } catch (_: Exception) {
            null
        }

        if (!active) return
        val pos = result?.first
        if (pos != null) onFound(pos) else onFinished(false)
    }
}
