package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.CobblemonSmartphoneFabricNetworkManager
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.minecraft.client.Minecraft

class CobblemonSmartphoneFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Force the 3D hand models to be baked (they are not tied to a registered item).
        // The ModelLoaderMixin registers them under the "inventory" variant; this call
        // ensures the JSON is loaded into the unbaked model cache before that happens.
        ModelLoadingPlugin.register { context ->
            SmartphoneColor.entries.forEach { color ->
                context.addModels(color.getHandModelPath())
            }
        }

        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.OPEN_SMARTPHONE)

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client: Minecraft ->
            while (SmartphoneKeybinds.OPEN_SMARTPHONE.consumeClick()) {
                // Use Fabric-specific handler that supports Trinkets
                FabricSmartphoneKeybindHandler.handleKeybind()
            }
        })

        CobblemonSmartphoneFabricNetworkManager.registerClientHandlers()
    }
}