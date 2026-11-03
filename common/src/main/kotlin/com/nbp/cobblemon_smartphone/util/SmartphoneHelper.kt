package com.nbp.cobblemon_smartphone.util

import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor

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
    const val ROTOM_DATA_TAG = "fused_rotom_data"
    const val ROTOM_ACCESSORY_TAG = "rotom_accessory"
    const val ROTOM_RIBBON_COLOR_TAG = "rotom_ribbon_color"
    const val ROTOM_ACCESSORY_COLOR_TAG = "rotom_accessory_color"

    fun getDyeDisplayRgb(color: DyeColor): Int {
        return when (color) {
            DyeColor.WHITE -> 0xF9FFFE
            DyeColor.ORANGE -> 0xF9801D
            DyeColor.MAGENTA -> 0xC74EBD
            DyeColor.LIGHT_BLUE -> 0x3AB3DA
            DyeColor.YELLOW -> 0xFED83D
            DyeColor.LIME -> 0x80C71F
            DyeColor.PINK -> 0xFF69B4
            DyeColor.GRAY -> 0x555E62
            DyeColor.LIGHT_GRAY -> 0xA8A8A2
            DyeColor.CYAN -> 0x169C9C
            DyeColor.PURPLE -> 0x8932B8
            DyeColor.BLUE -> 0x3C44AA
            DyeColor.BROWN -> 0x835432
            DyeColor.GREEN -> 0x5E7C16
            DyeColor.RED -> 0xE02E26
            DyeColor.BLACK -> 0x2D2D32
        }
    }

    fun findClosestDyeColor(rgb: Int): DyeColor {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return DyeColor.entries.minByOrNull { dc ->
            val c = getDyeDisplayRgb(dc)
            val dr = r - ((c shr 16) and 0xFF)
            val dg = g - ((c shr 8) and 0xFF)
            val db = b - (c and 0xFF)
            dr * dr + dg * dg + db * db
        } ?: DyeColor.PINK
    }

    fun ItemStack.getAccessoryDyeColor(defaultColor: DyeColor = DyeColor.PINK): DyeColor {
        val dyed = this.get(DataComponents.DYED_COLOR)
        val customData = this.get(DataComponents.CUSTOM_DATA)
        if (customData != null) {
            val tag = customData.copyTag()
            val colorName = when {
                tag.contains(ROTOM_ACCESSORY_COLOR_TAG) -> tag.getString(ROTOM_ACCESSORY_COLOR_TAG)
                tag.contains(ROTOM_RIBBON_COLOR_TAG) -> tag.getString(ROTOM_RIBBON_COLOR_TAG)
                else -> null
            }
            if (colorName != null) {
                val byName = DyeColor.byName(colorName, null)
                if (byName != null) {
                    if (dyed != null && (dyed.rgb() and 0xFFFFFF) != getDyeDisplayRgb(byName)) {
                        return findClosestDyeColor(dyed.rgb())
                    }
                    return byName
                }
            }
        }
        if (dyed != null) {
            return findClosestDyeColor(dyed.rgb())
        }
        return defaultColor
    }

    fun ItemStack.getRibbonDyeColor(): DyeColor? {
        val customData = this.get(DataComponents.CUSTOM_DATA)
        val hasTag = customData != null && (customData.copyTag().contains(ROTOM_ACCESSORY_COLOR_TAG) || customData.copyTag().contains(ROTOM_RIBBON_COLOR_TAG))
        val dyed = this.get(DataComponents.DYED_COLOR)
        if (!hasTag && dyed == null) return null
        return getAccessoryDyeColor(DyeColor.PINK)
    }

    fun ItemStack.getBowtieDyeColor(): DyeColor? {
        val customData = this.get(DataComponents.CUSTOM_DATA)
        val hasTag = customData != null && (customData.copyTag().contains(ROTOM_ACCESSORY_COLOR_TAG) || customData.copyTag().contains(ROTOM_RIBBON_COLOR_TAG))
        val dyed = this.get(DataComponents.DYED_COLOR)
        if (!hasTag && dyed == null) return null
        return getAccessoryDyeColor(DyeColor.LIGHT_BLUE)
    }

    fun ItemStack.setAccessoryDyeColor(color: DyeColor?) {
        val existingData = this.get(DataComponents.CUSTOM_DATA)
        val tag = existingData?.copyTag() ?: CompoundTag()
        if (color != null) {
            tag.putString(ROTOM_ACCESSORY_COLOR_TAG, color.serializedName)
            tag.putString(ROTOM_RIBBON_COLOR_TAG, color.serializedName)
            this.set(DataComponents.DYED_COLOR, DyedItemColor(getDyeDisplayRgb(color), false))
        } else {
            tag.remove(ROTOM_ACCESSORY_COLOR_TAG)
            tag.remove(ROTOM_RIBBON_COLOR_TAG)
            this.remove(DataComponents.DYED_COLOR)
        }
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    fun ItemStack.setRibbonDyeColor(color: DyeColor?) = setAccessoryDyeColor(color)

    fun getRibbonTintColor(stack: ItemStack): Int {
        val defaultColor = if (stack.item == CobblemonSmartphoneItems.ROTOM_BOWTIE) DyeColor.LIGHT_BLUE else DyeColor.PINK
        val color = stack.getAccessoryDyeColor(defaultColor)
        return (0xFF shl 24) or getDyeDisplayRgb(color)
    }

    fun createColoredRibbon(color: DyeColor): ItemStack {
        val stack = ItemStack(CobblemonSmartphoneItems.ROTOM_RIBBON)
        stack.setAccessoryDyeColor(color)
        return stack
    }

    fun createColoredBowtie(color: DyeColor): ItemStack {
        val stack = ItemStack(CobblemonSmartphoneItems.ROTOM_BOWTIE)
        stack.setAccessoryDyeColor(color)
        return stack
    }

    fun createAccessoryStackFromPhone(phoneStack: ItemStack): ItemStack? {
        return when (phoneStack.getRotomAccessory()) {
            "bow" -> createColoredRibbon(phoneStack.getRibbonDyeColor() ?: DyeColor.PINK)
            "tie" -> createColoredBowtie(phoneStack.getBowtieDyeColor() ?: DyeColor.LIGHT_BLUE)
            else -> null
        }
    }

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

    fun ItemStack.saveFusedPokemon(pokemon: Pokemon, registryAccess: RegistryAccess) {
        val existingData = this.get(DataComponents.CUSTOM_DATA)
        val tag = existingData?.copyTag() ?: CompoundTag()
        val rotomTag = pokemon.saveToNBT(registryAccess)
        tag.put(ROTOM_DATA_TAG, rotomTag)
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    fun ItemStack.getFusedPokemon(registryAccess: RegistryAccess): Pokemon? {
        val customData = this.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = customData.copyTag()
        if (!tag.contains(ROTOM_DATA_TAG)) return null
        return try {
            val rotomTag = tag.getCompound(ROTOM_DATA_TAG)
            Pokemon().loadFromNBT(registryAccess, rotomTag)
        } catch (e: Exception) {
            null
        }
    }

    fun ItemStack.clearFusedPokemon() {
        val existingData = this.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = existingData.copyTag()
        tag.remove(ROTOM_DATA_TAG)
        tag.remove(ROTOM_PHONE_TAG)
        tag.remove(ROTOM_PHONE_ALT_TAG)
        tag.remove(ROTOM_ACCESSORY_TAG)
        tag.remove(ROTOM_RIBBON_COLOR_TAG)
        tag.remove(ROTOM_ACCESSORY_COLOR_TAG)
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    fun ItemStack.getRotomAccessory(): String? {
        if (!this.isRotomPhone()) return null
        val customData = this.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = customData.copyTag()
        if (!tag.contains(ROTOM_ACCESSORY_TAG)) return null
        val acc = tag.getString(ROTOM_ACCESSORY_TAG)
        return if (acc.isNotBlank()) acc else null
    }

    fun ItemStack.setRotomAccessory(accessory: String?) {
        val existingData = this.get(DataComponents.CUSTOM_DATA)
        val tag = existingData?.copyTag() ?: CompoundTag()
        if (accessory != null && accessory.isNotBlank()) {
            tag.putString(ROTOM_ACCESSORY_TAG, accessory)
        } else {
            tag.remove(ROTOM_ACCESSORY_TAG)
        }
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    enum class RotomGender {
        NONE, FEMALE, MALE
    }

    fun getRotomGender(player: Player): RotomGender {
        val phone = getEquippedRotomPhone(player) ?: return RotomGender.NONE
        return when (phone.getRotomAccessory()) {
            "bow" -> RotomGender.FEMALE
            "tie" -> RotomGender.MALE
            else -> RotomGender.NONE
        }
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
