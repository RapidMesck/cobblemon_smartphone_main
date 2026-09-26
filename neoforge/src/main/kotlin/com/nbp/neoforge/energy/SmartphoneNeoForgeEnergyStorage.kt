package com.nbp.neoforge.energy

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.energy.receiveEnergy
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.energy.IEnergyStorage

class SmartphoneNeoForgeEnergyStorage(private val stack: ItemStack) : IEnergyStorage {
    override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
        return stack.receiveEnergy(maxReceive, simulate)
    }

    override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
        return 0
    }

    override fun getEnergyStored(): Int {
        return stack.getEnergy()
    }

    override fun getMaxEnergyStored(): Int {
        return stack.getMaxEnergy()
    }

    override fun canExtract(): Boolean {
        return false
    }

    override fun canReceive(): Boolean {
        return CobblemonSmartphone.config.energy.enableEnergy
    }
}
