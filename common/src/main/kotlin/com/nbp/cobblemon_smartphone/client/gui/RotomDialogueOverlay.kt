package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.nbp.cobblemon_smartphone.client.RotomDialogueClientState
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.joml.Quaternionf

object RotomDialogueOverlay {

    private const val BOX_WIDTH = 236
    private const val PADDING = 7
    private const val TOP_BAR_HEIGHT = 15
    private const val TEXT_SCALE = 0.85f
    private const val MODEL_BOX_SIZE = 54

    private val posableState = FloatingState()

    private fun resolvePokemonModel(dialogue: RotomDialogueClientState.ActiveDialogue): RenderablePokemon? {
        if (dialogue.cachedModel != null) return dialogue.cachedModel
        var species: Species? = null
        if (dialogue.nationalDexId > 0) {
            species = PokemonSpecies.getByPokedexNumber(dialogue.nationalDexId)
                ?: PokemonSpecies.species.firstOrNull { it.nationalPokedexNumber == dialogue.nationalDexId }
        }
        if (species == null) {
            species = PokeInfoDataProvider.findSpecies(dialogue.speciesName)
        }
        if (species == null) {
            species = PokemonSpecies.species.firstOrNull {
                it.name.equals(dialogue.speciesName, ignoreCase = true) ||
                it.resourceIdentifier.path.equals(dialogue.speciesName, ignoreCase = true) ||
                it.showdownId().equals(dialogue.speciesName, ignoreCase = true)
            }
        }
        val resolved = if (species != null) RenderablePokemon(species, emptySet()) else null
        dialogue.cachedModel = resolved
        return resolved
    }

    fun render(guiGraphics: GuiGraphics) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val dialogue = RotomDialogueClientState.getActiveDialogue(player.uuid) ?: return

        val window = mc.window
        val screenWidth = window.guiScaledWidth
        val font = mc.font

        val elapsedMs = dialogue.getElapsedMs()
        val maxDurationMs = dialogue.maxDurationMs
        val remainingMs = maxDurationMs - elapsedMs
        val dismissProg = dialogue.getDismissProgress()

        // Fade in suave nos primeiros 180ms e fade out acelerado ao fechar
        val fadeInNorm = (elapsedMs / 180.0f).coerceIn(0.0f, 1.0f)
        val alphaNorm = if (dialogue.isDismissing) {
            (1.0f - dismissProg).coerceIn(0.0f, 1.0f)
        } else when {
            remainingMs < 400L -> (remainingMs / 400.0f).coerceIn(0.0f, 1.0f)
            else -> fadeInNorm
        }
        if (alphaNorm <= 0.01f || (elapsedMs >= maxDurationMs && !dialogue.isDismissing)) {
            if (elapsedMs >= maxDurationMs) {
                RotomDialogueClientState.dismissLocalDialogue()
            }
            return
        }

        val isEn = !dialogue.language.equals("pt", ignoreCase = true)
        val isBrief = dialogue.isBriefMode
        val currentBoxWidth = if (isBrief) 148 else BOX_WIDTH

        // Quebra o texto da curiosidade levando em conta a escala reduzida (0.85f)
        val invScale = 1.0f / TEXT_SCALE
        val maxTextWidth = ((BOX_WIDTH - (PADDING * 2)) * invScale).toInt()
        val fullText = dialogue.curiosityText
        val totalChars = fullText.length

        // Efeito máquina de escrever (~45 caracteres por segundo nos primeiros segundos)
        val visibleChars = if (isBrief) totalChars else ((elapsedMs / 22L).toInt()).coerceIn(0, totalChars)
        val textToRender = if (isBrief) "" else fullText.substring(0, visibleChars)

        val formattedText = Component.literal(textToRender)
        val lines = if (isBrief) emptyList() else font.split(formattedText, maxTextWidth)
        val scaledLineSpacing = (font.lineHeight + 2) * TEXT_SCALE
        val textHeight = (lines.size * scaledLineSpacing).toInt()
        val totalBoxHeight = if (isBrief) MODEL_BOX_SIZE else (TOP_BAR_HEIGHT + PADDING + textHeight + 13)

        // Posicionamento elegante e compacto com saída acelerada (slide-out) para a direita
        val slideOutX = (dismissProg * dismissProg * 140.0f).toInt()
        val boxX = screenWidth - currentBoxWidth - 14 + slideOutX
        val popOffset = (1.0f - fadeInNorm) * 6.0f
        val boxY = (16 + popOffset).toInt().coerceAtLeast(8)

        val alphaInt = (alphaNorm * 255).toInt().coerceIn(0, 255)
        val bgAlpha = (alphaNorm * 0x90).toInt().coerceIn(0, 255)
        val borderAlpha = (alphaNorm * 255).toInt().coerceIn(0, 255)

