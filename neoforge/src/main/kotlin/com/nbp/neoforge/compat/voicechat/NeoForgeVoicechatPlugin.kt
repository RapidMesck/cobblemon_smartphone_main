package com.nbp.neoforge.compat.voicechat

import com.nbp.cobblemon_smartphone.compat.voicechat.SmartphoneVoicechatPlugin
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin

/**
 * NeoForge discovers voice chat plugins by annotation, so this subclass exists only to carry it.
 * All behaviour lives in [SmartphoneVoicechatPlugin]; Fabric points its `voicechat` entrypoint at
 * that class directly and needs no equivalent.
 */
@ForgeVoicechatPlugin
class NeoForgeVoicechatPlugin : SmartphoneVoicechatPlugin()
