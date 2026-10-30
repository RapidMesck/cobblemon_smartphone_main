package com.nbp.cobblemon_smartphone.social

object SocialTagHelper {
    private val HASHTAG_REGEX = Regex("""(?<=^|\s)#([a-zA-Z0-9_]{1,24})\b""")

    /**
     * Extracts all unique `#tag` hashtags from the text (without the `#` prefix).
     */
    fun extractHashtags(text: String): Set<String> {
        if (!text.contains('#')) return emptySet()
        return HASHTAG_REGEX.findAll(text).map { it.groupValues[1] }.toSet()
    }

    /**
     * Checks if the text contains the given [hashtag] (case-insensitive).
     */
    fun hasHashtag(text: String, hashtag: String): Boolean {
        if (!text.contains('#') || hashtag.isBlank()) return false
        val clean = hashtag.removePrefix("#").trim()
        return extractHashtags(text).any { it.equals(clean, ignoreCase = true) }
    }
}
