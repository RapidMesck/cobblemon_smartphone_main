package com.nbp.cobblemon_smartphone.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PokeInfoRequestLimiterTest {
    @Test
    fun `limits each player independently and allows boundary`() {
        val limiter = PokeInfoRequestLimiter(150)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        assertTrue(limiter.tryAcquire(first, 1_000))
        assertFalse(limiter.tryAcquire(first, 1_149))
        assertTrue(limiter.tryAcquire(first, 1_150))
        assertTrue(limiter.tryAcquire(second, 1_001))
    }
}
