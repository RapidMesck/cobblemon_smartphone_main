package com.nbp.cobblemon_smartphone.api

/**
 * Client-side cache of the player's quick action hotkey bindings (slot index -> action id).
 * Populated from the server on join and updated locally when the player reassigns a slot
 * in the Quick Actions screen.
 */
object QuickActionBindings {
    private var bindings: Map<Int, String> = emptyMap()

    fun setBindings(newBindings: Map<Int, String>) {
        bindings = newBindings
    }

    fun currentBindings(): Map<Int, String> = bindings

    fun actionIdFor(slot: Int): String? = bindings[slot]
}
