package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import java.util.UUID

/**
 * An interactive poll attached to a [SocialPost].
 *
 * Each user can vote for at most one option at a time (voting again moves their vote).
 */
data class SocialPoll(
    val question: String,
    val options: List<String>,
    val votes: MutableMap<Int, MutableSet<UUID>> = mutableMapOf()
) {
    fun hasVoted(uuid: UUID): Boolean = votes.values.any { it.contains(uuid) }

    fun getVotedOption(uuid: UUID): Int? = votes.entries.find { it.value.contains(uuid) }?.key

    fun vote(uuid: UUID, optionIndex: Int): Boolean {
        if (optionIndex !in options.indices) return false
        val current = getVotedOption(uuid)
        if (current == optionIndex) return false // Already voted this option

        // Remove from previous option if any
        votes.values.forEach { it.remove(uuid) }
        votes.computeIfAbsent(optionIndex) { mutableSetOf() }.add(uuid)
        return true
    }

    fun getTotalVotes(): Int = votes.values.sumOf { it.size }

    fun getVotesForOption(optionIndex: Int): Int = votes[optionIndex]?.size ?: 0

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putString(QUESTION_KEY, question)

        val optionsList = ListTag()
        options.forEach { optionsList.add(StringTag.valueOf(it)) }
        tag.put(OPTIONS_KEY, optionsList)

        val votesCompound = CompoundTag()
        votes.forEach { (index, voters) ->
            val voterList = ListTag()
            voters.forEach { voterList.add(net.minecraft.nbt.NbtUtils.createUUID(it)) }
            votesCompound.put(index.toString(), voterList)
        }
        tag.put(VOTES_KEY, votesCompound)
        return tag
    }

    companion object {
        private const val QUESTION_KEY = "question"
        private const val OPTIONS_KEY = "options"
        private const val VOTES_KEY = "votes"

        fun fromNbt(tag: CompoundTag): SocialPoll? {
            val question = tag.getString(QUESTION_KEY)
            if (question.isBlank()) return null

            val optionsList = tag.getList(OPTIONS_KEY, Tag.TAG_STRING.toInt())
            val options = (0 until optionsList.size).map { optionsList.getString(it) }
            if (options.isEmpty()) return null

            val poll = SocialPoll(question, options)
            if (tag.contains(VOTES_KEY)) {
                val votesCompound = tag.getCompound(VOTES_KEY)
                for (key in votesCompound.allKeys) {
                    val index = key.toIntOrNull() ?: continue
                    val voterList = votesCompound.getList(key, Tag.TAG_INT_ARRAY.toInt())
                    val set = voterList.mapNotNull { runCatching { net.minecraft.nbt.NbtUtils.loadUUID(it) }.getOrNull() }.toMutableSet()
                    poll.votes[index] = set
                }
            }
            return poll
        }
    }
}
