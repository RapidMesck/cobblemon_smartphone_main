package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.actions.EnderAction
import com.nbp.cobblemon_smartphone.actions.HealAction
import com.nbp.cobblemon_smartphone.actions.OpenCobblenavAction
import com.nbp.cobblemon_smartphone.actions.OpenCobbledollarsAction
import com.nbp.cobblemon_smartphone.actions.OpenPcAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.client.BuiltinResourcePack
import com.nbp.cobblemon_smartphone.client.ResourcePackActivationBehavior
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.PackType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CobblemonSmartphone {
    const val ID = "cobblemon_smartphone"
    val LOGGER: Logger = LoggerFactory.getLogger(ID)

    @JvmStatic
    val builtinPacks = listOf(
        BuiltinResourcePack(
            id = "oldsmartphone",
            name = "Old Smartphone Textures",
            packType = PackType.CLIENT_RESOURCES,
            activationBehavior = ResourcePackActivationBehavior.NORMAL
        )
    )

    lateinit var config: SmartphoneConfig
    lateinit var implementation: Implementation

    fun registerDefaultActions() {
        SmartphoneActionRegistry.register(HealAction)
        SmartphoneActionRegistry.register(OpenPcAction)
        SmartphoneActionRegistry.register(EnderAction)
        SmartphoneActionRegistry.register(OpenCobblenavAction)
        SmartphoneActionRegistry.register(OpenCobbledollarsAction)
    }

    fun init(implementation: Implementation) {
        config = SmartphoneConfig.load()
        this.implementation = implementation
        implementation.registerItems()
        registerDefaultActions()
    }

    fun getSmartphoneActionRegistry() = SmartphoneActionRegistry
}