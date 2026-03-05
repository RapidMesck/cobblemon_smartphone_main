package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "cobblemon_smartphone", bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object CobblemonSmartphoneNeoForgeClient {
    // Lazy init so the key mapping object is created only when the event fires.
    private val OPEN_SMARTPHONE by lazy { SmartphoneKeybinds.OPEN_SMARTPHONE }

    @SubscribeEvent
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_SMARTPHONE)
    }
    // 3D hand model registration is handled by ModelLoaderMixin (common module),
    // which injects into ModelBakery.<init> and registers all smartphone_3d models
    // under the "inventory" variant for both Fabric and NeoForge.
}