        val colorShadow = (alphaNorm * 0x35).toInt() shl 24
        val colorBg = (bgAlpha shl 24) or 0x091424
        val colorBorder = (borderAlpha shl 24) or 0x00E5FF
        val colorTopAccent = (borderAlpha shl 24) or 0xFFA000

        // ==========================================
        // CAIXA MENOR CONECTADA: MODELO 3D DO POKÉMON
        // ==========================================
        val modelBoxX = boxX - MODEL_BOX_SIZE - 4
        val modelBoxY = boxY

        // 1. Sombra da caixa do modelo
        guiGraphics.fill(modelBoxX + 3, modelBoxY + 3, modelBoxX + MODEL_BOX_SIZE + 3, modelBoxY + MODEL_BOX_SIZE + 3, colorShadow)

        // 2. Conector físico/holográfico ligando as duas caixas
        val bridgeX1 = modelBoxX + MODEL_BOX_SIZE
        val bridgeX2 = boxX
        val bridgeY1 = boxY + 14
        val bridgeY2 = boxY + 34
        guiGraphics.fill(bridgeX1, bridgeY1 + 2, bridgeX2, bridgeY2 + 2, colorShadow)
        guiGraphics.fill(bridgeX1, bridgeY1, bridgeX2, bridgeY2, colorBg)
        guiGraphics.fill(bridgeX1, bridgeY1, bridgeX2, bridgeY1 + 1, colorBorder)
        guiGraphics.fill(bridgeX1, bridgeY2 - 1, bridgeX2, bridgeY2, colorBorder)
        val pulseAlpha = (((Math.sin(elapsedMs * 0.008) * 0.5 + 0.5) * borderAlpha)).toInt().coerceIn(0, 255)
        guiGraphics.fill(bridgeX1, bridgeY1 + 8, bridgeX2, bridgeY1 + 10, (pulseAlpha shl 24) or 0x00E5FF)

        // 3. Placa e bordas da caixa do modelo 3D
        guiGraphics.fill(modelBoxX, modelBoxY, modelBoxX + MODEL_BOX_SIZE, modelBoxY + MODEL_BOX_SIZE, colorBg)
        guiGraphics.renderOutline(modelBoxX, modelBoxY, MODEL_BOX_SIZE, MODEL_BOX_SIZE, colorBorder)
        guiGraphics.fill(modelBoxX + 1, modelBoxY + 1, modelBoxX + MODEL_BOX_SIZE - 1, modelBoxY + 3, colorTopAccent)

        // 4. Detalhes de HUD cibernético e pedestal holográfico
        guiGraphics.fill(modelBoxX + 8, modelBoxY + MODEL_BOX_SIZE - 6, modelBoxX + MODEL_BOX_SIZE - 8, modelBoxY + MODEL_BOX_SIZE - 4, (borderAlpha shl 24) or 0x00E5FF)
        guiGraphics.fill(modelBoxX + 14, modelBoxY + MODEL_BOX_SIZE - 8, modelBoxX + MODEL_BOX_SIZE - 14, modelBoxY + MODEL_BOX_SIZE - 6, (alphaInt shl 24) or 0x4000E5FF)

        // 5. Renderização do modelo 3D animado do Pokémon
        val model = resolvePokemonModel(dialogue)
        if (model != null) {
            guiGraphics.flush()
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest()

            val matrices = guiGraphics.pose()
            matrices.pushPose()
            val centerX = modelBoxX + MODEL_BOX_SIZE / 2.0
            val centerY = modelBoxY + 7.0
            matrices.translate(centerX, centerY, 50.0)

            // Rotação suave tipo vitrine holográfica 3D (turntable)
            val rotAngle = 25.0f + (Math.sin(elapsedMs * 0.0022) * 25.0).toFloat()
            val rotation = Quaternionf().rotateY(Math.toRadians(rotAngle.toDouble()).toFloat())

            drawProfilePokemon(
                model,
                matrices,
                rotation,
                PoseType.PROFILE,
                posableState,
                0f,
                19f
            )
            matrices.popPose()

            guiGraphics.flush()
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest()
        }

        // ==========================================
        // CAIXA PRINCIPAL: INFORMAÇÕES E CURIOSIDADES
        // ==========================================

        // 1. Sombra projetada
        guiGraphics.fill(boxX + 3, boxY + 3, boxX + currentBoxWidth + 3, boxY + totalBoxHeight + 3, colorShadow)

        // 2. Placa de fundo de vidro cibernético
        guiGraphics.fill(boxX, boxY, boxX + currentBoxWidth, boxY + totalBoxHeight, colorBg)

        // 3. Borda neon ciano
        guiGraphics.renderOutline(boxX, boxY, currentBoxWidth, totalBoxHeight, colorBorder)

