package com.nbp.cobblemon_smartphone.curiosity

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class PokemonCuriosity(
    val id: Int = 0,
    val pokedex_number: String = "",
    val name: String = "",
    val generation: Int = 1,
    val texto_en: String = "",
    val texto_pt: String = "",
    val texto_tela: String = "",
    val texto_ia: String = ""
) {
    /**
     * Retorna a curiosidade no idioma especificado ("en" = Inglês, "pt" = Português).
     */
    fun getCuriosityText(language: String): String {
        val lang = language.lowercase().trim()
        if (lang == "pt" || lang.startsWith("pt_") || lang == "portugues") {
            if (texto_pt.isNotBlank()) return texto_pt
            if (texto_tela.isNotBlank() && isPortugueseText(texto_tela)) return texto_tela
            return texto_en.ifBlank { texto_tela }
        }
        // Padrão: Inglês
        if (texto_en.isNotBlank()) return texto_en
        if (texto_tela.isNotBlank() && isEnglishText(texto_tela)) return texto_tela
        return texto_pt.ifBlank { texto_tela }
    }

    /**
     * Retorna a descrição da curiosidade limpa no idioma selecionado, sem o prefixo "Nome: ".
     */
    fun getCleanDescription(language: String = "en"): String {
        val raw = getCuriosityText(language)
        val colonIndex = raw.indexOf(':')
        if (colonIndex in 1..30) {
            val prefix = raw.substring(0, colonIndex).trim()
            if (prefix.equals(name, ignoreCase = true) || prefix.contains(name, ignoreCase = true)) {
                return raw.substring(colonIndex + 1).trim()
            }
        }
        return raw.trim()
    }

    val cleanDescription: String
        get() = getCleanDescription("en")

    private fun isPortugueseText(text: String): Boolean {
        val lower = " " + text.lowercase() + " "
        return lower.contains(" de ") || lower.contains(" do ") || lower.contains(" da ") ||
               lower.contains(" para ") || lower.contains(" com ") || lower.contains(" um ") ||
               lower.contains(" uma ") || lower.contains(" seu ") || lower.contains(" sua ") ||
               lower.contains(" não ") || lower.contains(" são ")
    }

    private fun isEnglishText(text: String): Boolean {
        val lower = " " + text.lowercase() + " "
        return lower.contains(" the ") || lower.contains(" of ") || lower.contains(" to ") ||
               lower.contains(" and ") || lower.contains(" with ") || lower.contains(" this ") ||
               lower.contains(" that ") || lower.contains(" are ")
    }
}

object PokemonCuriosityRepository {
    private val byId = ConcurrentHashMap<Int, PokemonCuriosity>()
    private val byNormalizedName = ConcurrentHashMap<String, PokemonCuriosity>()
    @Volatile
    private var initialized = false

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace(".", "")
            .replace("'", "")
            .replace("♀", "f")
            .replace("♂", "m")
    }

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                // Tenta carregar do recurso embutido no JAR do mod
                val stream = PokemonCuriosityRepository::class.java.getResourceAsStream(
                    "/assets/cobblemon_smartphone/data/curiosidades_pokemon.json"
                ) ?: run {
                    // Fallback para o arquivo no diretório de desenvolvimento caso executando em ambiente de teste
                    val devFile = File("/home/adrian/Documentos/Smartphone/TTS ROTOM/Pokedex_TTS/curiosidades_pokemon.json")
                    if (devFile.exists()) devFile.inputStream() else null
                }

                if (stream != null) {
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        val type = object : TypeToken<List<PokemonCuriosity>>() {}.type
                        val list: List<PokemonCuriosity> = Gson().fromJson(reader, type)
                        for (item in list) {
                            byId[item.id] = item
                            byNormalizedName[normalize(item.name)] = item
                        }
                        CobblemonSmartphone.LOGGER.info("Carregadas ${byId.size} curiosidades de Pokémon do Rotom Dex!")
                    }
                } else {
                    CobblemonSmartphone.LOGGER.warn("Arquivo curiosidades_pokemon.json não encontrado nos assets!")
                }
            } catch (e: Exception) {
                CobblemonSmartphone.LOGGER.error("Falha ao carregar curiosidades de Pokémon", e)
            } finally {
                initialized = true
            }
        }
    }

    fun getById(id: Int): PokemonCuriosity? {
        if (!initialized) init()
        return byId[id]
    }

    fun getByName(name: String): PokemonCuriosity? {
        if (!initialized) init()
        return byNormalizedName[normalize(name)]
    }

    fun getCuriosity(speciesName: String, nationalDexId: Int? = null): PokemonCuriosity? {
        if (!initialized) init()
        if (nationalDexId != null && nationalDexId > 0) {
            val byDex = byId[nationalDexId]
            if (byDex != null) return byDex
        }
        return getByName(speciesName)
    }

    /**
     * Verifica se existe uma curiosidade personalizada específica para uma dada forma/variante do Pokémon.
     * Retorna false se o Pokémon for uma variante (como regional, forma especial, etc.) que não possui uma curiosidade única própria.
     */
    fun hasDistinctFormCuriosity(speciesName: String, formName: String): Boolean {
        if (formName.isBlank() || formName.equals("normal", ignoreCase = true) || formName.equals("standard", ignoreCase = true)) {
            return true
        }
        val candidateNames = listOf(
            "$formName $speciesName",
            "$speciesName $formName",
            "${speciesName}_$formName"
        )
        return candidateNames.any { getByName(it) != null }
    }
}
