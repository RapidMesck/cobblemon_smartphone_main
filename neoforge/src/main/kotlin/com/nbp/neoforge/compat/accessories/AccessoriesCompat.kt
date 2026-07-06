package com.nbp.neoforge.compat.accessories

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import io.wispforest.accessories.api.AccessoriesCapability
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Accessories compatibility layer for Cobblemon Smartphone.
 * This class is only loaded when Accessories is present.
 *
 * Unlike Trinkets/Curios, Accessories is fully data-driven: smartphones are made
 * equippable through the `smartphone` slot definition plus the `accessories:smartphone`
 * item tag, so no code-side item registration is required. This layer only reads the
 * equipped state.
 */
object AccessoriesCompat {

    /**
     * @return the smartphone ItemStack equipped in any Accessories slot, or null.
     */
    fun getEquippedSmartphone(player: Player): ItemStack? {
        return try {
            val capability = AccessoriesCapability.get(player) ?: return null
            capability.getFirstEquipped { stack -> stack.item is SmartphoneItem }?.stack()
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.debug("Error getting smartphone from Accessories: ${e.message}")
            null
        }
    }

    /**
     * @return true if a smartphone is equipped in any Accessories slot.
     */
    fun hasSmartphoneEquipped(player: Player): Boolean {
        return try {
            val capability = AccessoriesCapability.get(player) ?: return false
            capability.isEquipped { stack -> stack.item is SmartphoneItem }
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.debug("Error checking Accessories: ${e.message}")
            false
        }
    }
}
