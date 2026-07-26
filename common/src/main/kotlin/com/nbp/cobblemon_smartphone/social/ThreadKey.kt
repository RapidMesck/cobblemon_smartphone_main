package com.nbp.cobblemon_smartphone.social

import java.util.UUID

/**
 * Identity of a 1:1 DM thread.
 *
 * The pair is normalised on construction so that `of(a, b)` and `of(b, a)` are the same key —
 * otherwise the same conversation would end up stored twice, one copy per direction.
 */
data class ThreadKey private constructor(val first: UUID, val second: UUID) {

    fun other(self: UUID): UUID = if (self == first) second else first

    fun involves(uuid: UUID): Boolean = uuid == first || uuid == second

    fun serialize(): String = "$first$SEPARATOR$second"

    companion object {
        private const val SEPARATOR = "|"

        fun of(a: UUID, b: UUID): ThreadKey =
            if (a.toString() <= b.toString()) ThreadKey(a, b) else ThreadKey(b, a)

        fun deserialize(raw: String): ThreadKey? {
            val parts = raw.split(SEPARATOR, limit = 2)
            if (parts.size != 2) return null
            return runCatching { of(UUID.fromString(parts[0]), UUID.fromString(parts[1])) }.getOrNull()
        }
    }
}
