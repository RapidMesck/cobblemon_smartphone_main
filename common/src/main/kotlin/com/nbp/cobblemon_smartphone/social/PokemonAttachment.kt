package com.nbp.cobblemon_smartphone.social

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

/**
 * Immutable snapshot of a Pokémon at the moment it was attached to a post.
 *
 * Deliberately a snapshot rather than a reference: if the author later releases, trades or
 * evolves the Pokémon, the post must keep showing what was actually posted.
 *
 * [aspects] is Cobblemon's native encoding for shiny/form/gender variations, so it is stored
 * verbatim and fed straight into `RenderablePokemon(species, aspects)` when rendering.
 */
data class PokemonAttachment(
    val species: String,
    val aspects: Set<String>,
    val level: Int,
    val nickname: String?,
    val ivs: List<Int> = emptyList(),
    val evs: List<Int> = emptyList(),
    val gender: String? = null,
    val ability: String? = null,
    val nature: String? = null,
    val types: List<String> = emptyList()
) {
    fun encode(buffer: net.minecraft.network.RegistryFriendlyByteBuf) {
        buffer.writeUtf(species)
        buffer.writeVarInt(aspects.size)
        aspects.forEach(buffer::writeUtf)
        buffer.writeVarInt(level)
        buffer.writeBoolean(nickname != null)
        nickname?.let(buffer::writeUtf)
        buffer.writeVarInt(ivs.size)
        ivs.forEach(buffer::writeVarInt)
        buffer.writeVarInt(evs.size)
        evs.forEach(buffer::writeVarInt)
        buffer.writeBoolean(gender != null)
        gender?.let(buffer::writeUtf)
        buffer.writeBoolean(ability != null)
        ability?.let(buffer::writeUtf)
        buffer.writeBoolean(nature != null)
        nature?.let(buffer::writeUtf)
        buffer.writeVarInt(types.size)
        types.forEach(buffer::writeUtf)
    }
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putString(SPECIES_KEY, species)
        tag.putInt(LEVEL_KEY, level)
        nickname?.let { tag.putString(NICKNAME_KEY, it) }
        tag.putIntArray(IVS_KEY, ivs)
        tag.putIntArray(EVS_KEY, evs)
        gender?.let { tag.putString(GENDER_KEY, it) }
        ability?.let { tag.putString(ABILITY_KEY, it) }
        nature?.let { tag.putString(NATURE_KEY, it) }
        val typeList = ListTag()
        types.forEach { typeList.add(StringTag.valueOf(it)) }
        tag.put(TYPES_KEY, typeList)
        val aspectList = ListTag()
        aspects.forEach { aspectList.add(StringTag.valueOf(it)) }
        tag.put(ASPECTS_KEY, aspectList)
        return tag
    }

    companion object {
        private val ORDERED_STATS = listOf(
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
        )

        fun fromPokemon(
            pokemon: Pokemon,
            showDetails: Boolean = true,
            showIvs: Boolean = true,
            showEvs: Boolean = true,
            showAbility: Boolean = true,
            showNature: Boolean = true
        ): PokemonAttachment = PokemonAttachment(
            species = pokemon.species.resourceIdentifier.toString(),
            aspects = pokemon.aspects,
            level = pokemon.level,
            nickname = pokemon.nickname?.string,
            ivs = if (showDetails && showIvs) ORDERED_STATS.map { pokemon.ivs[it] ?: 0 } else emptyList(),
            evs = if (showDetails && showEvs) ORDERED_STATS.map { pokemon.evs[it] ?: 0 } else emptyList(),
            gender = pokemon.gender.name,
            ability = if (showDetails && showAbility) pokemon.ability.displayName else null,
            nature = if (showDetails && showNature) pokemon.effectiveNature.displayName else null,
            types = if (showDetails) listOfNotNull(pokemon.form.primaryType.name, pokemon.form.secondaryType?.name) else emptyList()
        )

        fun decode(buffer: net.minecraft.network.RegistryFriendlyByteBuf): PokemonAttachment {
            val species = buffer.readUtf(128)
            val aspectCount = buffer.readVarInt().coerceIn(0, 64)
            val aspects = List(aspectCount) { buffer.readUtf(64) }.toSet()
            val level = buffer.readVarInt()
            val nickname = if (buffer.readBoolean()) buffer.readUtf(64) else null
            val ivCount = buffer.readVarInt().coerceIn(0, 6)
            val ivs = List(ivCount) { buffer.readVarInt() }
            val evCount = buffer.readVarInt().coerceIn(0, 6)
            val evs = List(evCount) { buffer.readVarInt() }
            val gender = if (buffer.readBoolean()) buffer.readUtf(32) else null
            val ability = if (buffer.readBoolean()) buffer.readUtf(128) else null
            val nature = if (buffer.readBoolean()) buffer.readUtf(128) else null
            val typeCount = buffer.readVarInt().coerceIn(0, 8)
            val types = List(typeCount) { buffer.readUtf(32) }
            return PokemonAttachment(species, aspects, level, nickname, ivs, evs, gender, ability, nature, types)
        }
        private const val SPECIES_KEY = "species"
        private const val ASPECTS_KEY = "aspects"
        private const val LEVEL_KEY = "level"
        private const val NICKNAME_KEY = "nickname"
        private const val IVS_KEY = "ivs"
        private const val EVS_KEY = "evs"
        private const val GENDER_KEY = "gender"
        private const val ABILITY_KEY = "ability"
        private const val NATURE_KEY = "nature"
        private const val TYPES_KEY = "types"

        fun fromNbt(tag: CompoundTag): PokemonAttachment? {
            val species = tag.getString(SPECIES_KEY)
            if (species.isBlank()) return null
            val aspects = tag.getList(ASPECTS_KEY, Tag.TAG_STRING.toInt())
                .map { it.asString }
                .toSet()
            return PokemonAttachment(
                species = species,
                aspects = aspects,
                level = tag.getInt(LEVEL_KEY),
                nickname = if (tag.contains(NICKNAME_KEY)) tag.getString(NICKNAME_KEY) else null,
                ivs = if (tag.contains(IVS_KEY, Tag.TAG_INT_ARRAY.toInt())) tag.getIntArray(IVS_KEY).toList() else emptyList(),
                evs = if (tag.contains(EVS_KEY, Tag.TAG_INT_ARRAY.toInt())) tag.getIntArray(EVS_KEY).toList() else emptyList(),
                gender = if (tag.contains(GENDER_KEY)) tag.getString(GENDER_KEY) else null,
                ability = if (tag.contains(ABILITY_KEY)) tag.getString(ABILITY_KEY) else null,
                nature = if (tag.contains(NATURE_KEY)) tag.getString(NATURE_KEY) else null,
                types = tag.getList(TYPES_KEY, Tag.TAG_STRING.toInt()).map { it.asString }
            )
        }
    }
}
