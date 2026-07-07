package com.nbp.cobblemon_smartphone.api

/**
 * Client-side cache of the player's preferred smartphone action order (list of action ids).
 * Populated from the server on join and updated locally when the player reorders actions
 * in the settings screen.
 */
object SmartphoneActionOrder {
    private var order: List<String> = emptyList()

    fun setOrder(newOrder: List<String>) {
        order = newOrder
    }

    fun currentOrder(): List<String> = order

    /**
     * Sorts [actions] according to the saved order. Actions not present in the saved order
     * (new actions, or ids no longer known) keep their original relative order and are
     * appended after every explicitly ordered action.
     */
    fun apply(actions: List<SmartphoneAction>): List<SmartphoneAction> {
        if (order.isEmpty()) return actions
        val indexOf = order.withIndex().associate { (index, actionId) -> actionId to index }
        return actions.sortedBy { indexOf[it.id] ?: Int.MAX_VALUE }
    }
}
