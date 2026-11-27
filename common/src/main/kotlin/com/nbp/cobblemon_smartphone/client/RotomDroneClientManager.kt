package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.RotomDroneStatePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomDroneStatePacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RotomDroneClientManager {
    private const val DRONE_HALF_SIZE = 0.24
    private const val ACCEL_LERP = 0.36
    private const val SPRINT_MULTIPLIER = 1.65

    data class RemoteDrone(
        val playerUuid: UUID,
        var prevX: Double,
        var prevY: Double,
        var prevZ: Double,
        var currX: Double,
        var currY: Double,
        var currZ: Double,
        var targetX: Double,
        var targetY: Double,
        var targetZ: Double,
        var prevYaw: Float,
        var currYaw: Float,
        var targetYaw: Float,
        var prevPitch: Float,
        var currPitch: Float,
        var targetPitch: Float,
        var lastUpdateMs: Long = System.currentTimeMillis()
    )

    private var active: Boolean = false
    private var droneDimension: ResourceKey<Level>? = null
    private var prevPos: Vec3 = Vec3.ZERO
    private var pos: Vec3 = Vec3.ZERO
    private var vel: Vec3 = Vec3.ZERO
    private var prevYaw: Float = 0f
    private var yaw: Float = 0f
    private var prevPitch: Float = 0f
    private var pitch: Float = 0f

    private var activeColor: SmartphoneColor = SmartphoneColor.RED
    private var activeStack: ItemStack? = null
    private var photoDraft: SocialPhotoClient.ComposeDraft? = null

    private var capturingPhoto: Boolean = false
    private var captureTicks: Int = 0
    private var previousHideGui: Boolean = false

    private var syncTickCounter: Int = 0
    private var lastSentPos: Vec3 = Vec3.ZERO
    private var lastSentYaw: Float = 0f
    private var lastSentPitch: Float = 0f
    private var lastHurtTime: Int = 0
    private var lastClientDimension: ResourceKey<Level>? = null

    private val remoteDrones = ConcurrentHashMap<UUID, RemoteDrone>()

    @JvmStatic
    fun isActive(): Boolean = active

    @JvmStatic
    fun isCapturingPhoto(): Boolean = capturingPhoto

    fun isPlayerInDrone(uuid: UUID): Boolean {
        val localUuid = runCatching { Minecraft.getInstance().player?.uuid }.getOrNull()
        if (localUuid == uuid && active) return true
        return remoteDrones.containsKey(uuid)
    }

    fun getRemoteDrones(): Collection<RemoteDrone> = remoteDrones.values

    fun getDistanceToPlayer(): Double {
        val player = Minecraft.getInstance().player ?: return 0.0
        return pos.distanceTo(player.eyePosition)
    }

    fun getMaxRange(): Double {
        val raw = RotomScannerClientState.clientConfig.droneMaxRange
        return raw.takeIf { it.isFinite() }?.coerceIn(4.0, 256.0) ?: 32.0
    }

    fun canTakeAerialPhoto(): Boolean {
        val cfg = RotomScannerClientState.clientConfig
        return cfg.droneAllowPhoto && cfg.enableSocial
    }

    fun getInterpolatedPos(partialTick: Float): Vec3 {
        val pt = partialTick.toDouble()
        return Vec3(
            Mth.lerp(pt, prevPos.x, pos.x),
            Mth.lerp(pt, prevPos.y, pos.y),
            Mth.lerp(pt, prevPos.z, pos.z)
        )
    }

    fun getInterpolatedYaw(partialTick: Float): Float = Mth.rotLerp(partialTick, prevYaw, yaw)

    fun getInterpolatedPitch(partialTick: Float): Float = Mth.lerp(partialTick, prevPitch, pitch)

    fun toggleFromKeybind() {
        if (active) {
            deactivate()
            return
        }
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.screen != null) return
        val phone = SmartphoneHelper.getSmartphone(player)
        if (phone == null || !phone.isRotomPhone()) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_drone.requires_rotom").withStyle(ChatFormatting.YELLOW),
                true
            )
            return
        }
        val color = (phone.item as? SmartphoneItem)?.getColor() ?: SmartphoneColor.RED
        activate(color, phone)
    }

    fun activate(
        color: SmartphoneColor,
        smartphoneStack: ItemStack?,
        composeDraft: SocialPhotoClient.ComposeDraft? = null
    ): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val cfg = RotomScannerClientState.clientConfig

        if (!cfg.enableRotom || !cfg.enableDrone) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_drone.disabled").withStyle(ChatFormatting.RED),
                true
            )
            return false
        }

        val dimId = level.dimension().location().toString()
        if (cfg.droneBlockedDimensions.any { it.equals(dimId, ignoreCase = true) }) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_drone.dimension_blocked").withStyle(ChatFormatting.RED),
                true
            )
            return false
        }

        val effectivePhone = smartphoneStack?.takeIf { it.isRotomPhone() }
            ?: SmartphoneHelper.getSmartphone(player)?.takeIf { it.isRotomPhone() }
        if (effectivePhone == null) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_drone.requires_rotom").withStyle(ChatFormatting.YELLOW),
                true
            )
            return false
        }

        if (!player.isAlive || player.isSleeping || player.isSpectator) {
            return false
        }

        // Cancel competing modes cleanly before saving previousHideGui
        if (com.nbp.cobblemon_smartphone.client.scanner.ScannerManager.isInUse()) {
            com.nbp.cobblemon_smartphone.client.scanner.ScannerManager.deactivate()
        }
        if (SocialPhotoClient.isCameraActive()) {
            SocialPhotoClient.cancelCamera()
        }

        val startPos = computeStartPosition(level, player.eyePosition, player.yRot, player.xRot)
        pos = startPos
        prevPos = startPos
        vel = Vec3.ZERO
        yaw = player.yRot
        prevYaw = yaw
        pitch = player.xRot.coerceIn(-89.5f, 89.5f)
        prevPitch = pitch

        val wasActive = active
        active = true
        droneDimension = level.dimension()
        activeColor = color
        activeStack = effectivePhone.copy()
        photoDraft = composeDraft
        capturingPhoto = false
        captureTicks = 0
        if (!wasActive) {
            previousHideGui = mc.options.hideGui
        }
        mc.options.hideGui = true
        syncTickCounter = 0
        lastSentPos = startPos
        lastSentYaw = yaw
        lastSentPitch = pitch
        lastHurtTime = player.hurtTime

        mc.setScreen(null)
        player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.55f, 1.25f)
        RotomDroneStatePacket(true, pos.x, pos.y, pos.z, yaw, pitch).sendToServer()
        return true
    }

    @JvmStatic
    fun deactivate(reopenDraft: Boolean = true) {
        if (!active && !capturingPhoto) return
        val mc = Minecraft.getInstance()
        val draft = photoDraft

        active = false
        droneDimension = null
        vel = Vec3.ZERO
        capturingPhoto = false
        captureTicks = 0
        photoDraft = null
        mc.options.hideGui = previousHideGui

        mc.player?.playSound(CobblemonSounds.POKEDEX_CLOSE, 0.5f, 1.15f)
        if (mc.connection != null) {
            RotomDroneStatePacket(false).sendToServer()
        }

        if (reopenDraft && draft != null && mc.player != null) {
            mc.setScreen(
                com.nbp.cobblemon_smartphone.client.gui.SocialComposeScreen(
                    draft.color,
                    draft.stack,
                    draft.text,
                    dmTargetUuid = draft.dmTargetUuid,
                    dmTargetName = draft.dmTargetName
                )
            )
        }
    }

    fun reset() {
        val mc = runCatching { Minecraft.getInstance() }.getOrNull()
        if ((active || capturingPhoto) && mc != null) {
            mc.options.hideGui = previousHideGui
        }
        active = false
        droneDimension = null
        vel = Vec3.ZERO
        capturingPhoto = false
        captureTicks = 0
        photoDraft = null
        activeStack = null
        lastClientDimension = null
        remoteDrones.clear()
    }

    @JvmStatic
    fun turn(deltaYaw: Double, deltaPitch: Double) {
        if (!active) return
        val dPitch = (deltaPitch * 0.15).toFloat()
        val dYaw = (deltaYaw * 0.15).toFloat()
        yaw += dYaw
        pitch = (pitch + dPitch).coerceIn(-89.5f, 89.5f)
    }

    @JvmStatic
    fun onRightClick() {
        if (!active || capturingPhoto) return
        if (!canTakeAerialPhoto()) return
        val mc = Minecraft.getInstance()
        if (mc.screen != null) return

        capturingPhoto = true
        captureTicks = 2
    }

    fun handleServerSync(packet: SyncRotomDroneStatePacket) {
        val mc = Minecraft.getInstance()
        val localUuid = mc.player?.uuid
        if (packet.playerUuid == localUuid) {
            if (!packet.active && active) {
                deactivate(reopenDraft = true)
            }
            return
        }

        if (!packet.active) {
            remoteDrones.remove(packet.playerUuid)
            return
        }

        val now = System.currentTimeMillis()
        val existing = remoteDrones[packet.playerUuid]
        if (existing != null) {
            existing.targetX = packet.x
            existing.targetY = packet.y
            existing.targetZ = packet.z
            existing.targetYaw = packet.yaw
            existing.targetPitch = packet.pitch
            existing.lastUpdateMs = now
        } else {
            remoteDrones[packet.playerUuid] = RemoteDrone(
                playerUuid = packet.playerUuid,
                prevX = packet.x,
                prevY = packet.y,
                prevZ = packet.z,
                currX = packet.x,
                currY = packet.y,
                currZ = packet.z,
                targetX = packet.x,
                targetY = packet.y,
                targetZ = packet.z,
                prevYaw = packet.yaw,
                currYaw = packet.yaw,
                targetYaw = packet.yaw,
                prevPitch = packet.pitch,
                currPitch = packet.pitch,
                targetPitch = packet.pitch,
                lastUpdateMs = now
            )
        }
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        val currentDim = mc.level?.dimension()
        if (currentDim != lastClientDimension) {
            lastClientDimension = currentDim
            remoteDrones.clear()
        }
        tickRemoteDrones()
        if (!active) return

        val player = mc.player
        val level = mc.level
        if (player == null || level == null) {
            reset()
            return
        }

        // Keep vanilla/Cobblemon HUD hidden even if player presses F1 while piloting
        if (!mc.options.hideGui) {
            mc.options.hideGui = true
        }

        val cfg = RotomScannerClientState.clientConfig
        val hasRotom = SmartphoneHelper.getSmartphone(player)?.isRotomPhone() == true
        val tookDamage = player.hurtTime > lastHurtTime && player.hurtTime > 0
        lastHurtTime = player.hurtTime

        if (!cfg.enableRotom || !cfg.enableDrone || !hasRotom
            || !player.isAlive || player.isSleeping || player.isSpectator
            || level.dimension() != droneDimension
            || mc.screen != null
            || tookDamage
        ) {
            deactivate(reopenDraft = true)
            return
        }

        // Photo capture pipeline: wait 2 ticks with HUD hidden, then capture clean screenshot
        if (capturingPhoto) {
            if (--captureTicks <= 0) {
                val draft = photoDraft ?: SocialPhotoClient.ComposeDraft(
                    color = activeColor,
                    stack = activeStack?.copy(),
                    text = ""
                )
                capturingPhoto = false
                SocialPhotoClient.captureImmediateFromDrone(draft)
                // Exit drone mode without reopening an empty compose screen (photo processor opens it)
                deactivate(reopenDraft = false)
            }
            return
        }

        prevPos = pos
        prevYaw = yaw
        prevPitch = pitch

        // Compute desired movement from keyboard state
        val opts = mc.options
        var forward = 0.0
        var strafe = 0.0
        var vertical = 0.0
        if (opts.keyUp.isDown) forward += 1.0
        if (opts.keyDown.isDown) forward -= 1.0
        if (opts.keyRight.isDown) strafe += 1.0
        if (opts.keyLeft.isDown) strafe -= 1.0
        if (opts.keyJump.isDown) vertical += 1.0
        if (opts.keyShift.isDown) vertical -= 1.0

        val yawRad = Math.toRadians(yaw.toDouble())
        val fwdX = -sin(yawRad)
        val fwdZ = cos(yawRad)
        val rightX = -cos(yawRad)
        val rightZ = -sin(yawRad)

        var desiredX = fwdX * forward + rightX * strafe
        var desiredZ = fwdZ * forward + rightZ * strafe
        val horizLen = sqrt(desiredX * desiredX + desiredZ * desiredZ)
        if (horizLen > 1.0) {
            desiredX /= horizLen
            desiredZ /= horizLen
        }

        val baseSpeed = cfg.droneSpeed.takeIf { it.isFinite() }?.coerceIn(0.1, 2.0) ?: 0.45
        val speed = if (opts.keySprint.isDown) baseSpeed * SPRINT_MULTIPLIER else baseSpeed
        val targetVel = Vec3(desiredX * speed, vertical * speed * 0.85, desiredZ * speed)

        vel = Vec3(
            Mth.lerp(ACCEL_LERP, vel.x, targetVel.x),
            Mth.lerp(ACCEL_LERP, vel.y, targetVel.y),
            Mth.lerp(ACCEL_LERP, vel.z, targetVel.z)
        )

        if (vel.lengthSqr() > 1e-7) {
            val moved = moveWithFairplayCollision(level, pos, vel, cfg.droneAllowFluids)
            // Zero velocity on axes that collided so drone doesn't push awkwardly
            val actualDelta = moved.subtract(pos)
            vel = Vec3(
                if (kotlin.math.abs(actualDelta.x - vel.x) > 1e-4) 0.0 else vel.x,
                if (kotlin.math.abs(actualDelta.y - vel.y) > 1e-4) 0.0 else vel.y,
                if (kotlin.math.abs(actualDelta.z - vel.z) > 1e-4) 0.0 else vel.z
            )
            pos = moved
        }

        // Enforce maximum signal range around player
        val anchor = player.eyePosition
        val maxRange = getMaxRange()
        val offset = pos.subtract(anchor)
        val dist = offset.length()
        if (dist > maxRange + 6.0) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_smartphone.rotom_drone.out_of_range").withStyle(ChatFormatting.YELLOW),
                true
            )
            deactivate(reopenDraft = true)
            return
        } else if (dist > maxRange && dist > 0.001) {
            val clampedTarget = anchor.add(offset.scale(maxRange / dist))
            val pullDelta = clampedTarget.subtract(pos)
            pos = moveWithFairplayCollision(level, pos, pullDelta, cfg.droneAllowFluids)
        }

        // Throttled network sync to server for multiplayer rendering & range validation
        syncTickCounter++
        val movedDistSqr = pos.distanceToSqr(lastSentPos)
        val yawDiff = kotlin.math.abs(Mth.wrapDegrees(yaw - lastSentYaw))
        val pitchDiff = kotlin.math.abs(pitch - lastSentPitch)
        if (syncTickCounter >= 2 && (movedDistSqr > 0.0025 || yawDiff > 1.5f || pitchDiff > 1.5f || syncTickCounter >= 10)) {
            syncTickCounter = 0
            lastSentPos = pos
            lastSentYaw = yaw
            lastSentPitch = pitch
            RotomDroneStatePacket(true, pos.x, pos.y, pos.z, yaw, pitch).sendToServer()
        }
    }

    private fun tickRemoteDrones() {
        val now = System.currentTimeMillis()
        val iter = remoteDrones.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val drone = entry.value
            // Stale cleanup if no packet for 15 seconds
            if (now - drone.lastUpdateMs > 15_000L) {
                iter.remove()
                continue
            }
            drone.prevX = drone.currX
            drone.prevY = drone.currY
            drone.prevZ = drone.currZ
            drone.prevYaw = drone.currYaw
            drone.prevPitch = drone.currPitch

            drone.currX = Mth.lerp(0.35, drone.currX, drone.targetX)
            drone.currY = Mth.lerp(0.35, drone.currY, drone.targetY)
            drone.currZ = Mth.lerp(0.35, drone.currZ, drone.targetZ)
            drone.currYaw = drone.currYaw + Mth.wrapDegrees(drone.targetYaw - drone.currYaw) * 0.35f
            drone.currPitch = Mth.lerp(0.35f, drone.currPitch, drone.targetPitch)
        }
    }

    private fun computeStartPosition(level: Level, eyePos: Vec3, yRot: Float, xRot: Float): Vec3 {
        val yawRad = Math.toRadians(yRot.toDouble())
        val pitchRad = Math.toRadians(xRot.toDouble())
        val forward = Vec3(
            -sin(yawRad) * cos(pitchRad),
            -sin(pitchRad),
            cos(yawRad) * cos(pitchRad)
        ).scale(0.85)
        return moveWithFairplayCollision(level, eyePos, forward, RotomScannerClientState.clientConfig.droneAllowFluids)
    }

    private fun droneBoxAt(p: Vec3): AABB {
        return AABB(
            p.x - DRONE_HALF_SIZE, p.y - DRONE_HALF_SIZE, p.z - DRONE_HALF_SIZE,
            p.x + DRONE_HALF_SIZE, p.y + DRONE_HALF_SIZE, p.z + DRONE_HALF_SIZE
        )
    }

    private fun moveWithFairplayCollision(level: Level, current: Vec3, delta: Vec3, allowFluids: Boolean): Vec3 {
        val box = droneBoxAt(current)
        val collidedDelta = Entity.collideBoundingBox(null, delta, box, level, emptyList())
        val candidate = current.add(collidedDelta)

        if (!allowFluids && isInFluid(level, candidate)) {
            // Try sliding per axis without entering fluids
            var result = current
            val stepX = Vec3(collidedDelta.x, 0.0, 0.0)
            if (stepX.x != 0.0 && !isInFluid(level, result.add(stepX))) {
                result = result.add(stepX)
            }
            val stepY = Vec3(0.0, collidedDelta.y, 0.0)
            if (stepY.y != 0.0 && !isInFluid(level, result.add(stepY))) {
                result = result.add(stepY)
            }
            val stepZ = Vec3(0.0, 0.0, collidedDelta.z)
            if (stepZ.z != 0.0 && !isInFluid(level, result.add(stepZ))) {
                result = result.add(stepZ)
            }
            return result
        }
        return candidate
    }

    private fun isInFluid(level: Level, p: Vec3): Boolean {
        val bp = BlockPos.containing(p.x, p.y, p.z)
        return !level.getFluidState(bp).isEmpty
    }
}
