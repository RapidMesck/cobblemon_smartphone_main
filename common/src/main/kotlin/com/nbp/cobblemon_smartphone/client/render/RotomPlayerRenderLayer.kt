package com.nbp.cobblemon_smartphone.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRotomAccessory
import net.minecraft.client.Camera
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
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

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

        // Número total de interações / coreografias possíveis no ponto âncora
        const val TOTAL_INTERACTION_PHASES = 8
        const val PHASE_DURATION_SEC = 4.0

        @JvmStatic
        @Volatile
        var isRenderingWorld: Boolean = false

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
            var socialProgress: Float = 0.0f
            var lastAnchor: SocialAnchor? = null
            var fpCompanionYaw: Float = Float.NaN
            var initialized: Boolean = false
        }

        data class SocialAnchor(
            val leaderUuid: UUID,
            var anchorX: Double = 0.0,
            var anchorY: Double = 0.0,
            var anchorZ: Double = 0.0,
            var dirX: Double = 1.0,
            var dirZ: Double = 0.0,
            var perpX: Double = 0.0,
            var perpZ: Double = 1.0,
            var patternSeed: Int = 1,
            var startGameTime: Long = 0L,
            var endTimeMs: Long = 0L,
            var lastActiveTime: Long = 0L
        )

        private val playerAnimStates = ConcurrentHashMap<UUID, RotomAnimState>()

        fun isSmartphoneScreen(screen: Screen?): Boolean {
            if (screen == null) return false
            val name = screen.javaClass.name
            return name.startsWith("com.nbp.cobblemon_smartphone.client.gui") && !name.contains("Overlay")
        }

        /**
         * Renderiza o próprio Rotom do jogador local em Primeira Pessoa diretamente no mundo.
         * Chamado pelo LevelRendererMixin logo após o loop de entidades quando a câmera está em primeira pessoa.
         */
        @JvmStatic
        fun renderFirstPersonForLocalPlayer(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            camera: Camera,
            partialTick: Float
        ) {
            if (camera.isDetached) return
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return
            if (player.isInvisible || player.isSleeping || player.isDeadOrDying || player.isSpectator) return

            val packedLight = mc.entityRenderDispatcher.getPackedLightCoords(player, partialTick)
            val limbSwing = player.walkAnimation.position(partialTick)
            val limbSwingAmount = player.walkAnimation.speed(partialTick)
            val ageInTicks = player.tickCount + partialTick
            val bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
            val headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
            val netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw)
            val headPitch = Mth.lerp(partialTick, player.xRotO, player.xRot)

            renderPlayerRotom(
                poseStack = poseStack,
                bufferSource = bufferSource,
                packedLight = packedLight,
                player = player,
                limbSwing = limbSwing,
                limbSwingAmount = limbSwingAmount,
                partialTick = partialTick,
                ageInTicks = ageInTicks,
                netHeadYaw = netHeadYaw,
                headPitch = headPitch,
                isFirstPersonPass = true
            )
        }

        private fun selectPhaseForStep(patternSeed: Int, stepIndex: Int): Int {
            val baseOrder = IntArray(TOTAL_INTERACTION_PHASES) { it }
            val rng = Random(patternSeed xor (stepIndex / TOTAL_INTERACTION_PHASES))
            for (i in TOTAL_INTERACTION_PHASES - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = baseOrder[i]
                baseOrder[i] = baseOrder[j]
                baseOrder[j] = tmp
            }
            return baseOrder[stepIndex.coerceAtLeast(0) % TOTAL_INTERACTION_PHASES]
        }

        fun renderPlayerRotom(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            player: AbstractClientPlayer,
            limbSwing: Float,
            limbSwingAmount: Float,
            partialTick: Float,
            ageInTicks: Float,
            netHeadYaw: Float,
            headPitch: Float,
            isFirstPersonPass: Boolean
        ) {
            if (player.isInvisible || player.isSleeping || player.isDeadOrDying) return

            val smartphoneStack = RotomClientManager.getEffectiveRotomPhone(player) ?: return
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

            val now = System.currentTimeMillis()
            val dt = if (animState.lastRenderTime == 0L) 0.016f else ((now - animState.lastRenderTime) / 1000f).coerceIn(0.001f, 0.1f)
            animState.lastRenderTime = now

            val targetProgress = if (isScreenOpen) 1.0f else 0.0f
            if (animState.openProgress < targetProgress) {
                animState.openProgress = (animState.openProgress + dt * TRANSITION_SPEED).coerceAtMost(targetProgress)
            } else if (animState.openProgress > targetProgress) {
                animState.openProgress = (animState.openProgress - dt * TRANSITION_SPEED).coerceAtLeast(targetProgress)
            }

            if (playerAnimStates.size > 50) {
                playerAnimStates.entries.removeIf { now - it.value.lastRenderTime > 60_000L }
            }

            val p = animState.openProgress
            val t = p * p * p * (p * (p * 6.0f - 15.0f) + 10.0f)

            // 1. Procurar outros jogadores próximos (até 8 blocos) que TAMBÉM tenham um Rotom Phone
            val nearbyPlayer = player.level().getEntitiesOfClass(
                Player::class.java,
                player.boundingBox.inflate(8.0)
            ) { other ->
                other != player && other.isAlive && !other.isInvisible && RotomClientManager.getEffectiveRotomPhone(other) != null
            }.minByOrNull { it.distanceToSqr(player) }

            val bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
            val targetGazeYaw: Float
            val targetGazePitch: Float

            if (nearbyPlayer != null) {
                val dx = nearbyPlayer.x - player.x
                val dy = nearbyPlayer.eyeY - (player.y + 1.2)
                val dz = nearbyPlayer.z - player.z
                val distXZ = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)

                val targetWorldYaw = (Mth.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90.0f
                val relYaw = Mth.wrapDegrees(targetWorldYaw - bodyYaw)

                targetGazeYaw = YAW_FACING_FRONT + relYaw
                targetGazePitch = -(kotlin.math.atan2(dy, distXZ) * (180.0 / Math.PI)).toFloat().coerceIn(-35.0f, 35.0f)
            } else if (isLocalPlayer) {
                when (mc.options.cameraType) {
                    CameraType.THIRD_PERSON_BACK -> {
                        targetGazeYaw = YAW_FACING_BACK - netHeadYaw * 0.25f
                        targetGazePitch = headPitch * 0.20f
                    }
                    CameraType.THIRD_PERSON_FRONT -> {
                        targetGazeYaw = YAW_FACING_FRONT + netHeadYaw * 0.25f
                        targetGazePitch = -headPitch * 0.20f
                    }
                    CameraType.FIRST_PERSON -> {
                        targetGazeYaw = YAW_FACING_BACK - netHeadYaw * 0.20f
                        targetGazePitch = headPitch * 0.15f
                    }
                }
            } else {
                targetGazeYaw = YAW_FACING_FRONT + netHeadYaw * 0.25f
                targetGazePitch = -headPitch * 0.20f
            }

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

            val openTargetYaw = YAW_FACING_FRONT + netHeadYaw
            val openTargetPitch = -headPitch

            val finalTargetYaw = if (t > 0.0f) {
                val diff = Mth.wrapDegrees(openTargetYaw - targetGazeYaw)
                targetGazeYaw + diff * t
            } else {
                targetGazeYaw
            }
            val finalTargetPitch = Mth.lerp(t, targetGazePitch, openTargetPitch)

            if (!animState.initialized) {
                animState.currentYaw = finalTargetYaw
                animState.currentPitch = finalTargetPitch
                animState.initialized = true
            } else {
                val yawDiff = Mth.wrapDegrees(finalTargetYaw - animState.currentYaw)
                animState.currentYaw += yawDiff * (dt * 5.0f).coerceIn(0.0f, 1.0f)
                animState.currentPitch += (finalTargetPitch - animState.currentPitch) * (dt * 5.0f).coerceIn(0.0f, 1.0f)
            }

            val targetLagZ = (limbSwingAmount * 0.70f).coerceAtMost(0.85f)
            val targetTilt = (limbSwingAmount * 20.0f).coerceAtMost(24.0f)
            val bodyYawDelta = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO)
            val targetBank = (-bodyYawDelta * 3.5f).coerceIn(-24.0f, 24.0f)

            animState.currentLagZ += (targetLagZ - animState.currentLagZ) * (dt * 3.2f).coerceIn(0.0f, 1.0f)
            animState.currentTiltPitch += (targetTilt - animState.currentTiltPitch) * (dt * 4.0f).coerceIn(0.0f, 1.0f)
            animState.currentBankRoll += (targetBank - animState.currentBankRoll) * (dt * 4.5f).coerceIn(0.0f, 1.0f)

            val effectiveLagZ = animState.currentLagZ * (1.0f - t)
            val effectiveTilt = animState.currentTiltPitch * (1.0f - t)
            val effectiveBank = animState.currentBankRoll * (1.0f - t)

            val time = ageInTicks
            val hoverSpeed = Mth.lerp(t, 0.075f, 0.16f)
            val hoverY = (Mth.sin(time * hoverSpeed) * 0.070f + Mth.sin(time * 0.038f) * 0.035f)
            val hoverX = (Mth.cos(time * 0.048f) * 0.050f + Mth.sin(time * 0.022f) * 0.020f) * (1.0f - t)
            val hoverZ = (Mth.sin(time * 0.055f) * 0.055f) * (1.0f - t)

            val idleSwayYaw = (Mth.cos(time * 0.042f) * 5.0f) * (1.0f - t)
            val idleSwayPitch = (Mth.sin(time * 0.058f) * 4.5f) * (1.0f - t)
            val idleSwayRoll = (Mth.sin(time * 0.048f) * 6.5f) * (1.0f - t)

            val targetCrouch = if (player.isCrouching) 1.0f else 0.0f
            val crouchSpeed = if (player.isCrouching) 2.6f else 3.0f
            animState.crouchProgress += (targetCrouch - animState.crouchProgress) * (dt * crouchSpeed).coerceIn(0.0f, 1.0f)

            val playerWorldY = Mth.lerp(partialTick.toDouble(), player.yo, player.y)
            if (animState.lastPlayerY.isNaN()) {
                animState.lastPlayerY = playerWorldY
                animState.jumpLagY = 0.0f
                animState.jumpVelocityY = 0.0f
            } else {
                val deltaPlayerY = (playerWorldY - animState.lastPlayerY).toFloat()
                animState.lastPlayerY = playerWorldY

                if (kotlin.math.abs(deltaPlayerY) < 2.5f) {
                    animState.jumpLagY = (animState.jumpLagY + deltaPlayerY * 0.65f).coerceIn(-0.65f, 0.65f)
                } else {
                    animState.jumpLagY = 0.0f
                    animState.jumpVelocityY = 0.0f
                }
            }

            val springK = 28.0f
            val dampingC = 7.5f
            val springForce = -animState.jumpLagY * springK - animState.jumpVelocityY * dampingC
            animState.jumpVelocityY = (animState.jumpVelocityY + springForce * dt).coerceIn(-8.0f, 8.0f)
            animState.jumpLagY = (animState.jumpLagY + animState.jumpVelocityY * dt).coerceIn(-0.75f, 0.75f)

            val effectiveJumpLagY = animState.jumpLagY * (1.0f - t)
            val jumpTiltPitch = (animState.jumpLagY * 12.0f).coerceIn(-15.0f, 15.0f) * (1.0f - t)
            val runBob = (Mth.sin(time * 0.35f) * 0.035f * limbSwingAmount) * (1.0f - t)

            val yShoulder = Mth.lerp(animState.crouchProgress, Y_SHOULDER_STANDING, Y_SHOULDER_CROUCHING)
            val posX = Mth.lerp(t, X_SHOULDER + hoverX, targetFaceX)
            val posY = Mth.lerp(t, yShoulder + hoverY + runBob + effectiveJumpLagY, targetFaceY)
            val posZ = Mth.lerp(t, Z_SHOULDER + effectiveLagZ + hoverZ, targetFaceZ)

            val finalYaw = animState.currentYaw + idleSwayYaw
            val finalPitch = animState.currentPitch + idleSwayPitch
            val finalRoll = idleSwayRoll

            // 2. Consultar o gerenciador para obter o Ponto Âncora compartilhado (se houver uma interação ativa)
            val sharedAnchor = if (t < 0.1f) {
                RotomClientManager.getActiveSharedAnchor(player, nearbyPlayer, partialTick, now, isScreenOpen)
            } else {
                null
            }

            if (sharedAnchor != null) {
                animState.lastAnchor = sharedAnchor
            }

            val targetSocial = if (sharedAnchor != null) 1.0f else 0.0f
            animState.socialProgress += (targetSocial - animState.socialProgress) * (dt * 2.2f).coerceIn(0.0f, 1.0f)
            if (animState.socialProgress <= 0.001f && sharedAnchor == null) {
                animState.socialProgress = 0.0f
                animState.lastAnchor = null
            }
            val socialT = animState.socialProgress * animState.socialProgress * (3f - 2f * animState.socialProgress)

            // 3. Calcular a posição e rotação da coreografia no Ponto Âncora compartilhado
            val activeAnchor = animState.lastAnchor
            var targetDanceWorldX = 0.0
            var targetDanceWorldY = 0.0
            var targetDanceWorldZ = 0.0
            var rotWorldYaw = bodyYaw
            var rotPitch = 0.0f
            var rotRoll = 0.0f

            if (socialT > 0.001f && activeAnchor != null) {
                val isLeader = (player.uuid == activeAnchor.leaderUuid)
                val sideSign = if (isLeader) 1.0 else -1.0
                val phaseOffset = if (isLeader) 0.0 else Math.PI

                fun worldFacingYaw(wx: Double, wz: Double): Float {
                    return (Mth.atan2(wz, wx) * (180.0 / Math.PI)).toFloat() - 90.0f
                }

                val baseRadius = 0.40
                val baseWorldX = activeAnchor.perpX * (baseRadius * sideSign)
                val baseWorldZ = activeAnchor.perpZ * (baseRadius * sideSign)
                val facePartnerWorldYaw = worldFacingYaw(-sideSign * activeAnchor.perpX, -sideSign * activeAnchor.perpZ)

                val elapsedTicks = (player.level().gameTime - activeAnchor.startGameTime).coerceAtLeast(0L)
                val sessionSec = (elapsedTicks + partialTick) / 20.0
                val stepIndex = (sessionSec / PHASE_DURATION_SEC).toInt()
                val phaseTime = (sessionSec % PHASE_DURATION_SEC).toFloat()
                val currentPhase = selectPhaseForStep(activeAnchor.patternSeed, stepIndex)

                // Envelope de suavização nas bordas de cada fase (primeiros e últimos 0.45s) para transição fluida entre fases
                val env = when {
                    phaseTime < 0.45f -> {
                        val u = (phaseTime / 0.45f).coerceIn(0.0f, 1.0f)
                        u * u * (3.0f - 2.0f * u)
                    }
                    phaseTime > (PHASE_DURATION_SEC.toFloat() - 0.45f) -> {
                        val u = ((PHASE_DURATION_SEC.toFloat() - phaseTime) / 0.45f).coerceIn(0.0f, 1.0f)
                        u * u * (3.0f - 2.0f * u)
                    }
                    else -> 1.0f
                }

                var phaseOffX = baseWorldX
                var phaseOffY = 0.0
                var phaseOffZ = baseWorldZ
                var phaseYaw = facePartnerWorldYaw
                var phasePitch = 0.0f
                var phaseRoll = 0.0f

                when (currentPhase) {
                    0 -> {
                        // INTERAÇÃO 1: CIRANDINHA ORBITAL (Voo em círculo inclinado como aviãozinho)
                        val orbitAngle = (phaseTime / 4.0f) * (2.0 * Math.PI) + phaseOffset
                        val cO = Math.cos(orbitAngle)
                        val sO = Math.sin(orbitAngle)

                        phaseOffX = (activeAnchor.perpX * cO + activeAnchor.dirX * sO) * baseRadius
                        phaseOffZ = (activeAnchor.perpZ * cO + activeAnchor.dirZ * sO) * baseRadius
                        phaseOffY = Math.sin(orbitAngle * 2.0) * 0.06 * env

                        val tanX = -activeAnchor.perpX * sO + activeAnchor.dirX * cO
                        val tanZ = -activeAnchor.perpZ * sO + activeAnchor.dirZ * cO
                        val flightYaw = worldFacingYaw(tanX, tanZ)
                        phaseYaw = facePartnerWorldYaw + Mth.wrapDegrees(flightYaw - facePartnerWorldYaw) * env
                        phaseRoll = -20.0f * env
                        phasePitch = (Math.cos(orbitAngle * 2.0) * 6.0).toFloat() * env
                    }
                    1 -> {
                        // INTERAÇÃO 2: MORTAIS / BACKFLIPS SINCRONIZADOS
                        val sub = phaseTime % 2.0f
                        if (sub < 0.30f) {
                            val pPrep = sub / 0.30f
                            phaseOffY = (-Mth.sin(pPrep * Math.PI.toFloat()) * 0.05).toDouble()
                            phasePitch = -Mth.sin(pPrep * Math.PI.toFloat()) * 14.0f
                        } else if (sub < 1.65f) {
                            val pFlip = (sub - 0.30f) / 1.35f
                            val smoothFlip = pFlip * pFlip * (3.0f - 2.0f * pFlip)
                            phaseOffY = (Mth.sin(pFlip * Math.PI.toFloat()) * 0.38).toDouble()
                            phasePitch = smoothFlip * 360.0f
                            phaseRoll = Mth.sin(pFlip * Math.PI.toFloat() * 2.0f) * 14.0f * sideSign.toFloat()
                        } else {
                            val pLand = (sub - 1.65f) / 0.35f
                            phaseOffY = (-Mth.sin(pLand * Math.PI.toFloat()) * 0.03).toDouble()
                        }
                    }
                    2 -> {
                        // INTERAÇÃO 3: OITO INFINITO / PERSEGUIÇÃO (Figure-8 Lemniscate Chase)
                        val t8 = (phaseTime / 4.0f) * (2.0 * Math.PI) + phaseOffset
                        val figPerp = Math.cos(t8) * (baseRadius * 1.20)
                        val figDir = Math.sin(2.0 * t8) * (baseRadius * 0.85)

                        val rawX = activeAnchor.perpX * figPerp + activeAnchor.dirX * figDir
                        val rawZ = activeAnchor.perpZ * figPerp + activeAnchor.dirZ * figDir
                        phaseOffX = Mth.lerp(env.toDouble(), baseWorldX, rawX)
                        phaseOffZ = Mth.lerp(env.toDouble(), baseWorldZ, rawZ)
                        phaseOffY = Math.cos(2.0 * t8) * 0.09 * sideSign * env

                        val velPerp = -Math.sin(t8) * (baseRadius * 1.20)
                        val velDir = 2.0 * Math.cos(2.0 * t8) * (baseRadius * 0.85)
                        val velWorldX = activeAnchor.perpX * velPerp + activeAnchor.dirX * velDir
                        val velWorldZ = activeAnchor.perpZ * velPerp + activeAnchor.dirZ * velDir
                        val flightYaw = worldFacingYaw(velWorldX, velWorldZ)

                        phaseYaw = facePartnerWorldYaw + Mth.wrapDegrees(flightYaw - facePartnerWorldYaw) * env
                        phaseRoll = (Math.sin(2.0 * t8) * 24.0).toFloat() * env
                        phasePitch = (-Math.sin(2.0 * t8) * 10.0 * sideSign).toFloat() * env
                    }
                    3 -> {
                        // INTERAÇÃO 4: ESPIRAL DUPLA / DNA (Double Helix Vertical Corkscrew)
                        val helixAngle = (phaseTime / 4.0f) * (4.0 * Math.PI) + phaseOffset
                        val climbWave = Math.sin((phaseTime / 4.0) * Math.PI)
                        val r = baseRadius * (1.0 - 0.30 * climbWave)
                        val cH = Math.cos(helixAngle)
                        val sH = Math.sin(helixAngle)

                        phaseOffX = (activeAnchor.perpX * cH + activeAnchor.dirX * sH) * r
                        phaseOffZ = (activeAnchor.perpZ * cH + activeAnchor.dirZ * sH) * r
                        phaseOffY = climbWave * 0.38 * env

                        val tanX = -activeAnchor.perpX * sH + activeAnchor.dirX * cH
                        val tanZ = -activeAnchor.perpZ * sH + activeAnchor.dirZ * cH
                        val flightYaw = worldFacingYaw(tanX, tanZ)

                        phaseYaw = facePartnerWorldYaw + Mth.wrapDegrees(flightYaw - facePartnerWorldYaw) * env
                        phasePitch = (-Math.cos((phaseTime / 4.0) * Math.PI) * 18.0).toFloat() * env
                        phaseRoll = -22.0f * env
                    }
                    4 -> {
                        // INTERAÇÃO 5: PULINHOS & DANCINHA ALEGRE (Bouncing Wiggle Dance)
                        val bounceCycle = phaseTime * Math.PI.toFloat() * 2.0f
                        val hop = kotlin.math.abs(Mth.sin(bounceCycle))
                        phaseOffY = (hop * hop * 0.24f * env).toDouble()

                        val sway = Math.sin(phaseTime * Math.PI * 2.0) * 0.15 * sideSign * env
                        phaseOffX = baseWorldX + activeAnchor.dirX * sway
                        phaseOffZ = baseWorldZ + activeAnchor.dirZ * sway

                        phaseRoll = Mth.sin(bounceCycle) * 20.0f * sideSign.toFloat() * env
                        phasePitch = -hop * 16.0f * env
                    }
                    5 -> {
                        // INTERAÇÃO 6: ENCONTRÃO CARINHOSO NO CENTRO & PIRUETA 360°
                        if (phaseTime < 2.0f) {
                            val approach = Math.sin((phaseTime / 2.0) * Math.PI)
                            val curR = baseRadius * (1.0 - approach * 0.72)
                            phaseOffX = activeAnchor.perpX * (curR * sideSign)
                            phaseOffZ = activeAnchor.perpZ * (curR * sideSign)
                            phaseOffY = approach * 0.08
                            phaseRoll = (approach * 22.0 * sideSign).toFloat()
                            phasePitch = (-approach * 10.0).toFloat()
                        } else {
                            val pSpin = ((phaseTime - 2.0f) / 1.65f).coerceIn(0.0f, 1.0f)
                            val smoothSpin = pSpin * pSpin * (3.0f - 2.0f * pSpin)
                            phaseOffY = Math.sin(pSpin * Math.PI) * 0.18
                            phaseYaw = facePartnerWorldYaw + smoothSpin * 360.0f * sideSign.toFloat()
                            phaseRoll = (Math.sin(pSpin * Math.PI * 2.0) * 12.0 * sideSign).toFloat()
                        }
                    }
                    6 -> {
                        // INTERAÇÃO 7: GIRO PARAFUSO / AILERON BARREL ROLLS
                        val loopA = (phaseTime / 4.0) * (2.0 * Math.PI)
                        val swayDir = Math.sin(loopA) * 0.14 * sideSign * env
                        phaseOffX = baseWorldX + activeAnchor.dirX * swayDir
                        phaseOffZ = baseWorldZ + activeAnchor.dirZ * swayDir
                        phaseOffY = (1.0 - Math.cos(loopA)) * 0.12 * env

                        val sub = phaseTime % 2.0f
                        if (sub in 0.25f..1.65f) {
                            val pRoll = (sub - 0.25f) / 1.40f
                            val smoothRoll = pRoll * pRoll * (3.0f - 2.0f * pRoll)
                            phaseRoll = smoothRoll * 360.0f * sideSign.toFloat()
                        }
                    }
                    else -> {
                        // INTERAÇÃO 8: PEGA-PEGA ZIG-ZAG (Playful Tag & Feints)
                        val zig = Math.sin(phaseTime * Math.PI * 1.5) * 0.25 * env
                        val zag = Math.cos(phaseTime * Math.PI * 3.0) * 0.11 * sideSign * env
                        phaseOffX = activeAnchor.perpX * (baseRadius * sideSign + zag) + activeAnchor.dirX * zig
                        phaseOffZ = activeAnchor.perpZ * (baseRadius * sideSign + zag) + activeAnchor.dirZ * zig
                        phaseOffY = kotlin.math.abs(Math.sin(phaseTime * Math.PI * 3.0)) * 0.14 * env

                        phaseYaw = facePartnerWorldYaw + (Math.cos(phaseTime * Math.PI * 1.5) * 28.0 * sideSign * env).toFloat()
                        phaseRoll = (-Math.cos(phaseTime * Math.PI * 1.5) * 20.0 * env).toFloat()
                        phasePitch = (-Math.sin(phaseTime * Math.PI * 3.0) * 10.0 * env).toFloat()
                    }
                }

                targetDanceWorldX = activeAnchor.anchorX + phaseOffX
                targetDanceWorldY = activeAnchor.anchorY + phaseOffY
                targetDanceWorldZ = activeAnchor.anchorZ + phaseOffZ
                rotWorldYaw = phaseYaw
                rotPitch = phasePitch
                rotRoll = phaseRoll
            }

            poseStack.pushPose()

            // Verifica se estamos renderizando no mundo 3D (e não no inventário 2D)
            val inWorldSpace = isFirstPersonPass || (isRenderingWorld && kotlin.math.abs(poseStack.last().pose().m00()) < 3.0f)

            if (inWorldSpace) {
                val camPos = mc.gameRenderer.mainCamera.position
                val shoulderCamPos = Vector3f()
                val shoulderWorldYaw: Float
                val baseModelScale: Float

                if (isFirstPersonPass) {
                    val camYaw = mc.gameRenderer.mainCamera.yRot
                    val camPitch = mc.gameRenderer.mainCamera.xRot

                    // Segue o corpo/câmera de forma bem solta para que, ao virar para a esquerda, olhar para baixo
                    // ou correr, o Rotom saia naturalmente da tela e apareça só de vez em quando no canto superior direito
                    val targetAnchorYaw = bodyYaw + Mth.wrapDegrees(camYaw - bodyYaw) * 0.55f
                    if (animState.fpCompanionYaw.isNaN()) {
                        animState.fpCompanionYaw = targetAnchorYaw
                    } else {
                        val diff = Mth.wrapDegrees(targetAnchorYaw - animState.fpCompanionYaw)
                        animState.fpCompanionYaw += diff * (dt * 2.4f).coerceIn(0.0f, 1.0f)
                        val clampedDiff = Mth.wrapDegrees(camYaw - animState.fpCompanionYaw).coerceIn(-105.0f, 105.0f)
                        animState.fpCompanionYaw = camYaw - clampedDiff
                    }

                    val fpRad = Math.toRadians(animState.fpCompanionYaw.toDouble())
                    val fwdX = -Math.sin(fpRad)
                    val fwdZ = Math.cos(fpRad)
                    // Vetor para a DIREITA do jogador (canto superior direito)
                    val rightX = -Math.cos(fpRad)
                    val rightZ = -Math.sin(fpRad)

                    // Oscilação lenta de avanço/recuo mantendo o mesmo ângulo no canto superior direito, porém mais próximo do ombro
                    val wanderFwd = (Mth.sin(time * 0.022f) * 0.14f) - 0.03f
                    val fpFwd = 0.44 + wanderFwd - effectiveLagZ * 0.45 + hoverZ * 0.5f
                    val fpRight = 0.28 + hoverX * 0.5f
                    // Positivo em Y = acima da linha dos olhos (canto SUPERIOR direito)
                    val fpUp = 0.22 - animState.crouchProgress * 0.03 - hoverY * 0.4f - runBob - effectiveJumpLagY

                    val idleCamX = (fwdX * fpFwd + rightX * fpRight).toFloat()
                    val idleCamY = fpUp.toFloat()
                    val idleCamZ = (fwdZ * fpFwd + rightZ * fpRight).toFloat()

                    val cPitchRad = Math.toRadians(camPitch.toDouble())
                    val cYawRad = Math.toRadians(camPitch.toDouble()).let { Math.toRadians(camYaw.toDouble()) }
                    val lookX = -Math.sin(cYawRad) * Math.cos(cPitchRad)
                    val lookY = -Math.sin(cPitchRad)
                    val lookZ = Math.cos(cYawRad) * Math.cos(cPitchRad)

                    val openCamX = (lookX * FACE_VIEW_DISTANCE).toFloat()
                    val openCamY = (lookY * FACE_VIEW_DISTANCE - 0.04).toFloat()
                    val openCamZ = (lookZ * FACE_VIEW_DISTANCE).toFloat()

                    shoulderCamPos.set(
                        Mth.lerp(t, idleCamX, openCamX),
                        Mth.lerp(t, idleCamY, openCamY),
                        Mth.lerp(t, idleCamZ, openCamZ)
                    )

                    val idleFpWorldYaw = if (nearbyPlayer != null) {
                        bodyYaw + (finalYaw - YAW_FACING_FRONT)
                    } else {
                        animState.fpCompanionYaw - 145.0f + idleSwayYaw
                    }
                    val openFpWorldYaw = camYaw
                    shoulderWorldYaw = idleFpWorldYaw + Mth.wrapDegrees(openFpWorldYaw - idleFpWorldYaw) * t
                    // Mais próximo do ombro e com escala equilibrada (0.55f) para não parecer distante
                    val fpScaleFactor = Mth.lerp(maxOf(t, socialT), 0.55f, 1.0f)
                    baseModelScale = 0.9375f * player.scale * fpScaleFactor
                } else {
                    // Em terceira pessoa ou jogadores remotos: extrai a posição exata do ombro no espaço da câmera
                    poseStack.last().pose().transformPosition(posX, posY, posZ, shoulderCamPos)
                    shoulderWorldYaw = bodyYaw + (finalYaw - YAW_FACING_FRONT)
                    baseModelScale = 0.9375f * player.scale
                }

                val danceCamX = if (activeAnchor != null) (targetDanceWorldX - camPos.x).toFloat() else shoulderCamPos.x
                val danceCamY = if (activeAnchor != null) (targetDanceWorldY - camPos.y).toFloat() else shoulderCamPos.y
                val danceCamZ = if (activeAnchor != null) (targetDanceWorldZ - camPos.z).toFloat() else shoulderCamPos.z

                val finalCamX = Mth.lerp(socialT, shoulderCamPos.x, danceCamX)
                val finalCamY = Mth.lerp(socialT, shoulderCamPos.y, danceCamY)
                val finalCamZ = Mth.lerp(socialT, shoulderCamPos.z, danceCamZ)

                val currentWorldYaw = if (activeAnchor != null) {
                    shoulderWorldYaw + Mth.wrapDegrees(rotWorldYaw - shoulderWorldYaw) * socialT
                } else {
                    shoulderWorldYaw
                }
                val currentPitch = Mth.lerp(socialT, finalPitch, rotPitch)
                val currentRoll = Mth.lerp(socialT, finalRoll, rotRoll)

                // Reseta o PoseStack para o Espaço de Mundo puro (relativo à câmera),
                // garantindo 0% de vínculo com a movimentação ou rotação do jogador durante a dança!
                poseStack.last().pose().identity()
                poseStack.last().normal().identity()
                poseStack.translate(finalCamX, finalCamY, finalCamZ)

                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - currentWorldYaw))
                poseStack.scale(-baseModelScale, -baseModelScale, baseModelScale)

                val combinedTiltPitch = (effectiveTilt + jumpTiltPitch) * (1.0f - socialT)
                if (combinedTiltPitch > 0.01f || combinedTiltPitch < -0.01f) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(combinedTiltPitch))
                }
                val effectiveBankRoll = effectiveBank * (1.0f - socialT)
                if (effectiveBankRoll != 0.0f) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(effectiveBankRoll))
                }

                poseStack.mulPose(Axis.YP.rotationDegrees(YAW_FACING_FRONT))
                poseStack.mulPose(Axis.XP.rotationDegrees(currentPitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(currentRoll))
                poseStack.scale(SCALE, -SCALE, -SCALE)
            } else {
                // Renderização no inventário 2D (Paperdoll)
                poseStack.translate(posX, posY, posZ)
                poseStack.mulPose(Axis.YP.rotationDegrees(finalYaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(finalPitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(finalRoll))
                poseStack.scale(SCALE, -SCALE, -SCALE)
            }

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

            // Renderizar acessório 3D (Laço colorido ou Gravata Borboleta)
            val accessory = smartphoneStack.getRotomAccessory()
            if (accessory != null) {
                val accessoryStack = SmartphoneHelper.createAccessoryStackFromPhone(smartphoneStack)
                if (accessoryStack != null) {
                    val accBakedModel = mc.itemRenderer.getModel(accessoryStack, player.level(), player, 0)
                    poseStack.pushPose()
                    if (accessory == "bow") {
                        // Laço na lateral da cabeça / testa (ajustado 1 pixel para baixo e 1 para direita)
                        poseStack.translate(0.096, -0.016, 0.065)
                        poseStack.scale(0.22f, 0.22f, 0.22f)
                        poseStack.mulPose(Axis.ZP.rotationDegrees(-18.0f))
                    } else {
                        // Gravata borboleta abaixo da boca (descida 2 pixels)
                        poseStack.translate(0.0, -0.24, 0.065)
                        poseStack.scale(0.22f, 0.22f, 0.22f)
                    }
                    mc.itemRenderer.render(
                        accessoryStack,
                        ItemDisplayContext.NONE,
                        false,
                        poseStack,
                        bufferSource,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        accBakedModel
                    )
                    poseStack.popPose()
                }
            }

            poseStack.popPose()
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
        renderPlayerRotom(
            poseStack = poseStack,
            bufferSource = bufferSource,
            packedLight = packedLight,
            player = player,
            limbSwing = limbSwing,
            limbSwingAmount = limbSwingAmount,
            partialTick = partialTick,
            ageInTicks = ageInTicks,
            netHeadYaw = netHeadYaw,
            headPitch = headPitch,
            isFirstPersonPass = false
        )
    }
}
