package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RotomDroneStatePacket(
    val active: Boolean,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) : CobblemonSmartphoneNetworkPacket<RotomDroneStatePacket> {
    companion object {
        val ID = smartphoneResource("rotom_drone_state")

        fun decode(buffer: RegistryFriendlyByteBuf): RotomDroneStatePacket {
            val active = buffer.readBoolean()
            val x = if (active) buffer.readDouble() else 0.0
            val y = if (active) buffer.readDouble() else 0.0
            val z = if (active) buffer.readDouble() else 0.0
            val yaw = if (active) buffer.readFloat() else 0f
            val pitch = if (active) buffer.readFloat() else 0f
            return RotomDroneStatePacket(active, x, y, z, yaw, pitch)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(active)
        if (active) {
            buffer.writeDouble(x)
            buffer.writeDouble(y)
            buffer.writeDouble(z)
            buffer.writeFloat(yaw)
            buffer.writeFloat(pitch)
        }
    }
}
