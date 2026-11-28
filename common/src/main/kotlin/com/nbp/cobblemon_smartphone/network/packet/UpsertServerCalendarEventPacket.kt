package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class UpsertServerCalendarEventPacket(
    val event: CalendarEvent,
    val announceInSocial: Boolean = false
) : CobblemonSmartphoneNetworkPacket<UpsertServerCalendarEventPacket> {
    companion object {
        val ID = smartphoneResource("upsert_server_calendar_event")

        fun decode(buffer: RegistryFriendlyByteBuf): UpsertServerCalendarEventPacket {
            val event = CalendarEvent.readFromBuf(buffer)
            val announce = buffer.readBoolean()
            return UpsertServerCalendarEventPacket(event, announce)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        event.writeToBuf(buffer)
        buffer.writeBoolean(announceInSocial)
    }
}
