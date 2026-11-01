package com.nbp.cobblemon_smartphone.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity

object RotomAutoScannerRenderer {

    @JvmStatic
    fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camera: Camera,
        partialTick: Float
    ) {
        if (!RotomScannerClientState.clientConfig.enableScanner) return
        val activeScans = RotomScannerClientState.getActiveScans()
        if (activeScans.isEmpty()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camPos = camera.position
        val age = (mc.player?.tickCount ?: 0).toFloat() + partialTick

        try {
            for (scan in activeScans) {
                if (scan.phase == com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket.PHASE_RETURNING ||
                    scan.phase == com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket.PHASE_IDLE) {
                    continue
                }

                val entity = level.getEntity(scan.targetEntityId) ?: continue
                if (!entity.isAlive) continue

                // Interpolated entity position relative to camera
                val entX = Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x)
                val entY = Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y)
                val entZ = Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)

                val relX = (entX - camPos.x).toFloat()
                val relY = (entY - camPos.y).toFloat()
                val relZ = (entZ - camPos.z).toFloat()

                poseStack.pushPose()
                poseStack.translate(relX, relY, relZ)

                val halfW = (entity.bbWidth * 0.55f).coerceAtLeast(0.35f)
                val halfD = (entity.bbWidth * 0.55f).coerceAtLeast(0.35f)
                val h = entity.bbHeight.coerceAtLeast(0.6f)

                // Cybernetic Blue Colors
                val r = if (scan.isPrioritized) 0.15f else 0.0f
                val g = if (scan.isPrioritized) 0.95f else 0.85f
                val b = 1.0f

                if (scan.phase == com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket.PHASE_APPROACHING) {
                    // Durante a aproximação suave do Rotom: apenas retículo suave indicando alvo travado
                    val pulse = 0.40f + Mth.sin(age * 0.25f) * 0.15f
                    val lineBuffer = bufferSource.getBuffer(RenderType.lines())
                    renderCornerBrackets(poseStack, lineBuffer, -halfW, 0f, -halfD, halfW, h, halfD, r, g, b, pulse)
                    (bufferSource as? MultiBufferSource.BufferSource)?.endBatch(RenderType.lines())
                    renderApproachInfo(poseStack, bufferSource, camera, h, scan)
                } else if (scan.phase == com.nbp.cobblemon_smartphone.network.packet.SyncRotomScanPacket.PHASE_SCANNING) {
                    // Escaneamento ativo: efeito holográfico com anéis, laser de varredura e barra de progresso
                    val alpha = 0.95f
                    val lineBuffer = bufferSource.getBuffer(RenderType.lines())
                    renderCornerBrackets(poseStack, lineBuffer, -halfW, 0f, -halfD, halfW, h, halfD, r, g, b, alpha)

                    // 2. Concentric Holographic Rotating Rings
                    val ringRadius1 = halfW * 1.35f
                    val ringRadius2 = halfW * 1.55f
                    val angle1 = age * 0.07f
                    val angle2 = -age * 0.05f
                    renderHoloRing(poseStack, lineBuffer, 0f, h * 0.2f, 0f, ringRadius1, angle1, 32, r, g, b, alpha * 0.8f)
                    renderHoloRing(poseStack, lineBuffer, 0f, h * 0.8f, 0f, ringRadius2, angle2, 28, r, g, b, alpha * 0.75f)

                    // 3. Sweeping Laser Scanline (moving up and down)
                    val sweep = Mth.sin(age * 0.18f) * 0.5f + 0.5f
                    val scanY = h * sweep
                    renderHoloRing(poseStack, lineBuffer, 0f, scanY, 0f, halfW * 1.25f, age * 0.15f, 32, 0.3f, 1.0f, 1.0f, 1.0f)
                    (bufferSource as? MultiBufferSource.BufferSource)?.endBatch(RenderType.lines())

                    // 4. Floating 3D Holographic Info & Progress Indicator
                    renderHoloInfo(poseStack, bufferSource, camera, entity, h, scan, partialTick)
                }

                poseStack.popPose()
            }
        } catch (_: Throwable) {
            // Previne que qualquer falha de pipeline de renderização interrompa o loop do Minecraft
        }
    }

    private fun renderApproachInfo(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camera: Camera,
        h: Float,
        scan: RotomScannerClientState.ActiveScan
    ) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        poseStack.pushPose()
        poseStack.translate(0f, h + 0.35f, 0f)
        poseStack.mulPose(camera.rotation())
        poseStack.scale(-0.014f, -0.014f, 0.014f)

        val header = if (scan.isPrioritized) "§b[★ APROXIMANDO DO ALVO ★]" else "§b[ROTOM SE APROXIMANDO...]"
        font.drawInBatch(
            header,
            -font.width(header) / 2f,
            -6f,
            0xFFFFFFFF.toInt(),
            false,
            poseStack.last().pose(),
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0x40001020,
            15728880
        )

        poseStack.popPose()
    }

    private fun renderCornerBrackets(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val pose = poseStack.last()
        val lenX = ((maxX - minX) * 0.25f).coerceIn(0.12f, 0.4f)
        val lenY = ((maxY - minY) * 0.25f).coerceIn(0.12f, 0.4f)
        val lenZ = ((maxZ - minZ) * 0.25f).coerceIn(0.12f, 0.4f)

        fun drawLine(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float) {
            val nx = x2 - x1
            val ny = y2 - y1
            val nz = z2 - z1
            val l = Mth.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
            buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx / l, ny / l, nz / l)
            buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx / l, ny / l, nz / l)
        }

        // Draw for each of the 8 box corners
        val xs = floatArrayOf(minX, maxX)
        val ys = floatArrayOf(minY, maxY)
        val zs = floatArrayOf(minZ, maxZ)

        for (x in xs) {
            for (y in ys) {
                for (z in zs) {
                    val dx = if (x == minX) lenX else -lenX
                    val dy = if (y == minY) lenY else -lenY
                    val dz = if (z == minZ) lenZ else -lenZ

                    drawLine(x, y, z, x + dx, y, z)
                    drawLine(x, y, z, x, y + dy, z)
                    drawLine(x, y, z, x, y, z + dz)
                }
            }
        }
    }

    private fun renderHoloRing(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        cx: Float, cy: Float, cz: Float,
        radius: Float,
        rotationAngle: Float,
        segments: Int,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val pose = poseStack.last()
        val step = (Math.PI * 2 / segments).toFloat()

        for (i in 0 until segments) {
            val a1 = rotationAngle + i * step
            val a2 = rotationAngle + (i + 1) * step
            val x1 = cx + Mth.cos(a1) * radius
            val z1 = cz + Mth.sin(a1) * radius
            val x2 = cx + Mth.cos(a2) * radius
            val z2 = cz + Mth.sin(a2) * radius

            buffer.addVertex(pose, x1, cy, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f)
            buffer.addVertex(pose, x2, cy, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f)
        }
    }

    private fun renderHoloInfo(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camera: Camera,
        entity: Entity,
        h: Float,
        scan: RotomScannerClientState.ActiveScan,
        partialTick: Float
    ) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        poseStack.pushPose()
        poseStack.translate(0f, h + 0.45f, 0f)
        poseStack.mulPose(camera.rotation())
        poseStack.scale(-0.016f, -0.016f, 0.016f)

        val pct = ((scan.progressTicks + partialTick) / scan.totalTicks.toFloat() * 100f).toInt().coerceIn(0, 100)

        // Line 1: Header
        val header = if (scan.isPrioritized) "§b[★ ROTOM PRIORITÁRIO ★]" else "§b[ROTOM AUTO SCAN]"
        font.drawInBatch(
            header,
            -font.width(header) / 2f,
            -20f,
            0xFFFFFFFF.toInt(),
            false,
            poseStack.last().pose(),
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0x40001020,
            15728880
        )

        // Line 2: Progress bar
        val filled = (pct / 10).coerceIn(0, 10)
        val empty = 10 - filled
        val bar = "§b[§a" + "■".repeat(filled) + "§7" + "□".repeat(empty) + "§b] §f$pct%"
        font.drawInBatch(
            bar,
            -font.width(bar) / 2f,
            -8f,
            0xFFFFFFFF.toInt(),
            false,
            poseStack.last().pose(),
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0x40001020,
            15728880
        )

        // Line 3: Status
        val status = if (scan.isAlreadyRegistered) "§7(Já Registrado)" else "§e⚡ NOVO POKÉMON ⚡"
        font.drawInBatch(
            status,
            -font.width(status) / 2f,
            4f,
            0xFFFFFFFF.toInt(),
            false,
            poseStack.last().pose(),
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0x40001020,
            15728880
        )

        poseStack.popPose()
    }
}
