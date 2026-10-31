package com.nbp.cobblemon_smartphone.client.render

import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.SetSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRibbonDyeColor
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
    private var lastLocalState: Boolean? = null
    private val remoteOpenStates = ConcurrentHashMap<UUID, Boolean>()

    data class RemoteRotomVisual(
        val hasRotom: Boolean,
        val phoneColor: String,
        val accessory: String,
        val ribbonColor: String,
        val cachedStack: ItemStack?
    )

    data class ClientFallbackPairState(
        var nextCheckTimeMs: Long = 0L,
        var lastSeenNearMs: Long = 0L,
        var isFirstMeetingCheck: Boolean = true
    )

    private val remoteRotomVisuals = ConcurrentHashMap<UUID, RemoteRotomVisual>()
    private val activeSocialAnchors = ConcurrentHashMap<Pair<UUID, UUID>, RotomPlayerRenderLayer.Companion.SocialAnchor>()
    private val fallbackPairStates = ConcurrentHashMap<Pair<UUID, UUID>, ClientFallbackPairState>()
    @Volatile
    private var serverSocialSyncReceived: Boolean = false

    fun isPlayerOpen(uuid: UUID): Boolean {
        return remoteOpenStates[uuid] == true
    }

    fun setRemotePlayerOpen(uuid: UUID, isOpen: Boolean) {
        if (isOpen) {
            remoteOpenStates[uuid] = true
        } else {
            remoteOpenStates.remove(uuid)
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
        if (!hasRotom) {
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
        stack.setRotomPhone(true)
        if (accessory.isNotBlank()) {
            stack.setRotomAccessory(accessory)
            if (accessory == "bow") {
                val dye = DyeColor.byName(ribbonColor, DyeColor.PINK) ?: DyeColor.PINK
                stack.setRibbonDyeColor(dye)
            }
        }
        remoteRotomVisuals[uuid] = RemoteRotomVisual(
            hasRotom = true,
            phoneColor = phoneColor,
            accessory = accessory,
            ribbonColor = ribbonColor,
            cachedStack = stack
        )
    }

    /**
     * Retorna o ItemStack do Rotom Phone do jogador (checando inventário local/mãos primeiro
     * e usando o estado sincronizado pelo servidor para jogadores remotos com o item no inventário).
     */
    fun getEffectiveRotomPhone(player: Player): ItemStack? {
        val direct = SmartphoneHelper.getEquippedRotomPhone(player)
        if (direct != null) return direct
        if (player == Minecraft.getInstance().player) return null
        return remoteRotomVisuals[player.uuid]?.cachedStack
    }

    fun onServerSocialSession(
        player1Uuid: UUID,
        player2Uuid: UUID,
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
        val pairKey = if (player1Uuid < player2Uuid) Pair(player1Uuid, player2Uuid) else Pair(player2Uuid, player1Uuid)
        if (!active) {
            activeSocialAnchors.remove(pairKey)
            return
        }
        val now = System.currentTimeMillis()
        val gameTime = Minecraft.getInstance().level?.gameTime ?: 0L
        val dLen = kotlin.math.sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.01)
        val uX = dirX / dLen
        val uZ = dirZ / dLen
        activeSocialAnchors[pairKey] = RotomPlayerRenderLayer.Companion.SocialAnchor(
            leaderUuid = pairKey.first,
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
    }

    /**
     * Obtém (ou sorteia no modo fallback local) a sessão de interação social compartilhada
     * entre dois jogadores próximos. Garante que ambos os Rotoms usem exatamente o mesmo Ponto Âncora.
     */
    fun getActiveSharedAnchor(
        player: Player,
        nearbyPlayer: Player?,
        partialTick: Float,
        now: Long,
        isLocalScreenOpen: Boolean
    ): RotomPlayerRenderLayer.Companion.SocialAnchor? {
        if (nearbyPlayer == null) return null

        val p1 = if (player.uuid < nearbyPlayer.uuid) player else nearbyPlayer
        val p2 = if (player.uuid < nearbyPlayer.uuid) nearbyPlayer else player
        val pairKey = Pair(p1.uuid, p2.uuid)

        val p1Open = if (p1 == Minecraft.getInstance().player) isLocalScreenOpen else isPlayerOpen(p1.uuid)
        val p2Open = if (p2 == Minecraft.getInstance().player) isLocalScreenOpen else isPlayerOpen(p2.uuid)

        val existing = activeSocialAnchors[pairKey]
        if (existing != null) {
            // Verifica se a sessão ainda é válida (tempo não expirou, ninguém abriu a tela, e não se afastaram > 7.5 blocos da âncora)
            val d1Sqr = p1.distanceToSqr(existing.anchorX, existing.anchorY - 1.0, existing.anchorZ)
            val d2Sqr = p2.distanceToSqr(existing.anchorX, existing.anchorY - 1.0, existing.anchorZ)
            if (now < existing.endTimeMs && !p1Open && !p2Open && d1Sqr <= 56.25 && d2Sqr <= 56.25) {
                existing.lastActiveTime = now
                return existing
            } else {
                activeSocialAnchors.remove(pairKey)
                val fb = fallbackPairStates.computeIfAbsent(pairKey) { ClientFallbackPairState() }
                fb.nextCheckTimeMs = now + 18_000L
                fb.lastSeenNearMs = now
                fb.isFirstMeetingCheck = false
                return null
            }
        }

        // Se o servidor já está coordenando as sessões via pacote, aguardamos o trigger do servidor
        if (serverSocialSyncReceived) {
            return null
        }

        // Fallback determinístico no cliente (caso o servidor ainda não tenha enviado pacotes):
        if (p1Open || p2Open) return null
        val distSqr = p1.distanceToSqr(p2)
        if (distSqr > 36.0) return null

        val fb = fallbackPairStates.computeIfAbsent(pairKey) { ClientFallbackPairState() }
        if (now - fb.lastSeenNearMs > 12_000L) {
            fb.isFirstMeetingCheck = true
            if (fb.nextCheckTimeMs < now + 1_500L) {
                fb.nextCheckTimeMs = now + 1_500L
            }
        }
        fb.lastSeenNearMs = now

        if (now >= fb.nextCheckTimeMs) {
            val gameTimeBucket = (player.level().gameTime / 100L)
            val detSeed = pairKey.first.hashCode() xor pairKey.second.hashCode() xor gameTimeBucket.toInt()
            val detRandom = Random(detSeed)
            val chance = if (fb.isFirstMeetingCheck) 0.58f else 0.42f
            fb.isFirstMeetingCheck = false

            if (detRandom.nextFloat() < chance) {
                val x1 = Mth.lerp(partialTick.toDouble(), p1.xo, p1.x)
                val y1 = Mth.lerp(partialTick.toDouble(), p1.yo, p1.y)
                val z1 = Mth.lerp(partialTick.toDouble(), p1.zo, p1.z)
                val x2 = Mth.lerp(partialTick.toDouble(), p2.xo, p2.x)
                val y2 = Mth.lerp(partialTick.toDouble(), p2.yo, p2.y)
                val z2 = Mth.lerp(partialTick.toDouble(), p2.zo, p2.z)

                val anchorX = (x1 + x2) * 0.5
                val anchorY = (y1 + y2) * 0.5 + 1.35
                val anchorZ = (z1 + z2) * 0.5

                val dx = x2 - x1
                val dz = z2 - z1
                val dLen = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.01)
                val uX = dx / dLen
                val uZ = dz / dLen

                val durationMs = detRandom.nextInt(4, 6) * 4000L
                val anchor = RotomPlayerRenderLayer.Companion.SocialAnchor(
                    leaderUuid = pairKey.first,
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
                activeSocialAnchors[pairKey] = anchor
                return anchor
            } else {
                fb.nextCheckTimeMs = now + 6_000L
            }
        }
        return null
    }

    fun updateLocalState(isOpen: Boolean) {
        if (lastLocalState != isOpen) {
            lastLocalState = isOpen
            SetSmartphoneOpenStatePacket(isOpen).sendToServer()
        }
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        if (mc.player == null) {
            remoteRotomVisuals.clear()
            activeSocialAnchors.clear()
            fallbackPairStates.clear()
            serverSocialSyncReceived = false
            return
        }
        val isOpen = RotomPlayerRenderLayer.isSmartphoneScreen(mc.screen)
        updateLocalState(isOpen)
    }
}
