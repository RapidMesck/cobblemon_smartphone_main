package com.nbp.cobblemon_smartphone

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

    fun init(implementation: Implementation) {
        config = SmartphoneConfig.load()
        this.implementation = implementation
        implementation.registerItems()
    }
}