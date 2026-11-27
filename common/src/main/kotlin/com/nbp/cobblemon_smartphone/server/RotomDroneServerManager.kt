package com.nbp.cobblemon_smartphone.server

import com.cobblemon.mod.common.util.isInBattle
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.energy.consumeEnergy
import com.nbp.cobblemon_smartphone.energy.hasEnoughEnergy
import com.nbp.cobblemon_smartphone.network.packet.RotomDroneStatePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomDroneStatePacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

object RotomDroneServerManager {

    data class ActiveDrone(
        val playerUuid: UUID,
        var dimension: ResourceKey<Level>,
        var x: Double,
        var y: Double,
        var z: Double,
        var yaw: Float,
        var pitch: Float,
        var energyTickCounter: Int = 0,
        var lastBroadcastMs: Long = 0L
    )

    private val activeDrones = ConcurrentHashMap<UUID, ActiveDrone>()

    fun clearAll() {
        activeDrones.clear()
    }

    fun isPlayerInDrone(playerUuid: UUID): Boolean = activeDrones.containsKey(playerUuid)

    fun isDimensionBlocked(dimensionId: String, blockedDimensions: List<String>): Boolean {
        return blockedDimensions.any { it.equals(dimensionId, ignoreCase = true) }
    }

    fun handleClientUpdate(player: ServerPlayer, packet: RotomDroneStatePacket) {
        if (!packet.active) {
            stopDrone(player, notifyOwner = false)
            return
        }

        val cfg = CobblemonSmartphone.config
        val rotomCfg = cfg.rotom
        if (!cfg.features.enableRotom || !rotomCfg.enableDrone) {
            forceStop(player, Component.translatable("message.cobblemon_smartphone.rotom_drone.disabled").withStyle(ChatFormatting.RED))
            return
        }

        val dimId = player.level().dimension().location().toString()
        if (isDimensionBlocked(dimId, rotomCfg.droneBlockedDimensions)) {
            forceStop(player, Component.translatable("message.cobblemon_smartphone.rotom_drone.dimension_blocked").withStyle(ChatFormatting.RED))
            return
        }

        val rotomPhone = SmartphoneHelper.getEquippedRotomPhone(player)
        if (rotomPhone == null || !player.isAlive || player.isSleeping || player.isSpectator || player.isInBattle()) {
            forceStop(player, null)
            return
        }

        if (cfg.energy.enableEnergy && !rotomCfg.infiniteEnergy && rotomCfg.droneEnergyPerSecond > 0) {
            if (!activeDrones.containsKey(player.uuid) && !rotomPhone.hasEnoughEnergy(rotomCfg.droneEnergyPerSecond)) {
                forceStop(player, Component.translatable("message.cobblemon_smartphone.not_enough_energy").withColor(0xfd0100))
                return
            }
        }

        if (!packet.x.isFinite() || !packet.y.isFinite() || !packet.z.isFinite()
            || !packet.yaw.isFinite() || !packet.pitch.isFinite()
        ) {
            forceStop(player, null)
            return
        }

        // Fairplay server-side range enforcement (with small network latency tolerance)
        val configuredRange = rotomCfg.droneMaxRange.takeIf { it.isFinite() }?.coerceIn(4.0, 256.0) ?: 32.0
        val maxAllowed = configuredRange + 2.5
        val dx = packet.x - player.x
        val dy = packet.y - player.eyeY
        val dz = packet.z - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val clampedX: Double
        val clampedY: Double
        val clampedZ: Double
        if (dist > maxAllowed + 8.0) {
            forceStop(player, Component.translatable("message.cobblemon_smartphone.rotom_drone.out_of_range").withStyle(ChatFormatting.YELLOW))
            return
        } else if (dist > maxAllowed && dist > 0.001) {
            val scale = maxAllowed / dist
            clampedX = player.x + dx * scale
            clampedY = player.eyeY + dy * scale
            clampedZ = player.z + dz * scale
        } else {
            clampedX = packet.x
            clampedY = packet.y
            clampedZ = packet.z
        }

        val now = System.currentTimeMillis()
        val existing = activeDrones[player.uuid]
        val shouldBroadcast: Boolean
        if (existing != null) {
            existing.dimension = player.level().dimension()
            existing.x = clampedX
            existing.y = clampedY
            existing.z = clampedZ
            existing.yaw = packet.yaw
            existing.pitch = packet.pitch
            // Rate-limit broadcasts to at most 25Hz per pilot to prevent packet flooding on dedicated servers
            shouldBroadcast = (now - existing.lastBroadcastMs) >= 40L
            if (shouldBroadcast) {
                existing.lastBroadcastMs = now
            }
        } else {
            activeDrones[player.uuid] = ActiveDrone(
                playerUuid = player.uuid,
                dimension = player.level().dimension(),
                x = clampedX,
                y = clampedY,
                z = clampedZ,
                yaw = packet.yaw,
                pitch = packet.pitch,
                lastBroadcastMs = now
            )
            shouldBroadcast = true
        }

        if (shouldBroadcast) {
            broadcastToNearby(
                player,
                SyncRotomDroneStatePacket(player.uuid, true, clampedX, clampedY, clampedZ, packet.yaw, packet.pitch)
            )
        }
    }

