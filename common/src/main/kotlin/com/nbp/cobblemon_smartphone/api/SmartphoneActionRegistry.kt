package com.nbp.cobblemon_smartphone.api

object SmartphoneActionRegistry {
    private val actions = mutableListOf<SmartphoneAction>()
    private val datapackActions = mutableListOf<SmartphoneAction>()

    fun register(action: SmartphoneAction) {
        actions.add(action)
    }

    fun registerDatapackAction(action: SmartphoneAction) {
        datapackActions.add(action)
    }

    fun clearDatapackActions() {
        datapackActions.clear()
    }

    fun getEnabledActions(): List<SmartphoneAction> =
        (actions + datapackActions).filter { it.isEnabled() }
}