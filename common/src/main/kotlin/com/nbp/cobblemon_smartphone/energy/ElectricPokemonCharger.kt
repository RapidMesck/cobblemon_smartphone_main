package com.nbp.cobblemon_smartphone.energy

import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object ElectricPokemonCharger {
    private var tickCounter = 0

    fun tick(server: MinecraftServer) {
        val energyConfig = CobblemonSmartphone.config.energy
        if (!energyConfig.enableEnergy || !energyConfig.enableElectricPokemonCharging) {
            return
        }

        val interval = energyConfig.electricPokemonChargeInterval.coerceAtLeast(1)
        tickCounter++
        if (tickCounter % interval != 0) {
            return
        }

        val baseRate = energyConfig.electricPokemonChargeRate
        if (baseRate <= 0) {
            return
        }

        val players = server.playerList.players
        if (players.isEmpty()) {
            return
        }

        for (player in players) {
            chargePlayer(player, baseRate, energyConfig)
        }
    }

    private fun chargePlayer(
        player: ServerPlayer,
        baseRate: Int,
        config: com.nbp.cobblemon_smartphone.config.SmartphoneConfig.EnergyConfig
    ) {
        val chargeablePhones = getChargeableSmartphones(player)
        if (chargeablePhones.isEmpty()) {
            return
        }

        val electricCount = countElectricPokemon(player, config.requireElectricPokemonConscious)
        if (electricCount <= 0) {
            return
        }

        val effectiveRate = if (config.scaleRateWithElectricPokemonCount) {
            baseRate * electricCount
        } else {
            baseRate
        }

        var modified = false
        if (config.chargeAllSmartphones) {
            for (phone in chargeablePhones) {
                val received = phone.receiveEnergy(effectiveRate, simulate = false, ignoreLimit = true)
                if (received > 0) {
                    modified = true
                }
            }
        } else {
            for (phone in chargeablePhones) {
                val received = phone.receiveEnergy(effectiveRate, simulate = false, ignoreLimit = true)
                if (received > 0) {
                    modified = true
                    break
                }
            }
        }

        if (modified) {
            player.containerMenu.broadcastChanges()
            player.inventoryMenu.broadcastChanges()
        }
    }

    fun countElectricPokemon(player: ServerPlayer, requireConscious: Boolean): Int {
        var count = 0
        for (pokemon in player.party()) {
            if (requireConscious && pokemon.isFainted()) {
                continue
            }
            if (pokemon.types.contains(ElementalTypes.ELECTRIC)) {
                count++
            }
        }
        return count
    }

    private fun getChargeableSmartphones(player: ServerPlayer): List<ItemStack> {
        val result = mutableListOf<ItemStack>()

        // 1. Compat slots (Trinkets / Curios / Accessories)
        SmartphoneHelper.getSmartphoneImpl?.invoke(player)?.let { stack ->
            if (stack.item is SmartphoneItem && stack.getEnergy() < stack.getMaxEnergy() && result.none { it === stack }) {
                result.add(stack)
            }
        }

        // 2. Main inventory
        for (stack in player.inventory.items) {
            if (stack.item is SmartphoneItem && stack.getEnergy() < stack.getMaxEnergy() && result.none { it === stack }) {
                result.add(stack)
            }
        }

        // 3. Armor slots (if worn)
        for (stack in player.inventory.armor) {
            if (stack.item is SmartphoneItem && stack.getEnergy() < stack.getMaxEnergy() && result.none { it === stack }) {
                result.add(stack)
            }
        }

        // 4. Offhand
        val offhand = player.offhandItem
        if (offhand.item is SmartphoneItem && offhand.getEnergy() < offhand.getMaxEnergy() && result.none { it === offhand }) {
            result.add(offhand)
        }

        return result
    }
}
