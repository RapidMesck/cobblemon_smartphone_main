package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.CobblemonClient
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager
import com.nbp.cobblemon_smartphone.compat.SmartphoneCompatManager
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import net.minecraft.client.Minecraft

object FabricSmartphoneKeybindHandler {

    fun handleKeybind() {
        val player = Minecraft.getInstance().player ?: return

        val smartphoneItem = SmartphoneCompatManager.getSmartphoneItem(player)

        if (smartphoneItem != null) {
            Minecraft.getInstance().setScreen(SmartphoneScreen(smartphoneItem.getColor()))
            player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.5f, 1f)
        }
    }

    fun handleScannerKeybind() {
        val player = Minecraft.getInstance().player ?: return

        val hasSmartphone = SmartphoneCompatManager.getSmartphoneItem(player) != null
        if (!hasSmartphone) return

        if (ScannerManager.isActive) {
            ScannerManager.deactivate()
        } else {
            ScannerManager.activate()
        }
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    }

    fun onClientTick(client: Minecraft) {
        val player = client.player ?: return
        if (ScannerManager.isActive) {
            ScannerManager.tick(player)
        }
    }
}
