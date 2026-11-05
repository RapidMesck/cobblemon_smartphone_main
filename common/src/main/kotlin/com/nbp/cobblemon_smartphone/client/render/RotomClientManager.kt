package com.nbp.cobblemon_smartphone.client.render

import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SetSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setAccessoryDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomPhone
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object RotomClientManager {
    private var lastLocalOpen: Boolean? = null
    private var lastLocalHorizontal: Boolean? = null
    private var lastLocalSelfie: Boolean? = null
    private var lastLocalPhoto: Boolean? = null
    private val remoteOpenStates = ConcurrentHashMap<UUID, Boolean>()
    private val remoteHorizontalStates = ConcurrentHashMap<UUID, Boolean>()
    private val remoteSelfieStates = ConcurrentHashMap<UUID, Boolean>()
    private val remotePhotoStates = ConcurrentHashMap<UUID, Boolean>()

    data class RemoteRotomVisual(
        val hasRotom: Boolean,
        val phoneColor: String,
        val accessory: String,
        val ribbonColor: String,
        val cachedStack: ItemStack?
    )

    private val remoteRotomVisuals = ConcurrentHashMap<UUID, RemoteRotomVisual>()
    private val activeSessions = ConcurrentHashMap<UUID, RotomPlayerRenderLayer.Companion.SocialAnchor>()
    private val playerToSessionId = ConcurrentHashMap<UUID, UUID>()
    private val fallbackCheckStates = ConcurrentHashMap<UUID, Long>()
    @Volatile
    private var serverSocialSyncReceived: Boolean = false

    fun isPlayerOpen(uuid: UUID): Boolean {
        return remoteOpenStates[uuid] == true
    }

    fun isPlayerHorizontal(uuid: UUID): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player?.uuid == uuid) {
            return ScannerManager.isInUse() || SocialPhotoClient.isCameraActive()
        }
        return remoteHorizontalStates[uuid] == true
    }

    fun isPlayerSelfie(uuid: UUID): Boolean {
        val mc = runCatching { Minecraft.getInstance() }.getOrNull()
        if (mc?.player?.uuid == uuid) {
            return SocialPhotoClient.isCameraActive() && !mc.options.cameraType.isFirstPerson
        }
        return remoteSelfieStates[uuid] == true
    }

    fun isPlayerInPhotoMode(uuid: UUID): Boolean {
        val mc = runCatching { Minecraft.getInstance() }.getOrNull()
        if (mc?.player?.uuid == uuid) {
            return SocialPhotoClient.isCameraActive()
        }
        return remotePhotoStates[uuid] == true
    }

    fun setRemotePlayerOpen(
        uuid: UUID,
        isOpen: Boolean,
        isHorizontal: Boolean = false,
        isSelfie: Boolean = false,
        isPhotoMode: Boolean = false
    ) {
        if (isOpen) {
            remoteOpenStates[uuid] = true
            if (isHorizontal) {
                remoteHorizontalStates[uuid] = true
            } else {
                remoteHorizontalStates.remove(uuid)
            }
            if (isSelfie) {
                remoteSelfieStates[uuid] = true
            } else {
                remoteSelfieStates.remove(uuid)
            }
            if (isPhotoMode) {
                remotePhotoStates[uuid] = true
            } else {
                remotePhotoStates.remove(uuid)
            }
        } else {
            remoteOpenStates.remove(uuid)
            remoteHorizontalStates.remove(uuid)
            remoteSelfieStates.remove(uuid)
            remotePhotoStates.remove(uuid)
        }
    }

    fun setRemoteRotomEquipState(
        uuid: UUID,
        hasRotom: Boolean,
        phoneColor: String,
        accessory: String,
        ribbonColor: String
    ) {
        serverSocialSyncReceived = true
        if (phoneColor.isBlank()) {
            remoteRotomVisuals.remove(uuid)
            return
        }
        val colorEnum = SmartphoneColor.entries.firstOrNull { it.name.equals(phoneColor, ignoreCase = true) }
            ?: SmartphoneColor.RED
        val baseItem = when (colorEnum) {
            SmartphoneColor.WHITE -> CobblemonSmartphoneItems.WHITE_SMARTPHONE
            SmartphoneColor.ORANGE -> CobblemonSmartphoneItems.ORANGE_SMARTPHONE
            SmartphoneColor.MAGENTA -> CobblemonSmartphoneItems.MAGENTA_SMARTPHONE
            SmartphoneColor.LIGHT_BLUE -> CobblemonSmartphoneItems.LIGHT_BLUE_SMARTPHONE
            SmartphoneColor.YELLOW -> CobblemonSmartphoneItems.YELLOW_SMARTPHONE
            SmartphoneColor.LIME -> CobblemonSmartphoneItems.LIME_SMARTPHONE
            SmartphoneColor.PINK -> CobblemonSmartphoneItems.PINK_SMARTPHONE
            SmartphoneColor.GRAY -> CobblemonSmartphoneItems.GRAY_SMARTPHONE
            SmartphoneColor.LIGHT_GRAY -> CobblemonSmartphoneItems.LIGHT_GRAY_SMARTPHONE
            SmartphoneColor.CYAN -> CobblemonSmartphoneItems.CYAN_SMARTPHONE
            SmartphoneColor.PURPLE -> CobblemonSmartphoneItems.PURPLE_SMARTPHONE
            SmartphoneColor.BLUE -> CobblemonSmartphoneItems.BLUE_SMARTPHONE
            SmartphoneColor.BROWN -> CobblemonSmartphoneItems.BROWN_SMARTPHONE
            SmartphoneColor.GREEN -> CobblemonSmartphoneItems.GREEN_SMARTPHONE
            SmartphoneColor.RED -> CobblemonSmartphoneItems.RED_SMARTPHONE
            SmartphoneColor.BLACK -> CobblemonSmartphoneItems.BLACK_SMARTPHONE
        }
        val stack = ItemStack(baseItem)
        if (hasRotom) {
            stack.setRotomPhone(true)
            if (accessory.isNotBlank()) {
                stack.setRotomAccessory(accessory)
                val defaultColor = if (accessory == "bow") DyeColor.PINK else DyeColor.LIGHT_BLUE
                val dye = DyeColor.byName(ribbonColor, defaultColor) ?: defaultColor
                stack.setAccessoryDyeColor(dye)
            }
        }
        remoteRotomVisuals[uuid] = RemoteRotomVisual(
            hasRotom = hasRotom,
            phoneColor = phoneColor,
            accessory = accessory,
            ribbonColor = ribbonColor,
            cachedStack = stack
        )
    }

    /**
     * Retorna o ItemStack do Smartphone (seja Rotom Phone ou normal) do jogador (checando inventário local/mãos primeiro
     * e usando o estado sincronizado pelo servidor para jogadores remotos com o item no inventário).
     */
    fun getEffectiveSmartphone(player: Player): ItemStack? {
        val direct = SmartphoneHelper.getSmartphone(player)
        if (direct != null) return direct
        if (player == Minecraft.getInstance().player) return null
        return remoteRotomVisuals[player.uuid]?.cachedStack
    }

    /**
     * Retorna o ItemStack do Rotom Phone do jogador (se possuir o upgrade do Rotom).
     */
    fun getEffectiveRotomPhone(player: Player): ItemStack? {
        val stack = getEffectiveSmartphone(player)
        return if (stack != null && stack.isRotomPhone()) stack else null
    }

    fun hasActiveSessionFor(uuid: UUID): Boolean {
        val sessId = playerToSessionId[uuid] ?: return false
        val sess = activeSessions[sessId] ?: return false
        return System.currentTimeMillis() < sess.endTimeMs
    }

    fun onServerSocialSession(
        sessionId: UUID,
        participants: List<UUID>,
        active: Boolean,
        anchorX: Double,
        anchorY: Double,
        anchorZ: Double,
        dirX: Double,
        dirZ: Double,
        patternSeed: Int,
        durationTicks: Int
    ) {
        serverSocialSyncReceived = true
        if (!active) {
            val old = activeSessions.remove(sessionId)
            if (old != null) {
                for (u in old.participants) {
                    playerToSessionId.remove(u)
                }
            }
            for (u in participants) {
                playerToSessionId.remove(u)
            }
            return
        }
        val now = System.currentTimeMillis()
        val gameTime = Minecraft.getInstance().level?.gameTime ?: 0L
        val dLen = kotlin.math.sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.01)
        val uX = dirX / dLen
        val uZ = dirZ / dLen
        val anchor = RotomPlayerRenderLayer.Companion.SocialAnchor(
            sessionId = sessionId,
            participants = participants,
            anchorX = anchorX,
            anchorY = anchorY,
            anchorZ = anchorZ,
            dirX = uX,
            dirZ = uZ,
            perpX = -uZ,
            perpZ = uX,
            patternSeed = patternSeed,
            startGameTime = gameTime,
            endTimeMs = now + (durationTicks * 50L).coerceAtLeast(12_000L),
            lastActiveTime = now
        )
        activeSessions[sessionId] = anchor
        for (u in participants) {
            playerToSessionId[u] = sessionId
        }
    }

    /**
     * Obtém (ou sorteia no modo fallback local) a sessão de interação social compartilhada
     * em grupo entre os jogadores próximos. Garante que todos os Rotoms compartilhem o mesmo Ponto Âncora.
     */
    fun getActiveSharedAnchor(
        player: Player,
        nearbyPlayers: List<Player>,
        partialTick: Float,
        now: Long,
        isLocalScreenOpen: Boolean
    ): RotomPlayerRenderLayer.Companion.SocialAnchor? {
        val currentSessId = playerToSessionId[player.uuid]
        if (currentSessId != null) {
            val existing = activeSessions[currentSessId]
            if (existing != null) {
                val isLocalPlayer = (player == Minecraft.getInstance().player)
                val isOpen = if (isLocalPlayer) isLocalScreenOpen else isPlayerOpen(player.uuid)
                val isDialogue = com.nbp.cobblemon_smartphone.client.RotomDialogueClientState.hasActiveDialogue(player.uuid)
                val distSqr = player.distanceToSqr(existing.anchorX, existing.anchorY - 1.0, existing.anchorZ)
                if (now < existing.endTimeMs && !isOpen && !isDialogue && distSqr <= 56.25) {
                    existing.lastActiveTime = now
                    return existing
                } else {
                    playerToSessionId.remove(player.uuid)
                    val remaining = existing.participants.filter { it != player.uuid }
                    if (remaining.size < 2) {
                        activeSessions.remove(currentSessId)
                        for (u in remaining) {
                            playerToSessionId.remove(u)
                        }
                    }
                    return null
                }
            }
        }

        // Se o servidor já está coordenando as sessões via pacote, aguardamos o trigger do servidor
        if (serverSocialSyncReceived) {
            return null
        }

        // Fallback determinístico no cliente (caso jogando singleplayer / sem pacotes do servidor):
        val isLocalPlayer = (player == Minecraft.getInstance().player)
        if (isLocalPlayer && isLocalScreenOpen) return null
        if (!isLocalPlayer && isPlayerOpen(player.uuid)) return null
        if (com.nbp.cobblemon_smartphone.client.RotomDialogueClientState.hasActiveDialogue(player.uuid)) return null

        val validNearby = nearbyPlayers.filter { other ->
            !isPlayerOpen(other.uuid) && other.distanceToSqr(player) <= 36.0
        }
        if (validNearby.isEmpty()) return null

        val cluster = (validNearby + player).distinctBy { it.uuid }.sortedBy { it.uuid }
        if (cluster.size < 2) return null

        val leaderUuid = cluster.first().uuid
        val nextCheck = fallbackCheckStates.getOrDefault(leaderUuid, 0L)
        if (now < nextCheck) return null

        val gameTimeBucket = (player.level().gameTime / 120L)
        val detSeed = leaderUuid.hashCode() xor gameTimeBucket.toInt()
        val detRandom = Random(detSeed)
        val chance = 0.48f
        fallbackCheckStates[leaderUuid] = now + 7_000L

        if (detRandom.nextFloat() < chance) {
            val count = cluster.size.toDouble()
            val anchorX = cluster.sumOf { Mth.lerp(partialTick.toDouble(), it.xo, it.x) } / count
            val anchorY = (cluster.sumOf { Mth.lerp(partialTick.toDouble(), it.yo, it.y) } / count) + 1.35
            val anchorZ = cluster.sumOf { Mth.lerp(partialTick.toDouble(), it.zo, it.z) } / count

            val p0 = cluster[0]
            val p1 = cluster[1]
            val dx = p1.x - p0.x
            val dz = p1.z - p0.z
            val dLen = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
            val uX = dx / dLen
            val uZ = dz / dLen

            val sessId = UUID.nameUUIDFromBytes("fallback-$leaderUuid-$gameTimeBucket".toByteArray())
            val durationMs = 18_000L
            val participantUuids = cluster.map { it.uuid }
            val anchor = RotomPlayerRenderLayer.Companion.SocialAnchor(
                sessionId = sessId,
                participants = participantUuids,
                anchorX = anchorX,
                anchorY = anchorY,
                anchorZ = anchorZ,
                dirX = uX,
                dirZ = uZ,
                perpX = -uZ,
                perpZ = uX,
                patternSeed = kotlin.math.abs(detSeed).coerceAtLeast(1),
                startGameTime = player.level().gameTime,
                endTimeMs = now + durationMs,
                lastActiveTime = now
            )
            activeSessions[sessId] = anchor
            for (u in participantUuids) {
                playerToSessionId[u] = sessId
            }
            return anchor
        }

        return null
    }

    fun updateLocalState(
        isOpen: Boolean,
        isHorizontal: Boolean = false,
        isSelfie: Boolean = false,
        isPhotoMode: Boolean = false
    ) {
        if (lastLocalOpen != isOpen || lastLocalHorizontal != isHorizontal || lastLocalSelfie != isSelfie || lastLocalPhoto != isPhotoMode) {
            lastLocalOpen = isOpen
            lastLocalHorizontal = isHorizontal
            lastLocalSelfie = isSelfie
            lastLocalPhoto = isPhotoMode
            SetSmartphoneOpenStatePacket(isOpen, isHorizontal, isSelfie, isPhotoMode).sendToServer()
        }
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        if (mc.player == null) {
            remoteRotomVisuals.clear()
            remoteOpenStates.clear()
            remoteHorizontalStates.clear()
            remoteSelfieStates.clear()
            remotePhotoStates.clear()
            activeSessions.clear()
            playerToSessionId.clear()
            fallbackCheckStates.clear()
            serverSocialSyncReceived = false
            lastLocalOpen = null
            lastLocalHorizontal = null
            lastLocalSelfie = null
            lastLocalPhoto = null
            return
        }
        val isScreenOpen = RotomPlayerRenderLayer.isSmartphoneScreen(mc.screen)
        val isScannerActive = ScannerManager.isInUse()
        val isPhotoActive = SocialPhotoClient.isCameraActive()
        val isSelfieActive = isPhotoActive && !mc.options.cameraType.isFirstPerson
        val isOpen = isScreenOpen || isScannerActive || isPhotoActive
        val isHorizontal = isScannerActive || isPhotoActive
        updateLocalState(isOpen, isHorizontal, isSelfieActive, isPhotoActive)
        com.nbp.cobblemon_smartphone.client.RotomScannerClientState.tick()
        com.nbp.cobblemon_smartphone.client.RotomDialogueClientState.tick()
    }
}
