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
    private val OPEN_SMARTPHONE by lazy { SmartphoneKeybinds.OPEN_SMARTPHONE }
    private val SCANNER by lazy { SmartphoneKeybinds.SCANNER }

    @SubscribeEvent
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_SMARTPHONE)
        event.register(SCANNER)
    }
}
