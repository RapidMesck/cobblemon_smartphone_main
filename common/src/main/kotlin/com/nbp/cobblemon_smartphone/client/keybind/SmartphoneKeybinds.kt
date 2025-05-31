package com.nbp.cobblemon_smartphone.client.keybind

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object SmartphoneKeybinds {
    private const val CATEGORY = "key.categories.cobblemon_smartphone"

    // Define o keybind com a tecla "P"
    val OPEN_SMARTPHONE: KeyMapping = KeyMapping(
        "key.cobblemon_smartphone.open",
        GLFW.GLFW_KEY_K,
        CATEGORY
    )

    // Essa função deve ser chamada a cada tick do cliente para tratar os keybinds
    fun handleKeybinds() {
            val player = Minecraft.getInstance().player ?: return

            // Verifica se o jogador tem um smartphone no inventário
            val smartphone = player.inventory.items.firstOrNull { it.item is SmartphoneItem }
            if (smartphone != null) {
                val smartphoneItem = smartphone.item as SmartphoneItem
                Minecraft.getInstance().setScreen(SmartphoneScreen(smartphoneItem.getColor()))
                player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.5f, 1f)
        }
    }
}
