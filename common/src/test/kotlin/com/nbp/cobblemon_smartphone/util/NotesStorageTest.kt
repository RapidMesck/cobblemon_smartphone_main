package com.nbp.cobblemon_smartphone.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotesStorageTest {

    @Test
    fun `encode and decode list tag round trips notes accurately`() {
        val notes = listOf(
            SmartphoneNote(
                id = "n1",
                title = "Shiny Hunt",
                content = "Check flower forest at night",
                pinned = true,
                colorTag = 2,
                updatedAt = 1000L
            ),
            SmartphoneNote(
                id = "n2",
                title = "Base Coords",
                content = "XYZ: 120, 64, -340 (Overworld)",
                pinned = false,
                colorTag = 4,
                updatedAt = 2000L
            )
        )

        val tag = NotesStorage.encodeToListTag(notes)
        val decoded = NotesStorage.decodeFromListTag(tag)

        assertEquals(2, decoded.size)
        // Pinned note sorts first even with older timestamp
        assertEquals("n1", decoded[0].id)
        assertEquals("Shiny Hunt", decoded[0].title)
        assertTrue(decoded[0].pinned)
        assertEquals(2, decoded[0].colorTag)
        assertEquals("n2", decoded[1].id)
        assertEquals("XYZ: 120, 64, -340 (Overworld)", decoded[1].content)
    }

    @Test
    fun `sanitize enforces limits, drops blank notes, clamps color tags and deduplicates ids`() {
        val raw = mutableListOf<SmartphoneNote>()
        raw.add(SmartphoneNote(id = "blank", title = "   ", content = ""))
        raw.add(SmartphoneNote(id = "dup", title = "A".repeat(100), content = "B".repeat(2500), colorTag = 99, updatedAt = 500L))
        raw.add(SmartphoneNote(id = "dup", title = "Second", content = "Body", colorTag = -5, updatedAt = 600L))
        for (i in 1..60) {
            raw.add(SmartphoneNote(id = "id_$i", title = "Note $i", content = "Content $i", updatedAt = i.toLong()))
        }

        val sanitized = NotesStorage.sanitize(raw)

        assertEquals(NotesStorage.MAX_NOTES, sanitized.size)
        assertEquals(sanitized.map { it.id }.toSet().size, sanitized.size)
        assertTrue(sanitized.none { it.title.isBlank() && it.content.isBlank() })
        assertTrue(sanitized.all { it.title.length <= NotesStorage.MAX_TITLE_LENGTH })
        assertTrue(sanitized.all { it.content.length <= NotesStorage.MAX_CONTENT_LENGTH })
        assertTrue(sanitized.all { it.colorTag in 0 until NotesStorage.COLOR_TAG_COUNT })
    }

    @Test
    fun `filterNotes matches title and content case insensitively while preserving pin order`() {
        val notes = listOf(
            SmartphoneNote(id = "1", title = "Breeding", content = "Eevee with Wish", pinned = false, updatedAt = 300L),
            SmartphoneNote(id = "2", title = "Trades", content = "Need Eevee stone", pinned = true, updatedAt = 100L),
            SmartphoneNote(id = "3", title = "Mining", content = "Diamond Y=-58", pinned = false, updatedAt = 400L)
        )

        val matched = NotesStorage.filterNotes(notes, "eevee")
        assertEquals(listOf("2", "1"), matched.map { it.id })
    }
}
