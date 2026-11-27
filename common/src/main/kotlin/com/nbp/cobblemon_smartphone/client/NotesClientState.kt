package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.SaveNotesPacket
import com.nbp.cobblemon_smartphone.util.NotesStorage
import com.nbp.cobblemon_smartphone.util.SmartphoneNote

/**
 * Client-side cache of the player's synced smartphone notes.
 * Mutations update the local list immediately and send [SaveNotesPacket] to the server.
 */
object NotesClientState {
    private var notes: List<SmartphoneNote> = emptyList()

    fun getNotes(): List<SmartphoneNote> = notes

    fun setFromServer(synced: List<SmartphoneNote>) {
        notes = NotesStorage.sanitize(synced)
    }

    fun upsert(note: SmartphoneNote): Boolean {
        val withoutOld = notes.filterNot { it.id == note.id }
        if (withoutOld.size >= NotesStorage.MAX_NOTES && notes.none { it.id == note.id }) {
            return false
        }
        val updated = NotesStorage.sanitize(withoutOld + note)
        notes = updated
        SaveNotesPacket(updated).sendToServer()
        return true
    }

    fun delete(noteId: String) {
        val updated = NotesStorage.sanitize(notes.filterNot { it.id == noteId })
        notes = updated
        SaveNotesPacket(updated).sendToServer()
    }

    fun togglePin(noteId: String) {
        val updated = NotesStorage.sanitize(
            notes.map { if (it.id == noteId) it.copy(pinned = !it.pinned) else it }
        )
        notes = updated
        SaveNotesPacket(updated).sendToServer()
    }

    fun clear() {
        notes = emptyList()
    }
}
