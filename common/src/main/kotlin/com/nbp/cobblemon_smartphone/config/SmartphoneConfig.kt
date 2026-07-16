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

    class Cooldowns {
        val healButton: Int = 60  // 1 minuto em segundos
        val pcButton: Int = 5     // 5 segundos
        val cloudButton: Int = 5  // 5 segundos
        val waystoneButton: Int = 5 // 5 segundos
        val pokedexButton: Int = 1 // 1 segundo
    }

    class Features {
        val enableHeal: Boolean = true
        val enablePC: Boolean = true
        val enableCloud: Boolean = true
        val enablePokenav: Boolean = true
        val enableCobbleDollars: Boolean = true
        val enableWaystone: Boolean = true
        val enablePokedex: Boolean = true
        val enablePokeInfo: Boolean = true
        val enableScanner: Boolean = true
        val enableCrafting: Boolean = true
        val enableQuickActions: Boolean = true
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
