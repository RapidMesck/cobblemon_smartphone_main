package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.CobblemonSmartphoneFabricNetworkManager
import net.fabricmc.api.ClientModInitializer

class CobblemonSmartphoneFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblemonSmartphoneFabricNetworkManager.registerClientHandlers()
    }
}