package com.nbp.cobblemon_smartphone.server

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
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
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRibbonDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRotomAccessory
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object RotomServerManager {
    private val openPlayers = ConcurrentHashMap.newKeySet<UUID>()
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
        val player1Uuid: UUID,
        val player2Uuid: UUID,
        val anchorX: Double,
        val anchorY: Double,
        val anchorZ: Double,
        val dirX: Double,
        val dirZ: Double,
        val patternSeed: Int,
        val durationTicks: Int,
        val endTimeMs: Long
    )

    data class PairCooldownState(
        var nextCheckTimeMs: Long = 0L,
        var lastSeenNearMs: Long = 0L,
        var isFirstMeetingCheck: Boolean = true
    )

    private val equippedSnapshots = ConcurrentHashMap<UUID, EquippedRotomSnapshot>()
    private val activeSocialSessions = ConcurrentHashMap<Pair<UUID, UUID>, ServerSocialSession>()
    private val pairCooldownStates = ConcurrentHashMap<Pair<UUID, UUID>, PairCooldownState>()

    fun updatePlayerConfig(playerUuid: UUID, config: RotomRadarConfig) {
        playerConfigs[playerUuid] = config
    }

    fun onPlayerSetOpen(player: ServerPlayer, isOpen: Boolean) {
        if (isOpen) {
            openPlayers.add(player.uuid)
        } else {
            openPlayers.remove(player.uuid)
        }

        // Broadcast para todos os outros jogadores sincronizarem a animação
        val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, isOpen)
        for (other in player.server.playerList.players) {
            if (other.uuid != player.uuid) {
                syncPacket.sendToPlayer(other)
            }
        }
    }

    fun onPlayerJoin(player: ServerPlayer) {
        // Envia para o jogador que acabou de entrar quais jogadores estão com o smartphone aberto
        for (openUuid in openPlayers) {
            SyncSmartphoneOpenStatePacket(openUuid, true).sendToPlayer(player)
        }

        // Envia o estado visual de Rotom Phone de todos os jogadores online
        for ((uuid, snap) in equippedSnapshots) {
            if (snap.hasRotom) {
                SyncRotomEquipStatePacket(
                    playerUuid = uuid,
                    hasRotom = true,
                    phoneColor = snap.phoneColor,
                    accessory = snap.accessory,
                    ribbonColor = snap.ribbonColor
                ).sendToPlayer(player)
            }
        }

        // Envia sessões sociais ativas
        for (session in activeSocialSessions.values) {
            SyncRotomSocialSessionPacket(
                player1Uuid = session.player1Uuid,
                player2Uuid = session.player2Uuid,
                active = true,
                anchorX = session.anchorX,
                anchorY = session.anchorY,
                anchorZ = session.anchorZ,
                dirX = session.dirX,
                dirZ = session.dirZ,
                patternSeed = session.patternSeed,
                durationTicks = session.durationTicks
            ).sendToPlayer(player)
        }

        // Carrega configurações do radar Rotom do jogador
        val config = RotomRadarStorage.read(player)
        playerConfigs[player.uuid] = config
        SyncRotomRadarPacket(config).sendToPlayer(player)
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        playerConfigs.remove(player.uuid)
        equippedSnapshots.remove(player.uuid)
        val prefix = "${player.uuid}:"
        alertCooldowns.keys.removeIf { it.startsWith(prefix) }

        // Encerra qualquer sessão social envolvendo o jogador que desconectou
        val toRemove = activeSocialSessions.entries.filter {
            it.key.first == player.uuid || it.key.second == player.uuid
        }
        for (entry in toRemove) {
            activeSocialSessions.remove(entry.key)
            val stopPacket = SyncRotomSocialSessionPacket(
                player1Uuid = entry.value.player1Uuid,
                player2Uuid = entry.value.player2Uuid,
                active = false,
                anchorX = entry.value.anchorX,
                anchorY = entry.value.anchorY,
                anchorZ = entry.value.anchorZ,
                dirX = entry.value.dirX,
                dirZ = entry.value.dirZ,
                patternSeed = entry.value.patternSeed,
                durationTicks = 0
            )
            for (other in player.server.playerList.players) {
                if (other.uuid != player.uuid) {
                    stopPacket.sendToPlayer(other)
                }
            }
        }

        if (openPlayers.remove(player.uuid)) {
            val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, false)
            for (other in player.server.playerList.players) {
                if (other.uuid != player.uuid) {
                    syncPacket.sendToPlayer(other)
                }
            }
        }
    }

    private fun updateEquippedRotomsAndSocialSessions(server: MinecraftServer, now: Long) {
        val players = server.playerList.players

        // 1. Sincronizar estado de Rotom Phone equipado/inventário de cada jogador
        val playersWithRotom = mutableListOf<ServerPlayer>()
        for (player in players) {
            val stack = SmartphoneHelper.getEquippedRotomPhone(player)
            val snapshot = if (stack != null && player.isAlive && !player.isInvisible) {
                playersWithRotom.add(player)
                val color = (stack.item as? SmartphoneItem)?.getColor()?.name ?: "RED"
                val acc = stack.getRotomAccessory() ?: ""
                val ribColor = stack.getRibbonDyeColor()?.serializedName ?: "pink"
                EquippedRotomSnapshot(true, color, acc, ribColor)
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
        val busyPlayers = HashSet<UUID>()

        // 2. Validar sessões sociais ativas (terminar se acabou o tempo, abriu o celular ou se afastou > 7.5 blocos da âncora)
        val iterator = activeSocialSessions.entries.iterator()
        while (iterator.hasNext()) {
            val (pairKey, session) = iterator.next()
            val p1 = playersByUuid[session.player1Uuid]
            val p2 = playersByUuid[session.player2Uuid]

            val valid = p1 != null && p2 != null &&
                p1.serverLevel() == p2.serverLevel() &&
                !openPlayers.contains(p1.uuid) && !openPlayers.contains(p2.uuid) &&
                !p1.isSleeping && !p2.isSleeping &&
                now < session.endTimeMs &&
                p1.distanceToSqr(session.anchorX, session.anchorY - 1.0, session.anchorZ) <= 56.25 &&
                p2.distanceToSqr(session.anchorX, session.anchorY - 1.0, session.anchorZ) <= 56.25

            if (!valid) {
                iterator.remove()
                val cdState = pairCooldownStates.computeIfAbsent(pairKey) { PairCooldownState() }
                // Cooldown de 16 a 26 segundos descansando no ombro antes de poder tentar interagir novamente
                cdState.nextCheckTimeMs = now + Random.nextLong(16_000L, 26_000L)
                cdState.lastSeenNearMs = now
                cdState.isFirstMeetingCheck = false

                val stopPkt = SyncRotomSocialSessionPacket(
                    player1Uuid = session.player1Uuid,
                    player2Uuid = session.player2Uuid,
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
                busyPlayers.add(session.player1Uuid)
                busyPlayers.add(session.player2Uuid)
                pairCooldownStates[pairKey]?.lastSeenNearMs = now
            }
        }

        // 3. Verificar pares próximos que não estão interagindo e sortear chance periódica de dar trigger
        for (i in 0 until playersWithRotom.size) {
            val a = playersWithRotom[i]
            if (busyPlayers.contains(a.uuid) || openPlayers.contains(a.uuid) || a.isSleeping) continue

            for (j in i + 1 until playersWithRotom.size) {
                val b = playersWithRotom[j]
                if (busyPlayers.contains(b.uuid) || openPlayers.contains(b.uuid) || b.isSleeping) continue
                if (a.serverLevel() != b.serverLevel()) continue

                val distSqr = a.distanceToSqr(b)
                if (distSqr > 36.0) continue // Precisam estar a até 6 blocos para iniciar

                val p1 = if (a.uuid < b.uuid) a else b
                val p2 = if (a.uuid < b.uuid) b else a
                val pairKey = Pair(p1.uuid, p2.uuid)

                val cdState = pairCooldownStates.computeIfAbsent(pairKey) { PairCooldownState() }
                // Se ficaram longe um do outro por mais de 12 segundos, trata como um novo encontro
                if (now - cdState.lastSeenNearMs > 12_000L) {
                    cdState.isFirstMeetingCheck = true
                    if (cdState.nextCheckTimeMs < now + 1_500L) {
                        cdState.nextCheckTimeMs = now + 1_500L // Breve olhar no ombro antes do primeiro sorteio
                    }
                }
                cdState.lastSeenNearMs = now

                if (now >= cdState.nextCheckTimeMs) {
                    val triggerChance = if (cdState.isFirstMeetingCheck) 0.58f else 0.42f
                    cdState.isFirstMeetingCheck = false

                    if (Random.nextFloat() < triggerChance) {
                        // Trigger de interação! Cria o ponto âncora compartilhado exato para ambos os jogadores
                        val anchorX = (p1.x + p2.x) * 0.5
                        val anchorY = (p1.y + p2.y) * 0.5 + 1.35
                        val anchorZ = (p1.z + p2.z) * 0.5

                        val dx = p2.x - p1.x
                        val dz = p2.z - p1.z
                        val dLen = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
                        val dirX = dx / dLen
                        val dirZ = dz / dLen

                        val durationSec = Random.nextInt(4, 6) * 4 // 16s ou 20s (4 ou 5 fases completas de 4s)
                        val durationTicks = durationSec * 20
                        val patternSeed = Random.nextInt(1, 1_000_000)

                        val session = ServerSocialSession(
                            player1Uuid = p1.uuid,
                            player2Uuid = p2.uuid,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            anchorZ = anchorZ,
                            dirX = dirX,
                            dirZ = dirZ,
                            patternSeed = patternSeed,
                            durationTicks = durationTicks,
                            endTimeMs = now + durationSec * 1000L
                        )
                        activeSocialSessions[pairKey] = session
                        busyPlayers.add(p1.uuid)
                        busyPlayers.add(p2.uuid)

                        val startPkt = SyncRotomSocialSessionPacket(
                            player1Uuid = p1.uuid,
                            player2Uuid = p2.uuid,
                            active = true,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            anchorZ = anchorZ,
                            dirX = dirX,
                            dirZ = dirZ,
                            patternSeed = patternSeed,
                            durationTicks = durationTicks
                        )
                        for (target in players) {
                            startPkt.sendToPlayer(target)
                        }
                        break
                    } else {
                        // Não deu trigger desta vez: espera mais 5 a 8 segundos antes de tentar novamente
                        cdState.nextCheckTimeMs = now + Random.nextLong(5_000L, 8_000L)
                    }
                }
            }
        }
    }

    fun tick(server: MinecraftServer) {
        if (++tickCounter % 20 != 0) return

        val now = System.currentTimeMillis()
        updateEquippedRotomsAndSocialSessions(server, now)

        if (tickCounter % 100 == 0) {
            alertCooldowns.entries.removeIf { it.value < now }
            pairCooldownStates.entries.removeIf { now - it.value.lastSeenNearMs > 120_000L }
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
                        soundAlert = config.soundAlert
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
}
