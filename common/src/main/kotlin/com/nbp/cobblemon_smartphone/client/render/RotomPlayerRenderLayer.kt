package com.nbp.cobblemon_smartphone.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext

class RotomPlayerRenderLayer(
    renderer: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(renderer) {

    companion object {
        val ROTOM_MODEL_LOCATION = ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/rotom"),
            "inventory"
        )

        // Escala geral do Rotom
        const val SCALE = 1.10f

        // Posição no ombro (Smartphone fechado) - flutuando um pouco acima do ombro
        const val X_SHOULDER = -0.65f
        const val Y_SHOULDER_STANDING = -0.40f
        const val Y_SHOULDER_CROUCHING = -0.15f
        const val Z_SHOULDER = -0.05f
        const val YAW_SHOULDER = 195.0f

        // Posição na frente do rosto (Smartphone aberto)
        const val X_FACE = 0.0f
        const val Y_FACE_STANDING = -0.35f
        const val Y_FACE_CROUCHING = -0.10f
        const val Z_FACE = -0.50f
        const val YAW_FACE = 15.0f // Vira para olhar direto no rosto do player

        // Velocidade da transição de voo (1.0 / 3.5 = ~0.28 segundos)
        const val TRANSITION_SPEED = 3.5f

        class RotomAnimState {
            var openProgress: Float = 0.0f
            var lastRenderTime: Long = 0L
        }

        private val playerAnimStates = java.util.concurrent.ConcurrentHashMap<java.util.UUID, RotomAnimState>()

        fun isSmartphoneScreen(screen: Screen?): Boolean {
            if (screen == null) return false
            val name = screen.javaClass.name
            return name.startsWith("com.nbp.cobblemon_smartphone.client.gui") && !name.contains("Overlay")
        }
    }

    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        player: AbstractClientPlayer,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTick: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        if (player.isInvisible || player.isSleeping || player.isDeadOrDying) return

        val orangeStack = SmartphoneHelper.getEquippedOrangeSmartphone(player) ?: return

        val mc = Minecraft.getInstance()
        val bakedModel = mc.modelManager.getModel(ROTOM_MODEL_LOCATION)
        if (bakedModel == mc.modelManager.missingModel) return

        val isLocalPlayer = (player == mc.player)
        val isScreenOpen = if (isLocalPlayer) {
            val open = isSmartphoneScreen(mc.screen)
            RotomClientManager.updateLocalState(open)
            open
        } else {
            RotomClientManager.isPlayerOpen(player.uuid)
        }

        val animState = playerAnimStates.computeIfAbsent(player.uuid) { RotomAnimState() }

        // Atualização da animação de transição baseada em delta de tempo real para cada jogador
        val now = System.currentTimeMillis()
        val dt = if (animState.lastRenderTime == 0L) 0.016f else ((now - animState.lastRenderTime) / 1000f).coerceIn(0.001f, 0.1f)
        animState.lastRenderTime = now

        val targetProgress = if (isScreenOpen) 1.0f else 0.0f
        if (animState.openProgress < targetProgress) {
            animState.openProgress = (animState.openProgress + dt * TRANSITION_SPEED).coerceAtMost(targetProgress)
        } else if (animState.openProgress > targetProgress) {
            animState.openProgress = (animState.openProgress - dt * TRANSITION_SPEED).coerceAtLeast(targetProgress)
        }

        // Limpeza periódica de estados obsoletos se acumular muitos jogadores
        if (playerAnimStates.size > 50) {
            playerAnimStates.entries.removeIf { now - it.value.lastRenderTime > 60_000L }
        }

        // Curva de interpolação suave (cubic smoothstep)
        val t = animState.openProgress * animState.openProgress * (3f - 2f * animState.openProgress)

        poseStack.pushPose()

        // Animação de flutuação e respiração
        val time = player.tickCount + partialTick
        val bobSpeed = Mth.lerp(t, 0.10f, 0.16f).toDouble()
        val bob = (Math.sin(time.toDouble() * bobSpeed) * 0.04).toFloat()
        val tilt = (Math.cos(time.toDouble() * 0.08) * 3.0).toFloat()

        // Reação ao olhar da cabeça do jogador
        val headTurn = netHeadYaw * 0.25f
        val headLookPitch = headPitch * 0.25f

        // Posições base interpoladas
        val yShoulder = if (player.isCrouching) Y_SHOULDER_CROUCHING else Y_SHOULDER_STANDING
        val yFace = if (player.isCrouching) Y_FACE_CROUCHING else Y_FACE_STANDING

        val posX = Mth.lerp(t, X_SHOULDER, X_FACE)
        val posY = Mth.lerp(t, yShoulder, yFace) + bob
        val posZ = Mth.lerp(t, Z_SHOULDER, Z_FACE)

        poseStack.translate(posX, posY, posZ)

        // Rotação interpolada: no ombro olha pra frente, na cara vira 180 graus pra olhar nos olhos do player
        val baseYaw = Mth.lerp(t, YAW_SHOULDER, YAW_FACE)
        val currentYaw = baseYaw + tilt + headTurn * (1.0f - t * 0.7f)
        val currentPitch = Mth.lerp(t, -headLookPitch, 12.0f - headLookPitch * 0.5f)
        val currentRoll = Mth.lerp(t, tilt * 0.5f, tilt * 0.2f)

        poseStack.mulPose(Axis.YP.rotationDegrees(currentYaw))
        poseStack.mulPose(Axis.XP.rotationDegrees(currentPitch))
        poseStack.mulPose(Axis.ZP.rotationDegrees(currentRoll))

        // Alinhamento de coordenadas do modelo com o espaço de entidade
        poseStack.scale(SCALE, -SCALE, -SCALE)

        mc.itemRenderer.render(
            orangeStack,
            ItemDisplayContext.NONE,
            false,
            poseStack,
            bufferSource,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            bakedModel
        )

        poseStack.popPose()
    }
}
