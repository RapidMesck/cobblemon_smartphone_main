package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.EventBusSubscriber.Bus
import net.neoforged.neoforge.client.event.ClientTickEvent

@EventBusSubscriber(modid = "cobblemon_smartphone", bus = Bus.GAME, value = [Dist.CLIENT])
object CobblemonSmartphoneNeoForgeTickHandler {
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        // Loop para tratar múltiplos cliques registrados no mesmo tick
        while (SmartphoneKeybinds.OPEN_SMARTPHONE.consumeClick()) {
            SmartphoneKeybinds.handleKeybinds()
        }
    }
}
