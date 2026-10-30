package com.nbp.cobblemon_smartphone.theme

data class SmartphoneThemeColors(
    // 1. Primary & Brand Accents
    val primary: Int = 0xFF3A96B6.toInt(),
    val primaryHover: Int = 0xFF4FB4D6.toInt(),
    val primaryBorder: Int = 0xFF79B7C9.toInt(),

    // 2. Surfaces & Containers
    val surface: Int = 0xFFEFFDFF.toInt(),
    val surfaceAlt: Int = 0xFFF2FBFD.toInt(),
    val surfaceHover: Int = 0xFFD0E8F5.toInt(),
    val shadow: Int = 0x35000000,

    // 3. Top Navigation & Header
    val headerNavy: Int = 0xFF163A49.toInt(),
    val headerNavyLight: Int = 0xFF24576A.toInt(),

    // 4. Typography
    val textPrimary: Int = 0xFFFFFFFF.toInt(),
    val textSecondary: Int = 0xFFE6FFFF.toInt(),
    val textMuted: Int = 0xFF8AA5AD.toInt(),
    val textDark: Int = 0xFF1A1A2E.toInt(),
    val textDarkMuted: Int = 0xFF555555.toInt(),
    val userName: Int = 0xFF5BD1DA.toInt(),

    // 5. Functional & Status Accents
    val gold: Int = 0xFFFFD700.toInt(),
    val danger: Int = 0xFFFD0100.toInt(),
    val success: Int = 0xFF3AAA55.toInt(),

    // 6. Direct time color override if specified
    val time: Int? = null,

    // 7. Battery & Hardware Indicator
    val batteryBorder: Int = 0xFF5BD1DA.toInt(),
    val batteryEmpty: Int = 0xFF1D4A59.toInt(),
    val batteryHigh: Int = 0xFF22C55E.toInt(),
    val batteryMed: Int = 0xFFEAB308.toInt(),
    val batteryLow: Int = 0xFFEF4444.toInt()
) {
    val timeColor: Int get() = time ?: textSecondary

    companion object {
        val DEFAULT = SmartphoneThemeColors()

        val DARK = SmartphoneThemeColors(
            primary = 0xFF5BD1DA.toInt(),
            primaryHover = 0xFF7CE3EB.toInt(),
            primaryBorder = 0xFF35454F.toInt(),
            surface = 0xFF1C1C22.toInt(),
            surfaceAlt = 0xFF22222A.toInt(),
            surfaceHover = 0xFF2E2E38.toInt(),
            headerNavy = 0xFF111114.toInt(),
            headerNavyLight = 0xFF1A1A20.toInt(),
            textPrimary = 0xFFFFFFFF.toInt(),
            textSecondary = 0xFFA0A0B0.toInt(),
            textMuted = 0xFF686878.toInt(),
            textDark = 0xFFF0F0F5.toInt(),
            textDarkMuted = 0xFFA0A0B2.toInt(),
            userName = 0xFF7CE3EB.toInt(),
            gold = 0xFFFFD700.toInt(),
            danger = 0xFFFF4444.toInt(),
            time = 0xFFA0A0B0.toInt(),
            batteryBorder = 0xFF5BD1DA.toInt(),
            batteryEmpty = 0xFF111114.toInt(),
            batteryHigh = 0xFF48BB78.toInt(),
            batteryMed = 0xFFE5B842.toInt(),
            batteryLow = 0xFFE05252.toInt()
        )

        val ROTOM = SmartphoneThemeColors(
            primary = 0xFFEA580C.toInt(),
            primaryHover = 0xFFF97316.toInt(),
            primaryBorder = 0xFF9A3412.toInt(),
            surface = 0xFF24140E.toInt(),
            surfaceAlt = 0xFF2D1912.toInt(),
            surfaceHover = 0xFF3B2218.toInt(),
            headerNavy = 0xFF160B07.toInt(),
            headerNavyLight = 0xFF23120C.toInt(),
            textPrimary = 0xFFFFFFFF.toInt(),
            textSecondary = 0xFFFFAA00.toInt(),
            textMuted = 0xFFA87E6F.toInt(),
            textDark = 0xFFFED7AA.toInt(),
            textDarkMuted = 0xFFC29E8E.toInt(),
            userName = 0xFFFFAA00.toInt(),
            gold = 0xFFFBBF24.toInt(),
            danger = 0xFFEF4444.toInt(),
            time = 0xFFFFAA00.toInt(),
            batteryBorder = 0xFFF97316.toInt(),
            batteryEmpty = 0xFF160B07.toInt(),
            batteryHigh = 0xFF2EB85C.toInt(),
            batteryMed = 0xFFFBBF24.toInt(),
            batteryLow = 0xFFEF4444.toInt()
        )

        fun parseHex(colorStr: String?): Int? {
            if (colorStr.isNullOrBlank()) return null
            return try {
                val clean = colorStr.removePrefix("#")
                if (clean.length == 6) {
                    (0xFF000000 or java.lang.Long.parseLong(clean, 16)).toInt()
                } else {
                    java.lang.Long.parseLong(clean, 16).toInt()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
