package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.platform.Lighting
import com.nbp.cobblemon_smartphone.client.GpsClientState
import com.nbp.cobblemon_smartphone.client.GpsClientState.SearchStatus
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneGpsItems
import com.nbp.cobblemon_smartphone.util.GpsDataProvider.BiomeInfo
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
 * In-world GPS tracker drawn over the HUD (and over open screens, via [ScreenRenderMixin]): a
 * floating 3D arrow (see [CobblemonSmartphoneGpsItems.GPS_ARROW]) in the bottom-right corner that
 * points at the tracked biome in full 3D — yaw *and* pitch — the way Cobblenav's TrackArrowOverlay
 * points at a tracked Pokémon, plus a status line with the biome name, distance and coordinates.
 * There's no close button here; tracking is stopped from the GPS screen itself.
 */
object GpsCompassOverlay {

    private val arrowStack by lazy { ItemStack(CobblemonSmartphoneGpsItems.GPS_ARROW) }

    private fun visible(): Boolean = GpsClientState.tracking && Minecraft.getInstance().player != null

    fun render(guiGraphics: GuiGraphics) {
        if (!visible()) return
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val rightEdge = window.guiScaledWidth - RIGHT_MARGIN
        val cx = rightEdge - BOUND_HALF
        val cy = window.guiScaledHeight - BOTTOM_OFFSET

        drawArrow(guiGraphics, cx, cy)
        drawStatusText(guiGraphics, rightEdge, cy)
    }

    /** Renders [CobblemonSmartphoneGpsItems.GPS_ARROW] as a static 3D model, oriented so it always
     *  points at the tracked spot regardless of which way the player is looking: the model's own
     *  rotation cancels out the player's look direction, then re-applies the bearing/pitch to the
     *  target on top, mirroring Cobblenav's TrackArrowOverlay. */
    private fun drawArrow(guiGraphics: GuiGraphics, cx: Int, cy: Int) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return

        val status = GpsClientState.searchStatus
        val targetPos = GpsClientState.targetPos
        if (status != SearchStatus.FOUND || targetPos == null) return

        val distanceVec = player.position().vectorTo(Vec3.atCenterOf(targetPos))
        val yaw = atan2(distanceVec.z, distanceVec.x).toFloat()
        val horizontalDistance = sqrt(distanceVec.x * distanceVec.x + distanceVec.z * distanceVec.z)
        // At long range the horizontal distance barely changes step to step while the height
        // difference can swing a lot (going over a hill, dropping into a ravine), which pointed the
        // arrow almost straight up/down for no useful reason. Fade pitch in only once the player is
        // close enough for it to actually mean something; from far away the arrow stays flat and
        // just gives a heading.
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

        // A real 3D model (unlike a flat generated-sprite item) needs directional lighting or its
        // faces read as randomly lit/unlit from whatever GL state the previous HUD draw left behind
        // — that's what showed up as the model flickering in and out. Full-bright + no overlay keeps
        // it consistently visible regardless of in-world light level, and flush() submits its
        // geometry immediately instead of letting it batch with (and get reordered against) other
        // HUD draws.
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

    /** [rightEdge] is a fixed screen-space anchor (not the arrow's center) so the text — which can
     *  be wider than the arrow itself, e.g. the coordinates line — grows leftward instead of
     *  spilling off the right edge of the screen. */
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
        val target = GpsClientState.target
        val name = target?.let(::localizedBiomeName) ?: ""
        return when (GpsClientState.searchStatus) {
            SearchStatus.SEARCHING -> lang("searching")
            SearchStatus.NOT_FOUND -> lang("not_found")
            SearchStatus.FOUND, SearchStatus.IDLE -> {
                val pos = GpsClientState.targetPos
                val player = Minecraft.getInstance().player
                if (pos == null || player == null) name else "$name — ${formatDistance(player, pos)}"
            }
        }
    }

    private fun coordsText(): String? {
        if (GpsClientState.searchStatus != SearchStatus.FOUND) return null
        val pos = GpsClientState.targetPos ?: return null
        return "X: ${pos.x} Y: ${pos.y} Z: ${pos.z}"
    }

    private fun localizedBiomeName(biome: BiomeInfo): String {
        val split = biome.id.split(':', limit = 2)
        if (split.size != 2) return biome.id
        val key = "biome.${split[0]}.${split[1]}"
        val viaI18n = Component.translatable(key)
        return if (viaI18n.string == key) biome.id else viaI18n.string
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

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.gps.$key").string

    private const val RIGHT_MARGIN = 8
    private const val BOTTOM_OFFSET = 50
    private const val BOUND_HALF = 18
    private const val ARROW_SCALE = 30f

    // Horizontal distance (blocks) over which vertical aim fades in: none at/above the start
    // distance, full pitch at/below the end distance.
    private const val PITCH_FADE_START_DISTANCE = 96.0
    private const val PITCH_FADE_END_DISTANCE = 24.0

    private const val STATUS_BG = 0xB0000000.toInt()
    private const val STATUS_TEXT = 0xFFFFFFFF.toInt()
}
