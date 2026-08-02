package com.nbp.cobblemon_smartphone.util

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.pokemon.requirements.*
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

    data class AbilityInfo(val name: String, val isHidden: Boolean, val descriptionKey: String)

    data class LocalizedText(val key: String, val args: List<String> = emptyList())

    data class EvoInfo(val targetName: String, val methods: List<LocalizedText>)

    data class MoveInfo(
        val level: Int,
        val name: String,
        val method: String,
        val type: String,
        val category: String,
        val power: Int,
        val accuracy: Int
    )

    data class FormInfo(val name: String, val displayName: String)

    data class SpawnEntryData(
        val bucket: String,
        val weight: Float,
        val biomes: List<String>,
        val minLevel: Int,
        val maxLevel: Int,
        val context: String,
        val time: LocalizedText?,
        val conditions: List<LocalizedText>,
        val antiConditions: List<LocalizedText>
    )

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
        val spawnEntries: List<SpawnEntryData>,
        val spawnBiomes: List<String>,
        val availableForms: List<FormInfo>,
        val selectedForm: String
    )

    fun all(): List<SpeciesInfo> {
        return PokemonSpecies.implemented.map { species ->
            SpeciesInfo(
                name = species.name,
                dexNumber = species.nationalPokedexNumber,
                primaryType = species.primaryType.name,
                secondaryType = species.secondaryType?.name
            )
        }.sortedBy { it.dexNumber }

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
        if (formName != null && species.forms.none {
                it.name.equals(formName, ignoreCase = true) ||
                        it.formOnlyShowdownId().equals(formName, ignoreCase = true)
            }) return null
        return buildDetail(species, formName)
    }

    /** Builds every server-defined form in one response so form navigation needs no extra packets. */
    fun getAllDetails(dexNumber: Int): List<SpeciesDetail> {
        val species = PokemonSpecies.getByPokedexNumber(dexNumber) ?: return emptyList()
        return (listOf(species.standardForm) + species.forms)
            .distinctBy { it.name.lowercase() }
            .map { buildDetail(species, it.name) }
    }

    private fun buildDetail(species: Species, formName: String? = null): SpeciesDetail {
        val form = if (formName != null) species.forms.firstOrNull {
            it.name.equals(formName, ignoreCase = true) ||
                    it.formOnlyShowdownId().equals(formName, ignoreCase = true)
        } else null
        val f = form ?: species.standardForm

        val abilities = f.abilities.toList().map { ability ->
            AbilityInfo(
                name = ability.template.name,
                isHidden = ability is HiddenAbility,
                descriptionKey = ability.template.description
            )
        }

        val evolutions = collectEvolutionChain(f)

        val moves = mutableListOf<MoveInfo>()
        val learnset = f.moves
        for ((level, moveList) in learnset.levelUpMoves) {
            for (template in moveList) {
                moves.add(
                    MoveInfo(
                        level = level, name = template.name, method = "level",
                        type = template.elementalType.name, category = template.damageCategory.name,
                        power = template.power.toInt(), accuracy = template.accuracy.toInt()
                    )
                )
            }
        }
        for (template in learnset.tmMoves) {
            moves.add(
                MoveInfo(
                    level = 0, name = template.name, method = "tm",
                    type = template.elementalType.name, category = template.damageCategory.name,
                    power = template.power.toInt(), accuracy = template.accuracy.toInt()
                )
            )
        }
        for (template in learnset.eggMoves) {
            moves.add(
                MoveInfo(
                    level = 0, name = template.name, method = "egg",
                    type = template.elementalType.name, category = template.damageCategory.name,
                    power = template.power.toInt(), accuracy = template.accuracy.toInt()
                )
            )
        }
        for (template in learnset.tutorMoves) {
            moves.add(
                MoveInfo(
                    level = 0, name = template.name, method = "tutor",
                    type = template.elementalType.name, category = template.damageCategory.name,
                    power = template.power.toInt(), accuracy = template.accuracy.toInt()
                )
            )
        }

        val preEvo = f.preEvolution?.species?.name

        val allForms = (listOf(species.standardForm) + species.forms).distinctBy { it.name }.map {
            FormInfo(
                name = it.name,
                displayName = "cobblemon.ui.pokedex.info.form.${it.formOnlyShowdownId()}"
            )
        }

        val matchingSpawnDetails = run {
            try {
                CobblemonSpawnPools.WORLD_SPAWN_POOL.details
                    .filterIsInstance<PokemonSpawnDetail>()
                    .filter { it.matchesSpecies(species) }
                    .filter { it.matchesSelectedForm(species, f) }
            } catch (_: Exception) {
                emptyList()
            }
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
            spawnEntries = matchingSpawnDetails.map { it.toSpawnEntryData() },
            spawnBiomes = matchingSpawnDetails.flatMap { it.validBiomes }.map { it.toString() }.distinct(),
            availableForms = allForms,
            selectedForm = f.name
        )
    }

    private fun buildEvoMethod(evo: Evolution): List<LocalizedText> {
        val baseMethod = when (evo) {
            is LevelUpEvolution -> {
                val level = evo.requirements.filterIsInstance<LevelRequirement>().firstOrNull()?.minLevel
                if (level != null && level > 1) {
                    localized("evolution.level", level)
                } else {
                    localized("evolution.level_up")
                }
            }

            is TradeEvolution -> localized("evolution.trade")
            is ItemInteractionEvolution -> extractItemName(evo)
            else -> localized("evolution.special")
        }

        return listOf(baseMethod) + buildExtraConditions(evo)
    }

    private fun buildExtraConditions(evo: Evolution): List<LocalizedText> {
        val conditions = mutableListOf<LocalizedText>()

        for (req in evo.requirements) {
            when (req) {
                is LevelRequirement -> Unit // already represented by the base method
                is FriendshipRequirement -> {
                    conditions.add(
                        if (req.amount > 0) localized("evolution.requirement.friendship", req.amount)
                        else localized("evolution.friendship")
                    )
                }

                is TimeRangeRequirement -> {
                    conditions.add(timeRangeToName(req.range))
                }

                is HeldItemRequirement -> conditions.add(
                    extractItemPredicateName(req.itemCondition)
                        ?: localized("evolution.requirement.held_item")
                )
                is OwnerHoldsItemRequirement -> conditions.add(
                    extractItemPredicateName(req.itemCondition)
                        ?: localized("evolution.requirement.owner_item")
                )
                is MoveSetRequirement -> conditions.add(localized("evolution.requirement.move", moveArg(req.move.name)))
                is MoveTypeRequirement -> conditions.add(localized("evolution.requirement.move_type", typeArg(req.type.name)))
                is WeatherRequirement -> {
                    if (req.isThundering == true) conditions.add(localized("evolution.requirement.thunder"))
                    else if (req.isRaining == true) conditions.add(localized("evolution.requirement.rain"))
                    else if (req.isRaining == false) conditions.add(localized("evolution.requirement.no_rain"))
                }
                is BiomeRequirement -> {
                    req.biomeCondition?.let { conditions.add(localized("evolution.requirement.biome", registryLikeToString(it))) }
                    req.biomeAnticondition?.let { conditions.add(localized("evolution.requirement.not_biome", registryLikeToString(it))) }
                }
                is PartyMemberRequirement -> conditions.add(describePartyRequirement(req))
                is PokemonPropertiesRequirement -> conditions.add(describePropertiesRequirement(req))
                is AttackDefenceRatioRequirement -> conditions.add(
                    localized("evolution.requirement.stat_ratio.${req.ratio.name.lowercase()}")
                )
                is MoonPhaseRequirement -> conditions.add(localized("evolution.requirement.moon", req.moonPhase.name.lowercase()))
                is WorldRequirement -> conditions.add(localized("evolution.requirement.world", req.identifier))
                is UseMoveRequirement -> conditions.add(
                    localized("evolution.requirement.use_move", moveArg(req.move.name), req.amount)
                )
                is DefeatRequirement -> conditions.add(describeDefeatRequirement(req))
                is BlocksTraveledRequirement -> conditions.add(localized("evolution.requirement.blocks", req.amount))
                is DamageTakenRequirement -> conditions.add(localized("evolution.requirement.damage", req.amount))
                is RecoilRequirement -> conditions.add(localized("evolution.requirement.recoil", req.amount))
                is BattleCriticalHitsRequirement -> conditions.add(localized("evolution.requirement.critical_hits", req.amount))
                is PropertyRangeRequirement -> conditions.add(
                    localized("evolution.requirement.property_range", req.feature, req.range.first, req.range.last)
                )
                is StatCompareRequirement -> conditions.add(localized("evolution.requirement.stat_compare", req.highStat, req.lowStat))
                is StatEqualRequirement -> conditions.add(localized("evolution.requirement.stat_equal", req.statOne, req.statTwo))
                is AnyRequirement -> conditions.add(localized("evolution.requirement.any"))
                else -> conditions.add(localized("evolution.special"))
            }
        }

        return conditions
    }

    private fun describeDefeatRequirement(req: DefeatRequirement): LocalizedText {
        return defeatRequirementText(
            req.amount,
            req.target.species,
            req.target.heldItem,
            req.target.type,
            humanizePropertyString(req.target.asString())
        )
    }

    internal fun defeatRequirementText(
        amount: Int,
        species: String?,
        heldItem: String?,
        type: String?,
        fallback: String
    ): LocalizedText {
        return when {
            species != null && heldItem != null -> localized(
                "evolution.requirement.defeat_holding",
                amount,
                pokemonArg(species),
                itemArg(heldItem)
            )
            species != null -> localized("evolution.requirement.defeat", amount, pokemonArg(species))
            type != null -> localized("evolution.requirement.defeat_type", amount, typeArg(type))
            else -> localized("evolution.requirement.defeat_target", amount, fallback)
        }
    }

    private fun describePartyRequirement(req: PartyMemberRequirement): LocalizedText {
        val prefix = if (req.contains) "evolution.requirement.party" else "evolution.requirement.not_party"
        return when {
            req.target.species != null -> localized("$prefix.species", pokemonArg(req.target.species!!))
            req.target.type != null -> localized("$prefix.type", typeArg(req.target.type!!))
            else -> localized(prefix, humanizePropertyString(req.target.asString()))
        }
    }

    private fun describePropertiesRequirement(req: PokemonPropertiesRequirement): LocalizedText {
        val species = req.target.species
        val heldItem = req.target.heldItem
        return when {
            species != null && heldItem != null -> localized(
                "evolution.requirement.properties_holding", pokemonArg(species), itemArg(heldItem)
            )
            species != null -> localized("evolution.requirement.properties_species", pokemonArg(species))
            req.target.type != null -> localized("evolution.requirement.properties_type", typeArg(req.target.type!!))
            else -> localized("evolution.requirement.properties", humanizePropertyString(req.target.asString()))
        }
    }

    private fun humanizePropertyString(value: String): String = value
        .replace('_', ' ')
        .replace('=', ':')

    private fun pokemonArg(value: String) = "pokemon:$value"
    private fun itemArg(value: String) = "item:$value"
    private fun moveArg(value: String) = "move:$value"
    private fun typeArg(value: String) = "type:$value"

    private fun extractItemPredicateName(predicate: Any): LocalizedText? {
        return try {
            val itemsField = predicate::class.java.getDeclaredField("items")
            itemsField.isAccessible = true
            val itemsOpt = itemsField.get(predicate) as java.util.Optional<*>
            val holderSet = itemsOpt.orElse(null) ?: return null
            val stream = holderSet::class.java.getMethod("stream").invoke(holderSet) as java.util.stream.Stream<*>
            val holder = stream.findFirst().orElse(null) ?: return null
            val item = holder::class.java.getMethod("value").invoke(holder) as net.minecraft.world.item.Item
            LocalizedText(item.descriptionId)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractItemName(evo: ItemInteractionEvolution): LocalizedText {
        return try {
            val predicate = evo.requiredContext
            val itemsField = predicate::class.java.getDeclaredField("items")
            itemsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val itemsOpt = itemsField.get(predicate) as java.util.Optional<*>
            val holderSet = itemsOpt.orElse(null) ?: return localized("evolution.item")
            val streamMethod = holderSet::class.java.getMethod("stream")

            @Suppress("UNCHECKED_CAST")
            val stream = streamMethod.invoke(holderSet) as java.util.stream.Stream<*>
            val holder = stream.findFirst().orElse(null) ?: return localized("evolution.item")
            val valueMethod = holder::class.java.getMethod("value")
            val item = valueMethod.invoke(holder) as net.minecraft.world.item.Item
            LocalizedText(item.descriptionId)
        } catch (_: Exception) {
            localized("evolution.item")
        }
    }

    private fun collectEvolutionChain(form: FormData, visited: MutableSet<String> = mutableSetOf()): List<EvoInfo> {
        val species = form.species
        val visitKey = "${species.resourceIdentifier}:${form.name.lowercase()}"
        if (!visited.add(visitKey)) return emptyList()

        val result = mutableListOf<EvoInfo>()
        for (evo in form.evolutions) {
            val targetName = evo.result.species?.let { speciesId ->
                try {
                    PokemonSpecies.getByIdentifier(speciesId.asIdentifierDefaultingNamespace())?.name
                } catch (_: Exception) {
                    speciesId.split(":").last()
                }
            } ?: continue

            result.add(EvoInfo(targetName = targetName, methods = buildEvoMethod(evo)))

            val targetSpecies = try {
                PokemonSpecies.getByIdentifier(evo.result.species!!.asIdentifierDefaultingNamespace())
            } catch (_: Exception) {
                null
            }
            if (targetSpecies != null) {
                val targetForm = evo.result.form?.let(targetSpecies::getFormByShowdownId)
                    ?: targetSpecies.getForm(evo.result.aspects)
                result.addAll(collectEvolutionChain(targetForm, visited))
            }
        }
        return result
    }

    private fun PokemonSpawnDetail.matchesSelectedForm(species: Species, selectedForm: FormData): Boolean {
        val formAspects = species.forms.flatMap { it.aspects }.toSet()
        return matchesSpawnForm(
            pokemon.form,
            pokemon.aspects,
            formAspects,
            selectedForm.name,
            selectedForm.formOnlyShowdownId(),
            selectedForm.aspects.toSet()
        )
    }

    private fun PokemonSpawnDetail.matchesSpecies(species: Species): Boolean {
        val configured = pokemon.species ?: return false
        return configured.equals(species.name, ignoreCase = true) ||
                configured.equals(species.resourceIdentifier.toString(), ignoreCase = true) ||
                configured.substringAfter(':').equals(species.resourceIdentifier.path, ignoreCase = true)
    }

    internal fun matchesSpawnForm(
        configuredForm: String?,
        configuredAspects: Set<String>,
        knownFormAspects: Set<String>,
        selectedName: String,
        selectedShowdownId: String,
        selectedAspects: Set<String>
    ): Boolean {
        if (configuredForm != null) {
            return configuredForm.equals(selectedName, ignoreCase = true) ||
                    configuredForm.equals(selectedShowdownId, ignoreCase = true)
        }
        val relevantAspects = configuredAspects.intersect(knownFormAspects)
        return relevantAspects.isEmpty() || relevantAspects.all { it in selectedAspects }
    }

// ─── Spawn entry helpers ───────────────────────────────────────────────

    internal fun PokemonSpawnDetail.toSpawnEntryData(): SpawnEntryData {
        val levelRange = pokemon.deriveLevelRange(levelRange)
        val time = conditions.flatMap { conditionTimeStrings(it) }.firstOrNull()

        return SpawnEntryData(
            bucket = bucket.name,
            weight = weight,
            biomes = validBiomes.map { it.toString() },
            minLevel = levelRange.first,
            maxLevel = levelRange.last,
            context = spawnablePositionType.name,
            time = time,
            conditions = conditions.flatMap { conditionStrings(it, isAnti = false) },
            antiConditions = anticonditions.flatMap { conditionStrings(it, isAnti = true) }
        )
    }

    private fun conditionStrings(cond: SpawningCondition<*>, isAnti: Boolean): List<LocalizedText> {
        val list = mutableListOf<LocalizedText>()

        if (cond.canSeeSky == true) list.add(localized("condition.${if (isAnti) "no_sky" else "clear_sky"}"))
        if (cond.isRaining == true) list.add(localized("condition.${if (isAnti) "no_rain" else "rain"}"))
        if (cond.isThundering == true) list.add(localized("condition.${if (isAnti) "no_thunder" else "thunder"}"))
        if (cond.isSlimeChunk == true) list.add(localized("condition.slime_chunk"))

        cond.moonPhase?.let { list.add(localized("condition.moon", it)) }

        if (cond.minLight != null || cond.maxLight != null) {
            val min = cond.minLight?.toString() ?: "0"
            val max = cond.maxLight?.toString() ?: "15"
            list.add(localized("condition.light", min, max))
        }

        cond.dimensions?.forEach { dim -> list.add(localized("condition.value", dim.toString())) }

        cond.structures?.forEach { struct ->
            try {
                struct.left().ifPresent { res -> list.add(localized("condition.value", res.toString())) }
            } catch (_: Exception) {
                try {
                    struct.right().ifPresent { tag -> list.add(localized("condition.value", "#${tag.location()}")) }
                } catch (_: Exception) {
                }
            }
        }

        // Subclass-specific fields (use if-chains so parent + child fields are both extracted)
        if (cond is com.cobblemon.mod.common.api.spawning.condition.AreaTypeSpawningCondition<*>) {
            cond.neededNearbyBlocks?.forEach { block ->
                list.add(localized("condition.value", registryLikeToString(block)))
            }
            if (cond.minHeight != null || cond.maxHeight != null) {
                val minH = cond.minHeight?.toString() ?: "-"
                val maxH = cond.maxHeight?.toString() ?: "-"
                list.add(localized("condition.height", minH, maxH))
            }
        }
        if (cond is com.cobblemon.mod.common.api.spawning.condition.GroundedTypeSpawningCondition<*>) {
            cond.neededBaseBlocks?.forEach { block ->
                list.add(localized("condition.base", registryLikeToString(block)))
            }
        }
        if (cond is com.cobblemon.mod.common.api.spawning.condition.SubmergedTypeSpawningCondition<*>) {
            cond.fluid?.let { list.add(localized("condition.fluid", registryLikeToString(it))) }
            cond.fluidIsSource?.let { if (it) list.add(localized("condition.source_fluid")) }
            if (cond.minDepth != null || cond.maxDepth != null) {
                val minD = cond.minDepth?.toString() ?: "-"
                val maxD = cond.maxDepth?.toString() ?: "-"
                list.add(localized("condition.depth", minD, maxD))
            }
        }
        if (cond is com.cobblemon.mod.common.api.spawning.condition.SurfaceTypeSpawningCondition<*>) {
            cond.fluid?.let { list.add(localized("condition.fluid", registryLikeToString(it))) }
        }
        if (cond is com.cobblemon.mod.common.api.spawning.condition.FishingSpawningCondition) {
            cond.rod?.let { list.add(localized("condition.rod", registryLikeToString(it))) }
            cond.neededNearbyBlocks?.forEach { block ->
                list.add(localized("condition.value", registryLikeToString(block)))
            }
            cond.bait?.let { list.add(localized("condition.bait", it.toString())) }
            cond.rodType?.let { list.add(localized("condition.rod_type", it.toString())) }
            if (cond.minLureLevel != null || cond.maxLureLevel != null) {
                val minL = cond.minLureLevel?.toString() ?: "-"
                val maxL = cond.maxLureLevel?.toString() ?: "-"
                list.add(localized("condition.lure", minL, maxL))
            }
        }

        return list
    }

    private fun <T> registryLikeToString(cond: com.cobblemon.mod.common.api.conditional.RegistryLikeCondition<T>): String {
        return when (cond) {
            is com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition<*> -> cond.identifier.path
            is com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition<*> -> "#${cond.tag.location()}"
            else -> cond.toString()
        }
    }

    private fun conditionTimeStrings(cond: SpawningCondition<*>): List<LocalizedText> {
        val timeRange = cond.timeRange ?: return emptyList()
        return listOf(timeRangeToName(timeRange))
    }

    private fun timeRangeToName(timeRange: TimeRange): LocalizedText {
        for ((name, tr) in TimeRange.timeRanges) {
            if (name == "any") continue
            if (tr.ranges == timeRange.ranges) return localized("time.$name")
        }
        return localized("condition.value", timeRange.ranges.joinToString(", ") { "${it.first}-${it.last}" })
    }

    private fun localized(key: String, vararg args: Any): LocalizedText = LocalizedText(
        key = "cobblemon_smartphone.pokeinfo.$key",
        args = args.map(Any::toString)
    )
}
