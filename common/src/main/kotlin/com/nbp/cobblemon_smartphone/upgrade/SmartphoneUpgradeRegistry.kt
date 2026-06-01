package com.nbp.cobblemon_smartphone.upgrade

import net.minecraft.world.item.ItemStack

object SmartphoneUpgradeRegistry {
    private val upgrades = mutableMapOf<String, SmartphoneUpgrade>()

    fun register(upgrade: SmartphoneUpgrade) {
        upgrades[upgrade.id] = upgrade
    }

    fun getUpgrade(id: String): SmartphoneUpgrade? = upgrades[id]

    fun getUpgradeByNbtKey(nbtKey: String): SmartphoneUpgrade? =
        upgrades.values.firstOrNull { it.nbtKey == nbtKey }

    fun getAllUpgrades(): Collection<SmartphoneUpgrade> = upgrades.values

    fun getInstalledUpgrades(stack: ItemStack): List<SmartphoneUpgrade> =
        upgrades.values.filter { stack.hasUpgrade(it.nbtKey) }
}
