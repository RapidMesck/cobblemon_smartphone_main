package com.nbp.cobblemon_smartphone.social

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
    val nickname: String?
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putString(SPECIES_KEY, species)
        tag.putInt(LEVEL_KEY, level)
        nickname?.let { tag.putString(NICKNAME_KEY, it) }
        val aspectList = ListTag()
        aspects.forEach { aspectList.add(StringTag.valueOf(it)) }
        tag.put(ASPECTS_KEY, aspectList)
        return tag
    }

    companion object {
        private const val SPECIES_KEY = "species"
        private const val ASPECTS_KEY = "aspects"
        private const val LEVEL_KEY = "level"
        private const val NICKNAME_KEY = "nickname"

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
                nickname = if (tag.contains(NICKNAME_KEY)) tag.getString(NICKNAME_KEY) else null
            )
        }
    }
}
