package com.nbp.cobblemon_smartphone.energy

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

const val ENERGY_TAG = "cobblemon_smartphone:energy"
const val EXTRA_CAPACITY_TAG = "cobblemon_smartphone:extra_energy_capacity"

fun ItemStack.getEnergy(): Int {
    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return 0
    val tag = customData.copyTag()
    return tag.getInt(ENERGY_TAG)
}

fun ItemStack.getMaxEnergy(): Int {
    val base = CobblemonSmartphone.config.energy.baseCapacity
    return base + getExtraCapacity()
}

fun ItemStack.getExtraCapacity(): Int {
    var extra = 0
    if (this.hasUpgrade("upgrade_battery_tier1")) extra += 1000
    if (this.hasUpgrade("upgrade_battery_tier2")) extra += 5000
    if (this.hasUpgrade("upgrade_battery_tier3")) extra += 15000

    val customData = this.get(DataComponents.CUSTOM_DATA)
    if (customData != null) {
        val tag = customData.copyTag()
        if (tag.contains(EXTRA_CAPACITY_TAG, CompoundTag.TAG_INT.toInt())) {
            extra += tag.getInt(EXTRA_CAPACITY_TAG)
        }
    }
    return extra
}

fun ItemStack.setEnergy(amount: Int) {
    val max = getMaxEnergy()
    val clamped = amount.coerceIn(0, max)
    val customData = this.get(DataComponents.CUSTOM_DATA)
    val tag = customData?.copyTag() ?: CompoundTag()
    tag.putInt(ENERGY_TAG, clamped)
    this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
}

fun ItemStack.receiveEnergy(maxReceive: Int, simulate: Boolean = false, ignoreLimit: Boolean = false): Int {
    if (!CobblemonSmartphone.config.energy.enableEnergy) return 0
    val current = getEnergy()
    val max = getMaxEnergy()
    val limit = if (ignoreLimit) 0 else CobblemonSmartphone.config.energy.maxReceive
    val allowed = if (limit > 0) minOf(limit, maxReceive) else maxReceive
    val energyReceived = minOf(max - current, allowed)
    if (energyReceived <= 0) return 0
    if (!simulate) {
        setEnergy(current + energyReceived)
    }
    return energyReceived
}

fun ItemStack.extractEnergy(maxExtract: Int, simulate: Boolean): Int {
    if (!CobblemonSmartphone.config.energy.enableEnergy) return 0
    val current = getEnergy()
    val energyExtracted = minOf(current, maxExtract)
    if (energyExtracted <= 0) return 0
    if (!simulate) {
        setEnergy(current - energyExtracted)
    }
    return energyExtracted
}

fun ItemStack.hasEnoughEnergy(cost: Int): Boolean {
    if (!CobblemonSmartphone.config.energy.enableEnergy || cost <= 0) return true
    return getEnergy() >= cost
}

fun ItemStack.consumeEnergy(cost: Int): Boolean {
    if (!CobblemonSmartphone.config.energy.enableEnergy || cost <= 0) return true
    if (!hasEnoughEnergy(cost)) return false
    setEnergy(getEnergy() - cost)
    return true
}

object SmartphoneEnergyHelper {
    /**
     * Checks if the player has a smartphone with enough energy for [actionId],
     * and deducts it if present. Sends a localized feedback message if insufficient.
     * Returns true if the action is allowed to proceed.
     */
    fun checkAndConsumeEnergy(player: ServerPlayer, actionId: String): Boolean {
        if (!CobblemonSmartphone.config.energy.enableEnergy) return true
        val cost = CobblemonSmartphone.config.energy.getCost(actionId)
        if (cost <= 0) return true

        val phone = SmartphoneHelper.getSmartphone(player)
        if (phone == null) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.no_smartphone").withColor(0xfd0100),
                true
            )
            return false
        }

        if (!phone.hasEnoughEnergy(cost)) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.insufficient_energy", cost).withColor(0xfd0100),
                true
            )
            return false
        }

        phone.consumeEnergy(cost)
        return true
    }
}
