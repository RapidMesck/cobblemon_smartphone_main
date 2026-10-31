package com.nbp.cobblemon_smartphone.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokedex.scanning.PokemonScannedEvent
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.PokedexLearnedInformation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.pokedex.ServerConfirmedRegisterPacket
import com.cobblemon.mod.common.pokedex.scanner.ScannableEntity
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.energy.consumeEnergy
import com.nbp.cobblemon_smartphone.energy.hasEnoughEnergy
import com.nbp.cobblemon_smartphone.network.packet.RotomScanCompletePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import com.nbp.cobblemon_smartphone.util.RotomScannerHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RotomAutoScannerManager {
    enum class ScanPhase(val id: Int) {
        IDLE(SyncRotomScanPacket.PHASE_IDLE),
        APPROACHING(SyncRotomScanPacket.PHASE_APPROACHING),
        SCANNING(SyncRotomScanPacket.PHASE_SCANNING),
        RETURNING(SyncRotomScanPacket.PHASE_RETURNING)
    }

    const val DEFAULT_SCAN_DISTANCE = 10.0
    const val DEFAULT_SCAN_DISTANCE_SQR = 100.0 // 10 blocks radius
    const val DEFAULT_APPROACH_TICKS = 18 // ~0.9s flight arrival before scanning starts
    const val DEFAULT_SCAN_DURATION_TICKS = 50 // 2.5 seconds at 20 ticks per second
    const val DEFAULT_RETURN_TICKS = 16 // ~0.8s flight back to player's shoulder
    const val DEFAULT_COOLDOWN_SECONDS = 90

    data class PlayerScanSession(
        val playerUuid: UUID,
        var targetEntityId: Int = -1,
        var phase: ScanPhase = ScanPhase.IDLE,
        var phaseTicks: Int = 0,
        var prioritizedEntityId: Int? = null,
        var isAlreadyRegistered: Boolean = false
    )

    private val playerSessions = ConcurrentHashMap<UUID, PlayerScanSession>()
    private val scanCooldowns = ConcurrentHashMap<String, Long>() // key = "playerUuid:entityId", value = timestamp

    fun setPlayerPriorityTarget(player: ServerPlayer, target: PokemonEntity) {
        if (!CobblemonSmartphone.config.features.enableScanner) return
        val rotomConfig = CobblemonSmartphone.config.rotom

        if (!rotomConfig.enableManualScanPriority) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_scan.manual_disabled")
                    .withStyle(ChatFormatting.RED),
                true
            )
            return
        }

        if (player.distanceToSqr(target) > rotomConfig.scanRadiusSqr) return
        if (rotomConfig.requireLineOfSight && !player.hasLineOfSight(target)) return

        val dex = Cobblemon.playerDataManager.getPokedexData(player)
        val scannable = target as? ScannableEntity
        val pokeData = scannable?.resolvePokemonScan()
        val isUnreg = RotomScannerHelper.isUnregisteredOrNewFor(dex, target)

        if (!isUnreg && !rotomConfig.allowScanAlreadyRegistered) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_scan.already_registered_disabled")
                    .withStyle(ChatFormatting.YELLOW),
                true
            )
            return
        }

        val rotomPhone = SmartphoneHelper.getEquippedRotomPhone(player)
        if (CobblemonSmartphone.config.energy.enableEnergy && rotomConfig.scanEnergyCost > 0) {
            if (rotomPhone == null || !rotomPhone.hasEnoughEnergy(rotomConfig.scanEnergyCost)) {
                player.displayClientMessage(
                    Component.translatable("message.cobblemon_smartphone.not_enough_energy").withColor(0xfd0100),
                    true
                )
                return
            }
        }

        val session = playerSessions.computeIfAbsent(player.uuid) { PlayerScanSession(player.uuid) }
        session.prioritizedEntityId = target.id
        session.targetEntityId = target.id
        session.phase = ScanPhase.APPROACHING
        session.phaseTicks = 0
        session.isAlreadyRegistered = !isUnreg

        val key = if (isUnreg) "message.cobblemon_smartphone.rotom_scan.target_locked" else "message.cobblemon_smartphone.rotom_scan.target_already_registered"
        player.displayClientMessage(
            Component.translatable(key, target.pokemon.species.name).withStyle(ChatFormatting.AQUA),
            true
        )

        broadcastScanSync(player, session)
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        playerSessions.remove(player.uuid)
        val prefix = "${player.uuid}:"
        scanCooldowns.keys.removeIf { it.startsWith(prefix) }
    }

    fun tick(server: MinecraftServer) {
        if (!CobblemonSmartphone.config.features.enableScanner) return
        val rotomConfig = CobblemonSmartphone.config.rotom

        val now = System.currentTimeMillis()
        if (server.tickCount % 100 == 0) {
            scanCooldowns.entries.removeIf { it.value < now }
        }

        val approachTicks = rotomConfig.approachTicks.coerceAtLeast(1)
        val scanDurationTicks = rotomConfig.scanDurationTicks.coerceAtLeast(10)
        val returnTicks = rotomConfig.returnTicks.coerceAtLeast(1)

        for (player in server.playerList.players) {
            val session = playerSessions.computeIfAbsent(player.uuid) { PlayerScanSession(player.uuid) }

            // 1. Check if the player has an equipped or inventory Rotom Phone
            val rotomPhone = SmartphoneHelper.getEquippedRotomPhone(player)
            if (rotomPhone == null || !player.isAlive || player.isSpectator) {
                if (session.targetEntityId != -1 || session.phase != ScanPhase.IDLE) {
                    session.targetEntityId = -1
                    session.phase = ScanPhase.IDLE
                    session.phaseTicks = 0
                    session.prioritizedEntityId = null
                    broadcastScanSync(player, session)
                }
                continue
            }

            // Check energy if enabled
            if (CobblemonSmartphone.config.energy.enableEnergy && rotomConfig.scanEnergyCost > 0) {
                if (!rotomPhone.hasEnoughEnergy(rotomConfig.scanEnergyCost)) {
                    if (session.targetEntityId != -1 && session.phase != ScanPhase.IDLE) {
                        session.phase = ScanPhase.RETURNING
                        session.phaseTicks = 0
                        broadcastScanSync(player, session)
                    }
                    continue
                }
            }

            // 2. Handle returning phase (Rotom flying back to player's shoulder)
            if (session.phase == ScanPhase.RETURNING) {
                session.phaseTicks++
                if (session.phaseTicks % 4 == 0) {
                    broadcastScanSync(player, session)
                }
                if (session.phaseTicks >= returnTicks) {
                    session.phase = ScanPhase.IDLE
                    session.targetEntityId = -1
                    session.phaseTicks = 0
                    session.prioritizedEntityId = null
                    broadcastScanSync(player, session)
                }
                continue
            }

            val config = RotomServerManager.getPlayerConfig(player.uuid) ?: RotomRadarConfig()

            // 3. Check if prioritized entity is active
            if (session.prioritizedEntityId != null) {
                val target = player.serverLevel().getEntity(session.prioritizedEntityId!!) as? PokemonEntity
                val isValid = target != null &&
                    target.isAlive &&
                    player.distanceToSqr(target) <= rotomConfig.scanRadiusSqr &&
                    (!rotomConfig.requireLineOfSight || player.hasLineOfSight(target))

                if (!isValid) {
                    player.displayClientMessage(
                        Component.translatable("message.cobblemon_smartphone.rotom_scan.target_lost")
                            .withStyle(ChatFormatting.RED),
                        true
                    )
                    session.prioritizedEntityId = null
                    session.phase = ScanPhase.RETURNING
                    session.phaseTicks = 0
                    broadcastScanSync(player, session)
                    continue
                }

                if (session.targetEntityId != target.id || session.phase == ScanPhase.IDLE) {
                    session.targetEntityId = target.id
                    session.phase = ScanPhase.APPROACHING
                    session.phaseTicks = 0
                    broadcastScanSync(player, session)
                }
            } else {
                // If auto-scan is turned off on server or in player's radar settings:
                if (!rotomConfig.enableAutoScanner || !config.autoScanPokedex) {
                    if (session.targetEntityId != -1 && session.phase != ScanPhase.IDLE) {
                        session.phase = ScanPhase.RETURNING
                        session.phaseTicks = 0
                        broadcastScanSync(player, session)
                    }
                    continue
                }

                // Validate current target
                if (session.targetEntityId != -1 && session.phase != ScanPhase.IDLE) {
                    val current = player.serverLevel().getEntity(session.targetEntityId) as? PokemonEntity
                    val dex = Cobblemon.playerDataManager.getPokedexData(player)
                    val isValid = current != null &&
                        current.isAlive &&
                        !current.pokemon.isFainted() &&
                        player.distanceToSqr(current) <= rotomConfig.scanRadiusSqr &&
                        (!rotomConfig.requireLineOfSight || player.hasLineOfSight(current)) &&
                        RotomScannerHelper.isUnregisteredOrNewFor(dex, current)

                    if (!isValid) {
                        session.phase = ScanPhase.RETURNING
                        session.phaseTicks = 0
                        broadcastScanSync(player, session)
                        continue
                    }
                }

                // If no active target, search within scanRadius for unregistered Pokémon
                if (session.targetEntityId == -1 || session.phase == ScanPhase.IDLE) {
                    val dex = Cobblemon.playerDataManager.getPokedexData(player)
                    val aabb = player.boundingBox.inflate(rotomConfig.scanRadius)
                    val candidates = player.serverLevel().getEntitiesOfClass(PokemonEntity::class.java, aabb) { entity ->
                        entity.isAlive &&
                            !entity.pokemon.isFainted() &&
                            player.distanceToSqr(entity) <= rotomConfig.scanRadiusSqr &&
                            (scanCooldowns["${player.uuid}:${entity.id}"] ?: 0L) <= now &&
                            (!rotomConfig.requireLineOfSight || player.hasLineOfSight(entity)) &&
                            RotomScannerHelper.isUnregisteredOrNewFor(dex, entity)
                    }

                    if (candidates.isNotEmpty()) {
                        // Check if any candidate matches the player's radar target species
                        val matchingRadarTarget = candidates.firstOrNull { entity ->
                            config.targets.any { t ->
                                t.enabled && t.species.isNotBlank() && t.species != "any" &&
                                    (t.species.equals(entity.pokemon.species.name, ignoreCase = true) ||
                                     t.species.equals(entity.pokemon.species.resourceIdentifier.path, ignoreCase = true))
                            }
                        }

                        val selected = matchingRadarTarget ?: candidates.minByOrNull { it.distanceToSqr(player) }
                        if (selected != null) {
                            session.targetEntityId = selected.id
                            session.phase = ScanPhase.APPROACHING
                            session.phaseTicks = 0
                            session.isAlreadyRegistered = false
                            broadcastScanSync(player, session)
                        }
                    }
                }
            }

            // 4. Progress active approach and scanning phases
            if (session.targetEntityId != -1 && session.phase != ScanPhase.IDLE && session.phase != ScanPhase.RETURNING) {
                val entity = player.serverLevel().getEntity(session.targetEntityId) as? PokemonEntity
                if (entity == null || !entity.isAlive) {
                    session.phase = ScanPhase.RETURNING
                    session.phaseTicks = 0
                    session.prioritizedEntityId = null
                    broadcastScanSync(player, session)
                    continue
                }

                session.phaseTicks++

                if (session.phase == ScanPhase.APPROACHING) {
                    // Sync periodically during flight arrival
                    if (session.phaseTicks == 1 || session.phaseTicks % 4 == 0) {
                        broadcastScanSync(player, session)
                    }

                    // Once arrival flight completes, begin active scan!
                    if (session.phaseTicks >= approachTicks) {
                        session.phase = ScanPhase.SCANNING
                        session.phaseTicks = 0
                        broadcastScanSync(player, session)
                    }
                } else if (session.phase == ScanPhase.SCANNING) {
                    // Spawn blue scan elements (cyan dust particles) around the Pokémon during active scan
                    if (player.serverLevel().players().any { it.distanceToSqr(entity) <= 256.0 }) {
                        val sweep = (Math.sin((player.tickCount + entity.id) * 0.25) * 0.5 + 0.5)
                        val py = entity.y + entity.bbHeight * sweep
                        val angle = ((player.tickCount * 0.3) % (Math.PI * 2))
                        val radius = entity.bbWidth * 0.75
                        val px = entity.x + Math.cos(angle) * radius
                        val pz = entity.z + Math.sin(angle) * radius

                        player.serverLevel().sendParticles(
                            DustParticleOptions(Vector3f(0.0f, 0.85f, 1.0f), 1.0f),
                            px, py, pz,
                            1, 0.01, 0.01, 0.01, 0.0
                        )
                    }

                    // Sync periodically
                    if (session.phaseTicks == 1 || session.phaseTicks % 5 == 0) {
                        broadcastScanSync(player, session)
                    }

                    // Scan Complete
                    if (session.phaseTicks >= scanDurationTicks) {
                        completeScan(player, session, entity)
                    }
                }
            }
        }
    }

    private fun completeScan(player: ServerPlayer, session: PlayerScanSession, entity: PokemonEntity) {
        val rotomConfig = CobblemonSmartphone.config.rotom
        val pokemon = entity.pokemon
        val dex = Cobblemon.playerDataManager.getPokedexData(player)
        val scannable = entity as? ScannableEntity
        val pokeData = scannable?.resolvePokemonScan()
        val isUnregistered = RotomScannerHelper.isUnregisteredOrNewFor(dex, entity)

        val ivTotal = pokemon.ivs.getEffectiveBattleTotal()
        val ivPercent = (ivTotal * 100) / 186
        val level = pokemon.level
        val nature = pokemon.nature.name.path

        if (isUnregistered && pokeData != null && rotomConfig.registerUnregisteredToDex) {
            val newInfo = dex.getNewInformation(pokeData)
            if (entity.owner === player) {
                dex.obtain(pokemon)
            } else {
                dex.encounter(pokeData)
            }
            CobblemonEvents.POKEMON_SCANNED.post(PokemonScannedEvent(player, pokeData, entity))
            ServerConfirmedRegisterPacket(pokeData.getApparentSpecies().resourceIdentifier, newInfo).sendToPlayer(player)

            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_scan.registered", pokemon.species.name)
                    .withStyle(ChatFormatting.AQUA),
                true
            )
        } else {
            val ivDisplay = if (rotomConfig.revealIvPercentage) "$ivPercent%" else "??%"
            val natureDisplay = if (rotomConfig.revealNature) nature else "??"
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_scan.info", pokemon.species.name, level, natureDisplay, ivDisplay)
                    .withStyle(ChatFormatting.AQUA),
                true
            )
        }

        // Consume energy if configured
        val rotomPhone = SmartphoneHelper.getEquippedRotomPhone(player)
        if (CobblemonSmartphone.config.energy.enableEnergy && rotomConfig.scanEnergyCost > 0 && rotomPhone != null) {
            rotomPhone.consumeEnergy(rotomConfig.scanEnergyCost)
        }

        // Send completion packet to player and nearby players
        val completePacket = RotomScanCompletePacket(
            scannerPlayerUuid = player.uuid,
            targetEntityId = entity.id,
            speciesName = pokemon.species.name,
            isAlreadyRegistered = !isUnregistered,
            level = level,
            nature = if (rotomConfig.revealNature) nature else "",
            ivPercent = if (rotomConfig.revealIvPercentage) ivPercent else -1
        )
        sendToNearby(player, completePacket)

        // Set cooldown so it won't repeatedly scan the same Pokémon
        val cooldownMs = rotomConfig.rescanCooldownSeconds.coerceAtLeast(1) * 1000L
        scanCooldowns["${player.uuid}:${entity.id}"] = System.currentTimeMillis() + cooldownMs

        // Transition into returning phase (Rotom gracefully flies back to shoulder)
        session.phase = ScanPhase.RETURNING
        session.phaseTicks = 0
        session.prioritizedEntityId = null
        broadcastScanSync(player, session)
    }

    private fun broadcastScanSync(player: ServerPlayer, session: PlayerScanSession) {
        val totalTicks = CobblemonSmartphone.config.rotom.scanDurationTicks.coerceAtLeast(10)
        val packet = SyncRotomScanPacket(
            scannerPlayerUuid = player.uuid,
            targetEntityId = session.targetEntityId,
            phase = session.phase.id,
            progressTicks = if (session.phase == ScanPhase.SCANNING) session.phaseTicks else if (session.phase == ScanPhase.RETURNING) totalTicks else 0,
            totalTicks = totalTicks,
            isPrioritized = session.prioritizedEntityId != null && session.prioritizedEntityId == session.targetEntityId,
            isAlreadyRegistered = session.isAlreadyRegistered
        )
        sendToNearby(player, packet)
    }

    private fun sendToNearby(player: ServerPlayer, packet: com.nbp.cobblemon_smartphone.network.packet.CobblemonSmartphoneNetworkPacket<*>) {
        packet.sendToPlayer(player)
        for (other in player.server.playerList.players) {
            if (other.uuid != player.uuid && other.serverLevel() == player.serverLevel() && other.distanceToSqr(player) <= 1024.0) {
                packet.sendToPlayer(other)
            }
        }
    }
}
