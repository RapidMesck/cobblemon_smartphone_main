package com.nbp.cobblemon_smartphone.network.handler

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.network.packet.OpenCobbledollarsShopPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Method

object OpenCobbledollarsShopHandler : ServerNetworkPacketHandler<OpenCobbledollarsShopPacket> {
    private const val COBBLEDOLLARS_MOD_ID = "cobbledollars"
    private const val PLAYER_EXTENSIONS_CLASS = "fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt"
    private const val OPEN_SHOP_METHOD = "openShop"

    override fun handle(packet: OpenCobbledollarsShopPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (!isModLoaded(COBBLEDOLLARS_MOD_ID)) {
                player.displayClientMessage(Component.translatable("message.nbp.cobbledollars.unavailable").withColor(0xfd0100), true)
                return@execute
            }

            try {
                val extensionsClass = Class.forName(PLAYER_EXTENSIONS_CLASS)
                val openShopMethod = resolveOpenShopMethod(extensionsClass, player)
                openShopMethod.invoke(null, player)
            } catch (e: Exception) {
                player.displayClientMessage(Component.translatable("message.nbp.cobbledollars.open_failed").withColor(0xfd0100), true)
            }
        }
    }

    private fun resolveOpenShopMethod(extensionsClass: Class<*>, player: ServerPlayer): Method {
        return extensionsClass.methods.firstOrNull { method ->
            method.name == OPEN_SHOP_METHOD
                && method.parameterCount == 1
                && method.parameters[0].type.isAssignableFrom(player.javaClass)
        } ?: throw NoSuchMethodException("Could not resolve openShop(Player) method")
    }
}