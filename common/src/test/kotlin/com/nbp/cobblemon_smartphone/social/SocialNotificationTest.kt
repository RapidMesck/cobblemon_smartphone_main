package com.nbp.cobblemon_smartphone.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialNotificationTest {

    @Test
    fun `notification serializes to and from NBT`() {
        val actorUuid = UUID.randomUUID()
        val notif = SocialNotification(
            id = 42L,
            targetUuid = UUID.randomUUID(),
            actorUuid = actorUuid,
            actorName = "Red",
            type = SocialNotificationType.LIKE,
            referenceId = 101L,
            message = "Awesome post!",
            timestamp = 1700000000000L,
            read = false
        )

        val nbt = notif.toNbt()
        val restored = SocialNotification.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(42L, restored?.id)
        assertEquals(actorUuid, restored?.actorUuid)
        assertEquals("Red", restored?.actorName)
        assertEquals(SocialNotificationType.LIKE, restored?.type)
        assertEquals(101L, restored?.referenceId)
        assertEquals("Awesome post!", restored?.message)
        assertEquals(1700000000000L, restored?.timestamp)
        assertFalse(restored?.read == true)
    }

    @Test
    fun `notification read state updates correctly`() {
        val data = SocialData()
        val recipient = UUID.randomUUID()
        val actor = UUID.randomUUID()

        val n1 = data.addNotification(recipient, actor, "Blue", SocialNotificationType.LIKE, 1L, "")
        val n2 = data.addNotification(recipient, actor, "Blue", SocialNotificationType.COMMENT, 1L, "Cool!")

        assertNotNull(n1)
        assertNotNull(n2)
        assertEquals(2, data.unreadNotificationCountFor(recipient))

        // Mark individual read
        val marked = data.markNotificationRead(recipient, n1!!.id)
        assertTrue(marked)
        assertEquals(1, data.unreadNotificationCountFor(recipient))

        // Mark all read (id = 0L)
        data.markNotificationRead(recipient, 0L)
        assertEquals(0, data.unreadNotificationCountFor(recipient))
    }

    @Test
    fun `notification list enforces max size limit`() {
        val data = SocialData()
        val recipient = UUID.randomUUID()
        val actor = UUID.randomUUID()

        // Insert 60 notifications (limit is 50)
        for (i in 1..60) {
            data.addNotification(recipient, actor, "Player$i", SocialNotificationType.FOLLOW, 0L, "")
        }

        val notifications = data.notificationsFor(recipient)
        assertEquals(50, notifications.size)
        // Most recent should be Player60
        assertEquals("Player60", notifications.first().actorName)
        // Oldest retained should be Player11
        assertEquals("Player11", notifications.last().actorName)
    }
}
