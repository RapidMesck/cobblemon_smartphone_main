package com.nbp.cobblemon_smartphone.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.PokedexLearnedInformation
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.pokedex.ServerConfirmedRegisterPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.pc
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.RotomAlertPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomEquipStatePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomRadarPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomSocialSessionPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import com.nbp.cobblemon_smartphone.rotom.RotomRadarStorage
import com.nbp.cobblemon_smartphone.rotom.RotomTarget
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.clearFusedPokemon
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getBowtieDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getFusedPokemon
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRibbonDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.saveFusedPokemon
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomPhone
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object RotomServerManager {
    private val openPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val horizontalPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val selfiePlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val photoPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val playerConfigs = ConcurrentHashMap<UUID, RotomRadarConfig>()
    // Cooldown map: key = "playerUuid:entityUuid", value = expiration timestamp ms
    private val alertCooldowns = ConcurrentHashMap<String, Long>()
    private var tickCounter = 0

    data class EquippedRotomSnapshot(
        val hasRotom: Boolean,
        val phoneColor: String,
        val accessory: String,
        val ribbonColor: String
    )

    data class ServerSocialSession(
        val sessionId: UUID,
        val participants: MutableSet<UUID>,
        val anchorX: Double,
        val anchorY: Double,
        val anchorZ: Double,
        val dirX: Double,
        val dirZ: Double,
        val patternSeed: Int,
        val durationTicks: Int,
        val endTimeMs: Long,
        val dataShareCheckTimeMs: Long,
        var hasSharedData: Boolean = false
    )

    private val equippedSnapshots = ConcurrentHashMap<UUID, EquippedRotomSnapshot>()
    private val activeSocialSessions = ConcurrentHashMap<UUID, ServerSocialSession>()
    private val playerToSession = ConcurrentHashMap<UUID, UUID>()
    private val playerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val clusterCheckCooldowns = ConcurrentHashMap<UUID, Long>()

    fun updatePlayerConfig(playerUuid: UUID, config: RotomRadarConfig) {
        playerConfigs[playerUuid] = config
    }

    fun getPlayerConfig(playerUuid: UUID): RotomRadarConfig? = playerConfigs[playerUuid]

    fun isPlayerInPhotoMode(uuid: UUID): Boolean = photoPlayers.contains(uuid) || selfiePlayers.contains(uuid)
    fun isPlayerSelfie(uuid: UUID): Boolean = selfiePlayers.contains(uuid)
    fun isPlayerOpen(uuid: UUID): Boolean = openPlayers.contains(uuid)

    fun onPlayerSetOpen(
        player: ServerPlayer,
        isOpen: Boolean,
        isHorizontal: Boolean = false,
        isSelfie: Boolean = false,
        isPhotoMode: Boolean = false
    ) {
        if (isOpen) {
            openPlayers.add(player.uuid)
            if (isHorizontal) {
                horizontalPlayers.add(player.uuid)
            } else {
                horizontalPlayers.remove(player.uuid)
            }
            if (isSelfie) {
                selfiePlayers.add(player.uuid)
            } else {
                selfiePlayers.remove(player.uuid)
            }
            if (isPhotoMode) {
                photoPlayers.add(player.uuid)
            } else {
                photoPlayers.remove(player.uuid)
            }
        } else {
            openPlayers.remove(player.uuid)
            horizontalPlayers.remove(player.uuid)
            selfiePlayers.remove(player.uuid)
            photoPlayers.remove(player.uuid)
        }

        // Broadcast para todos os outros jogadores sincronizarem a animação
        val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, isOpen, isHorizontal, isSelfie, isPhotoMode)
        for (other in player.server.playerList.players) {
            if (other.uuid != player.uuid) {
                syncPacket.sendToPlayer(other)
            }
        }
    }

    fun onPlayerJoin(player: ServerPlayer) {
        // Envia para o jogador que acabou de entrar quais jogadores estão com o smartphone aberto
        for (openUuid in openPlayers) {
            val isHorizontal = horizontalPlayers.contains(openUuid)
            val isSelfie = selfiePlayers.contains(openUuid)
            val isPhoto = photoPlayers.contains(openUuid)
            SyncSmartphoneOpenStatePacket(openUuid, true, isHorizontal, isSelfie, isPhoto).sendToPlayer(player)
        }

        // Envia o estado visual de Smartphone / Rotom Phone de todos os jogadores online
        for ((uuid, snap) in equippedSnapshots) {
            if (snap.phoneColor.isNotBlank()) {
                SyncRotomEquipStatePacket(
                    playerUuid = uuid,
                    hasRotom = snap.hasRotom,
                    phoneColor = snap.phoneColor,
                    accessory = snap.accessory,
                    ribbonColor = snap.ribbonColor
                ).sendToPlayer(player)
            }
        }

        // Envia sessões sociais ativas em grupo
        val now = System.currentTimeMillis()
        for (session in activeSocialSessions.values) {
            val remainingTicks = ((session.endTimeMs - now) / 50L).toInt().coerceAtLeast(20)
            SyncRotomSocialSessionPacket(
                sessionId = session.sessionId,
                participants = session.participants.toList(),
                active = true,
                anchorX = session.anchorX,
                anchorY = session.anchorY,
                anchorZ = session.anchorZ,
                dirX = session.dirX,
                dirZ = session.dirZ,
                patternSeed = session.patternSeed,
                durationTicks = remainingTicks
            ).sendToPlayer(player)
        }

        // Carrega configurações do radar Rotom do jogador
        val config = RotomRadarStorage.read(player)
        playerConfigs[player.uuid] = config
        SyncRotomRadarPacket(config).sendToPlayer(player)
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        RotomAutoScannerManager.onPlayerDisconnect(player)
        playerConfigs.remove(player.uuid)
        equippedSnapshots.remove(player.uuid)
        val prefix = "${player.uuid}:"
        alertCooldowns.keys.removeIf { it.startsWith(prefix) }
        playerCooldowns.remove(player.uuid)

        // Encerra ou remove o jogador de qualquer sessão social ativa
        val sessId = playerToSession.remove(player.uuid)
        if (sessId != null) {
            val session = activeSocialSessions[sessId]
            if (session != null) {
                session.participants.remove(player.uuid)
                val now = System.currentTimeMillis()
                if (session.participants.size < 2) {
                    activeSocialSessions.remove(sessId)
                    for (u in session.participants) {
                        playerToSession.remove(u)
                        playerCooldowns[u] = now + Random.nextLong(16_000L, 26_000L)
                    }
                    val stopPacket = SyncRotomSocialSessionPacket(
                        sessionId = sessId,
                        participants = session.participants.toList(),
                        active = false,
                        anchorX = session.anchorX,
                        anchorY = session.anchorY,
                        anchorZ = session.anchorZ,
                        dirX = session.dirX,
                        dirZ = session.dirZ,
                        patternSeed = session.patternSeed,
                        durationTicks = 0
                    )
                    for (other in player.server.playerList.players) {
                        if (other.uuid != player.uuid) {
                            stopPacket.sendToPlayer(other)
                        }
                    }
                } else {
                    val updatePacket = SyncRotomSocialSessionPacket(
                        sessionId = sessId,
                        participants = session.participants.toList(),
                        active = true,
                        anchorX = session.anchorX,
                        anchorY = session.anchorY,
                        anchorZ = session.anchorZ,
                        dirX = session.dirX,
                        dirZ = session.dirZ,
                        patternSeed = session.patternSeed,
                        durationTicks = ((session.endTimeMs - now) / 50L).toInt().coerceAtLeast(20)
                    )
                    for (other in player.server.playerList.players) {
                        if (other.uuid != player.uuid) {
                            updatePacket.sendToPlayer(other)
                        }
                    }
                }
            }
        }

        horizontalPlayers.remove(player.uuid)
        selfiePlayers.remove(player.uuid)
        photoPlayers.remove(player.uuid)
        if (openPlayers.remove(player.uuid)) {
            val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, false, false, false, false)
            for (other in player.server.playerList.players) {
                if (other.uuid != player.uuid) {
                    syncPacket.sendToPlayer(other)
                }
            }
        }
    }

    private fun updateEquippedRotomsAndSocialSessions(server: MinecraftServer, now: Long) {
        val players = server.playerList.players

        // 1. Sincronizar estado de Smartphone / Rotom Phone equipado/inventário de cada jogador
        val playersWithRotom = mutableListOf<ServerPlayer>()
        for (player in players) {
            val rotomStack = SmartphoneHelper.getEquippedRotomPhone(player)
            val stack = rotomStack ?: SmartphoneHelper.getSmartphone(player)
            val isRotom = stack?.isRotomPhone() == true
            val snapshot = if (stack != null && player.isAlive && !player.isInvisible) {
                if (isRotom) {
                    playersWithRotom.add(player)
                }
                val color = (stack.item as? SmartphoneItem)?.getColor()?.name ?: "RED"
                val acc = if (isRotom) (stack.getRotomAccessory() ?: "") else ""
                val defaultColor = if (acc == "bow") DyeColor.PINK else DyeColor.LIGHT_BLUE
                val accColor = (if (acc == "bow") stack.getRibbonDyeColor() else stack.getBowtieDyeColor()) ?: defaultColor
                EquippedRotomSnapshot(isRotom, color, acc, accColor.serializedName)
            } else {
                EquippedRotomSnapshot(false, "", "", "")
            }

            val prev = equippedSnapshots[player.uuid]
            if (prev != snapshot) {
                equippedSnapshots[player.uuid] = snapshot
                val pkt = SyncRotomEquipStatePacket(
                    playerUuid = player.uuid,
                    hasRotom = snapshot.hasRotom,
                    phoneColor = snapshot.phoneColor,
                    accessory = snapshot.accessory,
                    ribbonColor = snapshot.ribbonColor
                )
                for (target in players) {
                    pkt.sendToPlayer(target)
                }
            }
        }

        val playersByUuid = playersWithRotom.associateBy { it.uuid }

        // 2. Validar sessões sociais ativas em grupo (se desativado no config, encerra e ignora)
        if (!CobblemonSmartphone.config.rotom.enableSocialInteractions) {
            if (activeSocialSessions.isNotEmpty()) {
                activeSocialSessions.clear()
                playerToSession.clear()
            }
            return
        }

        val sessionIter = activeSocialSessions.entries.iterator()
        while (sessionIter.hasNext()) {
            val (sessionId, session) = sessionIter.next()

            // Remove participantes que não são mais válidos
            val invalidParticipants = mutableListOf<UUID>()
            for (pUuid in session.participants) {
                val p = playersByUuid[pUuid]
                val isBusy = openPlayers.contains(pUuid) || RotomAutoScannerManager.isPlayerInDialogueOrScanning(pUuid)
                val valid = p != null &&
                    !isBusy &&
                    !p.isSleeping &&
                    p.distanceToSqr(session.anchorX, session.anchorY - 1.0, session.anchorZ) <= 56.25

                if (!valid) {
                    invalidParticipants.add(pUuid)
                }
            }

            for (pUuid in invalidParticipants) {
                session.participants.remove(pUuid)
                playerToSession.remove(pUuid)
                playerCooldowns[pUuid] = now + Random.nextLong(16_000L, 26_000L)
            }

            val isExpired = now >= session.endTimeMs
            if (isExpired || session.participants.size < 2) {
                // Sessão encerrada
                sessionIter.remove()
                for (pUuid in session.participants) {
                    playerToSession.remove(pUuid)
                    playerCooldowns[pUuid] = now + Random.nextLong(16_000L, 26_000L)
                }

                val stopPkt = SyncRotomSocialSessionPacket(
                    sessionId = sessionId,
                    participants = session.participants.toList(),
                    active = false,
                    anchorX = session.anchorX,
                    anchorY = session.anchorY,
                    anchorZ = session.anchorZ,
                    dirX = session.dirX,
                    dirZ = session.dirZ,
                    patternSeed = session.patternSeed,
                    durationTicks = 0
                )
                for (target in players) {
                    stopPkt.sendToPlayer(target)
                }
            } else {
                // Sessão ativa: verificar compartilhamento de dados da Pokédex no ponto médio
                if (!session.hasSharedData && now >= session.dataShareCheckTimeMs) {
                    session.hasSharedData = true
                    trySocialDataShare(server, session)
                }

                if (invalidParticipants.isNotEmpty()) {
                    // Participante saiu, mas a sessão continua com os outros!
                    val updatePkt = SyncRotomSocialSessionPacket(
                        sessionId = sessionId,
                        participants = session.participants.toList(),
                        active = true,
                        anchorX = session.anchorX,
                        anchorY = session.anchorY,
                        anchorZ = session.anchorZ,
                        dirX = session.dirX,
                        dirZ = session.dirZ,
                        patternSeed = session.patternSeed,
                        durationTicks = ((session.endTimeMs - now) / 50L).toInt().coerceAtLeast(20)
                    )
                    for (target in players) {
                        updatePkt.sendToPlayer(target)
                    }
                }
            }
        }

        // 3. Permitir que jogadores ociosos se juntem a sessões ativas existentes por perto!
        for (session in activeSocialSessions.values) {
            for (candidate in playersWithRotom) {
                if (playerToSession.containsKey(candidate.uuid)) continue
                val isBusy = openPlayers.contains(candidate.uuid) || RotomAutoScannerManager.isPlayerInDialogueOrScanning(candidate.uuid)
                if (isBusy || candidate.isSleeping) continue
                if (now < playerCooldowns.getOrDefault(candidate.uuid, 0L)) continue

                val distSqr = candidate.distanceToSqr(session.anchorX, session.anchorY - 1.0, session.anchorZ)
                if (distSqr <= 30.25) { // Até 5.5 blocos da âncora ativa
                    session.participants.add(candidate.uuid)
                    playerToSession[candidate.uuid] = session.sessionId

                    val joinPkt = SyncRotomSocialSessionPacket(
                        sessionId = session.sessionId,
                        participants = session.participants.toList(),
                        active = true,
                        anchorX = session.anchorX,
                        anchorY = session.anchorY,
                        anchorZ = session.anchorZ,
                        dirX = session.dirX,
                        dirZ = session.dirZ,
                        patternSeed = session.patternSeed,
                        durationTicks = ((session.endTimeMs - now) / 50L).toInt().coerceAtLeast(20)
                    )
                    for (target in players) {
                        joinPkt.sendToPlayer(target)
                    }
                }
            }
        }

        // 4. Formar novas sessões em grupo a partir de jogadores ociosos próximos
        val idlePlayers = playersWithRotom.filter { p ->
            !playerToSession.containsKey(p.uuid) &&
            !openPlayers.contains(p.uuid) &&
            !RotomAutoScannerManager.isPlayerInDialogueOrScanning(p.uuid) &&
            !p.isSleeping &&
            now >= playerCooldowns.getOrDefault(p.uuid, 0L)
        }

        if (idlePlayers.size >= 2) {
            // Agrupar em componentes conectados de jogadores próximos (<= 6.0 blocos de distância)
            val visited = HashSet<UUID>()
            for (p in idlePlayers) {
                if (visited.contains(p.uuid)) continue
                val cluster = mutableListOf<ServerPlayer>()
                val queue = ArrayDeque<ServerPlayer>()
                queue.add(p)
                visited.add(p.uuid)

                while (queue.isNotEmpty()) {
                    val curr = queue.removeFirst()
                    cluster.add(curr)
                    for (other in idlePlayers) {
                        if (!visited.contains(other.uuid) && curr.serverLevel() == other.serverLevel()) {
                            if (curr.distanceToSqr(other) <= 36.0) {
                                visited.add(other.uuid)
                                queue.add(other)
                            }
                        }
                    }
                }

                if (cluster.size >= 2) {
                    val clusterLeader = cluster.minByOrNull { it.uuid }!!.uuid
                    val nextCheck = clusterCheckCooldowns.getOrDefault(clusterLeader, 0L)
                    if (now >= nextCheck) {
                        clusterCheckCooldowns[clusterLeader] = now + Random.nextLong(5_000L, 8_000L)
                        // Chance de ~48% de iniciar a interação social em grupo
                        if (Random.nextFloat() < 0.48f) {
                            val count = cluster.size.toDouble()
                            val anchorX = cluster.sumOf { it.x } / count
                            val anchorY = (cluster.sumOf { it.y } / count) + 1.35
                            val anchorZ = cluster.sumOf { it.z } / count

                            val p0 = cluster[0]
                            val p1 = cluster[1]
                            val dx = p1.x - p0.x
                            val dz = p1.z - p0.z
                            val dLen = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
                            val uX = dx / dLen
                            val uZ = dz / dLen

                            val sessionId = UUID.randomUUID()
                            val durationSec = Random.nextInt(4, 6) * 4 // 16s ou 20s
                            val durationTicks = durationSec * 20
                            val participantUuids = cluster.map { it.uuid }.toMutableSet()
                            val newSession = ServerSocialSession(
                                sessionId = sessionId,
                                participants = participantUuids,
                                anchorX = anchorX,
                                anchorY = anchorY,
                                anchorZ = anchorZ,
                                dirX = uX,
                                dirZ = uZ,
                                patternSeed = Random.nextInt(1, 1_000_000),
                                durationTicks = durationTicks,
                                endTimeMs = now + durationSec * 1000L,
                                dataShareCheckTimeMs = now + (durationSec * 500L),
                                hasSharedData = false
                            )

                            activeSocialSessions[sessionId] = newSession
                            for (c in cluster) {
                                playerToSession[c.uuid] = sessionId
                            }

                            val startPkt = SyncRotomSocialSessionPacket(
                                sessionId = sessionId,
                                participants = participantUuids.toList(),
                                active = true,
                                anchorX = anchorX,
                                anchorY = anchorY,
                                anchorZ = anchorZ,
                                dirX = uX,
                                dirZ = uZ,
                                patternSeed = newSession.patternSeed,
                                durationTicks = durationTicks
                            )
                            for (target in players) {
                                startPkt.sendToPlayer(target)
                            }
                        }
                    }
                }
            }
        }
    }

    fun tick(server: MinecraftServer) {
        tickCounter++

        // Auto Pokédex Scanner ticks every single tick
        RotomAutoScannerManager.tick(server)

        if (tickCounter % 20 != 0) return

        val now = System.currentTimeMillis()
        updateEquippedRotomsAndSocialSessions(server, now)

        if (tickCounter % 100 == 0) {
            alertCooldowns.entries.removeIf { it.value < now }
            playerCooldowns.entries.removeIf { it.value < now }
            clusterCheckCooldowns.entries.removeIf { it.value < now }
        }

        for (player in server.playerList.players) {
            // Verifica se o jogador possui um Rotom Phone equipado/em mãos/inventário
            SmartphoneHelper.getEquippedRotomPhone(player) ?: continue

            val config = playerConfigs[player.uuid] ?: continue
            if (!config.enabled) continue
            if (!config.shinyAlert && !config.targetAlert) continue

            val radius = 36.0
            val aabb = player.boundingBox.inflate(radius)
            val entities = player.serverLevel().getEntitiesOfClass(PokemonEntity::class.java, aabb)

            for (entity in entities) {
                if (!entity.isAlive) continue
                val pokemon = entity.pokemon ?: continue
                if (!pokemon.isWild() || pokemon.isFainted()) continue

                val alertKey = "${player.uuid}:${entity.uuid}"
                val cd = alertCooldowns[alertKey]
                if (cd != null && cd > now) continue

                val isShiny = pokemon.shiny
                val shinyMatch = config.shinyAlert && isShiny
                val targetMatch = if (config.targetAlert) findMatchingTarget(pokemon, config.targets) else null

                if (shinyMatch || targetMatch != null) {
                    alertCooldowns[alertKey] = now + 60_000L // 60 segundos de cooldown por entidade

                    val dist = player.distanceTo(entity).toInt()
                    val ivTotal = pokemon.ivs.getEffectiveBattleTotal()
                    val ivPercent = (ivTotal * 100) / 186

                    RotomAlertPacket(
                        speciesName = pokemon.species.name,
                        isShiny = isShiny,
                        ivPercent = ivPercent,
                        nature = pokemon.nature.name.path,
                        distance = dist,
                        isTargetMatch = targetMatch != null,
                        soundAlert = config.soundAlert,
                        targetEntityId = entity.id,
                        targetX = entity.x,
                        targetY = entity.y + entity.bbHeight * 0.5,
                        targetZ = entity.z,
                        speciesId = pokemon.species.resourceIdentifier.toString()
                    ).sendToPlayer(player)
                }
            }
        }
    }

    private fun findMatchingTarget(pokemon: Pokemon, targets: List<RotomTarget>): RotomTarget? {
        if (targets.isEmpty()) return null
        val pokemonSpecies = pokemon.species.resourceIdentifier.path.lowercase()
        val pokemonSpeciesName = pokemon.species.name.lowercase()
        val pokemonNature = pokemon.nature.name.path.lowercase()
        val ivTotal = pokemon.ivs.getEffectiveBattleTotal()
        val ivPercent = (ivTotal * 100) / 186

        for (target in targets) {
            if (!target.enabled) continue

            // Filtro de espécie
            if (target.species.isNotBlank() && target.species != "any" && target.species != "*") {
                val tSpec = target.species.trim().lowercase()
                if (pokemonSpecies != tSpec && pokemonSpeciesName != tSpec) {
                    continue
                }
            }

            // Filtro de shiny
            if (target.shinyOnly && !pokemon.shiny) {
                continue
            }

            // Filtro de IV %
            if (target.minIvPercent > 0 && ivPercent < target.minIvPercent) {
                continue
            }

            // Filtro de IVs perfeitos (31)
            if (target.minPerfectIvs > 0) {
                val perfects = Stats.PERMANENT.count { stat -> pokemon.ivs.getOrDefault(stat) == 31 }
                if (perfects < target.minPerfectIvs) {
                    continue
                }
            }

            // Filtro de natureza
            if (target.nature.isNotBlank() && target.nature != "any") {
                val tNature = target.nature.trim().lowercase()
                if (pokemonNature != tNature) {
                    continue
                }
            }

            return target
        }
        return null
    }

    private fun trySocialDataShare(server: MinecraftServer, session: ServerSocialSession) {
        val rotomConfig = CobblemonSmartphone.config.rotom
        if (!rotomConfig.enableSocialInteractions || !rotomConfig.enableSocialDataShare || !rotomConfig.registerUnregisteredToDex) return
        if (Random.nextDouble() >= rotomConfig.socialDataShareChance) return

        val participants = session.participants
            .mapNotNull { server.playerList.getPlayer(it) }
            .filter { it.isAlive && !openPlayers.contains(it.uuid) }

        if (participants.size < 2) return

        var anyShared = false

        for (recipient in participants) {
            val recipientDex = Cobblemon.playerDataManager.getPokedexData(recipient)
            val potentialDonors = participants.filter { it.uuid != recipient.uuid }

            val candidates = mutableListOf<Pair<ServerPlayer, net.minecraft.resources.ResourceLocation>>()
            for (donor in potentialDonors) {
                val donorDex = Cobblemon.playerDataManager.getPokedexData(donor)
                for (speciesId in donorDex.speciesRecords.keys) {
                    if (donorDex.getHighestKnowledgeForSpecies(speciesId) != PokedexEntryProgress.UNREGISTERED) {
                        if (recipientDex.getHighestKnowledgeForSpecies(speciesId) == PokedexEntryProgress.UNREGISTERED) {
                            if (PokemonSpecies.getByIdentifier(speciesId) != null) {
                                candidates.add(donor to speciesId)
                            }
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                val (chosenDonor, chosenSpeciesId) = candidates.random()
                val species = PokemonSpecies.getByIdentifier(chosenSpeciesId)
                val formName = species?.standardForm?.name ?: ""

                val speciesRecord = recipientDex.getOrCreateSpeciesRecord(chosenSpeciesId)
                val formRecord = speciesRecord.getOrCreateFormRecord(formName)
                (formRecord.getSeenShinyStates() as? MutableSet<String>)?.add("normal")
                formRecord.setKnowledgeProgress(PokedexEntryProgress.SEEN)

                ServerConfirmedRegisterPacket(chosenSpeciesId, PokedexLearnedInformation.SPECIES).sendToPlayer(recipient)

                recipient.playNotifySound(CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON, SoundSource.PLAYERS, 1.0f, 1.0f)
                chosenDonor.playNotifySound(CobblemonSounds.POKEDEX_CLICK, SoundSource.PLAYERS, 0.8f, 1.2f)

                val speciesDisplayName = species?.translatedName ?: Component.literal(chosenSpeciesId.path.replaceFirstChar { it.uppercase() })

                recipient.displayClientMessage(
                    Component.translatable(
                        "message.cobblemon_smartphone.rotom_social.data_received",
                        speciesDisplayName,
                        chosenDonor.displayName
                    ).withStyle(ChatFormatting.AQUA),
                    false
                )

                chosenDonor.displayClientMessage(
                    Component.translatable(
                        "message.cobblemon_smartphone.rotom_social.data_shared",
                        speciesDisplayName,
                        recipient.displayName
                    ).withStyle(ChatFormatting.GREEN),
                    false
                )

                anyShared = true
            }
        }

        if (anyShared) {
            val level = participants.firstOrNull()?.serverLevel()
            if (level != null) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, session.anchorX, session.anchorY, session.anchorZ, 18, 0.4, 0.4, 0.4, 0.08)
                level.sendParticles(ParticleTypes.ENCHANT, session.anchorX, session.anchorY, session.anchorZ, 22, 0.5, 0.5, 0.5, 0.12)
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, session.anchorX, session.anchorY, session.anchorZ, 8, 0.35, 0.35, 0.35, 0.02)
            }
        }
    }

    fun fuseRotom(player: ServerPlayer, entityUuid: UUID): Boolean {
        val level = player.serverLevel()
        val entity = level.getEntity(entityUuid) as? PokemonEntity ?: return false
        if (!entity.isAlive || entity.isBattling || player.isInBattle()) return false
        if (player.distanceToSqr(entity) > 64.0) return false

        val pokemon = entity.pokemon
        val isRotom = pokemon.species.name.equals("rotom", ignoreCase = true) ||
            pokemon.species.resourceIdentifier.path == "rotom"
        if (!isRotom) return false
        if (entity.ownerUUID != player.uuid) return false

        val party = player.party()
        val rotomInParty = party.find { it.uuid == pokemon.uuid }
        if (rotomInParty == null) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_not_in_party")
                    .withStyle(ChatFormatting.RED),
                true
            )
            return false
        }

        // Find eligible smartphone: check main hand, off hand, then inventory
        val phoneStack = player.mainHandItem.takeIf { it.item is SmartphoneItem && !it.isRotomPhone() }
            ?: player.offhandItem.takeIf { it.item is SmartphoneItem && !it.isRotomPhone() }
            ?: player.inventory.items.firstOrNull { it.item is SmartphoneItem && !it.isRotomPhone() }

        if (phoneStack == null) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.no_smartphone_to_fuse")
                    .withStyle(ChatFormatting.RED),
                true
            )
            return false
        }

        // Return any held or cosmetic items
        val heldItem = rotomInParty.heldItem()
        if (!heldItem.isEmpty) {
            if (!player.inventory.add(heldItem.copy())) {
                player.drop(heldItem.copy(), false)
            }
        }
        val cosmeticItem = rotomInParty.cosmeticItem()
        if (!cosmeticItem.isEmpty) {
            if (!player.inventory.add(cosmeticItem.copy())) {
                player.drop(cosmeticItem.copy(), false)
            }
        }

        // Save full Rotom Pokemon data into the smartphone
        phoneStack.saveFusedPokemon(rotomInParty, level.registryAccess())
        phoneStack.setRotomPhone(true)

        // Play cry and effects
        entity.cry()
        val eyePos = entity.eyePosition
        val blockPos = entity.blockPosition()

        // Remove from party and discard entity
        party.remove(rotomInParty)
        entity.discard()

        level.playSound(
            null,
            blockPos,
            SoundEvents.LIGHTNING_BOLT_THUNDER,
            SoundSource.PLAYERS,
            0.7f,
            1.4f
        )
        level.playSound(
            null,
            blockPos,
            SoundEvents.LIGHTNING_BOLT_IMPACT,
            SoundSource.PLAYERS,
            0.5f,
            1.2f
        )

        level.sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            eyePos.x,
            eyePos.y,
            eyePos.z,
            35,
            0.3,
            0.3,
            0.3,
            0.15
        )
        level.sendParticles(
            ParticleTypes.FLASH,
            eyePos.x,
            eyePos.y,
            eyePos.z,
            1,
            0.0,
            0.0,
            0.0,
            0.0
        )

        player.containerMenu.broadcastChanges()
        player.inventoryMenu.broadcastChanges()

        player.displayClientMessage(
            Component.translatable("message.cobblemon_smartphone.became_rotom")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            true
        )
        return true
    }

    fun defuseRotom(player: ServerPlayer, targetPhoneStack: ItemStack? = null): Boolean {
        if (player.isInBattle()) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.cannot_defuse_in_battle")
                    .withStyle(ChatFormatting.RED),
                true
            )
            return false
        }

        val phoneStack = targetPhoneStack?.takeIf { it.isRotomPhone() }
            ?: player.mainHandItem.takeIf { it.isRotomPhone() }
            ?: player.offhandItem.takeIf { it.isRotomPhone() }
            ?: SmartphoneHelper.getSmartphoneImpl?.invoke(player)?.takeIf { it.isRotomPhone() }
            ?: player.inventory.items.firstOrNull { it.isRotomPhone() }

        if (phoneStack == null) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.no_rotom_phone_equipped")
                    .withStyle(ChatFormatting.RED),
                true
            )
            return false
        }

        val level = player.serverLevel()
        val rotom = phoneStack.getFusedPokemon(level.registryAccess())
            ?: PokemonSpecies.getByName("rotom")?.create(20)

        if (rotom == null) {
            return false
        }

        // Return any accessory first
        val returnedStack = SmartphoneHelper.createAccessoryStackFromPhone(phoneStack)
        if (returnedStack != null) {
            if (!player.inventory.add(returnedStack)) {
                player.drop(returnedStack, false)
            }
        }

        // Clear fused Pokemon & Rotom status
        phoneStack.clearFusedPokemon()

        // Add Rotom to party or PC
        val party = player.party()
        val addedToParty = party.add(rotom)
        if (!addedToParty) {
            val pc = player.pc()
            pc.add(rotom)
        }

        val blockPos = player.blockPosition()
        level.playSound(
            null,
            blockPos,
            SoundEvents.BEACON_DEACTIVATE,
            SoundSource.PLAYERS,
            0.7f,
            1.4f
        )
        level.playSound(
            null,
            blockPos,
            SoundEvents.LIGHTNING_BOLT_IMPACT,
            SoundSource.PLAYERS,
            0.4f,
            1.2f
        )

        level.sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            player.x,
            player.eyeY,
            player.z,
            25,
            0.3,
            0.3,
            0.3,
            0.1
        )

        player.containerMenu.broadcastChanges()
        player.inventoryMenu.broadcastChanges()

        val msgKey = if (addedToParty) "message.cobblemon_smartphone.rotom_defused_party" else "message.cobblemon_smartphone.rotom_defused_pc"
        player.displayClientMessage(
            Component.translatable(msgKey)
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            true
        )
        return true
    }
}
