package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.SaveRotomRadarPacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig

object RotomRadarClientState {
    var config: RotomRadarConfig = RotomRadarConfig()
        private set

    fun updateConfig(newConfig: RotomRadarConfig) {
        config = newConfig
    }

    fun saveConfig() {
        SaveRotomRadarPacket(config).sendToServer()
    }
}
