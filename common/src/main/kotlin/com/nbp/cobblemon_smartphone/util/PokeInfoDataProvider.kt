package com.nbp.cobblemon_smartphone.util

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.pokemon.requirements.FriendshipRequirement
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.pokemon.requirements.TimeRangeRequirement
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace

object PokeInfoDataProvider {

    data class SpeciesInfo(
        val name: String,
        val dexNumber: Int,
        val primaryType: String,
        val secondaryType: String?
    )

    data class StatsInfo(
        val hp: Int, val attack: Int, val defence: Int,
        val specialAttack: Int, val specialDefence: Int, val speed: Int
    ) {
        val total get() = hp + attack + defence + specialAttack + specialDefence + speed
    }

    data class AbilityInfo(val name: String, val isHidden: Boolean, val description: String)

    data class EvoInfo(val targetName: String, val method: String)

    data class MoveInfo(val level: Int, val name: String, val method: String, val type: String, val category: String, val power: Int, val accuracy: Int)

    data class FormInfo(val name: String, val displayName: String)

    data class SpeciesDetail(
        val name: String,
        val dexNumber: Int,
        val primaryType: String,
        val secondaryType: String?,
        val height: Float,
        val weight: Float,
        val baseStats: StatsInfo,
        val evYield: StatsInfo,
        val abilities: List<AbilityInfo>,
        val preEvolution: String?,
        val evolutions: List<EvoInfo>,
        val moves: List<MoveInfo>,
        val catchRate: Int,
        val baseExp: Int,
        val growthRate: String,
        val baseFriendship: Int,
        val eggGroups: List<String>,
        val eggCycles: Int,
        val maleRatio: Float,
        val spawnBiomes: List<String>,
        val availableForms: List<FormInfo>,
        val selectedForm: String
    )

    private var speciesCache: List<SpeciesInfo>? = null

    fun all(): List<SpeciesInfo> {
        if (speciesCache != null) return speciesCache!!

        speciesCache = PokemonSpecies.implemented.map { species ->
            SpeciesInfo(
                name = species.name,
                dexNumber = species.nationalPokedexNumber,
                primaryType = species.primaryType.name,
                secondaryType = species.secondaryType?.name
            )
        }.sortedBy { it.dexNumber }

        return speciesCache!!
    }

    fun search(query: String): List<SpeciesInfo> {
        if (query.isBlank()) return all()
        val q = query.lowercase()
        return all().filter {
            it.name.lowercase().contains(q) ||
            it.dexNumber.toString().startsWith(q)
        }
    }

    fun getDetail(dexNumber: Int, formName: String? = null): SpeciesDetail? {
        val species = PokemonSpecies.getByPokedexNumber(dexNumber) ?: return null
        return buildDetail(species, formName)
    }

    fun clearCache() {
        speciesCache = null
    }

    private fun buildDetail(species: Species, formName: String? = null): SpeciesDetail {
        val form = if (formName != null) species.forms.firstOrNull { it.name == formName } else null
        val f = form ?: species.standardForm

        val abilities = f.abilities.toList().map { ability ->
            AbilityInfo(
                name = ability.template.name,
                isHidden = ability is HiddenAbility,
                description = Component.translatable(ability.template.description).string
            )
        }

        val evolutions = collectEvolutionChain(species)

        val moves = mutableListOf<MoveInfo>()
        val learnset = f.moves
        for ((level, moveList) in learnset.levelUpMoves) {
            for (template in moveList) {
                moves.add(MoveInfo(
                    level = level, name = template.name, method = "level",
                    type = template.elementalType.name, category = template.damageCategory.name,
                    power = template.power.toInt(), accuracy = template.accuracy.toInt()
                ))
            }
        }
        for (template in learnset.tmMoves) {
            moves.add(MoveInfo(
                level = 0, name = template.name, method = "tm",
                type = template.elementalType.name, category = template.damageCategory.name,
                power = template.power.toInt(), accuracy = template.accuracy.toInt()
            ))
        }
        for (template in learnset.eggMoves) {
            moves.add(MoveInfo(
                level = 0, name = template.name, method = "egg",
                type = template.elementalType.name, category = template.damageCategory.name,
                power = template.power.toInt(), accuracy = template.accuracy.toInt()
            ))
        }
        for (template in learnset.tutorMoves) {
            moves.add(MoveInfo(
                level = 0, name = template.name, method = "tutor",
                type = template.elementalType.name, category = template.damageCategory.name,
                power = template.power.toInt(), accuracy = template.accuracy.toInt()
            ))
        }

        val preEvo = f.preEvolution?.species?.name

        val allForms = species.forms.map {
            FormInfo(name = it.name, displayName = it.formOnlyShowdownId().replaceFirstChar { c -> c.uppercase() })
        }

        return SpeciesDetail(
            name = species.name,
            dexNumber = species.nationalPokedexNumber,
            primaryType = f.primaryType.name,
            secondaryType = f.secondaryType?.name,
            height = f.height,
            weight = f.weight,
            baseStats = StatsInfo(
                hp = f.baseStats[Stats.HP] ?: 0,
                attack = f.baseStats[Stats.ATTACK] ?: 0,
                defence = f.baseStats[Stats.DEFENCE] ?: 0,
                specialAttack = f.baseStats[Stats.SPECIAL_ATTACK] ?: 0,
                specialDefence = f.baseStats[Stats.SPECIAL_DEFENCE] ?: 0,
                speed = f.baseStats[Stats.SPEED] ?: 0
            ),
            evYield = StatsInfo(
                hp = f.evYield[Stats.HP] ?: 0,
                attack = f.evYield[Stats.ATTACK] ?: 0,
                defence = f.evYield[Stats.DEFENCE] ?: 0,
                specialAttack = f.evYield[Stats.SPECIAL_ATTACK] ?: 0,
                specialDefence = f.evYield[Stats.SPECIAL_DEFENCE] ?: 0,
                speed = f.evYield[Stats.SPEED] ?: 0
            ),
            abilities = abilities,
            preEvolution = preEvo,
            evolutions = evolutions,
            moves = moves,
            catchRate = f.catchRate,
            baseExp = f.baseExperienceYield,
            growthRate = f.experienceGroup.name,
            baseFriendship = f.baseFriendship,
            eggGroups = f.eggGroups.map { it.showdownID },
            eggCycles = species.eggCycles,
            maleRatio = f.maleRatio,
            spawnBiomes = run {
                try {
                    CobblemonSpawnPools.WORLD_SPAWN_POOL.details
                        .filterIsInstance<PokemonSpawnDetail>()
                        .filter { it.pokemon.species.equals(species.name, ignoreCase = true) }
                        .flatMap { it.validBiomes }
                        .map { it.path }
                        .distinct()
                } catch (_: Exception) {
                    emptyList()
                }
            },
            availableForms = allForms,
            selectedForm = f.name
        )
    }

