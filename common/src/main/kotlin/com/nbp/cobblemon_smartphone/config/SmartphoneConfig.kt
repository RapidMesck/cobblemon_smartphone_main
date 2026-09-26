package com.nbp.cobblemon_smartphone.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class SmartphoneConfig {
    val ignoreUpgrades: List<String> = emptyList()
    val cooldowns = Cooldowns()
    val features = Features()
    val pokeInfo = PokeInfo()
    val gps = Gps()
    val structureCompass = StructureCompass()
    val social = Social()
    val energy = EnergyConfig()

    class Cooldowns {
        val healButton: Int = 60  // 1 minuto em segundos
        val pcButton: Int = 5     // 5 segundos
        val cloudButton: Int = 5  // 5 segundos
        val waystoneButton: Int = 5 // 5 segundos
        val tomsStorageButton: Int = 3 // 3 segundos
        val refinedStorageButton: Int = 3 // 3 segundos
        val ae2Button: Int = 3 // 3 segundos
        val pokedexButton: Int = 1 // 1 segundo
        val socialPost: Int = 30  // 30 segundos entre posts
        val socialMessage: Int = 1 // 1 segundo entre mensagens privadas
    }

    /**
     * [maxPosts] bounds the world save: the feed is a ring buffer and the oldest post is evicted
     * once the cap is passed. It also pairs with [Cooldowns.socialPost] — without a cooldown a
     * spammer could evict the whole server's history. Use 0 for unlimited.
     */
    class Social {
        val maxPosts: Int = 1000
        val maxPostLength: Int = 280
        val feedPageSize: Int = 20
        val maxMessagesPerThread: Int = 500
        val maxMessageLength: Int = 280
        val messagePageSize: Int = 30
        val threadPageSize: Int = 30
        val callRingTimeoutSeconds: Int = 30
    }

    class Gps {
        val maxSearchRadius: Int = 10000
    }

    /** Same lookup /locate structure uses. [searchRadiusChunks] matches vanilla's default search
     *  radius; [skipKnownStructures] mirrors the "skip known structures" argument — leave it false
     *  so a re-search after the world has been explored still finds the nearest instance. */
    class StructureCompass {
        val searchRadiusChunks: Int = 100
        val skipKnownStructures: Boolean = false
    }

    class Features {
        val enableHeal: Boolean = true
        val enablePC: Boolean = true
        val enableCloud: Boolean = true
        val enablePokenav: Boolean = true
        val enableFishingnav: Boolean = true
        val enableCobbleDollars: Boolean = true
        val enableWaystone: Boolean = true
        val enableTomsStorage: Boolean = true
        val enableRefinedStorage: Boolean = true
        val enableAE2: Boolean = true
        val enableRctTrainerCard: Boolean = true
        val enablePokedex: Boolean = true
        val enablePokeInfo: Boolean = true
        val enableGps: Boolean = true
        val enableStructureCompass: Boolean = true
        val enableScanner: Boolean = true
        val enableCrafting: Boolean = true
        val enableQuickActions: Boolean = true
        val enableSocial: Boolean = true
        val enableCalls: Boolean = true
    }

    class PokeInfo {
        val showBaseStats: Boolean = true
        val showAbilities: Boolean = true
        val showEvolution: Boolean = true
        val showTraining: Boolean = true
        val showSpawning: Boolean = true
        val showBreeding: Boolean = true
        val showTypeDefenses: Boolean = true
        val showLevelMoves: Boolean = true
        val showLearnableMoves: Boolean = true
    }

    data class ActionCost(
        val action: String = "",
        val cost: Int = 0
    )

    class EnergyConfig {
        var enableEnergy: Boolean = false
        var baseCapacity: Int = 1000
        var maxReceive: Int = 100
        var defaultCost: Int = 0
        var costs: List<ActionCost> = listOf(
            ActionCost("cobblemon_smartphone:heal", 250),
            ActionCost("cobblemon_smartphone:cloud", 100),
            ActionCost("cobblemon_smartphone:pc", 50),
            ActionCost("cobblemon_smartphone:gps", 50),
            ActionCost("cobblemon_smartphone:structure_compass", 100)
        )

        // Passive Electric Pokémon Party Charging
        var enableElectricPokemonCharging: Boolean = true
        var electricPokemonChargeRate: Int = 10
        var electricPokemonChargeInterval: Int = 20
        var requireElectricPokemonConscious: Boolean = true
        var scaleRateWithElectricPokemonCount: Boolean = false
        var chargeAllSmartphones: Boolean = false

        fun getCost(actionId: String): Int {
            if (!enableEnergy) return 0
            val match = costs.firstOrNull {
                it.action.equals(actionId, ignoreCase = true) ||
                actionId.endsWith(":${it.action}", ignoreCase = true)
            }
            if (match != null) return match.cost
            return com.nbp.cobblemon_smartphone.api.DatapackActionLoader.getActionFeCost(actionId) ?: defaultCost
        }
    }

    companion object {
        private const val PATH = "config/${CobblemonSmartphone.ID}.json"
        private val GSON: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()

        fun load(): SmartphoneConfig {
            val configFile = File(PATH)
            configFile.parentFile.mkdirs()

            var config: SmartphoneConfig
            try {
                if (!configFile.exists()) {
                    configFile.createNewFile()
                }
                FileReader(configFile).use { fileReader ->
                    val element = JsonParser.parseReader(fileReader)
                    if (element != null && element.isJsonObject) {
                        migrateIgnoreUpgrades(element.asJsonObject)
                    }
                    config = GSON.fromJson(element, SmartphoneConfig::class.java) ?: SmartphoneConfig()
                }
            } catch (e: Exception) {
                CobblemonSmartphone.LOGGER.error(e.message, e)
                config = SmartphoneConfig()
            }

            config.save()
            return config
        }

        private fun migrateIgnoreUpgrades(json: com.google.gson.JsonObject) {
            val ignoreUpgrades = json.get("ignoreUpgrades") ?: return
            if (!ignoreUpgrades.isJsonPrimitive || !ignoreUpgrades.asJsonPrimitive.isBoolean) {
                return
            }

            json.add("ignoreUpgrades", JsonArray())
        }
    }

    fun save() {
        val configFile = File(PATH)
        try {
            val fileWriter = FileWriter(configFile)
            GSON.toJson(this, fileWriter)
            fileWriter.flush()
            fileWriter.close()
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error(e.message, e)
        }
    }
}
