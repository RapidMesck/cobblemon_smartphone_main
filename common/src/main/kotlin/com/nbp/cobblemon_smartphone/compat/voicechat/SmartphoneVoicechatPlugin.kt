package com.nbp.cobblemon_smartphone.compat.voicechat

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent

/**
 * Our Simple Voice Chat plugin. This class references SVC types directly, which is safe because
 * *only SVC loads it*: Fabric resolves it through the `voicechat` entrypoint and NeoForge through
 * the `@ForgeVoicechatPlugin` annotation — neither happens when the mod is absent, so the class is
 * never touched and no NoClassDefFoundError is possible.
 *
 * Open so the NeoForge module can subclass it purely to carry the annotation.
 */
open class SmartphoneVoicechatPlugin : VoicechatPlugin {

    override fun getPluginId(): String = CobblemonSmartphone.ID

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(VoicechatServerStartedEvent::class.java) { event ->
            VoiceChatBridge.onServerApiReady(event.voicechat)
        }
        registration.registerEvent(VoicechatServerStoppedEvent::class.java) {
            VoiceChatBridge.onServerStopped()
        }
    }
}
