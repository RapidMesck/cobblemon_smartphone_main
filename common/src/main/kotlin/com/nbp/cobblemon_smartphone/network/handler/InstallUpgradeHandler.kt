package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.InstallUpgradePacket
import com.nbp.cobblemon_smartphone.network.packet.InstallUpgradeResultPacket
import com.nbp.cobblemon_smartphone.upgrade.addExtraEnergyCapacity
import com.nbp.cobblemon_smartphone.upgrade.addUpgrade
import com.nbp.cobblemon_smartphone.upgrade.getBatteryExtraCapacity
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.upgrade.isBatteryUpgradeFor
import com.nbp.cobblemon_smartphone.upgrade.isBatteryUpgradeKey
import com.nbp.cobblemon_smartphone.upgrade.isSdCardFor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object InstallUpgradeHandler : ServerNetworkPacketHandler<InstallUpgradePacket> {

    enum class InstallOutcome(val success: Boolean, val messageKey: String) {
        INSTALLED(true, "message.cobblemon_smartphone.app_store.installed"),
        BATTERY_INSTALLED(true, "message.cobblemon_smartphone.battery.installed"),
        INVALID_UPGRADE(false, "message.cobblemon_smartphone.app_store.invalid_upgrade"),
        NO_SMARTPHONE(false, "message.cobblemon_smartphone.app_store.no_smartphone"),
        ALREADY_INSTALLED(false, "message.cobblemon_smartphone.app_store.already_installed"),
        BATTERY_ALREADY_INSTALLED(false, "message.cobblemon_smartphone.battery.already_installed"),
        MISSING_SD_CARD(false, "message.cobblemon_smartphone.app_store.missing_sd_card"),
        MISSING_BATTERY_ITEM(false, "message.cobblemon_smartphone.battery.missing_item")
    }

    override fun handle(packet: InstallUpgradePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            val upgradeKey = packet.upgradeKey.trim()
            if (upgradeKey.isEmpty() || upgradeKey.length > 128) {
                InstallUpgradeResultPacket(upgradeKey, false, InstallOutcome.INVALID_UPGRADE.messageKey).sendToPlayer(player)
                return@execute
            }

            val matchingBattery = findMatchingBatteryItem(player, upgradeKey)
            val isBuiltInBattery = isBatteryUpgradeKey(upgradeKey)
            val isBattery = isBuiltInBattery || matchingBattery != null

            if (!isBattery && !com.nbp.cobblemon_smartphone.CobblemonSmartphone.config.features.enableAppStore) {
                InstallUpgradeResultPacket(upgradeKey, false, InstallOutcome.INVALID_UPGRADE.messageKey).sendToPlayer(player)
                return@execute
            }

            val smartphone = resolveTargetSmartphone(player)
            if (smartphone == null) {
                InstallUpgradeResultPacket(upgradeKey, false, InstallOutcome.NO_SMARTPHONE.messageKey).sendToPlayer(player)
                return@execute
            }

            if (smartphone.hasUpgrade(upgradeKey)) {
                val alreadyKey = if (isBattery) {
                    InstallOutcome.BATTERY_ALREADY_INSTALLED.messageKey
                } else {
                    InstallOutcome.ALREADY_INSTALLED.messageKey
                }
                InstallUpgradeResultPacket(upgradeKey, false, alreadyKey).sendToPlayer(player)
                return@execute
            }

            val upgradeItem = if (isBattery) {
                matchingBattery
            } else {
                findMatchingSdCard(player, upgradeKey)
            }
            if (upgradeItem == null) {
                val missingKey = if (isBattery) {
                    InstallOutcome.MISSING_BATTERY_ITEM.messageKey
                } else {
                    InstallOutcome.MISSING_SD_CARD.messageKey
                }
                InstallUpgradeResultPacket(upgradeKey, false, missingKey).sendToPlayer(player)
                return@execute
            }

            // Custom datapack batteries add their capacity via EXTRA_CAPACITY_TAG
            // (built-in tiers already get +1000/+5000/+15000 from SmartphoneEnergyHelper.getExtraCapacity)
            val addedCapacity = if (isBattery && !isBuiltInBattery) {
                upgradeItem.getBatteryExtraCapacity()
            } else {
                0
            }

            if (!player.abilities.instabuild) {
                upgradeItem.shrink(1)
            }

            smartphone.addUpgrade(upgradeKey)
            if (addedCapacity > 0) {
                smartphone.addExtraEnergyCapacity(addedCapacity)
            }
            player.inventory.setChanged()
            player.inventoryMenu.broadcastChanges()
            player.containerMenu.broadcastChanges()

            player.level().playSound(
                null as Player?,
                player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.7f,
                1.25f
            )

            val successKey = if (isBattery) {
                InstallOutcome.BATTERY_INSTALLED.messageKey
            } else {
                InstallOutcome.INSTALLED.messageKey
            }
            InstallUpgradeResultPacket(upgradeKey, true, successKey, addedCapacity).sendToPlayer(player)
        }
    }

    internal fun resolveTargetSmartphone(player: Player): ItemStack? {
        val main = player.mainHandItem
        if (main.item is SmartphoneItem) return main
        val off = player.offhandItem
        if (off.item is SmartphoneItem) return off
        val compat = SmartphoneHelper.getSmartphoneImpl?.invoke(player)
        if (compat != null && compat.item is SmartphoneItem) return compat
        return player.inventory.items.firstOrNull { it.item is SmartphoneItem }
    }

    internal fun findMatchingSdCard(player: Player, upgradeKey: String): ItemStack? {
        player.inventory.items.firstOrNull { it.isSdCardFor(upgradeKey) }?.let { return it }
        if (player.offhandItem.isSdCardFor(upgradeKey)) return player.offhandItem
        return null
    }

    internal fun findMatchingBatteryItem(player: Player, upgradeKey: String): ItemStack? {
        player.inventory.items.firstOrNull { it.isBatteryUpgradeFor(upgradeKey) }?.let { return it }
        if (player.offhandItem.isBatteryUpgradeFor(upgradeKey)) return player.offhandItem
        return null
    }
}
