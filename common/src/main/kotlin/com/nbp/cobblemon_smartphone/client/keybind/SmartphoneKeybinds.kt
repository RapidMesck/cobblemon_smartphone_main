package com.nbp.cobblemon_smartphone.client.keybind

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object SmartphoneKeybinds {
    private const val CATEGORY = "key.categories.cobblemon_smartphone"

    val OPEN_SMARTPHONE: KeyMapping = KeyMapping(
        "key.cobblemon_smartphone.open",
        GLFW.GLFW_KEY_K,
        CATEGORY
    )

    val SCANNER: KeyMapping = KeyMapping(
        "key.cobblemon_smartphone.scanner",
        GLFW.GLFW_KEY_C,
        CATEGORY
    )

    const val QUICK_ACTION_SLOT_COUNT = 6

    val QUICK_ACTION_SLOTS: List<KeyMapping> = (0 until QUICK_ACTION_SLOT_COUNT).map { index ->
        KeyMapping(
            "key.cobblemon_smartphone.quick_action_${index + 1}",
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
        )
    }

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
