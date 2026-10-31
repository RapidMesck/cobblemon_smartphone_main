package com.nbp.cobblemon_smartphone.server

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobblemon_smartphone.network.packet.RotomAlertPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomRadarPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import com.nbp.cobblemon_smartphone.rotom.RotomRadarStorage
import com.nbp.cobblemon_smartphone.rotom.RotomTarget
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RotomServerManager {
    private val openPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val playerConfigs = ConcurrentHashMap<UUID, RotomRadarConfig>()
    // Cooldown map: key = "playerUuid:entityUuid", value = expiration timestamp ms
    private val alertCooldowns = ConcurrentHashMap<String, Long>()
    private var tickCounter = 0

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

        // Carrega configurações do radar Rotom do jogador
        val config = RotomRadarStorage.read(player)
        playerConfigs[player.uuid] = config
        SyncRotomRadarPacket(config).sendToPlayer(player)
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        playerConfigs.remove(player.uuid)
        val prefix = "${player.uuid}:"
        alertCooldowns.keys.removeIf { it.startsWith(prefix) }

        if (openPlayers.remove(player.uuid)) {
            val syncPacket = SyncSmartphoneOpenStatePacket(player.uuid, false)
            for (other in player.server.playerList.players) {
                if (other.uuid != player.uuid) {
                    syncPacket.sendToPlayer(other)
                }
            }
        }
    }

    fun tick(server: MinecraftServer) {
        if (++tickCounter % 20 != 0) return

        val now = System.currentTimeMillis()
        if (tickCounter % 100 == 0) {
            alertCooldowns.entries.removeIf { it.value < now }
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
