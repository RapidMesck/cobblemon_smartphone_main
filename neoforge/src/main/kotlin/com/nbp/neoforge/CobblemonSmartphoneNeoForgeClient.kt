package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.compat.refinedstorage.RefinedStorageAccessHolder
import com.nbp.neoforge.compat.refinedstorage.RefinedStorageAccessImpl
import com.nbp.cobblemon_smartphone.compat.ae2.AE2AccessHolder
import com.nbp.neoforge.compat.ae2.AE2AccessImpl
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "cobblemon_smartphone", bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object CobblemonSmartphoneNeoForgeClient {
    private val OPEN_SMARTPHONE by lazy { SmartphoneKeybinds.OPEN_SMARTPHONE }
    private val SCANNER by lazy { SmartphoneKeybinds.SCANNER }

    @SubscribeEvent
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_SMARTPHONE)
        event.register(SCANNER)
        SmartphoneKeybinds.QUICK_ACTION_SLOTS.forEach { event.register(it) }
        // Call hotkeys only make sense (and only appear in Controls) when Simple Voice Chat is present.
        if (VoiceChatBridge.isModPresent) {
            event.register(SmartphoneKeybinds.ANSWER_CALL)
            event.register(SmartphoneKeybinds.DECLINE_CALL)
        }
    }

    @SubscribeEvent
    fun initializeOptionalClientCompat(event: FMLClientSetupEvent) {
        // Refined Storage serializes its slot reference in the menu-open packet.
        // Client setup runs after Refined Storage has initialized its API registry.
        if (ModList.get().isLoaded("refinedstorage")) {
            RefinedStorageAccessImpl.initializeClient()
        } else {
            RefinedStorageAccessHolder.instance?.initializeClient()
        }

        // AE2 serializes the ItemMenuHostLocator when opening the wireless terminal.
        // Register the smartphone locator on the client before any server menu arrives.
        if (ModList.get().isLoaded("ae2")) {
            AE2AccessImpl.initializeClient()
        } else {
            AE2AccessHolder.instance?.initializeClient()
        }
    }
}
