package com.nbp.cobblemon_smartphone.gps

import net.minecraft.core.BlockPos
import net.minecraft.core.QuartPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.biome.Biome

/**
 * Background spiral search for the nearest instance of a biome: rings of chunk centers expanding
 * outward from the origin, sampling the biome noise directly through the chunk generator's
 * [net.minecraft.world.level.biome.BiomeSource.getNoiseBiome] (the same lookup vanilla uses to
 * resolve a column's biome while generating a chunk) rather than loading or generating any chunk.
 * Several fixed Y bands per column cover both surface and cave biomes. Runs on a low-priority
 * daemon thread server-side, since the client only has biome data for chunks it has already loaded
 * and can't be trusted to search the wider world.
 */
class GpsBiomeSearchTask(
    private val level: ServerLevel,
    private val origin: BlockPos,
    private val targetKey: ResourceKey<Biome>,
    private val maxRadius: Int,
    private val onFound: (BlockPos) -> Unit,
    private val onFinished: (Boolean) -> Unit
) : Thread("cobblemon-smartphone-gps-search") {

    @Volatile private var active = true

    init {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }

    fun cancel() {
        active = false
    }

    override fun run() {
        val maxRing = ((maxRadius.coerceAtLeast(16) + 15) / 16)
        val chunkX0 = origin.x shr 4
        val chunkZ0 = origin.z shr 4
        val ySamples = intArrayOf(
            level.seaLevel,
            level.minBuildHeight + 16,
            0,
            level.maxBuildHeight - 16
        ).distinct().toIntArray()

        if (sampleChunk(chunkX0, chunkZ0, ySamples)) return

        for (ring in 1..maxRing) {
            if (!active) return

            var x = -ring
            var z = -ring
            while (x < ring) {
                if (sampleChunk(chunkX0 + x, chunkZ0 + z, ySamples)) return
                x++
            }
            while (z < ring) {
                if (sampleChunk(chunkX0 + x, chunkZ0 + z, ySamples)) return
                z++
            }
            while (x > -ring) {
                if (sampleChunk(chunkX0 + x, chunkZ0 + z, ySamples)) return
                x--
            }
            while (z > -ring) {
                if (sampleChunk(chunkX0 + x, chunkZ0 + z, ySamples)) return
                z--
            }
        }

        if (active) onFinished(false)
    }

    /** Returns true when the search should stop (found or cancelled). */
    private fun sampleChunk(chunkX: Int, chunkZ: Int, ySamples: IntArray): Boolean {
        if (!active) return true
        val px = (chunkX shl 4) + 8
        val pz = (chunkZ shl 4) + 8
        val sampler = level.chunkSource.randomState().sampler()
        val quartX = QuartPos.fromBlock(px)
        val quartZ = QuartPos.fromBlock(pz)
        val biomeSource = level.chunkSource.generator.biomeSource
        for (y in ySamples) {
            if (!active) return true
            try {
                val quartY = QuartPos.fromBlock(y)
                if (biomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler).`is`(targetKey)) {
                    onFound(BlockPos(px, y, pz))
                    return true
                }
            } catch (_: Exception) {
                return true
            }
        }
        return false
    }
}
