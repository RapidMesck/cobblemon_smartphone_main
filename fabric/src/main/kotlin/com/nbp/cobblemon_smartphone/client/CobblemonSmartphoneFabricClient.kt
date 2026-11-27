package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.CobblemonSmartphoneFabricNetworkManager
import com.nbp.cobblemon_smartphone.client.dev.GuiScreenshotHarness
import com.nbp.cobblemon_smartphone.client.gui.CallOverlay
import com.nbp.cobblemon_smartphone.client.gui.GpsCompassOverlay
import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager
import com.nbp.cobblemon_smartphone.client.RotomTrackerClientState
import com.nbp.cobblemon_smartphone.client.gui.RotomDialogueOverlay
import com.nbp.cobblemon_smartphone.client.gui.RotomDroneOverlay
import com.nbp.cobblemon_smartphone.client.gui.RotomTrackerOverlay
import com.nbp.cobblemon_smartphone.client.gui.StructureCompassOverlay
import com.nbp.cobblemon_smartphone.client.keybind.QuickActionDispatcher
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.client.render.RotomClientManager
import com.nbp.cobblemon_smartphone.client.social.CallClientTicker
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.compat.refinedstorage.RefinedStorageAccessHolder
import com.nbp.cobblemon_smartphone.compat.refinedstorage.RefinedStorageAccessImpl
import net.fabricmc.loader.api.FabricLoader
import com.nbp.cobblemon_smartphone.item.SdCardItem
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenClientState
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

class CobblemonSmartphoneFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Refined Storage serializes its slot reference in the menu-open packet.
        // Defer client-side decoder registration until CLIENT_STARTED so Refined Storage's own
        // ClientModInitializer is guaranteed to have initialized RefinedStorageApi regardless of load order.
        ClientLifecycleEvents.CLIENT_STARTED.register { _ ->
            try {
                if (FabricLoader.getInstance().isModLoaded("refinedstorage")) {
                    RefinedStorageAccessImpl.initializeClient()
                } else {
                    RefinedStorageAccessHolder.instance?.initializeClient()
                }
            } catch (t: Throwable) {
                CobblemonSmartphone.LOGGER.error("Failed to initialize Refined Storage client compatibility", t)
            }
        }

        com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper.clientPlayerProvider = { Minecraft.getInstance().player }
        com.nbp.cobblemon_smartphone.skin.SmartphoneSkinRegistry.clientResourceManagerProvider = { Minecraft.getInstance()?.resourceManager }
        RotomInteractionClientHandler.register()

        ColorProviderRegistry.ITEM.register(
            { stack, tintIndex -> if (tintIndex == 0) SmartphoneHelper.getRibbonTintColor(stack) else -1 },
            CobblemonSmartphoneItems.ROTOM_RIBBON,
            CobblemonSmartphoneItems.ROTOM_BOWTIE
        )
        ColorProviderRegistry.ITEM.register(
            { stack, tintIndex -> if (tintIndex == 1) SdCardItem.getTintColor(stack) else -1 },
            CobblemonSmartphoneItems.SD_CARD
        )

        ModelLoadingPlugin.register { context ->
            SmartphoneColor.entries.forEach { color ->
                context.addModels(color.getHandModelPath())
                context.addModels(color.getRotomModelPath())
            }
            context.addModels(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/rotom"))
            Minecraft.getInstance()?.resourceManager?.let { rm ->
                com.nbp.cobblemon_smartphone.skin.SmartphoneSkinRegistry.ensureLoaded(rm)
            }
            com.nbp.cobblemon_smartphone.skin.SmartphoneSkinRegistry.getAllModelLocations().forEach {
                context.addModels(it)
            }
        }

        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.OPEN_SMARTPHONE)
        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.SCANNER)
        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.DISMISS_DIALOGUE)
        KeyBindingHelper.registerKeyBinding(SmartphoneKeybinds.ROTOM_DRONE)
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
            while (SmartphoneKeybinds.DISMISS_DIALOGUE.consumeClick()) {
                RotomDialogueClientState.dismissLocalDialogue()
                RotomTrackerClientState.dismiss()
            }
            while (SmartphoneKeybinds.ROTOM_DRONE.consumeClick()) {
                RotomDroneClientManager.toggleFromKeybind()
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
            RotomClientManager.tick()
            RotomDroneClientManager.tick()
            SocialPhotoClient.tick()
            GpsClientState.tick()
            StructureCompassClientState.tick()
            RotomTrackerClientState.tick()
            GuiScreenshotHarness.tick(client)
        })

        HudRenderCallback.EVENT.register(HudRenderCallback { guiGraphics, _ ->
            if (RotomDroneClientManager.isActive()) {
                RotomDroneOverlay.render(guiGraphics)
                return@HudRenderCallback
            }
            CallOverlay.render(guiGraphics)
            GpsCompassOverlay.render(guiGraphics)
            StructureCompassOverlay.render(guiGraphics)
            SocialPhotoClient.renderCameraOverlay(guiGraphics)
            RotomDialogueOverlay.render(guiGraphics)
            RotomTrackerOverlay.render(guiGraphics)
        })

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SocialClientSession.clear()
            GpsClientState.reset()
            StructureCompassClientState.reset()
            RotomTrackerClientState.reset()
            RotomScannerClientState.reset()
            RotomDroneClientManager.reset()
            RotomClientManager.reset()
            SmartphoneHelper.clearClientContext()
            DatapackScreenClientState.clear()
            SmartphoneActionRegistry.clearDatapackActions()
            CobblemonSmartphone.config = SmartphoneConfig.load()
        }

        CobblemonSmartphoneFabricNetworkManager.registerClientHandlers()
    }
}
