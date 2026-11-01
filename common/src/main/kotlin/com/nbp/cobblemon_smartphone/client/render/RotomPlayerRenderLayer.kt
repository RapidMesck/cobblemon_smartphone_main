package com.nbp.cobblemon_smartphone.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.nbp.cobblemon_smartphone.client.RotomDialogueClientState
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
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
            var horizontalProgress: Float = 0.0f
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
            var scanProgress: Float = 0.0f
            var scanTargetX: Double = 0.0
            var scanTargetY: Double = 0.0
            var scanTargetZ: Double = 0.0
            var scanWorldYaw: Float = 0.0f
            var scanWorldPitch: Float = 0.0f
            var scanWorldRoll: Float = 0.0f
            var dialogueProgress: Float = 0.0f
        }

        data class SocialAnchor(
            val sessionId: UUID,
            val participants: List<UUID>,
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
        ) {
            val leaderUuid: UUID get() = participants.firstOrNull() ?: sessionId
        }

        private val playerAnimStates = ConcurrentHashMap<UUID, RotomAnimState>()

        fun isSmartphoneScreen(screen: Screen?): Boolean {
            if (screen == null) return false
            val name = screen.javaClass.name
            return name.startsWith("com.nbp.cobblemon_smartphone.client.gui") && !name.contains("Overlay")
        }

        /**
         * Renderiza o próprio Rotom do jogador local em Primeira Pessoa diretamente no mundo.
         * Chamado pelo LevelRendererMixin logo após o loop de entidades quando a câmera está em primeira pessoa.
         * Renderiza APENAS se o Rotom estiver fora do ombro se socializando com outros Rotoms!
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

            // Não precisa aparecer para o player em primeira pessoa quando estiver escaneando manualmente com a Pokédex
            if (ScannerManager.isInUse()) {
                return
            }

            // Não renderiza em primeira pessoa se o Rotom estiver descansando no ombro!
            // Renderiza se estiver socializando, escaneando Pokémon selvagem, apresentando curiosidade no diálogo,
            // em modo Pokédex scanner/câmera social horizontal, ou em transição suave de abertura/retorno ao ombro.
            val animState = playerAnimStates[player.uuid]
            val hasActiveSession = RotomClientManager.hasActiveSessionFor(player.uuid)
            val isTransitioningSocial = animState != null && animState.socialProgress > 0.001f
            val hasActiveScan = animState != null && animState.scanProgress > 0.001f
            val isScanningNow = RotomScannerClientState.getScanFor(player.uuid) != null
            val hasActiveDialogue = RotomDialogueClientState.shouldRotomFlyInFront(player.uuid)
            val isTransitioningDialogue = animState != null && animState.dialogueProgress > 0.001f
            val isDismissingDialogue = RotomDialogueClientState.isDismissing(player.uuid)
            val isScanningPokedex = ScannerManager.isInUse()
            val isSocialPhoto = SocialPhotoClient.isCameraActive()
            val isTransitioningHorizontal = animState != null && animState.horizontalProgress > 0.001f
            val isTransitioningOpen = animState != null && animState.openProgress > 0.001f

            if (!hasActiveSession && !isTransitioningSocial && !hasActiveScan && !isScanningNow
                && !hasActiveDialogue && !isTransitioningDialogue && !isDismissingDialogue
                && !isScanningPokedex && !isSocialPhoto && !isTransitioningHorizontal && !isTransitioningOpen) {
                return
            }

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
            if (isFirstPersonPass && ScannerManager.isInUse()) return

            val smartphoneStack = RotomClientManager.getEffectiveRotomPhone(player) ?: return
            val color = (smartphoneStack.item as? SmartphoneItem)?.getColor()
            val modelLocation = color?.let { ROTOM_COLOR_MODELS[it] } ?: ROTOM_MODEL_LOCATION

            val mc = Minecraft.getInstance()
            val bakedModel = mc.modelManager.getModel(modelLocation)
            if (bakedModel == mc.modelManager.missingModel) return

            val isLocalPlayer = (player == mc.player)
            val isScreenOpen = if (isLocalPlayer) {
                isSmartphoneScreen(mc.screen)
            } else {
                RotomClientManager.isPlayerOpen(player.uuid)
            }
            val isHorizontalMode = if (isLocalPlayer) {
                ScannerManager.isInUse() || SocialPhotoClient.isCameraActive()
            } else {
                RotomClientManager.isPlayerHorizontal(player.uuid)
            }

            if (isLocalPlayer) {
                RotomClientManager.updateLocalState(isScreenOpen || isHorizontalMode, isHorizontalMode)
            }

            val animState = playerAnimStates.computeIfAbsent(player.uuid) { RotomAnimState() }

            val now = System.currentTimeMillis()
            val dt = if (animState.lastRenderTime == 0L) 0.016f else ((now - animState.lastRenderTime) / 1000f).coerceIn(0.001f, 0.1f)
            animState.lastRenderTime = now

            val targetProgress = if (isScreenOpen || isHorizontalMode) 1.0f else 0.0f
            if (animState.openProgress < targetProgress) {
                animState.openProgress = (animState.openProgress + dt * TRANSITION_SPEED).coerceAtMost(targetProgress)
            } else if (animState.openProgress > targetProgress) {
                animState.openProgress = (animState.openProgress - dt * TRANSITION_SPEED).coerceAtLeast(targetProgress)
            }

            val targetHorizontal = if (isHorizontalMode) 1.0f else 0.0f
            val hSpeed = TRANSITION_SPEED * 1.5f
            if (animState.horizontalProgress < targetHorizontal) {
                animState.horizontalProgress = (animState.horizontalProgress + dt * hSpeed).coerceAtMost(targetHorizontal)
            } else if (animState.horizontalProgress > targetHorizontal) {
                animState.horizontalProgress = (animState.horizontalProgress - dt * hSpeed).coerceAtLeast(targetHorizontal)
            }

            if (playerAnimStates.size > 50) {
                playerAnimStates.entries.removeIf { now - it.value.lastRenderTime > 60_000L }
            }

            val p = animState.openProgress
            val t = p * p * p * (p * (p * 6.0f - 15.0f) + 10.0f)

            val hp = animState.horizontalProgress
            val hProg = hp * hp * (3.0f - 2.0f * hp)

            // 1. Procurar outros jogadores próximos (até 8 blocos) que TAMBÉM tenham um Rotom Phone
            val nearbyPlayers = player.level().getEntitiesOfClass(
                Player::class.java,
                player.boundingBox.inflate(8.0)
            ) { other ->
                other != player && other.isAlive && !other.isInvisible && RotomClientManager.getEffectiveRotomPhone(other) != null
            }
            val nearbyPlayer = nearbyPlayers.minByOrNull { it.distanceToSqr(player) }

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

            val finalTargetYaw = targetGazeYaw
            val finalTargetPitch = targetGazePitch

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
            val posX = X_SHOULDER + hoverX
            val posY = yShoulder + hoverY + runBob + effectiveJumpLagY
            val posZ = Z_SHOULDER + effectiveLagZ + hoverZ

            val finalYaw = animState.currentYaw + idleSwayYaw
            val finalPitch = animState.currentPitch + idleSwayPitch
            val finalRoll = idleSwayRoll

            // 2. Consultar o gerenciador para obter o Ponto Âncora compartilhado (se houver uma interação ativa)
            val isDialogueActive = RotomDialogueClientState.hasActiveDialogue(player.uuid)
            val isPhoneOpen = (t > 0.001f) || (hProg > 0.001f) || isScreenOpen || isHorizontalMode
            val sharedAnchor = if (!isPhoneOpen && !isDialogueActive) {
                RotomClientManager.getActiveSharedAnchor(player, nearbyPlayers, partialTick, now, isPhoneOpen)
            } else {
                null
            }

            if (sharedAnchor != null) {
                animState.lastAnchor = sharedAnchor
            } else if (isPhoneOpen || isDialogueActive) {
                animState.socialProgress = 0.0f
                animState.lastAnchor = null
            }

            val targetSocial = if (sharedAnchor != null) 1.0f else 0.0f
            animState.socialProgress += (targetSocial - animState.socialProgress) * (dt * 2.2f).coerceIn(0.0f, 1.0f)
            if (animState.socialProgress <= 0.001f && sharedAnchor == null) {
                animState.socialProgress = 0.0f
                animState.lastAnchor = null
            }
            val socialT = animState.socialProgress * animState.socialProgress * (3f - 2f * animState.socialProgress)

            // 2.1 Consultar se o Rotom Phone deste jogador está escaneando um Pokémon
            val activeScan = RotomScannerClientState.getScanFor(player.uuid)
            val scanEntity = if (activeScan != null && t < 0.1f) {
                player.level().getEntity(activeScan.targetEntityId)
            } else {
                null
            }

            val isScanningTarget = scanEntity != null && scanEntity.isAlive &&
                activeScan?.phase != com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket.PHASE_RETURNING &&
                activeScan?.phase != com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket.PHASE_IDLE
            val targetScan = if (isScanningTarget) 1.0f else 0.0f
            // Velocidade dinâmica linear baseada nas configurações sincronizadas com o servidor
            val approachSec = (RotomScannerClientState.clientConfig.approachTicks.coerceAtLeast(4) / 20.0f)
            val returnSec = (RotomScannerClientState.clientConfig.returnTicks.coerceAtLeast(4) / 20.0f)
            if (animState.scanProgress < targetScan) {
                animState.scanProgress = (animState.scanProgress + dt / approachSec).coerceAtMost(targetScan)
            } else if (animState.scanProgress > targetScan) {
                animState.scanProgress = (animState.scanProgress - dt / returnSec).coerceAtLeast(targetScan)
            }
            if (animState.scanProgress <= 0.001f && !isScanningTarget) {
                animState.scanProgress = 0.0f
            }
            // Curva de voo suave e elegante (Smoothstep): acelera do repouso e desacelera suavemente ao chegar
            val scanT = animState.scanProgress * animState.scanProgress * (3f - 2f * animState.scanProgress)

            // 2.2 Consultar se há diálogo ativo de curiosidade para este jogador após o scan
            val hasActiveDialogue = RotomDialogueClientState.shouldRotomFlyInFront(player.uuid)
            val dismissT = RotomDialogueClientState.getDismissProgress(player.uuid)
            val isDismissing = RotomDialogueClientState.isDismissing(player.uuid)
            val targetDialogue = if (hasActiveDialogue && !isDismissing && t < 0.1f && scanT < 0.35f) 1.0f else 0.0f
            val dialogueSpeed = if (targetDialogue > 0.5f) 2.0f else 2.6f
            if (animState.dialogueProgress < targetDialogue) {
                animState.dialogueProgress = (animState.dialogueProgress + dt * dialogueSpeed).coerceAtMost(targetDialogue)
            } else if (animState.dialogueProgress > targetDialogue) {
                animState.dialogueProgress = (animState.dialogueProgress - dt * dialogueSpeed).coerceAtLeast(targetDialogue)
            }
            if (animState.dialogueProgress <= 0.001f && !hasActiveDialogue && !isDismissing) {
                animState.dialogueProgress = 0.0f
            }
            val dialogueT = animState.dialogueProgress * animState.dialogueProgress * (3f - 2f * animState.dialogueProgress)

            // Gestos expressivos de fala (bobbing e inclinação fofa) enquanto conversa
            val talkBob = if (dialogueT > 0.001f) (Mth.sin(time * 0.40f) * 0.015f * dialogueT) else 0.0f
            val talkSway = if (dialogueT > 0.001f) (Mth.cos(time * 0.25f) * 0.012f * dialogueT) else 0.0f
            val talkPitch = if (dialogueT > 0.001f) (Mth.sin(time * 0.40f) * 6.0f * dialogueT) else 0.0f
            val talkRoll = if (dialogueT > 0.001f) (Mth.sin(time * 0.25f) * 5.0f * dialogueT) else 0.0f

            // Animação de saída de cena ao fechar o diálogo (voo gracioso para fora da tela)
            val dismissCurve = dismissT * dismissT * (3.0f - 2.0f * dismissT)
            val exitPitch = -dismissCurve * 26.0f
            val exitRoll = -dismissCurve * 38.0f
            val exitYaw = dismissCurve * 35.0f

            if (scanEntity != null && scanEntity.isAlive) {
                val entX = Mth.lerp(partialTick.toDouble(), scanEntity.xOld, scanEntity.x)
                val entY = Mth.lerp(partialTick.toDouble(), scanEntity.yOld, scanEntity.y)
                val entZ = Mth.lerp(partialTick.toDouble(), scanEntity.zOld, scanEntity.z)

                val plX = Mth.lerp(partialTick.toDouble(), player.xOld, player.x)
                val plZ = Mth.lerp(partialTick.toDouble(), player.zOld, player.z)

                val dx = plX - entX
                val dz = plZ - entZ
                val distXZ = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.05)
                val dirX = dx / distXZ
                val dirZ = dz / distXZ
                val perpX = -dirZ
                val perpZ = dirX

                val hoverDist = (scanEntity.bbWidth * 0.70 + 0.65).coerceIn(0.75, 2.0)

                // Oscilação suave de escaneamento em frente ao Pokémon
                val bobY = Math.sin(time * 0.16) * 0.08
                val bobSide = Math.cos(time * 0.10) * 0.12

                animState.scanTargetX = entX + dirX * hoverDist + perpX * bobSide
                animState.scanTargetY = entY + (scanEntity.bbHeight * 0.65).coerceAtLeast(0.4) + 0.15 + bobY
                animState.scanTargetZ = entZ + dirZ * hoverDist + perpZ * bobSide

                // Rotom voltado diretamente para o Pokémon
                val toPokeX = entX - animState.scanTargetX
                val toPokeY = (entY + scanEntity.bbHeight * 0.5) - animState.scanTargetY
                val toPokeZ = entZ - animState.scanTargetZ
                animState.scanWorldYaw = (Mth.atan2(toPokeZ, toPokeX) * (180.0 / Math.PI)).toFloat() - 90.0f
                animState.scanWorldPitch = -(kotlin.math.atan2(toPokeY, hoverDist) * (180.0 / Math.PI)).toFloat().coerceIn(-40.0f, 40.0f)
                animState.scanWorldRoll = (Math.sin(time * 0.22) * 12.0).toFloat()
            }

            // 3. Calcular a posição e rotação da coreografia no Ponto Âncora compartilhado (suporta grupos de 2 ou mais Rotoms)
            val activeAnchor = animState.lastAnchor
            var targetDanceWorldX = 0.0
            var targetDanceWorldY = 0.0
            var targetDanceWorldZ = 0.0
            var rotWorldYaw = bodyYaw
            var rotPitch = 0.0f
            var rotRoll = 0.0f

            if (socialT > 0.001f && activeAnchor != null) {
                val participants = activeAnchor.participants
                val totalCount = participants.size.coerceAtLeast(2)
                val pIndex = participants.indexOf(player.uuid).let { if (it >= 0) it else 0 }
                val participantAngle = (2.0 * Math.PI * pIndex) / totalCount.toDouble()
                val altSign = if (pIndex % 2 == 0) 1.0 else -1.0
                val sideSign = altSign

                fun worldFacingYaw(wx: Double, wz: Double): Float {
                    return (Mth.atan2(wz, wx) * (180.0 / Math.PI)).toFloat() - 90.0f
                }

                val baseRadius = if (totalCount <= 2) 0.42 else (0.42 + 0.08 * (totalCount - 2)).coerceAtMost(0.85)
                val cP = Math.cos(participantAngle)
                val sP = Math.sin(participantAngle)
                val baseWorldX = (activeAnchor.perpX * cP + activeAnchor.dirX * sP) * baseRadius
                val baseWorldZ = (activeAnchor.perpZ * cP + activeAnchor.dirZ * sP) * baseRadius
                val faceCenterWorldYaw = worldFacingYaw(-baseWorldX, -baseWorldZ)

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
                var phaseYaw = faceCenterWorldYaw
                var phasePitch = 0.0f
                var phaseRoll = 0.0f

                when (currentPhase) {
                    0 -> {
                        // INTERAÇÃO 1: CIRANDINHA ORBITAL (Voo em círculo inclinado em grupo como aviãozinho)
                        val orbitAngle = (phaseTime / 4.0f) * (2.0 * Math.PI) + participantAngle
                        val cO = Math.cos(orbitAngle)
                        val sO = Math.sin(orbitAngle)

                        phaseOffX = (activeAnchor.perpX * cO + activeAnchor.dirX * sO) * baseRadius
                        phaseOffZ = (activeAnchor.perpZ * cO + activeAnchor.dirZ * sO) * baseRadius
                        phaseOffY = Math.sin(orbitAngle * 2.0) * 0.06 * env

                        val tanX = -activeAnchor.perpX * sO + activeAnchor.dirX * cO
                        val tanZ = -activeAnchor.perpZ * sO + activeAnchor.dirZ * cO
                        val flightYaw = worldFacingYaw(tanX, tanZ)
                        phaseYaw = faceCenterWorldYaw + Mth.wrapDegrees(flightYaw - faceCenterWorldYaw) * env
                        phaseRoll = -20.0f * env
                        phasePitch = (Math.cos(orbitAngle * 2.0) * 6.0).toFloat() * env
                    }
                    1 -> {
                        // INTERAÇÃO 2: MORTAIS / BACKFLIPS SINCRONIZADOS EM GRUPO
                        val sub = phaseTime % 2.0f
                        phaseOffX = baseWorldX
                        phaseOffZ = baseWorldZ
                        phaseYaw = faceCenterWorldYaw
                        if (sub < 0.30f) {
                            val pPrep = sub / 0.30f
                            phaseOffY = (-Mth.sin(pPrep * Math.PI.toFloat()) * 0.05).toDouble()
                            phasePitch = -Mth.sin(pPrep * Math.PI.toFloat()) * 14.0f
                        } else if (sub < 1.65f) {
                            val pFlip = (sub - 0.30f) / 1.35f
                            val smoothFlip = pFlip * pFlip * (3.0f - 2.0f * pFlip)
                            phaseOffY = (Mth.sin(pFlip * Math.PI.toFloat()) * 0.38).toDouble()
                            phasePitch = smoothFlip * 360.0f
                            phaseRoll = Mth.sin(pFlip * Math.PI.toFloat() * 2.0f) * 14.0f * altSign.toFloat()
                        } else {
                            val pLand = (sub - 1.65f) / 0.35f
                            phaseOffY = (-Mth.sin(pLand * Math.PI.toFloat()) * 0.03).toDouble()
                        }
                    }
                    2 -> {
                        // INTERAÇÃO 3: OITO INFINITO / PERSEGUIÇÃO EM FILA (Figure-8 Rollercoaster Train)
                        val t8 = (phaseTime / 4.0f) * (2.0 * Math.PI) + participantAngle
                        val figPerp = Math.cos(t8) * (baseRadius * 1.20)
                        val figDir = Math.sin(2.0 * t8) * (baseRadius * 0.85)

                        val rawX = activeAnchor.perpX * figPerp + activeAnchor.dirX * figDir
                        val rawZ = activeAnchor.perpZ * figPerp + activeAnchor.dirZ * figDir
                        phaseOffX = Mth.lerp(env.toDouble(), baseWorldX, rawX)
                        phaseOffZ = Mth.lerp(env.toDouble(), baseWorldZ, rawZ)
                        phaseOffY = Math.cos(2.0 * t8) * 0.09 * altSign * env

                        val velPerp = -Math.sin(t8) * (baseRadius * 1.20)
                        val velDir = 2.0 * Math.cos(2.0 * t8) * (baseRadius * 0.85)
                        val velWorldX = activeAnchor.perpX * velPerp + activeAnchor.dirX * velDir
                        val velWorldZ = activeAnchor.perpZ * velPerp + activeAnchor.dirZ * velDir
                        val flightYaw = worldFacingYaw(velWorldX, velWorldZ)

                        phaseYaw = faceCenterWorldYaw + Mth.wrapDegrees(flightYaw - faceCenterWorldYaw) * env
                        phaseRoll = (Math.sin(2.0 * t8) * 24.0).toFloat() * env
                        phasePitch = (-Math.sin(2.0 * t8) * 10.0 * altSign).toFloat() * env
                    }
                    3 -> {
                        // INTERAÇÃO 4: ESPIRAL MULTI-HÉLICE / DNA (Multi-Helix Vertical Corkscrew)
                        val helixAngle = (phaseTime / 4.0f) * (4.0 * Math.PI) + participantAngle
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

                        phaseYaw = faceCenterWorldYaw + Mth.wrapDegrees(flightYaw - faceCenterWorldYaw) * env
                        phasePitch = (-Math.cos((phaseTime / 4.0) * Math.PI) * 18.0).toFloat() * env
                        phaseRoll = -22.0f * env
                    }
                    4 -> {
                        // INTERAÇÃO 5: PULINHOS & DANCINHA ALEGRE (Bouncing Wiggle Dance)
                        val bounceCycle = phaseTime * Math.PI.toFloat() * 2.0f
                        val hop = kotlin.math.abs(Mth.sin(bounceCycle))
                        phaseOffY = (hop * hop * 0.24f * env).toDouble()

                        val tanPerpX = -sP
                        val tanPerpZ = cP
                        val tanWorldX = activeAnchor.perpX * tanPerpX + activeAnchor.dirX * tanPerpZ
                        val tanWorldZ = activeAnchor.perpZ * tanPerpX + activeAnchor.dirZ * tanPerpZ
                        val sway = Math.sin(phaseTime * Math.PI * 2.0) * 0.15 * altSign * env
                        phaseOffX = baseWorldX + tanWorldX * sway
                        phaseOffZ = baseWorldZ + tanWorldZ * sway

                        phaseYaw = faceCenterWorldYaw
                        phaseRoll = Mth.sin(bounceCycle) * 20.0f * altSign.toFloat() * env
                        phasePitch = -hop * 16.0f * env
                    }
                    5 -> {
                        // INTERAÇÃO 6: ENCONTRÃO CARINHOSO NO CENTRO & PIRUETA 360° EM GRUPO
                        if (phaseTime < 2.0f) {
                            val approach = Math.sin((phaseTime / 2.0) * Math.PI)
                            val curR = baseRadius * (1.0 - approach * 0.72)
                            val cA = Math.cos(participantAngle)
                            val sA = Math.sin(participantAngle)
                            phaseOffX = (activeAnchor.perpX * cA + activeAnchor.dirX * sA) * curR
                            phaseOffZ = (activeAnchor.perpZ * cA + activeAnchor.dirZ * sA) * curR
                            phaseOffY = approach * 0.08
                            phaseYaw = faceCenterWorldYaw
                            phaseRoll = (approach * 22.0 * altSign).toFloat()
                            phasePitch = (-approach * 10.0).toFloat()
                        } else {
                            val pSpin = ((phaseTime - 2.0f) / 1.65f).coerceIn(0.0f, 1.0f)
                            val smoothSpin = pSpin * pSpin * (3.0f - 2.0f * pSpin)
                            phaseOffX = baseWorldX
                            phaseOffZ = baseWorldZ
                            phaseOffY = Math.sin(pSpin * Math.PI) * 0.18
                            phaseYaw = faceCenterWorldYaw + smoothSpin * 360.0f * altSign.toFloat()
                            phaseRoll = (Math.sin(pSpin * Math.PI * 2.0) * 12.0 * altSign).toFloat()
                        }
                    }
                    6 -> {
                        // INTERAÇÃO 7: GIRO PARAFUSO / AILERON BARREL ROLLS
                        val loopA = (phaseTime / 4.0) * (2.0 * Math.PI)
                        val swayDir = Math.sin(loopA) * 0.14 * altSign * env
                        phaseOffX = baseWorldX + activeAnchor.dirX * swayDir
                        phaseOffZ = baseWorldZ + activeAnchor.dirZ * swayDir
                        phaseOffY = (1.0 - Math.cos(loopA)) * 0.12 * env
                        phaseYaw = faceCenterWorldYaw

                        val sub = phaseTime % 2.0f
                        if (sub in 0.25f..1.65f) {
                            val pRoll = (sub - 0.25f) / 1.40f
                            val smoothRoll = pRoll * pRoll * (3.0f - 2.0f * pRoll)
                            phaseRoll = smoothRoll * 360.0f * altSign.toFloat()
                        }
                    }
                    else -> {
                        // INTERAÇÃO 8: PEGA-PEGA ZIG-ZAG (Playful Tag & Feints)
                        val zig = Math.sin(phaseTime * Math.PI * 1.5) * 0.22 * env
                        val zag = Math.cos(phaseTime * Math.PI * 3.0) * 0.10 * altSign * env
                        phaseOffX = baseWorldX * (1.0 + zig) + activeAnchor.dirX * zag
                        phaseOffZ = baseWorldZ * (1.0 + zig) + activeAnchor.dirZ * zag
                        phaseOffY = kotlin.math.abs(Math.sin(phaseTime * Math.PI * 3.0)) * 0.14 * env

                        phaseYaw = faceCenterWorldYaw + (Math.cos(phaseTime * Math.PI * 1.5) * 28.0 * altSign * env).toFloat()
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

                // Posição exata dos olhos do jogador no espaço de mundo (independente de câmera e perspectiva)
                val plX = Mth.lerp(partialTick.toDouble(), player.xOld, player.x)
                val plEyeY = Mth.lerp(partialTick.toDouble(), player.yOld, player.y) + player.getEyeHeight(player.pose)
                val plZ = Mth.lerp(partialTick.toDouble(), player.zOld, player.z)

                // Direção para onde o crosshair do jogador está apontando
                val lookYaw = if (isFirstPersonPass) mc.gameRenderer.mainCamera.yRot else Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
                val lookPitch = if (isFirstPersonPass) mc.gameRenderer.mainCamera.xRot else Mth.lerp(partialTick, player.xRotO, player.xRot)

                val lookYawRad = Math.toRadians(lookYaw.toDouble())
                val lookPitchRad = Math.toRadians(lookPitch.toDouble())
                val cosLookPitch = Mth.cos(lookPitchRad.toFloat())
                val sinLookPitch = Mth.sin(lookPitchRad.toFloat())
                val cosLookYaw = Mth.cos(lookYawRad.toFloat())
                val sinLookYaw = Mth.sin(lookYawRad.toFloat())

                val lookDirX = -sinLookYaw * cosLookPitch
                val lookDirY = -sinLookPitch
                val lookDirZ = cosLookYaw * cosLookPitch

                val upDirX = -sinLookYaw * sinLookPitch
                val upDirY = cosLookPitch
                val upDirZ = cosLookYaw * sinLookPitch

                // Posição no mundo exatamente na mira / crosshair do jogador (elevado ~4 pixels na vertical da visão)
                val upOffset = 0.25f
                val faceDist = if (hProg > 0.001f) Mth.lerp(hProg, 0.58f, 0.50f) else 0.58f
                val openWorldX = plX + lookDirX * faceDist + upDirX * upOffset
                val openWorldY = plEyeY + lookDirY * faceDist + upDirY * upOffset
                val openWorldZ = plZ + lookDirZ * faceDist + upDirZ * upOffset

                val openCamX = (openWorldX - camPos.x).toFloat()
                val openCamY = (openWorldY - camPos.y).toFloat()
                val openCamZ = (openWorldZ - camPos.z).toFloat()

                if (isFirstPersonPass) {
                    val camYaw = mc.gameRenderer.mainCamera.yRot
                    val camPitch = mc.gameRenderer.mainCamera.xRot

                    // Segue o corpo/câmera de forma bem solta para que, ao virar para a esquerda, olhar para baixo
                    // ou correr, o Rotom saia naturalmente da tela e apareça só de vez em quando no canto superior direito.
                    // Quando estiver falando no balão de diálogo, alinha-se suavemente no lado direito da visão.
                    val targetAnchorYaw = if (dialogueT > 0.001f) {
                        camYaw - 4.0f
                    } else {
                        bodyYaw + Mth.wrapDegrees(camYaw - bodyYaw) * 0.55f
                    }
                    if (animState.fpCompanionYaw.isNaN()) {
                        animState.fpCompanionYaw = targetAnchorYaw
                    } else {
                        val diff = Mth.wrapDegrees(targetAnchorYaw - animState.fpCompanionYaw)
                        val yawFollowSpeed = if (dialogueT > 0.001f) 4.5f else 2.4f
                        animState.fpCompanionYaw += diff * (dt * yawFollowSpeed).coerceIn(0.0f, 1.0f)
                        val maxDiff = if (dialogueT > 0.001f) 35.0f else 105.0f
                        val clampedDiff = Mth.wrapDegrees(camYaw - animState.fpCompanionYaw).coerceIn(-maxDiff, maxDiff)
                        animState.fpCompanionYaw = camYaw - clampedDiff
                    }

                    val fpRad = Math.toRadians(animState.fpCompanionYaw.toDouble())
                    val fwdX = -Math.sin(fpRad)
                    val fwdZ = Math.cos(fpRad)
                    // Vetor para a DIREITA do jogador (canto superior direito)
                    val rightX = -Math.cos(fpRad)
                    val rightZ = -Math.sin(fpRad)

                    // Posição física de repouso no ombro direito (completamente fora da visão da câmera em 1ª pessoa)
                    val shoulderOffscreenFwd = -0.32f
                    val shoulderOffscreenRight = 0.82f
                    val shoulderOffscreenUp = -0.46f

                    // Posição de fala na tela durante o diálogo (posicionado no lado direito da tela)
                    val dialogueFwd = 0.46f
                    val dialogueRight = 0.28f + talkSway
                    val dialogueUp = 0.18f + talkBob

                    // Interpolação suave ao entrar no diálogo
                    val dCurve = dialogueT * dialogueT * (3.0f - 2.0f * dialogueT)
                    val baseFpFwd = Mth.lerp(dCurve, shoulderOffscreenFwd, dialogueFwd)
                    val baseFpRight = Mth.lerp(dCurve, shoulderOffscreenRight, dialogueRight)
                    val baseFpUp = Mth.lerp(dCurve, shoulderOffscreenUp, dialogueUp)

                    // Animação de saída graciosa voando para fora da tela de volta ao ombro
                    val fpFwd = Mth.lerp(dismissCurve, baseFpFwd, shoulderOffscreenFwd - 0.20f)
                    val fpRight = Mth.lerp(dismissCurve, baseFpRight, shoulderOffscreenRight + 0.35f)
                    val fpUp = Mth.lerp(dismissCurve, baseFpUp, shoulderOffscreenUp - 0.15f)

                    val idleCamX = (fwdX * fpFwd + rightX * fpRight).toFloat()
                    val idleCamY = fpUp
                    val idleCamZ = (fwdZ * fpFwd + rightZ * fpRight).toFloat()

                    shoulderCamPos.set(
                        Mth.lerp(t, idleCamX, openCamX),
                        Mth.lerp(t, idleCamY, openCamY),
                        Mth.lerp(t, idleCamZ, openCamZ)
                    )

                    val idleFpWorldYaw = if (nearbyPlayer != null && dialogueT <= 0.001f) {
                        bodyYaw + (finalYaw - YAW_FACING_FRONT)
                    } else if (dialogueT > 0.001f) {
                        camYaw - 165.0f + idleSwayYaw * 0.3f - exitYaw
                    } else {
                        animState.fpCompanionYaw - 145.0f + idleSwayYaw
                    }
                    val openFpWorldYaw = lookYaw
                    shoulderWorldYaw = idleFpWorldYaw + Mth.wrapDegrees(openFpWorldYaw - idleFpWorldYaw) * t
                    // Mais próximo do ombro e com escala equilibrada (0.55f) para não parecer distante, ou 1.0f durante o diálogo
                    val fpScaleFactor = Mth.lerp(maxOf(t, socialT, scanT, dialogueT), 0.55f, 1.0f)
                    baseModelScale = 0.9375f * player.scale * fpScaleFactor
                } else {
                    // Em terceira pessoa ou jogadores remotos: extrai a posição do ombro e interpola para a mira
                    val idleShoulderCamPos = Vector3f()
                    poseStack.last().pose().transformPosition(posX, posY, posZ, idleShoulderCamPos)

                    shoulderCamPos.set(
                        Mth.lerp(t, idleShoulderCamPos.x, openCamX),
                        Mth.lerp(t, idleShoulderCamPos.y, openCamY),
                        Mth.lerp(t, idleShoulderCamPos.z, openCamZ)
                    )

                    val idleWorldYaw = bodyYaw + (finalYaw - YAW_FACING_FRONT)
                    val openWorldYaw = lookYaw
                    shoulderWorldYaw = idleWorldYaw + Mth.wrapDegrees(openWorldYaw - idleWorldYaw) * t
                    baseModelScale = 0.9375f * player.scale
                }

                // Posição no mundo para Rotom vir à frente do jogador relatar a curiosidade (terceira pessoa)
                val plFeetY = Mth.lerp(partialTick.toDouble(), player.yOld, player.y)
                val plYawRad = Math.toRadians(player.yRot.toDouble())
                val plFwdX = -Math.sin(plYawRad)
                val plFwdZ = Math.cos(plYawRad)
                val plRightX = -Math.cos(plYawRad)
                val plRightZ = -Math.sin(plYawRad)

                val reportWorldX = plX + plFwdX * 0.95 + plRightX * 0.18
                val reportWorldY = plFeetY + player.eyeHeight * 0.88 + Math.sin(time * 0.15) * 0.04
                val reportWorldZ = plZ + plFwdZ * 0.95 + plRightZ * 0.18

                val toPlayerX = plX - reportWorldX
                val toPlayerY = (plFeetY + player.eyeHeight) - reportWorldY
                val toPlayerZ = plZ - reportWorldZ
                val distXZ = kotlin.math.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ).coerceAtLeast(0.05)
                val reportWorldYaw = (Mth.atan2(toPlayerZ, toPlayerX) * (180.0 / Math.PI)).toFloat() - 90.0f
                val reportWorldPitch = -(kotlin.math.atan2(toPlayerY, distXZ) * (180.0 / Math.PI)).toFloat() + talkPitch
                val reportWorldRoll = talkRoll

                val reportCamX = (reportWorldX - camPos.x).toFloat()
                val reportCamY = (reportWorldY - camPos.y).toFloat()
                val reportCamZ = (reportWorldZ - camPos.z).toFloat()

                val danceCamX = if (activeAnchor != null) (targetDanceWorldX - camPos.x).toFloat() else shoulderCamPos.x
                val danceCamY = if (activeAnchor != null) (targetDanceWorldY - camPos.y).toFloat() else shoulderCamPos.y
                val danceCamZ = if (activeAnchor != null) (targetDanceWorldZ - camPos.z).toFloat() else shoulderCamPos.z

                val socialCamX = Mth.lerp(socialT, shoulderCamPos.x, danceCamX)
                val socialCamY = Mth.lerp(socialT, shoulderCamPos.y, danceCamY)
                val socialCamZ = Mth.lerp(socialT, shoulderCamPos.z, danceCamZ)

                val scanCamX = (animState.scanTargetX - camPos.x).toFloat()
                val scanCamY = (animState.scanTargetY - camPos.y).toFloat()
                val scanCamZ = (animState.scanTargetZ - camPos.z).toFloat()

                // Arco de elevação gracioso durante a trajetória de voo (ida e volta)
                val flightArc = (Math.sin(scanT * Math.PI) * 0.42).toFloat()
                // Inclinação aerodinâmica de voo (banking roll)
                val flightBank = (Math.sin(scanT * Math.PI) * 14.0).toFloat()

                val postScanCamX = Mth.lerp(scanT, socialCamX, scanCamX)
                val postScanCamY = Mth.lerp(scanT, socialCamY, scanCamY) + flightArc
                val postScanCamZ = Mth.lerp(scanT, socialCamZ, scanCamZ)

                val reportArc = if (dismissT > 0.0f) (Math.sin(dismissCurve.toDouble() * Math.PI) * 0.30).toFloat() else 0.0f
                val effectiveReportCamX = if (dismissT > 0.0f) Mth.lerp(dismissCurve, reportCamX, shoulderCamPos.x) else reportCamX
                val effectiveReportCamY = (if (dismissT > 0.0f) Mth.lerp(dismissCurve, reportCamY, shoulderCamPos.y) else reportCamY) + reportArc
                val effectiveReportCamZ = if (dismissT > 0.0f) Mth.lerp(dismissCurve, reportCamZ, shoulderCamPos.z) else reportCamZ

                val finalCamX = if (isFirstPersonPass) postScanCamX else Mth.lerp(dialogueT, postScanCamX, effectiveReportCamX)
                val finalCamY = if (isFirstPersonPass) postScanCamY else Mth.lerp(dialogueT, postScanCamY, effectiveReportCamY)
                val finalCamZ = if (isFirstPersonPass) postScanCamZ else Mth.lerp(dialogueT, postScanCamZ, effectiveReportCamZ)

                val socialWorldYaw = if (activeAnchor != null) {
                    shoulderWorldYaw + Mth.wrapDegrees(rotWorldYaw - shoulderWorldYaw) * socialT
                } else {
                    shoulderWorldYaw
                }
                val scanWorldYaw = socialWorldYaw + Mth.wrapDegrees(animState.scanWorldYaw - socialWorldYaw) * scanT
                val scanPitch = Mth.lerp(scanT, Mth.lerp(socialT, finalPitch, rotPitch), animState.scanWorldPitch)
                val horizontalRoll = hProg * 90.0f
                val scanRoll = Mth.lerp(scanT, Mth.lerp(socialT, finalRoll, rotRoll), animState.scanWorldRoll) + flightBank + (horizontalRoll * (1.0f - scanT))

                val effectiveReportYaw = if (dismissT > 0.0f) (reportWorldYaw + Mth.wrapDegrees(shoulderWorldYaw - reportWorldYaw) * dismissCurve) else reportWorldYaw
                val currentWorldYaw = if (isFirstPersonPass) scanWorldYaw else (scanWorldYaw + Mth.wrapDegrees(effectiveReportYaw - scanWorldYaw) * dialogueT)
                val openPitch = lookPitch
                val effectiveOpenPitch = Mth.lerp(t, scanPitch, openPitch)
                val currentPitch = if (isFirstPersonPass) {
                    if (dialogueT > 0.001f) scanPitch + talkPitch + exitPitch else effectiveOpenPitch
                } else {
                    if (dialogueT > 0.001f) Mth.lerp(dialogueT, scanPitch, reportWorldPitch) else effectiveOpenPitch
                }
                val currentRoll = if (isFirstPersonPass) scanRoll + talkRoll + exitRoll else Mth.lerp(dialogueT, scanRoll, reportWorldRoll + (if (dismissT > 0.0f) (Math.sin(dismissT.toDouble() * Math.PI) * 20.0).toFloat() else 0.0f))

                // Reseta o PoseStack para o Espaço de Mundo puro (relativo à câmera),
                // garantindo 0% de vínculo com a movimentação ou rotação do jogador durante a dança ou scan!
                poseStack.last().pose().identity()
                poseStack.last().normal().identity()
                poseStack.translate(finalCamX, finalCamY, finalCamZ)

                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - currentWorldYaw))
                poseStack.scale(-baseModelScale, -baseModelScale, baseModelScale)

                val combinedTiltPitch = (effectiveTilt + jumpTiltPitch) * (1.0f - maxOf(socialT, scanT, dialogueT, hProg))
                if (combinedTiltPitch > 0.01f || combinedTiltPitch < -0.01f) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(combinedTiltPitch))
                }
                val effectiveBankRoll = effectiveBank * (1.0f - maxOf(socialT, scanT, dialogueT, hProg))
                if (effectiveBankRoll != 0.0f) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(effectiveBankRoll))
                }

                val facingYaw = if (t > 0.001f) Mth.lerp(t, YAW_FACING_FRONT, 0.0f) else YAW_FACING_FRONT
                poseStack.mulPose(Axis.YP.rotationDegrees(facingYaw))

                poseStack.mulPose(Axis.XP.rotationDegrees(currentPitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(currentRoll))
                poseStack.scale(SCALE, -SCALE, -SCALE)
            } else {
                // Renderização no inventário 2D (Paperdoll)
                poseStack.translate(posX, posY, posZ)
                poseStack.mulPose(Axis.YP.rotationDegrees(finalYaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(finalPitch))
                val effectivePaperdollRoll = finalRoll + (if (hProg > 0.001f) hProg * 90.0f else 0.0f)
                poseStack.mulPose(Axis.ZP.rotationDegrees(effectivePaperdollRoll))
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
