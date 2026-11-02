package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.nbp.cobblemon_smartphone.network.packet.RotomAlertPacket
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object RotomTrackerClientState {

    data class TrackedPokemon(
        val speciesName: String,
        val speciesId: String,
        val isShiny: Boolean,
        val ivPercent: Int,
        val nature: String,
        var distance: Int,
        val isTargetMatch: Boolean,
        val targetEntityId: Int,
        var targetX: Double,
        var targetY: Double,
        var targetZ: Double,
        val alertTimeMs: Long = System.currentTimeMillis(),
        var cachedModel: RenderablePokemon? = null,
        var reached: Boolean = false,
        var resolved: Boolean = false
    ) {
        fun getElapsedMs(): Long = System.currentTimeMillis() - alertTimeMs
    }

    var currentTracked: TrackedPokemon? = null
        private set

    var animProgress: Float = 0.0f
        private set

    var isDismissing: Boolean = false
        private set

    private var lastTickTime: Long = 0L

    fun hasActiveTarget(): Boolean = currentTracked != null

    fun isVisible(): Boolean = currentTracked != null && animProgress > 0.001f

    fun onAlert(packet: RotomAlertPacket) {
        val config = RotomRadarClientState.config
        if (!config.hudTracker) return

        val tracked = TrackedPokemon(
            speciesName = packet.speciesName,
            speciesId = packet.speciesId,
            isShiny = packet.isShiny,
            ivPercent = packet.ivPercent,
            nature = packet.nature,
            distance = packet.distance,
            isTargetMatch = packet.isTargetMatch,
            targetEntityId = packet.targetEntityId,
            targetX = packet.targetX,
            targetY = packet.targetY,
            targetZ = packet.targetZ,
            alertTimeMs = System.currentTimeMillis()
        )

        currentTracked = tracked
        isDismissing = false
        if (animProgress < 0.1f) {
            animProgress = 0.01f
        }
    }

    fun dismiss() {
        if (currentTracked != null && !isDismissing) {
            isDismissing = true
        }
    }

    fun reset() {
        currentTracked = null
        animProgress = 0.0f
        isDismissing = false
    }

    fun resolvePokemonModel(tracked: TrackedPokemon): RenderablePokemon? {
        if (tracked.cachedModel != null) return tracked.cachedModel
        try {
            var species: Species? = null

            if (tracked.speciesId.isNotBlank()) {
                val parsedId = ResourceLocation.tryParse(tracked.speciesId)
                if (parsedId != null) {
                    species = PokemonSpecies.getByIdentifier(parsedId)
                }
            }
            if (species == null) {
                val parsed = ResourceLocation.tryParse(tracked.speciesName)
                if (parsed != null) {
                    species = PokemonSpecies.getByIdentifier(parsed)
                }
            }
            if (species == null) {
                val nameOnly = tracked.speciesName.substringAfter(':').lowercase()
                species = PokemonSpecies.getByName(nameOnly)
            }
            if (species == null) {
                species = PokeInfoDataProvider.findSpecies(tracked.speciesName)
            }
            if (species == null) {
                species = PokemonSpecies.species.firstOrNull {
                    it.resourceIdentifier.toString().equals(tracked.speciesName, ignoreCase = true) ||
                    it.resourceIdentifier.path.equals(tracked.speciesName, ignoreCase = true) ||
                    it.name.equals(tracked.speciesName, ignoreCase = true) ||
                    it.showdownId().equals(tracked.speciesName, ignoreCase = true)
                }
            }

            val aspects = if (tracked.isShiny) setOf("shiny") else emptySet()
            val resolved = if (species != null) RenderablePokemon(species, aspects) else null
            tracked.cachedModel = resolved
            return resolved
        } catch (_: Throwable) {
            return null
        }
    }

    fun getTargetWorldPos(mc: Minecraft): Vec3? {
        val target = currentTracked ?: return null
        val level = mc.level ?: return Vec3(target.targetX, target.targetY, target.targetZ)

        if (target.targetEntityId != -1) {
            val entity = level.getEntity(target.targetEntityId)
            if (entity is PokemonEntity && entity.isAlive && entity.pokemon?.isFainted() == false) {
                target.targetX = entity.x
                target.targetY = entity.y + entity.bbHeight * 0.5
                target.targetZ = entity.z
                return Vec3(target.targetX, target.targetY, target.targetZ)
            }
        }

        return Vec3(target.targetX, target.targetY, target.targetZ)
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val dt = if (lastTickTime == 0L) 0.05f else ((now - lastTickTime) / 1000.0f).coerceIn(0.001f, 0.1f)
        lastTickTime = now

        val target = currentTracked
        if (target == null) {
            animProgress = 0.0f
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level

        // Animation transition
        if (isDismissing) {
            animProgress = (animProgress - dt * 4.0f).coerceAtLeast(0.0f)
            if (animProgress <= 0.001f) {
                currentTracked = null
                isDismissing = false
                animProgress = 0.0f
                return
            }
        } else {
            animProgress = (animProgress + dt * 3.5f).coerceAtMost(1.0f)
        }

        if (player != null && level != null) {
            if (target.targetEntityId != -1) {
                val entity = level.getEntity(target.targetEntityId)
                if (entity is PokemonEntity) {
                    if (entity.isAlive && entity.pokemon?.isFainted() == false) {
                        target.targetX = entity.x
                        target.targetY = entity.y + entity.bbHeight * 0.5
                        target.targetZ = entity.z
                    } else {
                        target.resolved = true
                    }
                }
            }

            val dx = player.x - target.targetX
            val dy = player.y - target.targetY
            val dz = player.z - target.targetZ
            val currentDist = sqrt(dx * dx + dy * dy + dz * dz).toInt()
            target.distance = currentDist

            if (currentDist <= 4 && !target.reached) {
                target.reached = true
            }

            val elapsed = target.getElapsedMs()
            // Auto dismiss after 60 seconds, or 10 seconds after reached/resolved
            if (!isDismissing) {
                if (target.resolved && elapsed > 8_000L) {
                    dismiss()
                } else if (target.reached && elapsed > 20_000L) {
                    dismiss()
                } else if (elapsed > 60_000L) {
                    dismiss()
                }
            }
        }
    }
}
