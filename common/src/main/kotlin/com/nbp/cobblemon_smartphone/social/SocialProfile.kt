package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import java.util.UUID

/**
 * Persistent social profile for a player.
 */
data class SocialProfile(
    val uuid: UUID,
    var name: String,
    var bio: String = "",
    var favoriteSpecies: String? = null,
    var favoriteAspects: Set<String> = emptySet(),
    val followers: MutableSet<UUID> = mutableSetOf(),
    val following: MutableSet<UUID> = mutableSetOf(),
    var status: String = "",
    var bannerType: String = "",
    var pinnedPostId: Long = 0L,
    val teamShowcase: MutableList<PokemonAttachment> = mutableListOf(),
    var showTeam: Boolean = true,
    var acceptInteractions: Boolean = true,
    var dmMutualOnly: Boolean = false,
    var battleWins: Int = 0,
    var battleLosses: Int = 0,
    var tradesCompleted: Int = 0,
    var shiniesCaught: Int = 0,
    var legendariesCaught: Int = 0
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID(UUID_KEY, uuid)
        tag.putString(NAME_KEY, name)
        tag.putString(BIO_KEY, bio)
        favoriteSpecies?.let { tag.putString(FAVORITE_SPECIES_KEY, it) }

        val aspectList = ListTag()
        favoriteAspects.forEach { aspectList.add(StringTag.valueOf(it)) }
        tag.put(FAVORITE_ASPECTS_KEY, aspectList)

        val followerList = ListTag()
        followers.forEach { followerList.add(NbtUtils.createUUID(it)) }
        tag.put(FOLLOWERS_KEY, followerList)

        val followingList = ListTag()
        following.forEach { followingList.add(NbtUtils.createUUID(it)) }
        tag.put(FOLLOWING_KEY, followingList)

        if (status.isNotEmpty()) tag.putString(STATUS_KEY, status)
        if (bannerType.isNotEmpty()) tag.putString(BANNER_TYPE_KEY, bannerType)
        if (pinnedPostId > 0L) tag.putLong(PINNED_POST_KEY, pinnedPostId)

        if (teamShowcase.isNotEmpty()) {
            val teamList = ListTag()
            teamShowcase.take(MAX_TEAM_SIZE).forEach { teamList.add(it.toNbt()) }
            tag.put(TEAM_SHOWCASE_KEY, teamList)
        }

        tag.putBoolean(SHOW_TEAM_KEY, showTeam)
        tag.putBoolean(ACCEPT_INTERACTIONS_KEY, acceptInteractions)
        tag.putBoolean(DM_MUTUAL_ONLY_KEY, dmMutualOnly)

        if (battleWins > 0) tag.putInt(BATTLE_WINS_KEY, battleWins)
        if (battleLosses > 0) tag.putInt(BATTLE_LOSSES_KEY, battleLosses)
        if (tradesCompleted > 0) tag.putInt(TRADES_COMPLETED_KEY, tradesCompleted)
        if (shiniesCaught > 0) tag.putInt(SHINIES_CAUGHT_KEY, shiniesCaught)
        if (legendariesCaught > 0) tag.putInt(LEGENDARIES_CAUGHT_KEY, legendariesCaught)

        return tag
    }

    companion object {
        const val MAX_TEAM_SIZE = 6
        const val MAX_BIO_LENGTH = 150
        const val MAX_STATUS_LENGTH = 40

        val ALLOWED_BANNER_TYPES: Set<String> = setOf(
            "fire", "water", "grass", "electric", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock",
            "ghost", "dragon", "dark", "steel", "fairy", "normal"
        )

        fun sanitizeBannerType(raw: String): String {
            val normalized = raw.trim().lowercase()
            return if (normalized in ALLOWED_BANNER_TYPES) normalized else ""
        }

        private const val UUID_KEY = "uuid"
        private const val NAME_KEY = "name"
        private const val BIO_KEY = "bio"
        private const val FAVORITE_SPECIES_KEY = "favorite_species"
        private const val FAVORITE_ASPECTS_KEY = "favorite_aspects"
        private const val FOLLOWERS_KEY = "followers"
        private const val FOLLOWING_KEY = "following"
        private const val STATUS_KEY = "status"
        private const val BANNER_TYPE_KEY = "banner_type"
        private const val PINNED_POST_KEY = "pinned_post_id"
        private const val TEAM_SHOWCASE_KEY = "team_showcase"
        private const val SHOW_TEAM_KEY = "show_team"
        private const val ACCEPT_INTERACTIONS_KEY = "accept_interactions"
        private const val DM_MUTUAL_ONLY_KEY = "dm_mutual_only"
        private const val BATTLE_WINS_KEY = "battle_wins"
        private const val BATTLE_LOSSES_KEY = "battle_losses"
        private const val TRADES_COMPLETED_KEY = "trades_completed"
        private const val SHINIES_CAUGHT_KEY = "shinies_caught"
        private const val LEGENDARIES_CAUGHT_KEY = "legendaries_caught"

        fun fromNbt(tag: CompoundTag): SocialProfile? {
            if (!tag.hasUUID(UUID_KEY)) return null
            val uuid = tag.getUUID(UUID_KEY)
            val name = tag.getString(NAME_KEY)
            val bio = tag.getString(BIO_KEY)
            val favoriteSpecies = if (tag.contains(FAVORITE_SPECIES_KEY)) tag.getString(FAVORITE_SPECIES_KEY) else null
            val favoriteAspects = if (tag.contains(FAVORITE_ASPECTS_KEY)) {
                tag.getList(FAVORITE_ASPECTS_KEY, Tag.TAG_STRING.toInt()).map { it.asString }.toSet()
            } else {
                emptySet()
            }

            val followers = if (tag.contains(FOLLOWERS_KEY)) {
                tag.getList(FOLLOWERS_KEY, Tag.TAG_INT_ARRAY.toInt())
                    .mapNotNull { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
                    .toMutableSet()
            } else {
                mutableSetOf()
            }

            val following = if (tag.contains(FOLLOWING_KEY)) {
                tag.getList(FOLLOWING_KEY, Tag.TAG_INT_ARRAY.toInt())
                    .mapNotNull { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
                    .toMutableSet()
            } else {
                mutableSetOf()
            }

            val status = if (tag.contains(STATUS_KEY)) tag.getString(STATUS_KEY) else ""
            val bannerType = if (tag.contains(BANNER_TYPE_KEY)) tag.getString(BANNER_TYPE_KEY) else ""
            val pinnedPostId = if (tag.contains(PINNED_POST_KEY)) tag.getLong(PINNED_POST_KEY) else 0L

            val teamShowcase = if (tag.contains(TEAM_SHOWCASE_KEY)) {
                tag.getList(TEAM_SHOWCASE_KEY, Tag.TAG_COMPOUND.toInt())
                    .mapNotNull { PokemonAttachment.fromNbt(it as CompoundTag) }
                    .take(MAX_TEAM_SIZE)
                    .toMutableList()
            } else {
                mutableListOf()
            }

            val showTeam = if (tag.contains(SHOW_TEAM_KEY)) tag.getBoolean(SHOW_TEAM_KEY) else true
            val acceptInteractions = if (tag.contains(ACCEPT_INTERACTIONS_KEY)) tag.getBoolean(ACCEPT_INTERACTIONS_KEY) else true
            val dmMutualOnly = if (tag.contains(DM_MUTUAL_ONLY_KEY)) tag.getBoolean(DM_MUTUAL_ONLY_KEY) else false

            val battleWins = if (tag.contains(BATTLE_WINS_KEY)) tag.getInt(BATTLE_WINS_KEY) else 0
            val battleLosses = if (tag.contains(BATTLE_LOSSES_KEY)) tag.getInt(BATTLE_LOSSES_KEY) else 0
            val tradesCompleted = if (tag.contains(TRADES_COMPLETED_KEY)) tag.getInt(TRADES_COMPLETED_KEY) else 0
            val shiniesCaught = if (tag.contains(SHINIES_CAUGHT_KEY)) tag.getInt(SHINIES_CAUGHT_KEY) else 0
            val legendariesCaught = if (tag.contains(LEGENDARIES_CAUGHT_KEY)) tag.getInt(LEGENDARIES_CAUGHT_KEY) else 0

            return SocialProfile(
                uuid = uuid,
                name = name,
                bio = bio,
                favoriteSpecies = favoriteSpecies,
                favoriteAspects = favoriteAspects,
                followers = followers,
                following = following,
                status = status,
                bannerType = bannerType,
                pinnedPostId = pinnedPostId,
                teamShowcase = teamShowcase,
                showTeam = showTeam,
                acceptInteractions = acceptInteractions,
                dmMutualOnly = dmMutualOnly,
                battleWins = battleWins,
                battleLosses = battleLosses,
                tradesCompleted = tradesCompleted,
                shiniesCaught = shiniesCaught,
                legendariesCaught = legendariesCaught
            )
        }
    }
}
