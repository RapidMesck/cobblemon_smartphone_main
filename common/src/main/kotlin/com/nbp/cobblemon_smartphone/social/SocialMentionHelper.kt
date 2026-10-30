package com.nbp.cobblemon_smartphone.social

object SocialMentionHelper {
    private val MENTION_REGEX = Regex("""(?<=^|\s)@([a-zA-Z0-9_]{1,16})\b""")

    /**
     * Extracts all unique `@username` mentions from the given text (without the `@` prefix).
     */
    fun extractMentions(text: String): Set<String> {
        if (!text.contains('@')) return emptySet()
        return MENTION_REGEX.findAll(text).map { it.groupValues[1] }.toSet()
    }

    /**
     * Checks if the text mentions the given [username] (case-insensitive).
     */
    fun hasMention(text: String, username: String): Boolean {
        if (!text.contains('@') || username.isBlank()) return false
        return extractMentions(text).any { it.equals(username, ignoreCase = true) }
    }
}
