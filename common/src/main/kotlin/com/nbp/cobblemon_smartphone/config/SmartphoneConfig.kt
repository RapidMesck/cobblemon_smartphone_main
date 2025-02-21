package com.nbp.cobblemon_smartphone.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class SmartphoneConfig {
    val cooldowns = Cooldowns()
    val features = Features()

    class Cooldowns {
        val healButton: Int = 60  // 1 minuto em segundos
        val pcButton: Int = 5     // 5 segundos
        val cloudButton: Int = 5  // 5 segundos
    }

    class Features {
        val enableHeal: Boolean = true
        val enablePC: Boolean = true
        val enableCloud: Boolean = true
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
                val fileReader = FileReader(configFile)
                config = GSON.fromJson(fileReader, SmartphoneConfig::class.java) ?: SmartphoneConfig()
                fileReader.close()
            } catch (e: Exception) {
                CobblemonSmartphone.LOGGER.error(e.message, e)
                config = SmartphoneConfig()
            }

            config.save()
            return config
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
