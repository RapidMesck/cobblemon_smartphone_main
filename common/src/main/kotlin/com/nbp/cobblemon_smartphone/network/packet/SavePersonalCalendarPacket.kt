package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.calendar.CalendarEvent
import com.nbp.cobblemon_smartphone.calendar.CalendarPersonalStorage
import com.nbp.cobblemon_smartphone.calendar.PersonalCalendarData
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SavePersonalCalendarPacket(
    val data: PersonalCalendarData
) : CobblemonSmartphoneNetworkPacket<SavePersonalCalendarPacket> {
    companion object {
        val ID = smartphoneResource("save_personal_calendar")

        fun decode(buffer: RegistryFriendlyByteBuf): SavePersonalCalendarPacket {
            val eventCount = buffer.readVarInt().coerceIn(0, 200)
            val events = (0 until eventCount).map { CalendarEvent.readFromBuf(buffer) }

            val remCount = buffer.readVarInt().coerceIn(0, CalendarPersonalStorage.MAX_TRACKED_IDS)
            val reminders = LinkedHashSet<String>(remCount)
            repeat(remCount) { reminders.add(buffer.readUtf(CalendarEvent.MAX_ID_LENGTH)) }

            val seenCount = buffer.readVarInt().coerceIn(0, CalendarPersonalStorage.MAX_TRACKED_IDS)
            val seen = LinkedHashSet<String>(seenCount)
            repeat(seenCount) { seen.add(buffer.readUtf(CalendarEvent.MAX_ID_LENGTH)) }

            val use24h = buffer.readBoolean()
            return SavePersonalCalendarPacket(PersonalCalendarData(events, reminders, seen, use24h))
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        val clean = CalendarEvent.sanitizeList(data.events, 200, forceServerFlag = false)
        buffer.writeVarInt(clean.size)
        for (ev in clean) ev.writeToBuf(buffer)

        val rems = data.serverReminderIds.take(CalendarPersonalStorage.MAX_TRACKED_IDS)
        buffer.writeVarInt(rems.size)
        for (id in rems) buffer.writeUtf(id, CalendarEvent.MAX_ID_LENGTH)

        val seens = data.seenServerEventIds.take(CalendarPersonalStorage.MAX_TRACKED_IDS)
        buffer.writeVarInt(seens.size)
        for (id in seens) buffer.writeUtf(id, CalendarEvent.MAX_ID_LENGTH)

        buffer.writeBoolean(data.use24Hour)
    }
}
