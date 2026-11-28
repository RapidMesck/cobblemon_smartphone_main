package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class DeleteServerCalendarEventPacket(
    val eventId: String
) : CobblemonSmartphoneNetworkPacket<DeleteServerCalendarEventPacket> {
    companion object {
        val ID = smartphoneResource("delete_server_calendar_event")

        fun decode(buffer: RegistryFriendlyByteBuf): DeleteServerCalendarEventPacket {
            return DeleteServerCalendarEventPacket(buffer.readUtf(CalendarEvent.MAX_ID_LENGTH))
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(eventId.take(CalendarEvent.MAX_ID_LENGTH), CalendarEvent.MAX_ID_LENGTH)
    }
}
