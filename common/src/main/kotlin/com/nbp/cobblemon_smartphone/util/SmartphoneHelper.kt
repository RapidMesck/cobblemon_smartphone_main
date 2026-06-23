package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
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
        // Otherwise fall back to platform compat (Trinkets/Curios/inventory)
        return getSmartphoneImpl?.invoke(player)
            ?: player.inventory.items.firstOrNull { it.item is SmartphoneItem }
    }

    fun satisfiesUpgradeRequirement(player: Player, upgradeKey: String, actionId: String? = null): Boolean {
        if (actionId != null && CobblemonSmartphone.config.ignoreUpgrades.contains(actionId)) {
            return true
        }

        val smartphone = getSmartphone(player) ?: return false
        return smartphone.hasUpgrade(upgradeKey)
    }

    /**
     * Checks whether ANY smartphone in the player's possession has the given upgrade.
     * Searches inventory, offhand, and platform compat slots (Trinkets/Curios).
     * Use this on the server side where the specific smartphone context is unknown.
     */
    fun hasUpgradeOnAnySmartphone(player: Player, upgradeKey: String, actionId: String? = null): Boolean {
        if (actionId != null && CobblemonSmartphone.config.ignoreUpgrades.contains(actionId)) {
            return true
        }

        // Check all inventory slots
        if (player.inventory.items.any { it.isSmartphoneWithUpgrade(upgradeKey) }) return true
        // Check offhand
        if (player.offhandItem.isSmartphoneWithUpgrade(upgradeKey)) return true
        // Check platform compat (Trinkets/Curios)
        val compatSmartphone = getSmartphoneImpl?.invoke(player)
        if (compatSmartphone != null && compatSmartphone.isSmartphoneWithUpgrade(upgradeKey)) return true
        return false
    }

    private fun ItemStack.isSmartphoneWithUpgrade(upgradeKey: String): Boolean =
        this.item is SmartphoneItem && this.hasUpgrade(upgradeKey)
}
