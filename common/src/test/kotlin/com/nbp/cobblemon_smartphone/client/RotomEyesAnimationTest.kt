package com.nbp.cobblemon_smartphone.client

import com.google.gson.JsonParser
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import javax.imageio.ImageIO

class RotomEyesAnimationTest {

    @Test
    fun testRotomEyesTextureAndMetadata() {
        val textureStream = javaClass.getResourceAsStream("/assets/cobblemon_smartphone/textures/item/rotom_eyes.png")
        assertNotNull(textureStream, "rotom_eyes.png must exist in classpath")
        val image = ImageIO.read(textureStream)
        assertEquals(16, image.width, "Texture width must be 16")
        assertEquals(32, image.height, "Texture height must be 32 (two 16x16 frames)")

        val metaStream = javaClass.getResourceAsStream("/assets/cobblemon_smartphone/textures/item/rotom_eyes.png.mcmeta")
        assertNotNull(metaStream, "rotom_eyes.png.mcmeta must exist in classpath")
        val json = JsonParser.parseReader(InputStreamReader(metaStream)).asJsonObject
        assertTrue(json.has("animation"), "mcmeta must have 'animation' object")

        val animSection = AnimationMetadataSection.SERIALIZER.fromJson(json.getAsJsonObject("animation"))
        assertNotNull(animSection)
        val frameSize = animSection.calculateFrameSize(16, 32)
        assertEquals(16, frameSize.width())
        assertEquals(16, frameSize.height())
    }
}
