package com.nbp.cobblemon_smartphone.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.context.CommandContext
import com.nbp.cobblemon_smartphone.social.SocialData
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component

/**
 * Operator moderation for the Social app.
 *
 * Uses [GameProfileArgument] rather than EntityArgument so offline players can be banned too —
 * the player who needs moderating is often the one who just logged off.
 */
object SocialCommands {
    private const val OP_PERMISSION_LEVEL = 2

    private enum class Scope { POSTS, DMS, ALL }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("social")
                .requires { it.hasPermission(OP_PERMISSION_LEVEL) }
                .then(
                    Commands.literal("ban")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes { setBan(it, Scope.ALL, true) }
                                .then(Commands.literal("posts").executes { setBan(it, Scope.POSTS, true) })
                                .then(Commands.literal("dms").executes { setBan(it, Scope.DMS, true) })
                                .then(Commands.literal("all").executes { setBan(it, Scope.ALL, true) })
                        )
                )
                .then(
                    Commands.literal("unban")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes { setBan(it, Scope.ALL, false) }
                        )
                )
                .then(
                    Commands.literal("delete")
                        .then(
                            Commands.argument("id", LongArgumentType.longArg(1))
                                .executes { deletePost(it) }
                        )
                )
                .then(
                    Commands.literal("cleanphotos")
                        .executes { cleanPhotos(it) }
                )
                .then(
                    Commands.literal("reports")
                        .executes { listReports(it) }
                )
                .then(
                    Commands.literal("clearreports")
                        .executes { clearReports(it) }
                )
        )
    }

    private fun setBan(context: CommandContext<CommandSourceStack>, scope: Scope, banned: Boolean): Int {
        val source = context.source
        val server = source.server
        val profiles = GameProfileArgument.getGameProfiles(context, "targets")
        val data = SocialData.get(server)

        profiles.forEach { profile ->
            if (scope == Scope.POSTS || scope == Scope.ALL) data.setPostBanned(profile.id, banned)
            if (scope == Scope.DMS || scope == Scope.ALL) data.setDmBanned(profile.id, banned)

            val key = if (banned) "commands.nbp.social.banned" else "commands.nbp.social.unbanned"
            source.sendSuccess({ Component.translatable(key, profile.name, scope.name.lowercase()) }, true)
        }
        return profiles.size
    }

    private fun deletePost(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val id = LongArgumentType.getLong(context, "id")
        val removed = SocialData.get(source.server).removePost(id)

        if (removed) {
            source.sendSuccess({ Component.translatable("commands.nbp.social.deleted", id) }, true)
            return 1
        }
        source.sendFailure(Component.translatable("commands.nbp.social.not_found", id))
        return 0
    }

    private fun cleanPhotos(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server
        val deleted = com.nbp.cobblemon_smartphone.social.SocialPhotoManager.cleanupOrphans(server)
        source.sendSuccess({ Component.translatable("commands.nbp.social.cleaned_photos", deleted) }, true)
        return deleted
    }

    private fun listReports(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server
        val reports = SocialData.get(server).reports()
        if (reports.isEmpty()) {
            source.sendSuccess({ Component.translatable("commands.nbp.social.no_reports") }, false)
            return 0
        }
        source.sendSuccess({ Component.literal("=== Relatórios de Denúncia (${reports.size}) ===") }, false)
        reports.forEach { r ->
            val msg = "#${r.id} [${r.targetType}] Alvo #${r.targetId} | Por: ${r.reporterName} | Motivo: \"${r.reason}\""
            source.sendSuccess({ Component.literal(msg) }, false)
        }
        return reports.size
    }

    private fun clearReports(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server
        val count = SocialData.get(server).clearReports()
        source.sendSuccess({ Component.translatable("commands.nbp.social.cleared_reports", count) }, true)
        return count
    }
}
