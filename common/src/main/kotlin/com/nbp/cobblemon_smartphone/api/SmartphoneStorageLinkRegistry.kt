package com.nbp.cobblemon_smartphone.api

object SmartphoneStorageLinkRegistry {
    private val links = mutableListOf<SmartphoneStorageLink>()

    fun register(link: SmartphoneStorageLink) {
        links.add(link)
    }

    fun getAll(): List<SmartphoneStorageLink> = links
}
