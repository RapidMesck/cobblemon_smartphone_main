package com.nbp.cobblemon_smartphone.gps

import java.util.concurrent.atomic.AtomicInteger

/** Global concurrency cap for background search threads (GPS + Structure compass). Prevents a
 *  coordinated group of clients from spawning unbounded threads to exhaust server resources. */
object SearchConcurrencyLimiter {
    private const val MAX_CONCURRENT = 8
    private val active = AtomicInteger(0)

    /** Returns true if a new search slot was acquired, false if the cap is already reached. */
    fun tryAcquire(): Boolean {
        while (true) {
            val current = active.get()
            if (current >= MAX_CONCURRENT) return false
            if (active.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        active.decrementAndGet()
    }
}
