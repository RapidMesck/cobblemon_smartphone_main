package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.actions.EnderAction
import com.nbp.cobblemon_smartphone.actions.HealAction
import com.nbp.cobblemon_smartphone.actions.OpenPcAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import net.minecraft.client.Minecraft
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CobblemonSmartphone {
    const val ID = "cobblemon_smartphone"
    val LOGGER: Logger = LoggerFactory.getLogger(ID)

    lateinit var config: SmartphoneConfig
    lateinit var implementation: Implementation

    fun registerDefaultActions() {
        SmartphoneActionRegistry.register(HealAction)
        SmartphoneActionRegistry.register(OpenPcAction)
        SmartphoneActionRegistry.register(EnderAction)
    }

    fun init(implementation: Implementation) {
        config = SmartphoneConfig.load()
        this.implementation = implementation
        implementation.registerItems()
        registerDefaultActions()
    }

    fun getSmartphoneActionRegistry() = SmartphoneActionRegistry
}