    private fun buildEvoMethod(evo: Evolution): String {
        val baseMethod = when (evo) {
            is LevelUpEvolution -> {
                val level = evo.requirements.filterIsInstance<LevelRequirement>().firstOrNull()?.minLevel
                if (level != null && level > 1) "Lv $level" else "Level Up"
            }
            is TradeEvolution -> "Trade"
            is ItemInteractionEvolution -> extractItemName(evo)
            else -> "Special"
        }

        val conditions = buildExtraConditions(evo)
        return if (conditions.isNotEmpty()) "$baseMethod + $conditions" else baseMethod
    }

    private fun buildExtraConditions(evo: Evolution): String {
        val conditions = mutableListOf<String>()

        for (req in evo.requirements) {
            when (req) {
                is FriendshipRequirement -> {
                    conditions.add("Friendship")
                }
                is TimeRangeRequirement -> {
                    val firstRange = req.range.ranges.firstOrNull()
                    if (firstRange != null) {
                        val mid = (firstRange.first + firstRange.last) / 2
                        val label = when (mid) {
                            in 4000..10000 -> "Day"
                            in 16000..22000 -> "Night"
                            in 2000..5000 -> "Dawn"
                            in 11000..15000 -> "Dusk"
                            else -> null
                        }
                        if (label != null) conditions.add(label)
                    }
                }
            }
        }

        return conditions.joinToString(" + ")
    }

    private fun extractItemName(evo: ItemInteractionEvolution): String {
        return try {
            val predicate = evo.requiredContext
            val itemsField = predicate::class.java.getDeclaredField("items")
            itemsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val itemsOpt = itemsField.get(predicate) as java.util.Optional<*>
            val holderSet = itemsOpt.orElse(null) ?: return "Item"
            val streamMethod = holderSet::class.java.getMethod("stream")
            @Suppress("UNCHECKED_CAST")
            val stream = streamMethod.invoke(holderSet) as java.util.stream.Stream<*>
            val holder = stream.findFirst().orElse(null) ?: return "Item"
            val valueMethod = holder::class.java.getMethod("value")
            val item = valueMethod.invoke(holder) as net.minecraft.world.item.Item
            item.descriptionId.removePrefix("item.").split(".").last()
                .replace("_", " ").replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            "Item"
        }
    }

    private fun collectEvolutionChain(species: Species, visited: MutableSet<Int> = mutableSetOf()): List<EvoInfo> {
        if (species.nationalPokedexNumber in visited) return emptyList()
        visited.add(species.nationalPokedexNumber)

        val result = mutableListOf<EvoInfo>()
        for (evo in species.evolutions) {
            val targetName = evo.result.species?.let { speciesId ->
                try {
                    PokemonSpecies.getByIdentifier(speciesId.asIdentifierDefaultingNamespace())?.name
                } catch (_: Exception) {
                    speciesId.split(":").last()
                }
            } ?: continue

            result.add(EvoInfo(targetName = targetName, method = buildEvoMethod(evo)))

            val targetDex = try {
                PokemonSpecies.getByIdentifier(evo.result.species!!.asIdentifierDefaultingNamespace())?.nationalPokedexNumber
            } catch (_: Exception) { null }
            if (targetDex != null) {
                val targetSpecies = PokemonSpecies.getByPokedexNumber(targetDex)
                if (targetSpecies != null) {
                    result.addAll(collectEvolutionChain(targetSpecies, visited))
                }
            }
        }
        return result
    }
}
