package com.nbp.cobblemon_smartphone.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.CameraType
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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext

class RotomPlayerRenderLayer(
    renderer: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(renderer) {

    companion object {
        val ROTOM_MODEL_LOCATION = ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/rotom"),
            "inventory"
        )

        val ROTOM_COLOR_MODELS = SmartphoneColor.entries.associateWith { color ->
            ModelResourceLocation(color.getRotomModelPath(), "inventory")
        }

        // Escala geral do Rotom
        const val SCALE = 1.10f

        // Posição no ombro (Smartphone fechado) - flutuando um pouco acima do ombro
        const val X_SHOULDER = -0.65f
        const val Y_SHOULDER_STANDING = -0.40f
        const val Y_SHOULDER_CROUCHING = -0.15f
        const val Z_SHOULDER = 0.05f

        // Orientações base do Rotom
        const val YAW_FACING_FRONT = 15.0f  // Rosto do Rotom voltado para a frente
        const val YAW_FACING_BACK = 195.0f  // Rosto do Rotom voltado para as costas / trás

        // Distância confortável do smartphone na frente dos olhos quando aberto
        const val FACE_VIEW_DISTANCE = 0.78f

        // Velocidade da transição de voo mais suave e gradual (~0.42 segundos)
        const val TRANSITION_SPEED = 2.4f

        class RotomAnimState {
            var openProgress: Float = 0.0f
            var lastRenderTime: Long = 0L
            var currentYaw: Float = YAW_FACING_FRONT
            var currentPitch: Float = 0.0f
            var currentLagZ: Float = 0.0f
            var currentTiltPitch: Float = 0.0f
            var currentBankRoll: Float = 0.0f
            var crouchProgress: Float = 0.0f
            var lastPlayerY: Double = Double.NaN
            var jumpLagY: Float = 0.0f
            var jumpVelocityY: Float = 0.0f
            var initialized: Boolean = false
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

        val smartphoneStack = SmartphoneHelper.getEquippedRotomPhone(player) ?: return
        val color = (smartphoneStack.item as? SmartphoneItem)?.getColor()
        val modelLocation = color?.let { ROTOM_COLOR_MODELS[it] } ?: ROTOM_MODEL_LOCATION

        val mc = Minecraft.getInstance()
        val bakedModel = mc.modelManager.getModel(modelLocation)
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

        // Curva de interpolação suave (smootherstep quíntica para início e parada macios)
        val p = animState.openProgress
        val t = p * p * p * (p * (p * 6.0f - 15.0f) + 10.0f)

        // 1. Procurar outros jogadores próximos (até 7 blocos de distância) que TAMBÉM tenham um Rotom Phone
        val nearbyPlayer = player.level().getEntitiesOfClass(
            Player::class.java,
            player.boundingBox.inflate(7.0)
        ) { other ->
            other != player && other.isAlive && !other.isInvisible && SmartphoneHelper.getEquippedRotomPhone(other) != null
        }.minByOrNull { it.distanceToSqr(player) }

        val targetGazeYaw: Float
        val targetGazePitch: Float

        if (nearbyPlayer != null) {
            // Acompanhar o jogador próximo com o olhar
            val dx = nearbyPlayer.x - player.x
            val dy = nearbyPlayer.eyeY - (player.y + 1.2)
            val dz = nearbyPlayer.z - player.z
            val distXZ = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)

            val targetWorldYaw = (Mth.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90.0f
            val bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
            val relYaw = Mth.wrapDegrees(targetWorldYaw - bodyYaw)

            targetGazeYaw = YAW_FACING_FRONT + relYaw
            targetGazePitch = -(kotlin.math.atan2(dy, distXZ) * (180.0 / Math.PI)).toFloat().coerceIn(-35.0f, 35.0f)
        } else if (isLocalPlayer) {
            // Reações baseadas na perspectiva de câmera (F5) para o jogador local
            when (mc.options.cameraType) {
                CameraType.THIRD_PERSON_BACK -> {
                    // F5 costas: vira para trás para olhar na câmera / costas do player
                    targetGazeYaw = YAW_FACING_BACK - netHeadYaw * 0.25f
                    targetGazePitch = headPitch * 0.20f
                }
                CameraType.THIRD_PERSON_FRONT -> {
                    // F5 frente: volta para a frente olhando na câmera
                    targetGazeYaw = YAW_FACING_FRONT + netHeadYaw * 0.25f
                    targetGazePitch = -headPitch * 0.20f
                }
                CameraType.FIRST_PERSON -> {
                    // Primeira pessoa: frente olhando para a direção do player
                    targetGazeYaw = YAW_FACING_FRONT + netHeadYaw * 0.25f
                    targetGazePitch = -headPitch * 0.20f
                }
            }
        } else {
            // Jogadores remotos sem ninguém por perto: olham para frente
            targetGazeYaw = YAW_FACING_FRONT + netHeadYaw * 0.25f
            targetGazePitch = -headPitch * 0.20f
        }

        // Vetor e posição na frente exata de onde o player está olhando com a cabeça
        val eyeY = Mth.lerp(animState.crouchProgress, -0.38f, -0.15f)
        val headYawRad = Math.toRadians(netHeadYaw.toDouble())
        val headPitchRad = Math.toRadians(headPitch.toDouble())
        val cosHeadPitch = Mth.cos(headPitchRad.toFloat())
        val sinHeadPitch = Mth.sin(headPitchRad.toFloat())
        val cosHeadYaw = Mth.cos(headYawRad.toFloat())
        val sinHeadYaw = Mth.sin(headYawRad.toFloat())

        val targetFaceX = sinHeadYaw * cosHeadPitch * FACE_VIEW_DISTANCE
        val targetFaceY = eyeY + sinHeadPitch * FACE_VIEW_DISTANCE
        val targetFaceZ = -cosHeadYaw * cosHeadPitch * FACE_VIEW_DISTANCE

        // Ao abrir a tela, alinha diretamente para a frente dos olhos onde o jogador está olhando
        val openTargetYaw = YAW_FACING_FRONT + netHeadYaw
        val openTargetPitch = -headPitch

        val finalTargetYaw = if (t > 0.0f) {
            val diff = Mth.wrapDegrees(openTargetYaw - targetGazeYaw)
            targetGazeYaw + diff * t
        } else {
            targetGazeYaw
        }
        val finalTargetPitch = Mth.lerp(t, targetGazePitch, openTargetPitch)

        // Interpolação suave contínua de rotação para evitar mudanças bruscas
        if (!animState.initialized) {
            animState.currentYaw = finalTargetYaw
            animState.currentPitch = finalTargetPitch
            animState.initialized = true
        } else {
            val yawDiff = Mth.wrapDegrees(finalTargetYaw - animState.currentYaw)
            animState.currentYaw += yawDiff * (dt * 5.0f).coerceIn(0.0f, 1.0f)
            animState.currentPitch += (finalTargetPitch - animState.currentPitch) * (dt * 5.0f).coerceIn(0.0f, 1.0f)
        }

        // Efeito de movimento / inércia: quando o player corre, o Rotom fica mais solto, com maior distância para trás e inclinação suave
        val targetLagZ = (limbSwingAmount * 0.70f).coerceAtMost(0.85f)
        val targetTilt = (limbSwingAmount * 20.0f).coerceAtMost(24.0f)
        val bodyYawDelta = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO)
        val targetBank = (-bodyYawDelta * 3.5f).coerceIn(-24.0f, 24.0f)

        // Taxas de interpolação mais suaves para uma sensação elástica e fluida de flutuação
        animState.currentLagZ += (targetLagZ - animState.currentLagZ) * (dt * 3.2f).coerceIn(0.0f, 1.0f)
        animState.currentTiltPitch += (targetTilt - animState.currentTiltPitch) * (dt * 4.0f).coerceIn(0.0f, 1.0f)
        animState.currentBankRoll += (targetBank - animState.currentBankRoll) * (dt * 4.5f).coerceIn(0.0f, 1.0f)

        val effectiveLagZ = animState.currentLagZ * (1.0f - t)
        val effectiveTilt = animState.currentTiltPitch * (1.0f - t)
        val effectiveBank = animState.currentBankRoll * (1.0f - t)

        // Animação de hover livre, respiração e oscilação orgânica (mais solto e fluido)
        val time = player.tickCount + partialTick
        val hoverSpeed = Mth.lerp(t, 0.075f, 0.16f)
        val hoverY = (Mth.sin(time * hoverSpeed) * 0.070f + Mth.sin(time * 0.038f) * 0.035f)
        val hoverX = (Mth.cos(time * 0.048f) * 0.050f + Mth.sin(time * 0.022f) * 0.020f) * (1.0f - t)
        val hoverZ = (Mth.sin(time * 0.055f) * 0.055f) * (1.0f - t)

        // Balanço orgânico de rotação (pitch, roll e yaw mais soltos)
        val idleSwayYaw = (Mth.cos(time * 0.042f) * 5.0f) * (1.0f - t)
        val idleSwayPitch = (Mth.sin(time * 0.058f) * 4.5f) * (1.0f - t)
        val idleSwayRoll = (Mth.sin(time * 0.048f) * 6.5f) * (1.0f - t)

        // Delay e transição suave no Shift (crouch) como um ser independente
        val targetCrouch = if (player.isCrouching) 1.0f else 0.0f
        val crouchSpeed = if (player.isCrouching) 2.6f else 3.0f
        animState.crouchProgress += (targetCrouch - animState.crouchProgress) * (dt * crouchSpeed).coerceIn(0.0f, 1.0f)

        // Inércia vertical e delay ao pular / cair (física elástica no ar)
        val playerWorldY = Mth.lerp(partialTick.toDouble(), player.yo, player.y)

        if (animState.lastPlayerY.isNaN() || !animState.initialized) {
            animState.lastPlayerY = playerWorldY
            animState.jumpLagY = 0.0f
            animState.jumpVelocityY = 0.0f
        } else {
            val deltaPlayerY = (playerWorldY - animState.lastPlayerY).toFloat()
            animState.lastPlayerY = playerWorldY

            if (kotlin.math.abs(deltaPlayerY) < 2.5f) {
                // Quando o jogador sobe rapidamente (+Y no mundo), o Rotom fica para trás (+Y no modelo é para baixo)
                animState.jumpLagY = (animState.jumpLagY + deltaPlayerY * 0.65f).coerceIn(-0.65f, 0.65f)
            } else {
                // Teleporte ou respawn: reseta suavemente
                animState.jumpLagY = 0.0f
                animState.jumpVelocityY = 0.0f
            }
        }

        // Amortecimento elástico da inércia vertical para voltar suavemente à altura do ombro
        val springK = 28.0f
        val dampingC = 7.5f
        val springForce = -animState.jumpLagY * springK - animState.jumpVelocityY * dampingC
        animState.jumpVelocityY = (animState.jumpVelocityY + springForce * dt).coerceIn(-8.0f, 8.0f)
        animState.jumpLagY = (animState.jumpLagY + animState.jumpVelocityY * dt).coerceIn(-0.75f, 0.75f)

        val effectiveJumpLagY = animState.jumpLagY * (1.0f - t)
        val jumpTiltPitch = (animState.jumpLagY * 12.0f).coerceIn(-15.0f, 15.0f) * (1.0f - t)

        // Ondulação suave ao correr/andar
        val runBob = (Mth.sin(time * 0.35f) * 0.035f * limbSwingAmount) * (1.0f - t)

        poseStack.pushPose()

        // Posições base interpoladas com delay de agachamento
        val yShoulder = Mth.lerp(animState.crouchProgress, Y_SHOULDER_STANDING, Y_SHOULDER_CROUCHING)

        val posX = Mth.lerp(t, X_SHOULDER + hoverX, targetFaceX)
        val posY = Mth.lerp(t, yShoulder + hoverY + runBob + effectiveJumpLagY, targetFaceY)
        val posZ = Mth.lerp(t, Z_SHOULDER + effectiveLagZ + hoverZ, targetFaceZ)

        poseStack.translate(posX, posY, posZ)

        // Inclinação de voo para frente, curvas e reação de amortecimento vertical
        val combinedTiltPitch = effectiveTilt + jumpTiltPitch
        if (combinedTiltPitch > 0.01f || combinedTiltPitch < -0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(combinedTiltPitch))
        }
        if (effectiveBank != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(effectiveBank))
        }

        // Rotação de olhar / gaze do Rotom e oscilação suave de respiração
        val finalYaw = animState.currentYaw + idleSwayYaw
        val finalPitch = animState.currentPitch + idleSwayPitch
        val finalRoll = idleSwayRoll

        poseStack.mulPose(Axis.YP.rotationDegrees(finalYaw))
        poseStack.mulPose(Axis.XP.rotationDegrees(finalPitch))
        poseStack.mulPose(Axis.ZP.rotationDegrees(finalRoll))

        // Alinhamento de coordenadas do modelo com o espaço de entidade
        poseStack.scale(SCALE, -SCALE, -SCALE)

        mc.itemRenderer.render(
            smartphoneStack,
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
