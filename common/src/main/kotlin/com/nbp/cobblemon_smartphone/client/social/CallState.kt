package com.nbp.cobblemon_smartphone.client.social

import com.nbp.cobblemon_smartphone.social.CallStatus
import java.util.UUID

/**
 * Client mirror of the server's call state. The DM thread reads this each frame to render the call
 * button, and the incoming-call overlay is driven from it.
 */
object CallState {
    var status: CallStatus = CallStatus.IDLE
        private set
    var otherUuid: UUID? = null
        private set
    var otherName: String = ""
        private set

    /** When the current status began — used for the client-side ring/answer timeout. */
    var since: Long = 0L
        private set

    fun update(status: CallStatus, otherUuid: UUID, otherName: String) {
        if (this.status != status) since = System.currentTimeMillis()
        this.status = status
        this.otherUuid = otherUuid
        this.otherName = otherName
    }

    fun reset() {
        status = CallStatus.IDLE
        otherUuid = null
        otherName = ""
        since = 0L
    }

    fun isRingingFrom(uuid: UUID): Boolean = status == CallStatus.INCOMING && otherUuid == uuid
    fun isBusyWith(uuid: UUID): Boolean =
        (status == CallStatus.OUTGOING || status == CallStatus.IN_CALL) && otherUuid == uuid
    fun isIdle(): Boolean = status == CallStatus.IDLE
}