    fun onPlayerJoin(player: ServerPlayer) {
        val dim = player.level().dimension()
        for (drone in activeDrones.values) {
            if (drone.playerUuid != player.uuid && drone.dimension == dim) {
                SyncRotomDroneStatePacket(
                    drone.playerUuid,
                    true,
                    drone.x,
                    drone.y,
                    drone.z,
                    drone.yaw,
                    drone.pitch
                ).sendToPlayer(player)
            }
        }
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        stopDrone(player, notifyOwner = false)
    }

    fun tick(server: MinecraftServer) {
        if (activeDrones.isEmpty()) return

        val cfg = CobblemonSmartphone.config
        val rotomCfg = cfg.rotom
        val checkEnergy = cfg.energy.enableEnergy && !rotomCfg.infiniteEnergy && rotomCfg.droneEnergyPerSecond > 0

        val iterator = activeDrones.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, drone) = iterator.next()
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                iterator.remove()
                continue
            }

            val dimId = player.level().dimension().location().toString()
            val rotomPhone = SmartphoneHelper.getEquippedRotomPhone(player)

            val invalid = !cfg.features.enableRotom
                || !rotomCfg.enableDrone
                || rotomPhone == null
                || !player.isAlive
                || player.isSleeping
                || player.isSpectator
                || player.isInBattle()
                || player.level().dimension() != drone.dimension
                || isDimensionBlocked(dimId, rotomCfg.droneBlockedDimensions)

            if (invalid) {
                iterator.remove()
                SyncRotomDroneStatePacket(uuid, false).sendToPlayer(player)
                broadcastToAllExcept(server, uuid, SyncRotomDroneStatePacket(uuid, false))
                continue
            }

            if (checkEnergy) {
                drone.energyTickCounter++
                if (drone.energyTickCounter >= 20) {
                    drone.energyTickCounter = 0
                    if (!rotomPhone!!.consumeEnergy(rotomCfg.droneEnergyPerSecond)) {
                        iterator.remove()
                        player.displayClientMessage(
                            Component.translatable("message.cobblemon_smartphone.not_enough_energy").withColor(0xfd0100),
                            true
                        )
                        SyncRotomDroneStatePacket(uuid, false).sendToPlayer(player)
                        broadcastToAllExcept(server, uuid, SyncRotomDroneStatePacket(uuid, false))
                    }
                }
            }
        }
    }

    private fun stopDrone(player: ServerPlayer, notifyOwner: Boolean) {
        val removed = activeDrones.remove(player.uuid)
        if (removed != null || notifyOwner) {
            val stopPacket = SyncRotomDroneStatePacket(player.uuid, false)
            if (notifyOwner) {
                stopPacket.sendToPlayer(player)
            }
            broadcastToAllExcept(player.server, player.uuid, stopPacket)
        }
    }

    private fun forceStop(player: ServerPlayer, message: Component?) {
        activeDrones.remove(player.uuid)
        if (message != null) {
            player.displayClientMessage(message, true)
        }
        val stopPacket = SyncRotomDroneStatePacket(player.uuid, false)
        stopPacket.sendToPlayer(player)
        broadcastToAllExcept(player.server, player.uuid, stopPacket)
    }

    private fun broadcastToNearby(owner: ServerPlayer, packet: SyncRotomDroneStatePacket) {
        val dim = owner.level().dimension()
        val maxDistSqr = 128.0 * 128.0
        for (other in owner.server.playerList.players) {
            if (other.uuid != owner.uuid && other.level().dimension() == dim && other.distanceToSqr(owner) <= maxDistSqr) {
                packet.sendToPlayer(other)
            }
        }
    }

    private fun broadcastToAllExcept(server: MinecraftServer, ownerUuid: UUID, packet: SyncRotomDroneStatePacket) {
        for (other in server.playerList.players) {
            if (other.uuid != ownerUuid) {
                packet.sendToPlayer(other)
            }
        }
    }
}
