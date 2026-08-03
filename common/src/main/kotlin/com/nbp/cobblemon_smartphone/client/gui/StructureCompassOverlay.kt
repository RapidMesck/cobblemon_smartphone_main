package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.platform.Lighting
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.client.StructureCompassClientState
import com.nbp.cobblemon_smartphone.client.StructureCompassClientState.SearchStatus
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneGpsItems
import com.nbp.cobblemon_smartphone.util.StructureDataProvider.StructureInfo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * In-world structure tracker drawn over the HUD (and over open screens, via [ScreenRenderMixin]):
 * a near-identical mirror of [GpsCompassOverlay] for structures instead of biomes — same floating
 * 3D arrow (reusing [CobblemonSmartphoneGpsItems.GPS_ARROW]; there's nothing biome-specific about
 * the model itself), same bottom-right placement, same pitch fade-in, no close button. Stacks above
 * the GPS overlay (rather than on top of it) when both trackers happen to be active at once.
 */
object StructureCompassOverlay {

    private val arrowStack by lazy { ItemStack(CobblemonSmartphoneGpsItems.GPS_ARROW) }

    private fun visible(): Boolean = StructureCompassClientState.tracking && Minecraft.getInstance().player != null

    fun render(guiGraphics: GuiGraphics) {
        if (!visible()) return
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val rightEdge = window.guiScaledWidth - RIGHT_MARGIN
        val cx = rightEdge - BOUND_HALF
        val extraStack = if (GpsClientState.tracking) STACK_HEIGHT else 0
        val cy = window.guiScaledHeight - BOTTOM_OFFSET - extraStack

        drawArrow(guiGraphics, cx, cy)
        drawStatusText(guiGraphics, rightEdge, cy)
    }

    /** Renders [CobblemonSmartphoneGpsItems.GPS_ARROW] as a static 3D model, oriented so it always
     *  points at the tracked structure regardless of which way the player is looking; identical
     *  math to [GpsCompassOverlay.drawArrow]. */
    private fun drawArrow(guiGraphics: GuiGraphics, cx: Int, cy: Int) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return

        val status = StructureCompassClientState.searchStatus
        val targetPos = StructureCompassClientState.targetPos
        if (status != SearchStatus.FOUND || targetPos == null) return

        val distanceVec = player.position().vectorTo(Vec3.atCenterOf(targetPos))
        val yaw = atan2(distanceVec.z, distanceVec.x).toFloat()
        val horizontalDistance = sqrt(distanceVec.x * distanceVec.x + distanceVec.z * distanceVec.z)
        val pitchFactor = ((PITCH_FADE_START_DISTANCE - horizontalDistance) /
            (PITCH_FADE_START_DISTANCE - PITCH_FADE_END_DISTANCE)).coerceIn(0.0, 1.0)
        val pitch = (atan2(distanceVec.y, horizontalDistance) * pitchFactor).toFloat()

        val poseStack = guiGraphics.pose()
        poseStack.pushPose()
        poseStack.translate(cx.toDouble(), cy.toDouble(), 0.0)
        poseStack.mulPose(
            Quaternionf()
                .rotateZ(Math.PI.toFloat())
                .fromEulerXYZDegrees(Vector3f(player.xRot, -player.yRot, 0f))
                .rotateY(0.5f * Math.PI.toFloat() + yaw)
                .rotateX(-pitch)
        )
        poseStack.scale(ARROW_SCALE, ARROW_SCALE, -ARROW_SCALE)

        Lighting.setupFor3DItems()
        minecraft.itemRenderer.renderStatic(
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
        Lighting.setupForFlatItems()
        poseStack.popPose()
    }

    private fun drawStatusText(guiGraphics: GuiGraphics, rightEdge: Int, cy: Int) {
        val font = Minecraft.getInstance().font
        val lines = listOfNotNull(statusText(), coordsText())
        val lineHeight = font.lineHeight + 1
        val width = lines.maxOf { font.width(it) }
        val top = cy + BOUND_HALF + 6

        guiGraphics.fill(
            rightEdge - width - 4, top - 2,
            rightEdge + 4, top + lines.size * lineHeight + 1,
            STATUS_BG
        )
        lines.forEachIndexed { index, line ->
            val ty = top + index * lineHeight
            guiGraphics.drawString(font, line, rightEdge - font.width(line), ty, STATUS_TEXT, false)
        }
    }

    private fun statusText(): String {
        val target = StructureCompassClientState.target
        val name = target?.let(::prettyName) ?: ""
        return when (StructureCompassClientState.searchStatus) {
            SearchStatus.SEARCHING -> lang("searching")
            SearchStatus.NOT_FOUND -> lang("not_found")
            SearchStatus.FOUND, SearchStatus.IDLE -> {
                val pos = StructureCompassClientState.targetPos
                val player = Minecraft.getInstance().player
                if (pos == null || player == null) name else "$name — ${formatDistance(player, pos)}"
            }
        }
    }

    private fun coordsText(): String? {
        if (StructureCompassClientState.searchStatus != SearchStatus.FOUND) return null
        val pos = StructureCompassClientState.targetPos ?: return null
        return "X: ${pos.x} Y: ${pos.y} Z: ${pos.z}"
    }

    private fun prettyName(structure: StructureInfo): String {
        val path = structure.id.substringAfter(':')
        return path.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun formatDistance(player: net.minecraft.world.entity.player.Player, pos: net.minecraft.core.BlockPos): String {
        val dx = pos.x - player.blockPosition().x
        val dz = pos.z - player.blockPosition().z
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        val meters = dist.roundToInt()
        return if (meters < 1000) {
            "$meters m"
        } else {
            val km = Mth.floor(dist / 100.0) / 10.0
            "${km} km"
        }
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.structure_compass.$key").string

    private const val RIGHT_MARGIN = 8
    private const val BOTTOM_OFFSET = 50
    // Tall enough to clear the GPS overlay's own arrow + two-line status text stacked below it
    // (was 42, which left them overlapping by about 18px).
    private const val STACK_HEIGHT = 70
    private const val BOUND_HALF = 18
    private const val ARROW_SCALE = 30f

    private const val PITCH_FADE_START_DISTANCE = 96.0
    private const val PITCH_FADE_END_DISTANCE = 24.0

    private const val STATUS_BG = 0xB0000000.toInt()
    private const val STATUS_TEXT = 0xFFFFFFFF.toInt()
}
