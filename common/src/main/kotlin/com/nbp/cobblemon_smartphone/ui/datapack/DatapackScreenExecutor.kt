package com.nbp.cobblemon_smartphone.ui.datapack

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.DatapackActionFunction
import com.nbp.cobblemon_smartphone.energy.SmartphoneEnergyHelper
import com.nbp.cobblemon_smartphone.energy.consumeEnergy
import com.nbp.cobblemon_smartphone.energy.hasEnoughEnergy
import com.nbp.cobblemon_smartphone.network.packet.OpenDatapackScreenPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackScreenDataPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative server-side executor for datapack screen actions.
 * Actions are strictly resolved by key from the loaded [DatapackScreenDefinition] — never
 * accepting raw commands or arbitrary effects directly from client packets.
 */
object DatapackScreenExecutor {
    private val lastActionUse = ConcurrentHashMap<UUID, MutableMap<String, Long>>()

    fun sendDataSnapshot(player: ServerPlayer, screenId: String) {
        val def = DatapackScreenLoader.getScreen(screenId) ?: return
        if (SmartphoneHelper.getSmartphone(player) == null) return
        val evaluated = DatapackScreenServerEvaluator.evaluate(player, def)
        SyncDatapackScreenDataPacket(def.id, evaluated.placeholders, evaluated.lists).sendToPlayer(player)
    }

