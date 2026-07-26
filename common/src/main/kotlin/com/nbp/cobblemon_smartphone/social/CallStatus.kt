package com.nbp.cobblemon_smartphone.social

/**
 * The caller-perspective phase of a voice call, pushed from server to client so the UI can react.
 *
 * OUTGOING = we are ringing someone; INCOMING = someone is ringing us; IN_CALL = connected;
 * IDLE = no call (also used to tear down ringing UIs when a call ends/declines).
 */
enum class CallStatus {
    IDLE,
    OUTGOING,
    INCOMING,
    IN_CALL;

    companion object {
        fun byId(id: Int): CallStatus = entries.getOrElse(id) { IDLE }
    }
}
