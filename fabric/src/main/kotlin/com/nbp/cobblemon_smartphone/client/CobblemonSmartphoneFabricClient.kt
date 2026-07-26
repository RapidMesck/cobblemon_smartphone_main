package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.CobblemonSmartphoneFabricNetworkManager
import com.nbp.cobblemon_smartphone.client.gui.CallOverlay
import com.nbp.cobblemon_smartphone.client.keybind.QuickActionDispatcher
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.client.social.CallClientTicker
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

class CobblemonSmartphoneFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        ModelLoadingPlugin.register { context ->
            SmartphoneColor.entries.forEach { color ->
                context.addModels(color.getHandModelPath())
            }
        }

        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.OPEN_SMARTPHONE)
        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.SCANNER)
        SmartphoneKeybinds.QUICK_ACTION_SLOTS.forEach { KeyBindingHelper.registerKeyBinding(it) }
        // Call hotkeys only make sense (and only appear in Controls) when Simple Voice Chat is present.
        if (VoiceChatBridge.isModPresent) {
            KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.ANSWER_CALL)
            KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.DECLINE_CALL)
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client: Minecraft ->
            while (SmartphoneKeybinds.OPEN_SMARTPHONE.consumeClick()) {
                FabricSmartphoneKeybindHandler.handleKeybind()
            }
            while (SmartphoneKeybinds.SCANNER.consumeClick()) {
                FabricSmartphoneKeybindHandler.handleScannerKeybind()
            }
            SmartphoneKeybinds.QUICK_ACTION_SLOTS.forEachIndexed { index, keyMapping ->
                while (keyMapping.consumeClick()) {
                    QuickActionDispatcher.trigger(index)
                }
            }
            while (SmartphoneKeybinds.ANSWER_CALL.consumeClick()) {
                CallOverlay.pressAnswer()
            }
            while (SmartphoneKeybinds.DECLINE_CALL.consumeClick()) {
                CallOverlay.pressHangup()
            }
            FabricSmartphoneKeybindHandler.onClientTick(client)
            CallClientTicker.tick()
        })

        HudRenderCallback.EVENT.register(HudRenderCallback { guiGraphics, _ ->
            CallOverlay.render(guiGraphics)
        })

        CobblemonSmartphoneFabricNetworkManager.registerClientHandlers()
    }
}
