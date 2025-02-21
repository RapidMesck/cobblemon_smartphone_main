package com.nbp.cobblemon_smartphone

import com.cobblemon.mod.common.NetworkManager

interface Implementation {
    val networkManager: NetworkManager

    fun registerItems()

    fun registerCommands()
}