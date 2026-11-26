package com.nbp.cobblemon_smartphone.upgrade

import com.nbp.cobblemon_smartphone.energy.EXTRA_CAPACITY_TAG
import com.nbp.cobblemon_smartphone.item.BatteryUpgradeItem
import com.nbp.cobblemon_smartphone.item.SdCardItem
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

private const val UPGRADES_TAG = "cobblemon_smartphone:upgrades"
const val SD_CARD_UPGRADE_TAG = "cobblemon_smartphone:sd_card_upgrade"
const val SD_CARD_UPGRADE_SHORT_TAG = "upgrade"
const val BATTERY_UPGRADE_TAG = "cobblemon_smartphone:battery_upgrade"
const val BATTERY_UPGRADE_SHORT_TAG = "battery_upgrade"

fun ItemStack.hasUpgrade(nbtKey: String): Boolean {
    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return false
    val tag = customData.copyTag()
    if (!tag.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND.toInt())) return false
    val upgrades = tag.getCompound(UPGRADES_TAG)
    return upgrades.getBoolean(nbtKey)
}

fun ItemStack.addUpgrade(nbtKey: String) {
    val existingData = this.get(DataComponents.CUSTOM_DATA)
    val tag = existingData?.copyTag() ?: CompoundTag()
    val upgrades = if (tag.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND.toInt())) {
        tag.getCompound(UPGRADES_TAG)
    } else {
        CompoundTag()
    }
    upgrades.putBoolean(nbtKey, true)
    tag.put(UPGRADES_TAG, upgrades)
    this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
}

fun ItemStack.isSmartphone(): Boolean = this.item is SmartphoneItem

fun ItemStack.getSdCardUpgrade(): String? {
    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return null
    val tag = customData.copyTag()
    val raw = when {
        tag.contains(SD_CARD_UPGRADE_TAG, CompoundTag.TAG_STRING.toInt()) -> tag.getString(SD_CARD_UPGRADE_TAG)
        tag.contains(SD_CARD_UPGRADE_SHORT_TAG, CompoundTag.TAG_STRING.toInt()) -> tag.getString(SD_CARD_UPGRADE_SHORT_TAG)
        else -> null
    }
    return raw?.trim()?.takeIf { it.isNotEmpty() }
}

fun ItemStack.setSdCardUpgrade(upgradeKey: String) {
    val trimmed = upgradeKey.trim()
    val existingData = this.get(DataComponents.CUSTOM_DATA)
    val tag = existingData?.copyTag() ?: CompoundTag()
    if (trimmed.isEmpty()) {
        tag.remove(SD_CARD_UPGRADE_TAG)
        tag.remove(SD_CARD_UPGRADE_SHORT_TAG)
    } else {
        tag.putString(SD_CARD_UPGRADE_TAG, trimmed)
        tag.remove(SD_CARD_UPGRADE_SHORT_TAG)
    }
    if (tag.isEmpty) {
        this.remove(DataComponents.CUSTOM_DATA)
    } else {
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }
}

fun ItemStack.isSdCardFor(upgradeKey: String): Boolean =
    this.item is SdCardItem && this.getSdCardUpgrade() == upgradeKey

fun ItemStack.getCustomBatteryUpgradeKey(): String? {
    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return null
    val tag = customData.copyTag()
    val raw = when {
        tag.contains(BATTERY_UPGRADE_TAG, CompoundTag.TAG_STRING.toInt()) -> tag.getString(BATTERY_UPGRADE_TAG)
        tag.contains(BATTERY_UPGRADE_SHORT_TAG, CompoundTag.TAG_STRING.toInt()) -> tag.getString(BATTERY_UPGRADE_SHORT_TAG)
        else -> null
    }
    return raw?.trim()?.takeIf { it.isNotEmpty() }
}

fun ItemStack.getBatteryUpgradeKey(): String? {
    val batteryItem = this.item as? BatteryUpgradeItem ?: return null
    return this.getCustomBatteryUpgradeKey() ?: batteryItem.upgradeKey
}

fun ItemStack.getBatteryExtraCapacity(): Int {
    val batteryItem = this.item as? BatteryUpgradeItem ?: return 0
    val customData = this.get(DataComponents.CUSTOM_DATA)
    if (customData != null) {
        val tag = customData.copyTag()
        if (tag.contains(EXTRA_CAPACITY_TAG, CompoundTag.TAG_INT.toInt())) {
            val customCap = tag.getInt(EXTRA_CAPACITY_TAG)
            if (customCap > 0) return customCap
        }
    }
    return batteryItem.bonusCapacity
}

fun ItemStack.addExtraEnergyCapacity(extra: Int) {
    if (extra <= 0) return
    val existingData = this.get(DataComponents.CUSTOM_DATA)
    val tag = existingData?.copyTag() ?: CompoundTag()
    val current = tag.getInt(EXTRA_CAPACITY_TAG).coerceAtLeast(0)
    val updated = (current.toLong() + extra.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    tag.putInt(EXTRA_CAPACITY_TAG, updated)
    this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
}

fun ItemStack.isBatteryUpgradeFor(upgradeKey: String): Boolean {
    return this.item is BatteryUpgradeItem && this.getBatteryUpgradeKey() == upgradeKey
}

val BATTERY_UPGRADE_KEYS = setOf(
    "upgrade_battery_tier1",
    "upgrade_battery_tier2",
    "upgrade_battery_tier3"
)

fun isBatteryUpgradeKey(upgradeKey: String): Boolean = upgradeKey in BATTERY_UPGRADE_KEYS

fun createSdCardStack(upgradeKey: String): ItemStack {
    val stack = ItemStack(CobblemonSmartphoneItems.SD_CARD)
    stack.setSdCardUpgrade(upgradeKey)
    return stack
}
