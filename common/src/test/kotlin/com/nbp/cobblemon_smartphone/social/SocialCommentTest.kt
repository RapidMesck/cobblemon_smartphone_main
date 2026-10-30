package com.nbp.cobblemon_smartphone.social

import com.nbp.cobblemon_smartphone.network.SocialRequestLimiter
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialCommentTest {

    @Test
    fun `comment serializes to and from NBT`() {
        val authorUuid = UUID.randomUUID()
        val comment = SocialComment(
            id = 42L,
            postId = 10L,
            authorUuid = authorUuid,
            authorName = "Red",
            text = "Parabéns pelo Charizard shiny!",
            timestamp = 1700000000000L
        )

        val nbt = comment.toNbt()
        val restored = SocialComment.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(42L, restored?.id)
        assertEquals(10L, restored?.postId)
        assertEquals(authorUuid, restored?.authorUuid)
        assertEquals("Red", restored?.authorName)
        assertEquals("Parabéns pelo Charizard shiny!", restored?.text)
        assertEquals(1700000000000L, restored?.timestamp)
    }

    @Test
    fun `post with comments serializes to and from NBT`() {
        val authorUuid = UUID.randomUUID()
        val commenterUuid = UUID.randomUUID()
        val post = SocialPost(
            id = 1L,
            authorUuid = authorUuid,
            authorName = "Ash",
            text = "Capturei um Pikachu!",
            timestamp = 1700000000000L,
            attachment = null
        )
        post.comments.add(
            SocialComment(
                id = 101L,
                postId = 1L,
                authorUuid = commenterUuid,
                authorName = "Gary",
                text = "O meu é mais forte.",
                timestamp = 1700000050000L
            )
        )

        val nbt = post.toNbt()
        val restored = SocialPost.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(1, restored?.comments?.size)
        assertEquals("Gary", restored?.comments?.first()?.authorName)
        assertEquals("O meu é mais forte.", restored?.comments?.first()?.text)
    }

    @Test
    fun `post without comments tag deserializes safely with empty list`() {
        val authorUuid = UUID.randomUUID()
        val tag = CompoundTag().apply {
            putLong("id", 5L)
            putUUID("author", authorUuid)
            putString("author_name", "Brock")
            putString("text", "Ginásio aberto hoje!")
            putLong("timestamp", 1700000000000L)
        }

        val restored = SocialPost.fromNbt(tag)
        assertNotNull(restored)
        assertTrue(restored!!.comments.isEmpty())
    }

    @Test
    fun `request limiter regulates comment actions`() {
        val player = UUID.randomUUID()
        SocialRequestLimiter.clear(player)

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.COMMENT, 1_000L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.COMMENT, 1_200L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.COMMENT, 1_600L))

        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_COMMENTS, 1_600L))
        assertFalse(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_COMMENTS, 1_700L))
        assertTrue(SocialRequestLimiter.allow(player, SocialRequestLimiter.Action.REQUEST_COMMENTS, 1_900L))
    }
}
