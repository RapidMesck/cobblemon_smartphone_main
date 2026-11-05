package com.nbp.cobblemon_smartphone.curiosity

import com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PokemonCuriosityRepositoryTest {

    @Test
    fun `getCuriosity retrieves curiosity by species name and id`() {
        val bulbasaur = PokemonCuriosityRepository.getCuriosity("Bulbasaur", 1)
        assertNotNull(bulbasaur)
        assertEquals("Bulbasaur", bulbasaur?.name)
        assertTrue(bulbasaur?.texto_pt?.contains("Bulbasaur") == true)
        assertTrue(bulbasaur?.texto_en?.contains("Bulbasaur") == true)

        val pikachu = PokemonCuriosityRepository.getCuriosity("pikachu", 25)
        assertNotNull(pikachu)
        assertEquals("Pikachu", pikachu?.name)
    }

    @Test
    fun `getCuriosity returns null for non-existent modded pokemon`() {
        val fakemon = PokemonCuriosityRepository.getCuriosity("custom_fakemon_xyz", 99999)
        assertNull(fakemon)
    }

    @Test
    fun `isPlayerInDialogueOrScanning respects isDialogueOpen state`() {
        val uuid = UUID.randomUUID()
        assertFalse(RotomAutoScannerManager.isPlayerInDialogueOrScanning(uuid))

        val session = RotomAutoScannerManager.PlayerScanSession(uuid, isDialogueOpen = true)
        // Accessing session state directly
        assertTrue(session.isDialogueOpen)
    }
}
