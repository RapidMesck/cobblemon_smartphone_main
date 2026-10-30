package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialPollTest {

    @Test
    fun `poll voting mechanics work properly`() {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val poll = SocialPoll(
            question = "Qual seu inicial favorito?",
            options = listOf("Charmander", "Squirtle", "Bulbasaur")
        )

        assertEquals(0, poll.getTotalVotes())
        assertFalse(poll.hasVoted(user1))
        assertNull(poll.getVotedOption(user1))

        // User 1 votes for Charmander (index 0)
        assertTrue(poll.vote(user1, 0))
        assertEquals(1, poll.getTotalVotes())
        assertEquals(1, poll.getVotesForOption(0))
        assertEquals(0, poll.getVotesForOption(1))
        assertTrue(poll.hasVoted(user1))
        assertEquals(0, poll.getVotedOption(user1))

        // Duplicate vote on same option returns false
        assertFalse(poll.vote(user1, 0))
        assertEquals(1, poll.getTotalVotes())

        // User 2 votes for Bulbasaur (index 2)
        assertTrue(poll.vote(user2, 2))
        assertEquals(2, poll.getTotalVotes())
        assertEquals(1, poll.getVotesForOption(0))
        assertEquals(0, poll.getVotesForOption(1))
        assertEquals(1, poll.getVotesForOption(2))

        // User 1 changes vote to Squirtle (index 1)
        assertTrue(poll.vote(user1, 1))
        assertEquals(2, poll.getTotalVotes())
        assertEquals(0, poll.getVotesForOption(0))
        assertEquals(1, poll.getVotesForOption(1))
        assertEquals(1, poll.getVotesForOption(2))
        assertEquals(1, poll.getVotedOption(user1))

        // Invalid option returns false
        assertFalse(poll.vote(user1, 99))
    }

    @Test
    fun `poll serializes to and from NBT`() {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val poll = SocialPoll(
            question = "Melhor Mega Evolução?",
            options = listOf("Mega Lucario", "Mega Rayquaza")
        )
        poll.vote(user1, 0)
        poll.vote(user2, 1)

        val nbt = poll.toNbt()
        val restored = SocialPoll.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals("Melhor Mega Evolução?", restored?.question)
        assertEquals(listOf("Mega Lucario", "Mega Rayquaza"), restored?.options)
        assertEquals(2, restored?.getTotalVotes())
        assertEquals(0, restored?.getVotedOption(user1))
        assertEquals(1, restored?.getVotedOption(user2))
    }

    @Test
    fun `poll view correctly reflects vote counts and viewer status`() {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val spectator = UUID.randomUUID()

        val poll = SocialPoll(
            question = "Kanto ou Johto?",
            options = listOf("Kanto", "Johto")
        )
        poll.vote(user1, 0)
        poll.vote(user2, 0)

        val viewUser1 = SocialPollView.of(poll, user1)
        assertEquals("Kanto ou Johto?", viewUser1.question)
        assertEquals(2, viewUser1.totalVotes)
        assertEquals(listOf(2, 0), viewUser1.voteCounts)
        assertEquals(0, viewUser1.userVotedOption)

        val viewSpectator = SocialPollView.of(poll, spectator)
        assertEquals(2, viewSpectator.totalVotes)
        assertEquals(listOf(2, 0), viewSpectator.voteCounts)
        assertNull(viewSpectator.userVotedOption)
    }

    @Test
    fun `post with poll serializes to and from NBT`() {
        val authorUuid = UUID.randomUUID()
        val voterUuid = UUID.randomUUID()
        val poll = SocialPoll(
            question = "Enquete teste",
            options = listOf("Sim", "Não")
        )
        poll.vote(voterUuid, 0)

        val post = SocialPost(
            id = 55L,
            authorUuid = authorUuid,
            authorName = "Professor Oak",
            text = "Decidam!",
            timestamp = 1700000000000L,
            attachment = null,
            poll = poll
        )

        val nbt = post.toNbt()
        val restored = SocialPost.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(55L, restored?.id)
        assertNotNull(restored?.poll)
        assertEquals("Enquete teste", restored?.poll?.question)
        assertEquals(1, restored?.poll?.getTotalVotes())
        assertEquals(0, restored?.poll?.getVotedOption(voterUuid))
    }
}