        // 4. Friso elétrico laranja superior (estilo Rotom)
        guiGraphics.fill(boxX + 1, boxY + 1, boxX + currentBoxWidth - 1, boxY + 3, colorTopAccent)

        // 5. Rabicho / Ponta triangular do balão de fala apontando em direção ao Rotom Phone
        val tailX = boxX + currentBoxWidth - 24
        val tailY = boxY
        for (i in 0..7) {
            val ty = tailY - i
            val tx1 = tailX + i
            val tx2 = tailX + i + 2
            guiGraphics.fill(tx1, ty, tx2, ty + 1, colorBorder)
        }

        val keyName = com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds.DISMISS_DIALOGUE.translatedKeyMessage.string.uppercase().ifBlank { "T" }
        val hint = if (isEn) "§8[$keyName to close]§r" else "§8[$keyName para fechar]§r"
        val dividerColor = (borderAlpha shl 24) or 0x1A4060
        val icon = "§6⚡§r "

        if (isBrief) {
            // MODO RÁPIDO / DESCOBERTA (Sem texto longo de curiosidade)
            // 1. Cabeçalho Dex
            val dexPrefix = if (dialogue.pokedexNumber.isNotBlank()) "§b${dialogue.pokedexNumber}§r" else ""
            val headerTitle = "$icon§e§lRotom Dex §8• $dexPrefix"
            guiGraphics.drawString(font, headerTitle, boxX + PADDING, boxY + 5, 0xFFFFFFFF.toInt(), false)

            // Divisória
            guiGraphics.fill(boxX + PADDING, boxY + 16, boxX + currentBoxWidth - PADDING, boxY + 17, dividerColor)

            // 2. Nome do Pokémon
            guiGraphics.drawString(font, "§f§l${dialogue.curiosityTitle}", boxX + PADDING, boxY + 19, 0xFFFFFFFF.toInt(), false)

            // 3. Status de Descoberta / Escaneamento
            val statusText = if (!dialogue.isAlreadyRegistered) {
                if (isEn) "§a§l✓ Discovered!" else "§a§l✓ Descoberto!"
            } else {
                if (isEn) "§b§lℹ Scanned!" else "§b§lℹ Analisado!"
            }
            guiGraphics.drawString(font, statusText, boxX + PADDING, boxY + 31, 0xFFFFFFFF.toInt(), false)

            // 4. Dica de fechar com tecla configurada
            val hintWidth = font.width(hint)
            guiGraphics.drawString(font, hint, boxX + currentBoxWidth - PADDING - hintWidth, boxY + 42, (alphaInt shl 24) or 0x7E8C9B, false)
        } else {
            // MODO COMPLETO (Curiosidades detalhadas e efeito máquina de escrever)
            val dexPrefix = if (dialogue.pokedexNumber.isNotBlank()) "§b${dialogue.pokedexNumber}§r " else ""
            val headerTitle = "$icon§e§lRotom Dex §8• $dexPrefix§f§l${dialogue.curiosityTitle}"
            guiGraphics.drawString(font, headerTitle, boxX + PADDING, boxY + 5, 0xFFFFFFFF.toInt(), false)

            // Linha divisória fina sob o cabeçalho
            val dividerY = boxY + TOP_BAR_HEIGHT + 1
            guiGraphics.fill(boxX + PADDING, dividerY, boxX + currentBoxWidth - PADDING, dividerY + 1, dividerColor)

            // Texto da Curiosidade com fonte reduzida (0.85x)
            val textColor = (alphaInt shl 24) or 0xEAF2FB
            val poseStack = guiGraphics.pose()
            poseStack.pushPose()
            poseStack.translate((boxX + PADDING).toFloat(), (dividerY + 5).toFloat(), 0.0f)
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1.0f)
            var lineY = 0
            for (line in lines) {
                guiGraphics.drawString(font, line, 0, lineY, textColor, false)
                lineY += font.lineHeight + 2
            }
            poseStack.popPose()

            // Indicador piscante ▼ ao concluir a digitação e dica de fechar com a tecla configurada
            val isTypingDone = (visibleChars >= totalChars)
            if (isTypingDone) {
                val blink = ((elapsedMs / 450L) % 2 == 0L)
                if (blink) {
                    guiGraphics.drawString(font, "▼", boxX + currentBoxWidth - PADDING - 7, boxY + totalBoxHeight - 11, (borderAlpha shl 24) or 0x00E5FF, false)
                }
            }

            val hintWidth = font.width(hint)
            guiGraphics.drawString(font, hint, boxX + currentBoxWidth - PADDING - hintWidth - (if (isTypingDone) 11 else 0), boxY + totalBoxHeight - 10, (alphaInt shl 24) or 0x7E8C9B, false)
        }
    }
}
