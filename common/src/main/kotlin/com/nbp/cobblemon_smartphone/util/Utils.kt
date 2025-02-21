package com.nbp.cobblemon_smartphone.util

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.resources.ResourceLocation

fun smartphoneResource(name: String, namespace: String = CobblemonSmartphone.ID): ResourceLocation {
    return ResourceLocation.fromNamespaceAndPath(namespace, name)
}

fun log(message: String) {
    CobblemonSmartphone.LOGGER.info(message)
}
