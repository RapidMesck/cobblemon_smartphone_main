package com.nbp.cobblemon_smartphone.api

/**
 * Client-side cache of the action ids the player chose to hide from the smartphone home screen.
 * Populated from the server on join and updated locally when the player toggles visibility in the
 * settings screen.
 */
object SmartphoneHiddenActions {
    private var hidden: Set<String> = emptySet()

    fun setHidden(newHidden: List<String>) {
        hidden = newHidden.toSet()
    }

    fun currentHidden(): Set<String> = hidden

    fun isHidden(actionId: String): Boolean = actionId in hidden
}
