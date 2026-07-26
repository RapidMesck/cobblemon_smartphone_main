package com.nbp.cobblemon_smartphone.compat.voicechat

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded

/**
 * The single place that knows whether Simple Voice Chat is usable.
 *
 * Nothing here references an SVC class, so this object is always safe to load — including on
 * servers without the mod installed. Anything that *does* touch the API lives in [VoiceChatCalls],
 * which must only be reached after [isAvailable] returns true.
 */
object VoiceChatBridge {
    const val VOICECHAT_MOD_ID = "voicechat"

    /**
     * Set by the platform plugin once SVC hands us its server API. Typed as Any? so this class has
     * no SVC types in its signature; [VoiceChatCalls] casts it.
     */
    @JvmStatic
    var serverApi: Any? = null
        private set

    val isModPresent: Boolean by lazy { isModLoaded(VOICECHAT_MOD_ID) }

    /** True only when the mod is installed AND its API has handed us a server instance. */
    fun isAvailable(): Boolean = isModPresent && serverApi != null

    @JvmStatic
    fun onServerApiReady(api: Any) {
        serverApi = api
        CobblemonSmartphone.LOGGER.info("Simple Voice Chat detected — smartphone calls enabled")
    }

    @JvmStatic
    fun onServerStopped() {
        serverApi = null
    }
}
