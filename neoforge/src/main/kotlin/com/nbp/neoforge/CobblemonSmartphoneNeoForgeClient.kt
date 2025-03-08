package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

@EventBusSubscriber(modid = "cobblemon_smartphone", bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object CobblemonSmartphoneNeoForgeClient {
    // A propriedade é inicializada de forma lazy para garantir que o key mapping ainda não exista
    private val OPEN_SMARTPHONE by lazy { SmartphoneKeybinds.OPEN_SMARTPHONE }

    // O método é chamado durante o evento RegisterKeyMappingsEvent, que ocorre apenas no cliente físico
    @SubscribeEvent
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_SMARTPHONE)
    }
}