package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.client.RotomTrackerClientState
import com.nbp.cobblemon_smartphone.client.keybind.SmartphoneKeybinds
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneGpsItems
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * In-HUD 3D Tracker Overlay:
 * Displays a clean 3D directional arrow pointing at the tracked Pokémon in real-time,
 * with a 3D hologram of the Pokémon spinning above the arrow (clearly separated with
 * plenty of clearance), plus localized floating status text.
 *
 * Minimalist HUD presentation: no Rotom projector model, no clunky 2D window boxes.
 */
object RotomTrackerOverlay {

    private val arrowStack by lazy { ItemStack(CobblemonSmartphoneGpsItems.GPS_ARROW) }
    private val posableState = FloatingState()

    fun render(guiGraphics: GuiGraphics) {
        if (!RotomTrackerClientState.isVisible()) return

        val target = RotomTrackerClientState.currentTracked ?: return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        val window = mc.window
        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight
        val font = mc.font

        val anim = RotomTrackerClientState.animProgress
        val ease = anim * anim * (3.0f - 2.0f * anim)
        if (ease <= 0.01f) return

        // Posição no canto inferior direito, adaptando caso o GPS Compass esteja ativo
        val isGpsActive = GpsClientState.tracking
        val anchorX = if (isGpsActive) screenWidth - 100.0 else screenWidth - 44.0
        val anchorY = screenHeight - 24.0 + (1.0f - ease) * 35.0

        val poseStack = guiGraphics.pose()

        // 1. Seta 3D apontando em tempo real com precisão matemática absoluta para o Pokémon
        val targetWorldPos = RotomTrackerClientState.getTargetWorldPos(mc)
            ?: Vec3(target.targetX, target.targetY, target.targetZ)
        val distanceVec = player.position().vectorTo(targetWorldPos)
        val yaw = atan2(distanceVec.z, distanceVec.x).toFloat()
        val horizontalDistance = sqrt(distanceVec.x * distanceVec.x + distanceVec.z * distanceVec.z)

        // Inclinação suave da seta conforme proximidade vertical
        val pitchFactor = ((50.0 - horizontalDistance) / (50.0 - 5.0)).coerceIn(0.0, 1.0)
        val pitch = (atan2(distanceVec.y, horizontalDistance) * pitchFactor).toFloat()

        val arrowX = anchorX
        val arrowY = anchorY - 14.0
        poseStack.pushPose()
        poseStack.translate(arrowX, arrowY, 60.0)
        poseStack.mulPose(
            Quaternionf()
                .rotateZ(Math.PI.toFloat())
                .fromEulerXYZDegrees(Vector3f(player.xRot, -player.yRot, 0f))
                .rotateY(0.5f * Math.PI.toFloat() + yaw)
                .rotateX(-pitch)
        )
        val arrowScale = 22.0f * ease
        poseStack.scale(arrowScale, arrowScale, -arrowScale)

        Lighting.setupFor3DItems()
        mc.itemRenderer.renderStatic(
            arrowStack,
            ItemDisplayContext.GROUND,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            guiGraphics.bufferSource(),
            level,
            0
        )
        guiGraphics.flush()
        poseStack.popPose()

        // 2. Holograma 3D do Pokémon flutuando acima da seta com espaçamento claro (não fica em cima da seta)
        val model = RotomTrackerClientState.resolvePokemonModel(target)
        val holoX = anchorX
        val holoY = anchorY - 58.0
        if (model != null) {
            try {
                poseStack.pushPose()
                poseStack.translate(holoX, holoY, 90.0)
                val elapsedMs = System.currentTimeMillis() - target.alertTimeMs
                val rotAngle = (elapsedMs * 0.08f) % 360.0f
                val rotation = Quaternionf().rotateY(Math.toRadians(rotAngle.toDouble()).toFloat())

                RenderSystem.enableDepthTest()
                drawProfilePokemon(
                    model,
                    poseStack,
                    rotation,
                    PoseType.PROFILE,
                    posableState,
                    0f,
                    17.0f * ease
                )
                guiGraphics.flush()
                RenderSystem.disableDepthTest()
                poseStack.popPose()
            } catch (_: Throwable) {
                // Silencioso em caso de incompatibilidade momentânea de modelo
            }
        }

        Lighting.setupForFlatItems()

        // 3. Informações flutuantes no HUD (Nome, Distância, Altitude, IV) totalmente localizadas
        val isShiny = target.isShiny
        val shinyStar = if (isShiny) "§e§l★ " else ""
        val shinyEnd = if (isShiny) " ★" else ""
        val targetIcon = if (target.isTargetMatch) "§b§l🎯 " else ""
        val titleText = if (target.reached) {
            "§a§l✓ ${target.speciesName}"
        } else {
            "$shinyStar$targetIcon§f§l${target.speciesName}$shinyEnd"
        }

        val dyInt = (targetWorldPos.y - player.y).toInt()
        val absDy = abs(dyInt)
        val altText = when {
            dyInt > 2 -> " §b↑${absDy}m"
            dyInt < -2 -> " §c↓${absDy}m"
            else -> ""
        }
        val distColor = when {
            target.distance <= 15 -> "§a"
            target.distance <= 45 -> "§e"
            else -> "§6"
        }

        val inRangeStr = Component.translatable("cobblemon_smartphone.rotom.tracker_in_range").string
        val distLine = if (target.reached) {
            "§a§l$inRangeStr §7• §f${target.ivPercent}% IV"
        } else {
            "$distColor${target.distance}m$altText §7• §f${target.ivPercent}% IV"
        }

        val keyName = SmartphoneKeybinds.DISMISS_DIALOGUE.translatedKeyMessage.string.uppercase().ifBlank { "Y" }
        val hintText = "§7" + Component.translatable("cobblemon_smartphone.rotom.tracker_close_hint", keyName).string + "§r"

        // Alinha os textos à esquerda da seta e do holograma
        val textRightX = (anchorX - 20.0).toInt()
        val line1W = font.width(titleText)
        val line2W = font.width(distLine)
        val line3W = font.width(hintText)

        val textBaseY = (anchorY - 52.0).toInt()
        guiGraphics.drawString(font, titleText, textRightX - line1W, textBaseY, 0xFFFFFFFF.toInt(), true)
        guiGraphics.drawString(font, distLine, textRightX - line2W, textBaseY + 13, 0xFFFFFFFF.toInt(), true)
        guiGraphics.drawString(font, hintText, textRightX - line3W, textBaseY + 26, 0xFFAAAAAA.toInt(), true)
    }

    fun handleClick(): Boolean = false
    fun handleClick(mouseX: Double, mouseY: Double): Boolean = false
}
