package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.CobblemonSmartphoneFabricNetworkManager
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.Minecraft

class CobblemonSmartphoneFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.OPEN_SMARTPHONE)

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client: Minecraft ->
            while (SmartphoneKeybinds.OPEN_SMARTPHONE.consumeClick()) {
                SmartphoneKeybinds.handleKeybinds()
            }
        })

        CobblemonSmartphoneFabricNetworkManager.registerClientHandlers()
    }
}