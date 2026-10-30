package com.nbp.cobblemon_smartphone.social

import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Client projection of a [SocialPoll].
 *
 * Contains vote counts and whether the current viewer voted for any option.
 */
data class SocialPollView(
    val question: String,
    val options: List<String>,
    val voteCounts: List<Int>,
    val totalVotes: Int,
    val userVotedOption: Int?
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(question)
        buffer.writeVarInt(options.size)
        options.forEach { buffer.writeUtf(it) }
        buffer.writeVarInt(voteCounts.size)
        voteCounts.forEach { buffer.writeVarInt(it) }
        buffer.writeVarInt(totalVotes)
        buffer.writeBoolean(userVotedOption != null)
        userVotedOption?.let { buffer.writeVarInt(it) }
    }

    companion object {
        fun of(poll: SocialPoll, viewer: UUID): SocialPollView {
            val totalVotes = poll.getTotalVotes()
            val voteCounts = poll.options.indices.map { poll.getVotesForOption(it) }
            val userVotedOption = poll.getVotedOption(viewer)
            return SocialPollView(
                question = poll.question,
                options = poll.options,
                voteCounts = voteCounts,
                totalVotes = totalVotes,
                userVotedOption = userVotedOption
            )
        }

        fun decode(buffer: RegistryFriendlyByteBuf): SocialPollView {
            val question = buffer.readUtf()
            val optCount = buffer.readVarInt()
            val options = (0 until optCount).map { buffer.readUtf() }
            val countCount = buffer.readVarInt()
            val voteCounts = (0 until countCount).map { buffer.readVarInt() }
            val totalVotes = buffer.readVarInt()
            val userVotedOption = if (buffer.readBoolean()) buffer.readVarInt() else null
            return SocialPollView(
                question = question,
                options = options,
                voteCounts = voteCounts,
                totalVotes = totalVotes,
                userVotedOption = userVotedOption
            )
        }
    }
}
