package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.getModName
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.structure.Structure

object StructureDataProvider {

    data class StructureInfo(
        val id: String,
        val dimensions: List<String>,
        val sourceMod: String
    )

    /**
     * Gathers every registered structure with the dimensions whose generator can place it (read off
     * each dimension's structure sets) and the mod that added it (namespace-based, falling back to
     * the namespace itself). Runs server-side only: clients do not have access to a dimension's
     * generator/structure sets.
     */
    fun all(server: MinecraftServer): List<StructureInfo> {
        val registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE)

        val dimensionsByStructure = mutableMapOf<ResourceKey<Structure>, MutableList<String>>()
        for (level in server.allLevels) {
            val possibleSets = try {
                level.chunkSource.generatorState.possibleStructureSets()
            } catch (_: Exception) {
                emptyList()
            }
            val dimension = level.dimension().location().toString()
            for (structureSet in possibleSets) {
                for (entry in structureSet.value().structures()) {
                    entry.structure().unwrapKey().orElse(null)?.let { key ->
                        dimensionsByStructure.getOrPut(key) { mutableListOf() }.add(dimension)
                    }
                }
            }
        }

        return registry.registryKeySet().map { key ->
            StructureInfo(
                id = key.location().toString(),
                dimensions = dimensionsByStructure[key].orEmpty().distinct().sorted(),
                sourceMod = sourceModName(key.location().namespace)
            )
        }.sortedBy { it.id }
    }

    private fun sourceModName(namespace: String): String {
        if (namespace == "minecraft") return "Minecraft"
        return getModName(namespace) ?: namespace
    }
}
