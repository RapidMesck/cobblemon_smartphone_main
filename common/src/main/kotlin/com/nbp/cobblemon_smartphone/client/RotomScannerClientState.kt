package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.network.packet.RotomScanCompletePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomConfigPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RotomScannerClientState {

    data class ClientRotomConfig(
        var enableScanner: Boolean = true,
        var enableAutoScanner: Boolean = true,
        var enableManualScanPriority: Boolean = true,
        var enableSocialInteractions: Boolean = true,
        var allowScanAlreadyRegistered: Boolean = true,
        var requireLineOfSight: Boolean = true,
        var revealIvPercentage: Boolean = true,
        var revealNature: Boolean = true,
        var scanRadius: Double = 10.0,
        var scanDurationTicks: Int = 50,
        var approachTicks: Int = 38,
        var returnTicks: Int = 28,
        var infiniteEnergy: Boolean = true,
        var enableSocialDataShare: Boolean = true,
        var socialDataShareChance: Double = 0.35,
        var curiosityLanguage: String = "en"
    )

    var clientConfig: ClientRotomConfig = ClientRotomConfig()
        private set

    fun updateConfig(packet: SyncRotomConfigPacket) {
        clientConfig = ClientRotomConfig(
            enableScanner = packet.enableScanner,
            enableAutoScanner = packet.enableAutoScanner,
            enableManualScanPriority = packet.enableManualScanPriority,
            enableSocialInteractions = packet.enableSocialInteractions,
            allowScanAlreadyRegistered = packet.allowScanAlreadyRegistered,
            requireLineOfSight = packet.requireLineOfSight,
            revealIvPercentage = packet.revealIvPercentage,
            revealNature = packet.revealNature,
            scanRadius = packet.scanRadius,
            scanDurationTicks = packet.scanDurationTicks,
            approachTicks = packet.approachTicks,
            returnTicks = packet.returnTicks,
            infiniteEnergy = packet.infiniteEnergy,
            enableSocialDataShare = packet.enableSocialDataShare,
            socialDataShareChance = packet.socialDataShareChance,
            curiosityLanguage = packet.curiosityLanguage
        )
    }

    data class ActiveScan(
        val scannerPlayerUuid: UUID,
        var targetEntityId: Int,
        var phase: Int, // 0 = APPROACHING, 1 = SCANNING, 2 = RETURNING, -1 = IDLE
        var progressTicks: Int,
        var totalTicks: Int,
        var isPrioritized: Boolean,
        var isAlreadyRegistered: Boolean,
        var lastUpdateTime: Long = System.currentTimeMillis()
    )

    private val activeScans = ConcurrentHashMap<UUID, ActiveScan>()

    fun getActiveScans(): Collection<ActiveScan> = activeScans.values

    fun getScanFor(scannerUuid: UUID): ActiveScan? = activeScans[scannerUuid]

    fun isScanningOrReporting(scannerUuid: UUID): Boolean {
        val scan = activeScans[scannerUuid]
        val isScanning = scan != null && scan.phase != SyncRotomScanPacket.PHASE_IDLE
        val isReporting = RotomDialogueClientState.shouldRotomFlyInFront(scannerUuid)
        return isScanning || isReporting
    }

    fun updateScanState(packet: SyncRotomScanPacket) {
        if (packet.targetEntityId == -1 || packet.phase == SyncRotomScanPacket.PHASE_IDLE) {
            activeScans.remove(packet.scannerPlayerUuid)
        } else {
            val existing = activeScans[packet.scannerPlayerUuid]
            if (existing != null && existing.targetEntityId == packet.targetEntityId) {
                existing.phase = packet.phase
                existing.progressTicks = packet.progressTicks
                existing.totalTicks = packet.totalTicks
                existing.isPrioritized = packet.isPrioritized
                existing.isAlreadyRegistered = packet.isAlreadyRegistered
                existing.lastUpdateTime = System.currentTimeMillis()
            } else {
                activeScans[packet.scannerPlayerUuid] = ActiveScan(
                    scannerPlayerUuid = packet.scannerPlayerUuid,
                    targetEntityId = packet.targetEntityId,
                    phase = packet.phase,
                    progressTicks = packet.progressTicks,
                    totalTicks = packet.totalTicks,
                    isPrioritized = packet.isPrioritized,
                    isAlreadyRegistered = packet.isAlreadyRegistered,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun onScanComplete(packet: RotomScanCompletePacket) {
        val existing = activeScans[packet.scannerPlayerUuid]
        if (existing != null) {
            existing.phase = SyncRotomScanPacket.PHASE_RETURNING
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        // Audio feedback
        if (packet.isAlreadyRegistered) {
            player.playSound(CobblemonSounds.POKEDEX_SCAN_DETAIL, 0.7f, 1.0f)
        } else {
            player.playSound(CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON, 0.8f, 1.0f)
        }

        // Particle burst (blue/cyan) at entity position
        val entity = level.getEntity(packet.targetEntityId)
        if (entity != null) {
            val cyanDust = DustParticleOptions(Vector3f(0.0f, 0.95f, 1.0f), 1.5f)
            val electricDust = DustParticleOptions(Vector3f(0.2f, 0.75f, 1.0f), 1.2f)
            val midY = entity.y + entity.bbHeight * 0.5

            for (i in 0 until 35) {
                val angle = Math.random() * Math.PI * 2
                val speed = 0.08 + Math.random() * 0.12
                val vx = Math.cos(angle) * speed
                val vz = Math.sin(angle) * speed
                val vy = (Math.random() - 0.5) * 0.1

                level.addParticle(
                    if (i % 2 == 0) cyanDust else electricDust,
                    entity.x + (Math.random() - 0.5) * entity.bbWidth,
                    midY + (Math.random() - 0.5) * (entity.bbHeight * 0.5),
                    entity.z + (Math.random() - 0.5) * entity.bbWidth,
                    vx, vy, vz
                )
            }
            for (i in 0 until 12) {
                level.addParticle(
                    ParticleTypes.INSTANT_EFFECT,
                    entity.x + (Math.random() - 0.5) * entity.bbWidth,
                    midY + (Math.random() - 0.5) * (entity.bbHeight * 0.5),
                    entity.z + (Math.random() - 0.5) * entity.bbWidth,
                    0.0, 0.05, 0.0
                )
            }
        }
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        val localScan = activeScans[player.uuid]
        if (localScan != null) {
            // Scan loop sound and scan perimeter particles only during active SCANNING phase
            if (localScan.phase == SyncRotomScanPacket.PHASE_SCANNING) {
                if (player.tickCount % 10 == 0) {
                    player.playSound(CobblemonSounds.POKEDEX_SCAN_LOOP, 0.35f, 1.15f)
                }

                // Client-side blue particles around the target
                val target = level.getEntity(localScan.targetEntityId)
                if (target != null && target.isAlive) {
                    val sweep = (Math.sin(player.tickCount * 0.25) * 0.5 + 0.5)
                    val py = target.y + target.bbHeight * sweep
                    val angle = (player.tickCount * 0.35) % (Math.PI * 2)
                    val radius = target.bbWidth * 0.75
                    val px = target.x + Math.cos(angle) * radius
                    val pz = target.z + Math.sin(angle) * radius

                    level.addParticle(
                        DustParticleOptions(Vector3f(0.0f, 0.85f, 1.0f), 1.0f),
                        px, py, pz,
                        0.0, 0.02, 0.0
                    )
                }
            }
        }
    }
}
