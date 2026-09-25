package com.nbp.cobblemon_smartphone.compat

import com.nbp.cobblemon_smartphone.api.DatapackActionDefinition
import com.nbp.cobblemon_smartphone.compat.patchouli.PatchouliCompat
import com.nbp.cobblemon_smartphone.network.packet.OpenPatchouliBookPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncedActionData
import io.netty.buffer.Unpooled
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PatchouliActionTest {

    @Test
    fun `patchouli compat has correct mod id`() {
        assertEquals("patchouli", PatchouliCompat.MOD_ID)
    }

    @Test
    fun `datapack action definition parses patchouli book with various field names`() {
        val jsonSnakeBook = """
            {
                "id": "mypack:guide_snake",
                "texture": "mypack:textures/gui/buttons/guide.png",
                "hover_texture": "mypack:textures/gui/buttons/guide_hover.png",
                "patchouli_book": "mymod:guidebook"
            }
        """.trimIndent()
        val def1 = DatapackActionDefinition.fromJson(jsonSnakeBook)
        assertNotNull(def1)
        assertEquals("mymod:guidebook", def1?.patchouliBook)

        val jsonSnakeId = """
            {
                "id": "mypack:guide_id",
                "texture": "mypack:textures/gui/buttons/guide.png",
                "hover_texture": "mypack:textures/gui/buttons/guide_hover.png",
                "patchouli_id": "mymod:guidebook2"
            }
        """.trimIndent()
        val def2 = DatapackActionDefinition.fromJson(jsonSnakeId)
        assertNotNull(def2)
        assertEquals("mymod:guidebook2", def2?.patchouliBook)

        val jsonCamelBook = """
            {
                "id": "mypack:guide_camel",
                "texture": "mypack:textures/gui/buttons/guide.png",
                "hover_texture": "mypack:textures/gui/buttons/guide_hover.png",
                "patchouliBook": "mymod:guidebook3"
            }
        """.trimIndent()
        val def3 = DatapackActionDefinition.fromJson(jsonCamelBook)
        assertNotNull(def3)
        assertEquals("mymod:guidebook3", def3?.patchouliBook)

        val jsonCamelId = """
            {
                "id": "mypack:guide_camel_id",
                "texture": "mypack:textures/gui/buttons/guide.png",
                "hover_texture": "mypack:textures/gui/buttons/guide_hover.png",
                "patchouliId": "mymod:guidebook4"
            }
        """.trimIndent()
        val def4 = DatapackActionDefinition.fromJson(jsonCamelId)
        assertNotNull(def4)
        assertEquals("mymod:guidebook4", def4?.patchouliBook)
    }

    @Test
    fun `open patchouli book packet encode and decode`() {
        val nettyBuf = Unpooled.buffer()
        val buffer = RegistryFriendlyByteBuf(nettyBuf, net.minecraft.core.RegistryAccess.EMPTY)

        val packetWithBook = OpenPatchouliBookPacket("test_mod:test_book")
        packetWithBook.encode(buffer)

        val decodedWithBook = OpenPatchouliBookPacket.decode(buffer)
        assertEquals("test_mod:test_book", decodedWithBook.bookId)
    }

    @Test
    fun `sync datapack actions packet preserves patchouli book`() {
        val nettyBuf = Unpooled.buffer()
        val buffer = RegistryFriendlyByteBuf(nettyBuf, net.minecraft.core.RegistryAccess.EMPTY)

        val actions = listOf(
            SyncedActionData(
                id = "mypack:action1",
                texture = "mypack:tex1",
                hoverTexture = "mypack:tex1_h",
                requireUpgrade = "upgrade_x",
                requireMod = "mod_x",
                cooldownSeconds = 5,
                patchouliBook = "mod_x:book"
            ),
            SyncedActionData(
                id = "mypack:action2",
                texture = "mypack:tex2",
                hoverTexture = "mypack:tex2_h",
                requireUpgrade = null,
                requireMod = null,
                cooldownSeconds = 0,
                patchouliBook = null
            )
        )

        val packet = SyncDatapackActionsPacket(actions)
        packet.encode(buffer)

        val decoded = SyncDatapackActionsPacket.decode(buffer)
        assertEquals(2, decoded.actions.size)
        assertEquals("mod_x:book", decoded.actions[0].patchouliBook)
        assertNull(decoded.actions[1].patchouliBook)
    }
}
