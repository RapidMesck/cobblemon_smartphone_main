package com.nbp.cobblemon_smartphone.client.social

import net.minecraft.client.multiplayer.PlayerInfo
import java.util.UUID

/**
 * Utility to identify and filter out fake player entries injected into the tablist / online player
 * list by plugins such as NEZNAMY/TAB (specifically its Layout feature).
 *
 * TAB's Layout feature creates up to 80 (or more) fake entries using `new UUID(0, slot)` and names like
 * `" slot_11"`, `"|slot_11"`, `"!slot_11"`, `"!01"` or `""`, while hiding real players with `listed = false`.
 */
object FakePlayerFilter {

    private val TAB_SLOT_NAME_REGEX = Regex("^[|! _-]?slot_.*", RegexOption.IGNORE_CASE)

    fun isFakePlayer(info: PlayerInfo): Boolean =
        isFakePlayer(info.profile.id, info.profile.name)

    fun isFakePlayer(uuid: UUID, name: String?): Boolean {
        // 1. TAB plugin Layout slots use `new UUID(0, slot)` where slot is 1..80 (or up to hundreds),
        // or 0 for NIL_UUID.
        // Bedrock players via Floodgate also have mostSignificantBits == 0L, but their
        // leastSignificantBits is a 64-bit Xbox XUID (> 10^14), so checking 0..100_000L is completely safe.
        if (uuid.mostSignificantBits == 0L && uuid.leastSignificantBits in 0L..100_000L) {
            return true
        }

        // 2. Real players must have a non-blank username of at least 2 characters.
        if (name.isNullOrBlank() || name.trim().length < 2) {
            return true
        }

        // 3. TAB plugin layout entry names: "slot_X", " slot_X", "|slot_X", "!slot_X", etc.
        if (TAB_SLOT_NAME_REGEX.matches(name)) {
            return true
        }

        // 4. Invalid Minecraft / Gamertag usernames (leading/trailing spaces, pipes, color codes,
        // exclamation marks, tildes, percent signs, brackets used by fake player slots / NPCs).
        if (name.startsWith(" ") || name.endsWith(" ") ||
            name.startsWith("!") || name.startsWith("~") || name.startsWith("|") ||
            name.contains('|') || name.contains('§') || name.contains('%') ||
            name.contains('[') || name.contains(']')
        ) {
            return true
        }

        return false
    }
}
