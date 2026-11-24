package com.nbp.cobblemon_smartphone.ui.datapack

import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.energy.isInfiniteEnergy
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.ReadOnlyScoreInfo
import kotlin.math.roundToInt

/**
 * Server-side evaluator producing placeholder maps and list collections for datapack HD screens.
 */
object DatapackScreenServerEvaluator {
    private const val TICKS_PER_DAY = 24000L
    private const val MINUTES_PER_DAY = 1440
    private const val DAWN_MINUTES = 360

    data class EvaluatedData(
        val placeholders: Map<String, String>,
        val lists: Map<String, List<Map<String, String>>>
    )

    fun evaluate(player: ServerPlayer, definition: DatapackScreenDefinition): EvaluatedData {
        val placeholders = LinkedHashMap<String, String>()
        populateBuiltinPlaceholders(player, placeholders)

        for ((alias, spec) in definition.placeholders.orEmpty()) {
            placeholders[alias] = resolveCustomPlaceholder(player, spec, placeholders)
        }

        val lists = LinkedHashMap<String, List<Map<String, String>>>()
        for ((listKey, listDef) in definition.lists.orEmpty()) {
            val limit = (listDef.limit ?: 36).coerceIn(1, 64)
            val staticItems = listDef.items.orEmpty()
            lists[listKey] = when (listDef.type?.lowercase()) {
                "party" -> evaluatePartyList(player).take(limit)
                "inventory" -> evaluateInventoryList(player).take(limit)
                "static" -> staticItems.take(limit)
                else -> staticItems.take(limit)
            }
        }

        return EvaluatedData(placeholders, lists)
    }

    private fun populateBuiltinPlaceholders(player: ServerPlayer, out: MutableMap<String, String>) {
        out["player.name"] = player.gameProfile.name
        out["player.uuid"] = player.uuid.toString()
        out["player.level"] = player.experienceLevel.toString()
        out["player.health"] = player.health.roundToInt().toString()
        out["player.max_health"] = player.maxHealth.roundToInt().toString()
        out["player.food"] = player.foodData.foodLevel.toString()
        out["player.x"] = player.blockX.toString()
        out["player.y"] = player.blockY.toString()
        out["player.z"] = player.blockZ.toString()

        val level = player.serverLevel()
        val dayTime = level.dayTime
        val ticksToday = Math.floorMod(dayTime, TICKS_PER_DAY)
        val totalMinutes = ((ticksToday * MINUTES_PER_DAY / TICKS_PER_DAY) + DAWN_MINUTES) % MINUTES_PER_DAY
        out["world.time"] = "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
        out["world.day"] = (dayTime / TICKS_PER_DAY).toString()
        out["world.dimension"] = level.dimension().location().toString()
        val biomeId = level.getBiome(player.blockPosition()).unwrapKey()
            .map { it.location().toString() }
            .orElse("minecraft:plains")
        out["world.biome"] = biomeId

        val phone = SmartphoneHelper.getSmartphone(player)
        if (phone != null) {
            val cur = phone.getEnergy()
            val max = phone.getMaxEnergy()
            val isInf = phone.isInfiniteEnergy()
            out["phone.battery"] = if (isInf) "∞" else cur.toString()
            out["phone.battery_max"] = max.toString()
            out["phone.battery_pct"] = when {
                isInf -> "100"
                max <= 0 -> "0"
                else -> ((cur.toFloat() / max) * 100f).roundToInt().coerceIn(0, 100).toString()
            }
        } else {
            out["phone.battery"] = "0"
            out["phone.battery_max"] = "0"
            out["phone.battery_pct"] = "0"
        }

        val partyCount = runCatching { player.party().count() }.getOrDefault(0)
        out["party.count"] = partyCount.toString()
    }

    private fun resolveCustomPlaceholder(
        player: ServerPlayer,
        spec: String,
        builtins: Map<String, String>
    ): String {
        val trimmed = spec.trim()
        builtins[trimmed]?.let { return it }
        if (trimmed.startsWith("scoreboard:")) {
            val objectiveName = trimmed.removePrefix("scoreboard:").trim()
            return readScoreboard(player, objectiveName).toString()
        }
        return ""
    }

    private fun readScoreboard(player: ServerPlayer, objectiveName: String): Int {
        if (objectiveName.isBlank()) return 0
        val scoreboard = player.server.scoreboard
        val obj = scoreboard.getObjective(objectiveName) ?: return 0
        val info: ReadOnlyScoreInfo = scoreboard.getPlayerScoreInfo(player, obj) ?: return 0
        return info.value()
    }

    private fun evaluatePartyList(player: ServerPlayer): List<Map<String, String>> {
        return try {
            val party = player.party()
            party.mapIndexed { idx, mon ->
                val hpRatio = if (mon.maxHealth > 0) (mon.currentHealth.toFloat() / mon.maxHealth).coerceIn(0f, 1f) else 0f
                mapOf(
                    "index" to (idx + 1).toString(),
                    "species" to mon.species.resourceIdentifier.toString(),
                    "name" to mon.getDisplayName().string,
                    "level" to mon.level.toString(),
                    "hp" to mon.currentHealth.toString(),
                    "max_hp" to mon.maxHealth.toString(),
                    "hp_ratio" to "%.2f".format(java.util.Locale.ROOT, hpRatio),
                    "shiny" to mon.shiny.toString(),
                    "nature" to mon.nature.name.path
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun evaluateInventoryList(player: ServerPlayer): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        val inv = player.inventory
        for (slot in 0 until inv.containerSize) {
            val stack = inv.getItem(slot)
            if (stack.isEmpty) continue
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            out.add(
                mapOf(
                    "slot" to slot.toString(),
                    "item" to itemId,
                    "count" to stack.count.toString(),
                    "name" to stack.hoverName.string
                )
            )
        }
        return out
    }
}
