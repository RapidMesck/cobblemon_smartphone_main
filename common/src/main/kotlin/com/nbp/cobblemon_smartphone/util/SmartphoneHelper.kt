package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

object SmartphoneHelper {
    var getSmartphoneImpl: ((Player) -> ItemStack?)? = null

    var contextSmartphone: ItemStack? = null
    var contextColor: SmartphoneColor? = null

    fun getSmartphone(player: Player): ItemStack? {
        // If a specific smartphone context is set (e.g. from right-clicking it), use it
        contextSmartphone?.let { if (it.item is SmartphoneItem) return it }
        // Otherwise fall back to platform compat (Trinkets/Curios/inventory)
        return getSmartphoneImpl?.invoke(player)
            ?: player.inventory.items.firstOrNull { it.item is SmartphoneItem }
            ?: (if (player.offhandItem.item is SmartphoneItem) player.offhandItem else null)
    }

    const val ROTOM_PHONE_TAG = "is_rotom"
    const val ROTOM_PHONE_ALT_TAG = "rotom_phone"

    fun ItemStack.isRotomPhone(): Boolean {
        if (this.item !is SmartphoneItem) return false
        val customData = this.get(DataComponents.CUSTOM_DATA) ?: return false
        val tag = customData.copyTag()
        return tag.getBoolean(ROTOM_PHONE_TAG) || tag.getBoolean(ROTOM_PHONE_ALT_TAG)
    }

    fun ItemStack.setRotomPhone(isRotom: Boolean = true) {
        val existingData = this.get(DataComponents.CUSTOM_DATA)
        val tag = existingData?.copyTag() ?: CompoundTag()
        tag.putBoolean(ROTOM_PHONE_TAG, isRotom)
        tag.putBoolean(ROTOM_PHONE_ALT_TAG, isRotom)
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    /**
     * Checks if the player has a Rotom Phone (main hand, offhand, accessories, or anywhere in inventory).
     */
    fun getEquippedRotomPhone(player: Player): ItemStack? {
        val main = player.mainHandItem
        if (main.isRotomPhone()) return main
        val off = player.offhandItem
        if (off.isRotomPhone()) return off
        val compat = getSmartphoneImpl?.invoke(player)
        if (compat != null && compat.isRotomPhone()) return compat
        val inv = player.inventory.items.firstOrNull { it.isRotomPhone() }
        if (inv != null) return inv
        return null
    }

    /**
     * Checks if the player has any smartphone equipped (main hand, offhand, or accessory slot).
     * Does NOT match if the smartphone is merely sitting in normal inventory storage.
     */
    fun getEquippedSmartphone(player: Player): ItemStack? {
        val main = player.mainHandItem
        if (main.item is SmartphoneItem) {
            return main
        }
        val off = player.offhandItem
        if (off.item is SmartphoneItem) {
            return off
        }
        val compat = getSmartphoneImpl?.invoke(player)
        if (compat != null && compat.item is SmartphoneItem) {
            return compat
        }
        return null
    }

    /**
     * Checks if the player has the orange smartphone equipped (main hand, offhand, or accessory slot).
     * Does NOT match if the smartphone is merely sitting in normal inventory storage.
     */
    fun getEquippedOrangeSmartphone(player: Player): ItemStack? {
        val stack = getEquippedSmartphone(player) ?: return null
        return if ((stack.item as? SmartphoneItem)?.getColor() == SmartphoneColor.ORANGE) stack else null
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

    /**
     * Finds the first smartphone the player is carrying that has the given upgrade AND
     * satisfies [predicate] (e.g. "has a stored terminal link"). Searches inventory, offhand,
     * and platform compat slots (Trinkets/Curios), same order as [hasUpgradeOnAnySmartphone].
     */
    fun findSmartphoneWithUpgradeAndLink(
        player: Player,
        upgradeKey: String,
        predicate: (ItemStack) -> Boolean,
        actionId: String? = null
    ): ItemStack? {
        val ignoreUpgrade = actionId != null && CobblemonSmartphone.config.ignoreUpgrades.contains(actionId)

        fun matches(stack: ItemStack): Boolean =
            stack.item is SmartphoneItem && (ignoreUpgrade || stack.hasUpgrade(upgradeKey)) && predicate(stack)

        player.inventory.items.firstOrNull(::matches)?.let { return it }
        if (matches(player.offhandItem)) return player.offhandItem
        getSmartphoneImpl?.invoke(player)?.let { if (matches(it)) return it }
        return null
    }
}
