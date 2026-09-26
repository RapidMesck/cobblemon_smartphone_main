package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.network.packet.SyncEnergyConfigPacket
import net.minecraft.client.Minecraft

object SyncEnergyConfigHandler : ClientNetworkPacketHandler<SyncEnergyConfigPacket> {
    override fun handle(packet: SyncEnergyConfigPacket, client: Minecraft) {
        val energy = CobblemonSmartphone.config.energy
        energy.enableEnergy = packet.enableEnergy
        energy.baseCapacity = packet.baseCapacity
        energy.maxReceive = packet.maxReceive
        energy.defaultCost = packet.defaultCost
        energy.costs = packet.costs
        energy.enableElectricPokemonCharging = packet.enableElectricPokemonCharging
        energy.electricPokemonChargeRate = packet.electricPokemonChargeRate
        energy.electricPokemonChargeInterval = packet.electricPokemonChargeInterval
        energy.requireElectricPokemonConscious = packet.requireElectricPokemonConscious
        energy.scaleRateWithElectricPokemonCount = packet.scaleRateWithElectricPokemonCount
        energy.chargeAllSmartphones = packet.chargeAllSmartphones
    }
}
