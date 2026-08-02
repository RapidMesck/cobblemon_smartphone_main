package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.client.gui.SocialScreen
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object SocialAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:social"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/social.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/social_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val color = SmartphoneHelper.contextColor ?: return
        SocialFeedCache.refresh()
        Minecraft.getInstance().setScreen(SocialScreen(color, SmartphoneHelper.contextSmartphone))
    }

    /**
     * No upgrade gate on purpose: a social network has network effects. Gating it behind a
     * smithing recipe would fragment the player base and leave the feed empty early on.
     */
    override fun isEnabled(): Boolean = SocialClientSession.capabilities.enabled

    override fun badgeCount(): Int = SocialDmCache.unreadTotal
}
