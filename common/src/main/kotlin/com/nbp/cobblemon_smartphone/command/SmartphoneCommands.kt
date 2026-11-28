package com.nbp.cobblemon_smartphone.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.nbp.cobblemon_smartphone.network.packet.OpenDatapackScreenPacket
import com.nbp.cobblemon_smartphone.ui.datapack.DatapackScreenLoader
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Commands for inspecting and testing datapack-defined smartphone screens (`/smartphone screens`,
 * `/smartphone screen <id>`).
 */
object SmartphoneCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("smartphone")
                .then(
                    Commands.literal("screens")
                        .executes { ctx ->
                            val screens = DatapackScreenLoader.getAllScreens()
                            if (screens.isEmpty()) {
                                ctx.source.sendSuccess({ Component.literal("No datapack smartphone screens loaded.") }, false)
                            } else {
                                val ids = screens.joinToString(", ") { it.id }
                                ctx.source.sendSuccess({ Component.literal("Datapack screens (${screens.size}): $ids") }, false)
                            }
                            1
                        }
                )
                .then(
                    Commands.literal("screen")
                        .then(
                            Commands.argument("id", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val player = ctx.source.playerOrException
                                    if (SmartphoneHelper.getSmartphone(player) == null && !ctx.source.hasPermission(2)) {
                                        ctx.source.sendFailure(Component.translatable("message.cobblemon_smartphone.no_smartphone"))
                                        return@executes 0
                                    }
                                    val id = StringArgumentType.getString(ctx, "id").trim()
                                    val def = DatapackScreenLoader.getScreen(id)
                                    if (def == null) {
                                        ctx.source.sendFailure(Component.literal("Unknown datapack screen: $id"))
                                        return@executes 0
                                    }
                                    OpenDatapackScreenPacket(def.id).sendToPlayer(player)
                                    1
                                }
                        )
                )
                .then(CalendarCommands.buildSubcommand())
        )
    }
}
