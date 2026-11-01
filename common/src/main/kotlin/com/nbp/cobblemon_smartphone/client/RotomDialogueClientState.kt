package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.platform.InputConstants
import com.nbp.cobblemon_smartphone.curiosity.PokemonCuriosityRepository
import com.nbp.cobblemon_smartphone.network.packet.RotomScanCompletePacket
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RotomDialogueClientState {
    const val DISMISS_DURATION_MS = 600L

    data class ActiveDialogue(
        val scannerUuid: UUID,
        val speciesName: String,
        val nationalDexId: Int,
        val pokedexNumber: String,
        val curiosityTitle: String,
        val curiosityText: String,
        val startTimeMs: Long,
        val language: String = "en",
        val isBriefMode: Boolean = false,
        val isAlreadyRegistered: Boolean = false,
        var cachedModel: RenderablePokemon? = null,
        var isDismissing: Boolean = false,
        var dismissStartTimeMs: Long = 0L
    ) {
        val maxDurationMs: Long
            get() = if (isBriefMode) 4500L else 30_000L

        fun getElapsedMs(now: Long = System.currentTimeMillis()): Long = (now - startTimeMs).coerceAtLeast(0L)

        fun getDismissProgress(now: Long = System.currentTimeMillis()): Float {
            if (!isDismissing) return 0.0f
            return ((now - dismissStartTimeMs) / DISMISS_DURATION_MS.toFloat()).coerceIn(0.0f, 1.0f)
        }
    }

    private val activeDialogues = ConcurrentHashMap<UUID, ActiveDialogue>()
    private var wasKeyDown: Boolean = false

    fun getActiveDialogue(playerUuid: UUID): ActiveDialogue? {
        return activeDialogues[playerUuid]
    }

    fun hasActiveDialogue(playerUuid: UUID): Boolean = activeDialogues.containsKey(playerUuid)

    /**
     * Retorna true se o Rotom deve se deslocar para a frente do jogador para falar a curiosidade.
     * No modo compacto (apenas pokemon descoberto) ou silencioso, o Rotom NÃO vai para a frente do jogador!
     */
    fun shouldRotomFlyInFront(playerUuid: UUID): Boolean {
        val dialogue = activeDialogues[playerUuid] ?: return false
        return !dialogue.isBriefMode
    }

    fun getDismissProgress(playerUuid: UUID): Float {
        val dialogue = activeDialogues[playerUuid] ?: return 0.0f
        if (dialogue.isBriefMode) return 0.0f
        return dialogue.getDismissProgress()
    }

    fun isDismissing(playerUuid: UUID): Boolean {
        val dialogue = activeDialogues[playerUuid] ?: return false
        if (dialogue.isBriefMode) return false
        return dialogue.isDismissing
    }

    fun dismissDialogue(playerUuid: UUID) {
        val current = activeDialogues[playerUuid]
        if (current != null) {
            current.isDismissing = true
            current.dismissStartTimeMs = System.currentTimeMillis()
        }
    }

    fun dismissLocalDialogue() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val current = activeDialogues[player.uuid] ?: return
        if (current.isDismissing) return

        current.isDismissing = true
        current.dismissStartTimeMs = System.currentTimeMillis()
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1.25f)
        // Informa o servidor imediatamente para replicar aos outros jogadores na sessão
        com.nbp.cobblemon_smartphone.network.packet.CloseRotomDialoguePacket().sendToServer()
    }

    fun onScanComplete(packet: RotomScanCompletePacket) {
        val radarConfig = RotomRadarClientState.config
        if (radarConfig.isSilentMode) return

        val radarLang = radarConfig.language
        val lang = if (radarLang.isNotBlank()) radarLang else RotomScannerClientState.clientConfig.curiosityLanguage
        val rawSpecies = packet.speciesName
        val parsedRes = net.minecraft.resources.ResourceLocation.tryParse(rawSpecies)
        val speciesObj = if (parsedRes != null) com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(parsedRes) else null
        val actualName = speciesObj?.translatedName?.string ?: speciesObj?.name ?: rawSpecies.substringAfter(':').replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

        val curiosity = PokemonCuriosityRepository.getCuriosity(actualName, packet.nationalDexId)
        val isModWithoutCuriosity = (curiosity == null)
        val isBrief = radarConfig.isBriefMode || isModWithoutCuriosity

        val title = curiosity?.name?.ifBlank { null } ?: actualName
        val dexNum = if (curiosity != null) {
            curiosity.pokedex_number.ifBlank { null } ?: if (packet.nationalDexId > 0) "#%04d".format(packet.nationalDexId) else ""
        } else {
            if (packet.nationalDexId > 0 && parsedRes?.namespace == "cobblemon") "#%04d".format(packet.nationalDexId) else ""
        }
        val curiosityText = curiosity?.getCleanDescription(lang)?.ifBlank { null }
            ?: if (lang.equals("pt", ignoreCase = true)) {
                "Dados biológicos escaneados com sucesso! Este Pokémon agora está catalogado em sua Pokédex."
            } else {
                "Biological data scanned successfully! This Pokémon is now cataloged in your Pokédex."
            }

        val now = System.currentTimeMillis()
        val dialogue = ActiveDialogue(
            scannerUuid = packet.scannerPlayerUuid,
            speciesName = packet.speciesName,
            nationalDexId = packet.nationalDexId,
            pokedexNumber = dexNum,
            curiosityTitle = title,
            curiosityText = curiosityText,
            startTimeMs = now,
            language = lang,
            isBriefMode = isBrief,
            isAlreadyRegistered = packet.isAlreadyRegistered
        )

        activeDialogues[packet.scannerPlayerUuid] = dialogue

        val mc = Minecraft.getInstance()
        if (packet.scannerPlayerUuid == mc.player?.uuid) {
            val player = mc.player ?: return
            // Feedback sonoro amigável do Rotom ao entregar o relatório
            player.playSound(CobblemonSounds.POKEDEX_SCAN_DETAIL, 0.75f, 1.15f)
        }
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        val localPlayer = mc.player
        val now = System.currentTimeMillis()

        // 1. Atualizar e expirar todos os diálogos ativos (tanto do jogador local quanto de outros no multiplayer)
        val iterator = activeDialogues.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val uuid = entry.key
            val dialogue = entry.value

            if (dialogue.isDismissing) {
                if (now - dialogue.dismissStartTimeMs >= DISMISS_DURATION_MS) {
                    iterator.remove()
                }
            } else if (now - dialogue.startTimeMs >= dialogue.maxDurationMs) {
                dialogue.isDismissing = true
                dialogue.dismissStartTimeMs = now
                if (uuid == localPlayer?.uuid) {
                    com.nbp.cobblemon_smartphone.network.packet.CloseRotomDialoguePacket().sendToServer()
                }
            }
        }

        // 2. Verificar tecla de atalho para fechar diálogo do jogador local
        if (localPlayer == null) {
            wasKeyDown = false
            return
        }
        val current = activeDialogues[localPlayer.uuid]
        if (current == null || current.isDismissing) {
            wasKeyDown = false
            return
        }

        // Verifica a tecla configurada em controles (default Y, ou customizada pelo jogador)
        val keyMapping = com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds.DISMISS_DIALOGUE
        val isDownNow = keyMapping.isDown
        if ((keyMapping.consumeClick() || (isDownNow && !wasKeyDown)) && mc.screen == null) {
            wasKeyDown = isDownNow
            dismissLocalDialogue()
            return
        }
        wasKeyDown = isDownNow
    }
}
