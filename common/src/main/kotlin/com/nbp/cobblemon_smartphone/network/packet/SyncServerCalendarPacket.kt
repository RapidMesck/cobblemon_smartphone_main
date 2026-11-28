package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncServerCalendarPacket(
    val events: List<CalendarEvent>
) : CobblemonSmartphoneNetworkPacket<SyncServerCalendarPacket> {
    companion object {
        val ID = smartphoneResource("sync_server_calendar")

        fun decode(buffer: RegistryFriendlyByteBuf): SyncServerCalendarPacket {
            val count = buffer.readVarInt().coerceIn(0, 500)
            val events = (0 until count).map { CalendarEvent.readFromBuf(buffer) }
            return SyncServerCalendarPacket(events)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        val clean = CalendarEvent.sanitizeList(events, 500, forceServerFlag = true)
        buffer.writeVarInt(clean.size)
        for (ev in clean) ev.writeToBuf(buffer)
    }
}
