package com.nbp.cobblemon_smartphone.util

object PokeInfoLayout {
    private const val SEPARATOR_HEIGHT = 4

    /** Adds one separator after every section rendered on the current page. */
    fun totalHeight(sectionHeights: List<Int>): Int =
        sectionHeights.sum() + sectionHeights.size * SEPARATOR_HEIGHT

    /** Includes only enabled sections and one separator after every section actually rendered. */
    fun totalHeight(
        formHeight: Int,
        topHeight: Int,
        optionalSections: List<Pair<Boolean, Int>>
    ): Int {
        val heights = mutableListOf(formHeight, topHeight)
        optionalSections.filter { it.first }.forEach { heights += it.second }
        return totalHeight(heights)
    }
}
