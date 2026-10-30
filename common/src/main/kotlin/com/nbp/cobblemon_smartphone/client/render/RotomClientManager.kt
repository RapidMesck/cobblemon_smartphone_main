package com.nbp.cobblemon_smartphone.client.render

import com.nbp.cobblemon_smartphone.network.packet.SetSmartphoneOpenStatePacket
import net.minecraft.client.Minecraft
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RotomClientManager {
    private var lastLocalState: Boolean? = null
    private val remoteOpenStates = ConcurrentHashMap<UUID, Boolean>()

    fun isPlayerOpen(uuid: UUID): Boolean {
        return remoteOpenStates[uuid] == true
    }

    fun setRemotePlayerOpen(uuid: UUID, isOpen: Boolean) {
        if (isOpen) {
            remoteOpenStates[uuid] = true
        } else {
            remoteOpenStates.remove(uuid)
        }
    }

    fun updateLocalState(isOpen: Boolean) {
        if (lastLocalState != isOpen) {
            lastLocalState = isOpen
            SetSmartphoneOpenStatePacket(isOpen).sendToServer()
        }
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        val isOpen = RotomPlayerRenderLayer.isSmartphoneScreen(mc.screen)
        updateLocalState(isOpen)
    }
}
