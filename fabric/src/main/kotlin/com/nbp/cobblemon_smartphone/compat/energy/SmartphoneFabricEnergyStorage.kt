package com.nbp.cobblemon_smartphone.compat.energy

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.energy.setEnergy
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext
import team.reborn.energy.api.EnergyStorage

class SmartphoneFabricEnergyStorage(private val context: ContainerItemContext) : EnergyStorage {
    override fun supportsInsertion(): Boolean {
        return CobblemonSmartphone.config.energy.enableEnergy
    }

    override fun insert(maxAmount: Long, transaction: TransactionContext): Long {
        if (!supportsInsertion() || maxAmount <= 0) return 0
        val currentStack = context.itemVariant.toStack()
        val currentEnergy = currentStack.getEnergy()
        val maxEnergy = currentStack.getMaxEnergy()
        val limit = CobblemonSmartphone.config.energy.maxReceive.toLong()
        val allowedLimit = if (limit > 0) minOf(limit, maxAmount) else maxAmount
        val energyToInsert = minOf((maxEnergy - currentEnergy).toLong(), allowedLimit)
        if (energyToInsert <= 0) return 0

        val newStack = currentStack.copy()
        newStack.setEnergy(currentEnergy + energyToInsert.toInt())
        val exchanged = context.exchange(ItemVariant.of(newStack), 1, transaction)
        return if (exchanged == 1L) energyToInsert else 0
    }

    override fun supportsExtraction(): Boolean {
        return false
    }

    override fun extract(maxAmount: Long, transaction: TransactionContext): Long {
        return 0
    }

    override fun getAmount(): Long {
        return context.itemVariant.toStack().getEnergy().toLong()
    }

    override fun getCapacity(): Long {
        return context.itemVariant.toStack().getMaxEnergy().toLong()
    }

    companion object {
        fun register() {
            EnergyStorage.ITEM.registerForItems(
                { _, context -> SmartphoneFabricEnergyStorage(context) },
                *CobblemonSmartphoneItems.all().toTypedArray()
            )
        }
    }
}