    fun executeAction(
        server: MinecraftServer,
        player: ServerPlayer,
        screenId: String,
        actionId: String,
        clientInputs: Map<String, String>,
        itemContext: Map<String, String>
    ) {
        val def = DatapackScreenLoader.getScreen(screenId)
        if (def == null) {
            CobblemonSmartphone.LOGGER.warn("Player {} triggered action '{}' on unknown screen '{}'", player.gameProfile.name, actionId, screenId)
            return
        }
        val actionDef = def.actions.orEmpty()[actionId]
        if (actionDef == null) {
            CobblemonSmartphone.LOGGER.warn("Player {} triggered unknown action '{}' on screen '{}'", player.gameProfile.name, actionId, screenId)
            return
        }

        val phone = SmartphoneHelper.getSmartphone(player)
        if (phone == null) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.no_smartphone").withColor(0xfd0100),
                true
            )
            return
        }

        val requiredUpgrade = actionDef.requireUpgrade ?: def.requireUpgrade
        if (!requiredUpgrade.isNullOrBlank() &&
            !SmartphoneHelper.hasUpgradeOnAnySmartphone(player, requiredUpgrade, "${def.id}:$actionId")
        ) {
            CobblemonSmartphone.LOGGER.warn(
                "Player {} tried upgrade-locked screen action '{}:{}' without upgrade '{}'",
                player.gameProfile.name, def.id, actionId, requiredUpgrade
            )
            return
        }

        if (actionDef.cooldownSeconds > 0) {
            val nowSec = System.currentTimeMillis() / 1000
            val key = "${def.id}:$actionId"
            val playerCooldowns = lastActionUse.computeIfAbsent(player.uuid) { ConcurrentHashMap() }
            val elapsed = nowSec - (playerCooldowns[key] ?: 0L)
            if (elapsed < actionDef.cooldownSeconds) {
                val rem = (actionDef.cooldownSeconds - elapsed).toInt()
                player.displayClientMessage(
                    Component.translatable("message.nbp.datapack_action.cooldown", rem).withColor(0xfd0100),
                    true
                )
                return
            }
            playerCooldowns[key] = nowSec
        }

        if (actionDef.feCost > 0 && CobblemonSmartphone.config.energy.enableEnergy) {
            if (!phone.hasEnoughEnergy(actionDef.feCost)) {
                player.displayClientMessage(
                    Component.translatable("message.cobblemon_smartphone.insufficient_energy", actionDef.feCost)
                        .withColor(0xfd0100),
                    true
                )
                return
            }
            phone.consumeEnergy(actionDef.feCost)
        } else {
            // Also honor config override if configured per screen/action ID
            if (!SmartphoneEnergyHelper.checkAndConsumeEnergy(player, "${def.id}:$actionId")) {
                return
            }
        }

        val evaluatedBefore = DatapackScreenServerEvaluator.evaluate(player, def)
        val sanitizedInputs = clientInputs.mapValues { (_, v) -> DatapackExpr.sanitizeInputForCommand(v) }
        val sanitizedItem = itemContext.mapValues { (_, v) -> DatapackExpr.sanitizeInputForCommand(v) }
        val exprCtx = DatapackExpr.EvalContext(
            placeholders = evaluatedBefore.placeholders,
            lists = evaluatedBefore.lists,
            inputs = sanitizedInputs,
            item = sanitizedItem
        )

        var needsRefresh = false
        for (effect in actionDef.effects.orEmpty()) {
            when (effect.type?.lowercase()) {
                "message" -> {
                    val raw = effect.text ?: continue
                    val resolved = DatapackExpr.resolveTemplate(raw, exprCtx)
                    player.displayClientMessage(Component.literal(resolved), effect.actionbar)
                }
                "sound" -> {
                    val loc = effect.sound?.let(ResourceLocation::tryParse) ?: continue
                    BuiltInRegistries.SOUND_EVENT.getOptional(loc).ifPresent { evt ->
                        player.serverLevel().playSound(
                            null, player.blockPosition(), evt, SoundSource.PLAYERS,
                            (effect.volume ?: 0.7f).coerceIn(0f, 2f), (effect.pitch ?: 1.0f).coerceIn(0.5f, 2f)
                        )
                    }
                }
                "function" -> {
                    val fnId = effect.function ?: continue
                    DatapackActionFunction.fromId(fnId)?.execute(server, player)
                    needsRefresh = true
                }
                "give_item" -> {
                    val itemLoc = effect.item?.let { DatapackExpr.resolveTemplate(it, exprCtx) }?.let(ResourceLocation::tryParse) ?: continue
                    val item = BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(null) ?: continue
                    val count = (effect.count ?: 1).coerceIn(1, 64)
                    val stack = ItemStack(item, count)
                    if (!player.inventory.add(stack)) {
                        player.drop(stack, false)
                    }
                    needsRefresh = true
                }
                "consume_item" -> {
                    val itemLoc = effect.item?.let { DatapackExpr.resolveTemplate(it, exprCtx) }?.let(ResourceLocation::tryParse) ?: continue
                    val targetItem = BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(null) ?: continue
                    val needed = (effect.count ?: 1).coerceIn(1, 64)
                    val available = countItemInInventory(player, targetItem)
                    if (available < needed) {
                        player.displayClientMessage(
                            Component.literal("Missing required items (${targetItem.description.string} x$needed)")
                                .withColor(0xfd0100),
                            true
                        )
                        return
                    }
                    consumeItemFromInventory(player, targetItem, needed)
                    needsRefresh = true
                }
                "command" -> {
                    val rawCmd = effect.command ?: continue
                    val resolvedCmd = DatapackExpr.resolveTemplate(rawCmd, exprCtx).trim().removePrefix("/")
                    if (resolvedCmd.isNotEmpty() && !resolvedCmd.contains('\n') && !resolvedCmd.contains('\r')) {
                        val source = if (effect.asServer) {
                            server.createCommandSourceStack().withSuppressedOutput()
                        } else {
                            player.createCommandSourceStack()
                        }
                        server.commands.performPrefixedCommand(source, resolvedCmd)
                        needsRefresh = true
                    }
                }
                "open_screen" -> {
                    val target = effect.screen?.let { DatapackExpr.resolveTemplate(it, exprCtx) }?.trim() ?: continue
                    if (target.isNotEmpty()) {
                        OpenDatapackScreenPacket(target).sendToPlayer(player)
                    }
                }
                "refresh_data" -> {
                    needsRefresh = true
                }
            }
        }

        if (needsRefresh) {
            sendDataSnapshot(player, def.id)
        }
    }

    private fun countItemInInventory(player: ServerPlayer, target: net.minecraft.world.item.Item): Int {
        var total = 0
        for (i in 0 until player.inventory.containerSize) {
            val s = player.inventory.getItem(i)
            if (!s.isEmpty && s.`is`(target)) total += s.count
        }
        return total
    }

    private fun consumeItemFromInventory(player: ServerPlayer, target: net.minecraft.world.item.Item, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val s = player.inventory.getItem(i)
            if (!s.isEmpty && s.`is`(target)) {
                val take = minOf(remaining, s.count)
                s.shrink(take)
                remaining -= take
            }
        }
        player.inventory.setChanged()
    }
}
