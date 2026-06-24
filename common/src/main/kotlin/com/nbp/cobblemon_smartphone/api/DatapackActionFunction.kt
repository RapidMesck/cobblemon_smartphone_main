package com.nbp.cobblemon_smartphone.api

import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.nbp.cobblemon_smartphone.network.handler.HealPokemonHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobblenavPokenavHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobbledollarsShopHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPCHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPokedexHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenWaystonesWarpStoneHandler
import com.nbp.cobblemon_smartphone.network.handler.server.OpenEnderChestHandler
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavPokenavPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCobbledollarsShopPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

enum class DatapackActionFunction(val id: String) {
    OPEN_PC("open_pc") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            OpenPCHandler.execute(player, useNativeCooldown = false)
        }
    },
    HEAL_PARTY("heal_party") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            HealPokemonHandler.execute(player, useNativeCooldown = false)
        }
    },
    OPEN_POKEDEX("open_pokedex") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            OpenPokedexHandler.execute(player, PokedexType.RED, useNativeCooldown = false)
        }
    },
    OPEN_ENDER_CHEST("open_ender_chest") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            OpenEnderChestHandler.execute(player, useNativeCooldown = false)
        }
    },
    OPEN_POKENAV("open_pokenav") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            OpenCobblenavPokenavHandler.handle(OpenCobblenavPokenavPacket(), server, player)
        }
    },
    OPEN_WAYSTONE("open_waystone") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            OpenWaystonesWarpStoneHandler.execute(server, player, useNativeCooldown = false)
        }
    },
    OPEN_COBBLEDOLLARS_SHOP("open_cobbledollars_shop") {
        override fun execute(server: MinecraftServer, player: ServerPlayer) {
            OpenCobbledollarsShopHandler.handle(OpenCobbledollarsShopPacket(), server, player)
        }
    };

    abstract fun execute(server: MinecraftServer, player: ServerPlayer)

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): DatapackActionFunction? = byId[id]
        fun isKnown(id: String): Boolean = id in byId
    }
}
