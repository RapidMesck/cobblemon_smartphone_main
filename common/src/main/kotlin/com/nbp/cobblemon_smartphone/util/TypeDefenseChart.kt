package com.nbp.cobblemon_smartphone.util

object TypeDefenseChart {

    private val chart: Map<String, Map<String, Double>> = mapOf(
        "normal" to mapOf(
            "rock" to 0.5, "ghost" to 0.0, "steel" to 0.5
        ),
        "fire" to mapOf(
            "fire" to 0.5, "water" to 2.0, "grass" to 0.5, "ice" to 0.5,
            "ground" to 2.0, "bug" to 0.5, "rock" to 2.0, "steel" to 0.5, "fairy" to 0.5
        ),
        "water" to mapOf(
            "fire" to 0.5, "water" to 0.5, "grass" to 2.0, "electric" to 2.0,
            "ice" to 0.5, "steel" to 0.5
        ),
        "electric" to mapOf(
            "electric" to 0.5, "ground" to 2.0, "flying" to 0.5, "steel" to 0.5
        ),
        "grass" to mapOf(
            "fire" to 2.0, "water" to 0.5, "grass" to 0.5, "ice" to 2.0,
            "poison" to 2.0, "ground" to 0.5, "flying" to 2.0, "bug" to 2.0
        ),
        "ice" to mapOf(
            "fire" to 2.0, "ice" to 0.5, "fighting" to 2.0, "rock" to 2.0, "steel" to 2.0
        ),
        "fighting" to mapOf(
            "flying" to 2.0, "psychic" to 2.0, "bug" to 0.5, "rock" to 0.5,
            "dark" to 0.5, "fairy" to 2.0
        ),
        "poison" to mapOf(
            "grass" to 0.5, "fighting" to 0.5, "poison" to 0.5,
            "ground" to 2.0, "psychic" to 2.0, "bug" to 0.5, "fairy" to 0.5
        ),
        "ground" to mapOf(
            "water" to 2.0, "grass" to 2.0, "electric" to 0.0, "ice" to 2.0,
            "poison" to 0.5, "rock" to 0.5
        ),
        "flying" to mapOf(
            "electric" to 2.0, "grass" to 0.5, "ice" to 2.0, "fighting" to 0.5,
            "ground" to 0.0, "bug" to 0.5, "rock" to 2.0
        ),
        "psychic" to mapOf(
            "fighting" to 0.5, "psychic" to 0.5, "bug" to 2.0, "ghost" to 2.0, "dark" to 2.0
        ),
        "bug" to mapOf(
            "fire" to 2.0, "grass" to 0.5, "fighting" to 0.5, "ground" to 0.5,
            "flying" to 2.0, "rock" to 2.0
        ),
        "rock" to mapOf(
            "normal" to 0.5, "fire" to 0.5, "water" to 2.0, "grass" to 2.0,
            "fighting" to 2.0, "poison" to 0.5, "ground" to 2.0, "flying" to 0.5,
            "steel" to 2.0
        ),
        "ghost" to mapOf(
            "normal" to 0.0, "fighting" to 0.0, "poison" to 0.5, "bug" to 0.5,
            "ghost" to 2.0, "dark" to 2.0
        ),
        "dragon" to mapOf(
            "fire" to 0.5, "water" to 0.5, "grass" to 0.5, "electric" to 0.5,
            "ice" to 2.0, "dragon" to 2.0, "fairy" to 2.0
        ),
        "dark" to mapOf(
            "fighting" to 2.0, "psychic" to 0.0, "bug" to 2.0, "ghost" to 0.5,
            "dark" to 0.5, "fairy" to 2.0
        ),
        "steel" to mapOf(
            "normal" to 0.5, "fire" to 2.0, "grass" to 0.5, "ice" to 0.5,
            "fighting" to 2.0, "poison" to 0.0, "ground" to 2.0, "flying" to 0.5,
            "psychic" to 0.5, "bug" to 0.5, "rock" to 0.5, "dragon" to 0.5,
            "steel" to 0.5, "fairy" to 0.5
        ),
        "fairy" to mapOf(
            "fighting" to 0.5, "poison" to 2.0, "bug" to 0.5, "dragon" to 0.0,
            "dark" to 0.5, "steel" to 2.0
        )
    )

    fun getEffectiveness(types: List<String>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val allTypes = listOf(
            "normal", "fire", "water", "electric", "grass", "ice",
            "fighting", "poison", "ground", "flying", "psychic", "bug",
            "rock", "ghost", "dragon", "dark", "steel", "fairy"
        )
        allTypes.forEach { result[it] = 1.0 }

        for (type in types) {
            chart[type]?.forEach { (attackType, multiplier) ->
                result[attackType] = (result[attackType] ?: 1.0) * multiplier
            }
        }

        return result
    }

    fun multiplierText(value: Double): String = when (value) {
        0.0 -> "0"
        0.25 -> "\u00BC"
        0.5 -> "\u00BD"
        1.0 -> "-"
        2.0 -> "2"
        4.0 -> "4"
        else -> value.toString()
    }

    private val typeAbbr = mapOf(
        "normal" to "Nor", "fire" to "Fir", "water" to "Wat", "electric" to "Ele",
        "grass" to "Gra", "ice" to "Ice", "fighting" to "Fig", "poison" to "Poi",
        "ground" to "Gro", "flying" to "Fly", "psychic" to "Psy", "bug" to "Bug",
        "rock" to "Roc", "ghost" to "Gho", "dragon" to "Dra", "dark" to "Dar",
        "steel" to "Ste", "fairy" to "Fai"
    )

    fun typeAbbreviation(type: String): String = typeAbbr[type] ?: type.take(3)
}
