package com.nbp.cobblemon_smartphone.rotom

import com.nbp.cobblemon_smartphone.client.render.RotomPlayerRenderLayer
import net.minecraft.client.CameraType
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RotomDirectionConfigTest {

    @Test
    fun `config defaults and nbt round trip`() {
        val defaultCfg = RotomRadarConfig()
        assertFalse(defaultCfg.invertCameraDirection)
        assertTrue(defaultCfg.autoAdjustDirection)

        val customCfg = RotomRadarConfig(
            invertCameraDirection = true,
            autoAdjustDirection = false
        )
        val restored = RotomRadarConfig.fromTag(customCfg.toTag())
        assertTrue(restored.invertCameraDirection)
        assertFalse(restored.autoAdjustDirection)

        // Backwards compatibility when tags are absent
        val legacyRestored = RotomRadarConfig.fromTag(CompoundTag())
        assertFalse(legacyRestored.invertCameraDirection)
        assertTrue(legacyRestored.autoAdjustDirection)
    }

    @Test
    fun `computeCameraGaze respects invertCameraDirection when autoAdjustDirection is enabled`() {
        val headYaw = 20.0f
        val headPitch = 10.0f

        // Default (not inverted)
        val (backYaw, backPitch) = RotomPlayerRenderLayer.computeCameraGaze(
            CameraType.THIRD_PERSON_BACK, headYaw, headPitch,
            invertCameraDirection = false, autoAdjustDirection = true
        )
        assertEquals(RotomPlayerRenderLayer.YAW_FACING_BACK - headYaw * 0.25f, backYaw, 0.001f)
        assertEquals(headPitch * 0.20f, backPitch, 0.001f)

        val (frontYaw, frontPitch) = RotomPlayerRenderLayer.computeCameraGaze(
            CameraType.THIRD_PERSON_FRONT, headYaw, headPitch,
            invertCameraDirection = false, autoAdjustDirection = true
        )
        assertEquals(RotomPlayerRenderLayer.YAW_FACING_FRONT + headYaw * 0.25f, frontYaw, 0.001f)
        assertEquals(-headPitch * 0.20f, frontPitch, 0.001f)

        // Inverted (looks away from camera)
        val (invBackYaw, invBackPitch) = RotomPlayerRenderLayer.computeCameraGaze(
            CameraType.THIRD_PERSON_BACK, headYaw, headPitch,
            invertCameraDirection = true, autoAdjustDirection = true
        )
        assertEquals(RotomPlayerRenderLayer.YAW_FACING_FRONT + headYaw * 0.25f, invBackYaw, 0.001f)
        assertEquals(-headPitch * 0.20f, invBackPitch, 0.001f)

        val (invFrontYaw, invFrontPitch) = RotomPlayerRenderLayer.computeCameraGaze(
            CameraType.THIRD_PERSON_FRONT, headYaw, headPitch,
            invertCameraDirection = true, autoAdjustDirection = true
        )
        assertEquals(RotomPlayerRenderLayer.YAW_FACING_BACK - headYaw * 0.25f, invFrontYaw, 0.001f)
        assertEquals(headPitch * 0.20f, invFrontPitch, 0.001f)
    }

    @Test
    fun `computeCameraGaze locks to configured axis when autoAdjustDirection is disabled`() {
        for (cameraType in CameraType.entries) {
            val (normalYaw, normalPitch) = RotomPlayerRenderLayer.computeCameraGaze(
                cameraType, netHeadYaw = 30.0f, headPitch = 15.0f,
                invertCameraDirection = false, autoAdjustDirection = false
            )
            assertEquals(RotomPlayerRenderLayer.YAW_FACING_BACK, normalYaw, 0.001f)
            assertEquals(0.0f, normalPitch, 0.001f)

            val (invertedYaw, invertedPitch) = RotomPlayerRenderLayer.computeCameraGaze(
                cameraType, netHeadYaw = 30.0f, headPitch = 15.0f,
                invertCameraDirection = true, autoAdjustDirection = false
            )
            assertEquals(RotomPlayerRenderLayer.YAW_FACING_FRONT, invertedYaw, 0.001f)
            assertEquals(0.0f, invertedPitch, 0.001f)
        }
    }
}
