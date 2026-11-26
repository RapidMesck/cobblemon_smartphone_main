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

    fun getAllActions(): List<SmartphoneAction> =
        actions + datapackActions

    fun getActionById(id: String): SmartphoneAction? =
        (actions + datapackActions).firstOrNull { it.id == id }

    fun getEnabledActions(): List<SmartphoneAction> =
        (actions + datapackActions).filter { it.isEnabled() }
}