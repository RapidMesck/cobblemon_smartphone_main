package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.neoforge.keybind.NeoForgeSmartphoneKeybindHandler
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.EventBusSubscriber.Bus
import net.neoforged.neoforge.client.event.ClientTickEvent

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "cobblemon_smartphone", bus = Bus.GAME, value = [Dist.CLIENT])
object CobblemonSmartphoneNeoForgeTickHandler {
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        while (SmartphoneKeybinds.OPEN_SMARTPHONE.consumeClick()) {
            NeoForgeSmartphoneKeybindHandler.handleKeybind()
        }
        while (SmartphoneKeybinds.SCANNER.consumeClick()) {
            NeoForgeSmartphoneKeybindHandler.handleScannerKeybind()
        }
        NeoForgeSmartphoneKeybindHandler.onClientTick(net.minecraft.client.Minecraft.getInstance())
    }
}
