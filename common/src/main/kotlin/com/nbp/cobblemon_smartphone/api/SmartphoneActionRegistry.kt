package com.nbp.cobblemon_smartphone.api

object SmartphoneActionRegistry {
    private val actions = mutableListOf<SmartphoneAction>()

    fun register(action: SmartphoneAction) {
        actions.add(action)
    }

    fun getEnabledActions(): List<SmartphoneAction> =
        actions.filter { it.isEnabled() }
}