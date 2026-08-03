package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.getModName
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.Biome

object GpsDataProvider {

    data class BiomeInfo(
        val id: String,
        val tags: List<String>,
        val dimensions: List<String>,
        val sourceMod: String
    )

    /**
     * Gathers every registered biome with its tags, the dimensions whose biome source can contain
     * it, and the mod that added it (namespace-based, falling back to the namespace itself).
     * Runs server-side only: clients do not have access to biome sources of dimensions.
     */
    fun all(server: MinecraftServer): List<BiomeInfo> {
        val registry = server.registryAccess().registryOrThrow(Registries.BIOME)

        val tagsByBiome = mutableMapOf<ResourceKey<Biome>, MutableList<String>>()
        for (tagEntry in registry.getTags().toList()) {
            val label = "#${tagEntry.first.location()}"
            for (holder in tagEntry.second) {
                holder.unwrapKey().orElse(null)?.let { key ->
                    tagsByBiome.getOrPut(key) { mutableListOf() }.add(label)
                }
            }
        }

        val dimensionsByBiome = mutableMapOf<ResourceKey<Biome>, MutableList<String>>()
        for (level in server.allLevels) {
            val possible = try {
                level.chunkSource.generator.biomeSource.possibleBiomes()
            } catch (_: Exception) {
                emptySet()
            }
            val dimension = level.dimension().location().toString()
            for (holder in possible) {
                holder.unwrapKey().orElse(null)?.let { key ->
                    dimensionsByBiome.getOrPut(key) { mutableListOf() }.add(dimension)
                }
            }
        }

        return registry.registryKeySet().map { key ->
            BiomeInfo(
                id = key.location().toString(),
                tags = tagsByBiome[key].orEmpty().sorted(),
                dimensions = dimensionsByBiome[key].orEmpty().distinct().sorted(),
                sourceMod = sourceModName(key.location().namespace)
            )
        }.sortedBy { it.id }
    }

    private fun sourceModName(namespace: String): String {
        if (namespace == "minecraft") return "Minecraft"
        return getModName(namespace) ?: namespace
    }
}
