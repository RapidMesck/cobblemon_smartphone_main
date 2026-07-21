package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.AbilityInfo
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.EvoInfo
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.FormInfo
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.LocalizedText
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.MoveInfo
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.SpawnEntryData
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.SpeciesDetail
import com.nbp.cobblemon_smartphone.util.PokeInfoDataProvider.StatsInfo
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SpeciesDetailResponsePacket(val detail: SpeciesDetail?) :
    CobblemonSmartphoneNetworkPacket<SpeciesDetailResponsePacket> {
    companion object {
        val ID = smartphoneResource("species_detail_response")

        fun decode(buffer: RegistryFriendlyByteBuf): SpeciesDetailResponsePacket {
            val hasDetail = buffer.readBoolean()
            val detail = if (hasDetail) readDetail(buffer) else null
            return SpeciesDetailResponsePacket(detail)
        }

        private fun readDetail(buffer: RegistryFriendlyByteBuf): SpeciesDetail {
            return SpeciesDetail(
                name = buffer.readUtf(),
                dexNumber = buffer.readVarInt(),
                primaryType = buffer.readUtf(),
                secondaryType = buffer.readNullableUtf(),
                height = buffer.readFloat(),
                weight = buffer.readFloat(),
                baseStats = readStats(buffer),
                evYield = readStats(buffer),
                abilities = readList(buffer) { readAbility(buffer) },
                preEvolution = buffer.readNullableUtf(),
                evolutions = readList(buffer) {
                    EvoInfo(buffer.readUtf(), readList(buffer) { readLocalizedText(buffer) })
                },
                moves = readList(buffer) { readMove(buffer) },
                catchRate = buffer.readVarInt(),
                baseExp = buffer.readVarInt(),
                growthRate = buffer.readUtf(),
                baseFriendship = buffer.readVarInt(),
                eggGroups = readList(buffer) { buffer.readUtf() },
                eggCycles = buffer.readVarInt(),
                maleRatio = buffer.readFloat(),
                spawnEntries = readList(buffer) { readSpawnEntry(buffer) },
                spawnBiomes = readList(buffer) { buffer.readUtf() },
                availableForms = readList(buffer) { FormInfo(buffer.readUtf(), buffer.readUtf()) },
                selectedForm = buffer.readUtf()
            )
        }

        private fun readStats(buffer: RegistryFriendlyByteBuf) = StatsInfo(
            hp = buffer.readVarInt(), attack = buffer.readVarInt(), defence = buffer.readVarInt(),
            specialAttack = buffer.readVarInt(), specialDefence = buffer.readVarInt(), speed = buffer.readVarInt()
        )

        private fun readAbility(buffer: RegistryFriendlyByteBuf) = AbilityInfo(
            name = buffer.readUtf(), isHidden = buffer.readBoolean(), descriptionKey = buffer.readUtf()
        )

        private fun readMove(buffer: RegistryFriendlyByteBuf) = MoveInfo(
            level = buffer.readVarInt(), name = buffer.readUtf(), method = buffer.readUtf(),
            type = buffer.readUtf(), category = buffer.readUtf(),
            power = buffer.readVarInt(), accuracy = buffer.readVarInt()
        )

        private fun readSpawnEntry(buffer: RegistryFriendlyByteBuf) = SpawnEntryData(
            bucket = buffer.readUtf(), weight = buffer.readFloat(),
            biomes = readList(buffer) { buffer.readUtf() },
            minLevel = buffer.readVarInt(), maxLevel = buffer.readVarInt(),
            context = buffer.readUtf(),
            time = readNullableLocalizedText(buffer),
            conditions = readList(buffer) { readLocalizedText(buffer) },
            antiConditions = readList(buffer) { readLocalizedText(buffer) }
        )

        private fun readLocalizedText(buffer: RegistryFriendlyByteBuf) = LocalizedText(
            key = buffer.readUtf(),
            args = readList(buffer) { buffer.readUtf() }
        )

        private fun readNullableLocalizedText(buffer: RegistryFriendlyByteBuf): LocalizedText? =
            if (buffer.readBoolean()) readLocalizedText(buffer) else null

        private fun <T> readList(buffer: RegistryFriendlyByteBuf, reader: () -> T): List<T> {
            val count = buffer.readVarInt()
            return (0 until count).map { reader() }
        }

        private fun RegistryFriendlyByteBuf.readNullableUtf(): String? {
            return if (readBoolean()) readUtf() else null
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(detail != null)
        detail?.let { encodeDetail(buffer, it) }
    }

    private fun encodeDetail(buffer: RegistryFriendlyByteBuf, d: SpeciesDetail) {
        buffer.writeUtf(d.name)
        buffer.writeVarInt(d.dexNumber)
        buffer.writeUtf(d.primaryType)
        buffer.writeNullableUtf(d.secondaryType)
        buffer.writeFloat(d.height)
        buffer.writeFloat(d.weight)
        encodeStats(buffer, d.baseStats)
        encodeStats(buffer, d.evYield)
        encodeList(buffer, d.abilities) { encodeAbility(buffer, it) }
        buffer.writeNullableUtf(d.preEvolution)
        encodeList(buffer, d.evolutions) { evo ->
            buffer.writeUtf(evo.targetName)
            encodeList(buffer, evo.methods) { encodeLocalizedText(buffer, it) }
        }
        encodeList(buffer, d.moves) { encodeMove(buffer, it) }
        buffer.writeVarInt(d.catchRate)
        buffer.writeVarInt(d.baseExp)
        buffer.writeUtf(d.growthRate)
        buffer.writeVarInt(d.baseFriendship)
        encodeList(buffer, d.eggGroups) { buffer.writeUtf(it) }
        buffer.writeVarInt(d.eggCycles)
        buffer.writeFloat(d.maleRatio)
        encodeList(buffer, d.spawnEntries) { encodeSpawnEntry(buffer, it) }
        encodeList(buffer, d.spawnBiomes) { buffer.writeUtf(it) }
        encodeList(buffer, d.availableForms) { form ->
            buffer.writeUtf(form.name); buffer.writeUtf(form.displayName)
        }
        buffer.writeUtf(d.selectedForm)
    }

    private fun encodeStats(buffer: RegistryFriendlyByteBuf, s: StatsInfo) {
        buffer.writeVarInt(s.hp); buffer.writeVarInt(s.attack); buffer.writeVarInt(s.defence)
        buffer.writeVarInt(s.specialAttack); buffer.writeVarInt(s.specialDefence); buffer.writeVarInt(s.speed)
    }

    private fun encodeAbility(buffer: RegistryFriendlyByteBuf, a: AbilityInfo) {
        buffer.writeUtf(a.name); buffer.writeBoolean(a.isHidden); buffer.writeUtf(a.descriptionKey)
    }

    private fun encodeMove(buffer: RegistryFriendlyByteBuf, m: MoveInfo) {
        buffer.writeVarInt(m.level); buffer.writeUtf(m.name); buffer.writeUtf(m.method)
        buffer.writeUtf(m.type); buffer.writeUtf(m.category)
        buffer.writeVarInt(m.power); buffer.writeVarInt(m.accuracy)
    }

    private fun encodeSpawnEntry(buffer: RegistryFriendlyByteBuf, e: SpawnEntryData) {
        buffer.writeUtf(e.bucket); buffer.writeFloat(e.weight)
        encodeList(buffer, e.biomes) { buffer.writeUtf(it) }
        buffer.writeVarInt(e.minLevel); buffer.writeVarInt(e.maxLevel)
        buffer.writeUtf(e.context)
        buffer.writeBoolean(e.time != null)
        e.time?.let { encodeLocalizedText(buffer, it) }
        encodeList(buffer, e.conditions) { encodeLocalizedText(buffer, it) }
        encodeList(buffer, e.antiConditions) { encodeLocalizedText(buffer, it) }
    }

    private fun encodeLocalizedText(buffer: RegistryFriendlyByteBuf, text: LocalizedText) {
        buffer.writeUtf(text.key)
        encodeList(buffer, text.args) { buffer.writeUtf(it) }
    }

    private fun <T> encodeList(buffer: RegistryFriendlyByteBuf, list: List<T>, encoder: (T) -> Unit) {
        buffer.writeVarInt(list.size)
        list.forEach { encoder(it) }
    }

    private fun RegistryFriendlyByteBuf.writeNullableUtf(value: String?) {
        writeBoolean(value != null)
        value?.let { writeUtf(it) }
    }
}
