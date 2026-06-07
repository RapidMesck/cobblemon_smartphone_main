package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object SmartphoneHelper {
    var getSmartphoneImpl: ((Player) -> ItemStack?)? = null

    /**
     * When set, this smartphone is checked before any other lookup.
     * Set by SmartphoneScreen when opened, cleared when closed.
     */
    var contextSmartphone: ItemStack? = null

    fun getSmartphone(player: Player): ItemStack? {
        // If a specific smartphone context is set (e.g. from right-clicking it), use it
        contextSmartphone?.let { if (it.item is SmartphoneItem) return it }
        // Otherwise fall back to platform compat (Trinkets → Curios → inventory)
        return getSmartphoneImpl?.invoke(player)
            ?: player.inventory.items.firstOrNull { it.item is SmartphoneItem }
    }
}
