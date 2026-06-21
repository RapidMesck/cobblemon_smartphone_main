package com.nbp.neoforge.keybind

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.neoforge.compat.SmartphoneCompatManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object NeoForgeSmartphoneKeybindHandler {

    fun handleKeybind() {
        val player = Minecraft.getInstance().player ?: return

        val smartphone = SmartphoneCompatManager.getSmartphone(player)

        if (smartphone != null) {
            val smartphoneItem = smartphone.item as? SmartphoneItem ?: return
            Minecraft.getInstance().setScreen(SmartphoneScreen(smartphoneItem.getColor(), smartphone))
            player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.5f, 1f)
        }
    }

    fun handleScannerKeybind() {
        val player = Minecraft.getInstance().player ?: return

        val smartphoneItem = SmartphoneCompatManager.getSmartphoneItem(player)
        if (smartphoneItem == null) return

        if (!CobblemonSmartphone.config.features.enableScanner) {
            if (ScannerManager.isActive) {
                ScannerManager.deactivate()
            }
            player.displayClientMessage(
                Component.translatable("message.nbp.scanner.disabled").withColor(0xfd0100),
                true
            )
            return
        }

        if (ScannerManager.isActive) {
            ScannerManager.deactivate()
        } else {
            ScannerManager.activate(smartphoneItem.getColor().toPokedexType())
        }
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    fun onClientTick(client: Minecraft) {
        val player = client.player ?: return
        if (ScannerManager.isActive) {
            if (!CobblemonSmartphone.config.features.enableScanner) {
                ScannerManager.deactivate()
            }
            ScannerManager.tick(player)
        }
    }
}
