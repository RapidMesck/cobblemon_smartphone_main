package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncEnergyConfigPacket(
    val enableEnergy: Boolean,
    val baseCapacity: Int,
    val maxReceive: Int,
    val defaultCost: Int,
    val costs: List<SmartphoneConfig.ActionCost>,
    val enableElectricPokemonCharging: Boolean,
    val electricPokemonChargeRate: Int,
    val electricPokemonChargeInterval: Int,
    val requireElectricPokemonConscious: Boolean,
    val scaleRateWithElectricPokemonCount: Boolean,
    val chargeAllSmartphones: Boolean
) : CobblemonSmartphoneNetworkPacket<SyncEnergyConfigPacket> {
    companion object {
        val ID = smartphoneResource("sync_energy_config")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncEnergyConfigPacket {
            val enableEnergy = buffer.readBoolean()
            val baseCapacity = buffer.readVarInt()
            val maxReceive = buffer.readVarInt()
            val defaultCost = buffer.readVarInt()
            val size = buffer.readVarInt()
            val costs = (0 until size).map {
                val action = buffer.readUtf()
                val cost = buffer.readVarInt()
                SmartphoneConfig.ActionCost(action, cost)
            }
            val enableElectric = buffer.readBoolean()
            val chargeRate = buffer.readVarInt()
            val chargeInterval = buffer.readVarInt()
            val requireConscious = buffer.readBoolean()
            val scaleCount = buffer.readBoolean()
            val chargeAll = buffer.readBoolean()
            return SyncEnergyConfigPacket(
                enableEnergy, baseCapacity, maxReceive, defaultCost, costs,
                enableElectric, chargeRate, chargeInterval, requireConscious, scaleCount, chargeAll
            )
        }

        fun fromServerConfig(): SyncEnergyConfigPacket {
            val energy = CobblemonSmartphone.config.energy
            return SyncEnergyConfigPacket(
                enableEnergy = energy.enableEnergy,
                baseCapacity = energy.baseCapacity,
                maxReceive = energy.maxReceive,
                defaultCost = energy.defaultCost,
                costs = energy.costs,
                enableElectricPokemonCharging = energy.enableElectricPokemonCharging,
                electricPokemonChargeRate = energy.electricPokemonChargeRate,
                electricPokemonChargeInterval = energy.electricPokemonChargeInterval,
                requireElectricPokemonConscious = energy.requireElectricPokemonConscious,
                scaleRateWithElectricPokemonCount = energy.scaleRateWithElectricPokemonCount,
                chargeAllSmartphones = energy.chargeAllSmartphones
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(enableEnergy)
        buffer.writeVarInt(baseCapacity)
        buffer.writeVarInt(maxReceive)
        buffer.writeVarInt(defaultCost)
        buffer.writeVarInt(costs.size)
        for (cost in costs) {
            buffer.writeUtf(cost.action)
            buffer.writeVarInt(cost.cost)
        }
        buffer.writeBoolean(enableElectricPokemonCharging)
        buffer.writeVarInt(electricPokemonChargeRate)
        buffer.writeVarInt(electricPokemonChargeInterval)
        buffer.writeBoolean(requireElectricPokemonConscious)
        buffer.writeBoolean(scaleRateWithElectricPokemonCount)
        buffer.writeBoolean(chargeAllSmartphones)
    }
}
