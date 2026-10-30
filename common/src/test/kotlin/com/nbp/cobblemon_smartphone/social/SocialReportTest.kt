package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialReportTest {

    @Test
    fun `report serializes to and from NBT`() {
        val reporterUuid = UUID.randomUUID()
        val authorUuid = UUID.randomUUID()
        val report = SocialReport(
            id = 101L,
            targetType = ReportTargetType.POST,
            targetId = 55L,
            reporterUuid = reporterUuid,
            reporterName = "Misty",
            targetAuthorUuid = authorUuid,
            reason = "Inappropriate language",
            timestamp = 1700000000000L
        )

        val nbt = report.toNbt()
        val restored = SocialReport.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(101L, restored?.id)
        assertEquals(ReportTargetType.POST, restored?.targetType)
        assertEquals(55L, restored?.targetId)
        assertEquals(reporterUuid, restored?.reporterUuid)
        assertEquals("Misty", restored?.reporterName)
        assertEquals(authorUuid, restored?.targetAuthorUuid)
        assertEquals("Inappropriate language", restored?.reason)
        assertEquals(1700000000000L, restored?.timestamp)
    }

    @Test
    fun `report comment target type serializes properly`() {
        val reporterUuid = UUID.randomUUID()
        val authorUuid = UUID.randomUUID()
        val report = SocialReport(
            id = 102L,
            targetType = ReportTargetType.COMMENT,
            targetId = 77L,
            reporterUuid = reporterUuid,
            reporterName = "Brock",
            targetAuthorUuid = authorUuid,
            reason = "Spam",
            timestamp = 1700000005000L
        )

        val nbt = report.toNbt()
        val restored = SocialReport.fromNbt(nbt)

        assertNotNull(restored)
        assertEquals(ReportTargetType.COMMENT, restored?.targetType)
        assertEquals(77L, restored?.targetId)
        assertEquals("Brock", restored?.reporterName)
    }
}
