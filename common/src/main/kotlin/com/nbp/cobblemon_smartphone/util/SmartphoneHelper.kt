package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object SmartphoneHelper {
    var getSmartphoneImpl: ((Player) -> ItemStack?)? = null

    fun getSmartphone(player: Player): ItemStack? {
        return getSmartphoneImpl?.invoke(player)
            ?: player.inventory.items.firstOrNull { it.item is SmartphoneItem }
    }
}
