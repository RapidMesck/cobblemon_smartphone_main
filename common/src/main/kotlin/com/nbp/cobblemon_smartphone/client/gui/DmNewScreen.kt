package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.social.FakePlayerFilter
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.client.ui.hd.TextInputNode
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Online trainer directory built on [HdPhoneScreen] with instant HD search and DM thread launch.
 */
class DmNewScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null
) : HdPhoneScreen(color, smartphoneStack, Component.literal("Trainers")) {

    private var lastCandidatesUpdate = 0L
    private var cachedCandidates: List<PlayerInfo> = emptyList()

    override val viewportMode: ViewportMode = ViewportMode.Large

    override fun init() {
        super.init()
        lastCandidatesUpdate = 0L
        val st = stateStore.input("trainer_search")
        // Keep focus ready when user clicks input
    }

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
    }

    private fun candidates(): List<PlayerInfo> {
        val now = System.currentTimeMillis()
        if (now - lastCandidatesUpdate < 500L) return cachedCandidates
        lastCandidatesUpdate = now

        val minecraft = Minecraft.getInstance()
        val selfId = minecraft.player?.uuid ?: return emptyList()
        val selfName = minecraft.player?.gameProfile?.name
        val connection = minecraft.connection ?: return emptyList()

        val allPlayers = connection.onlinePlayers
        val hasTabFakePlayers = allPlayers.any { FakePlayerFilter.isFakePlayer(it) }
        val source = if (hasTabFakePlayers) allPlayers else connection.listedOnlinePlayers

        cachedCandidates = source
            .filter {
                it.profile.id != selfId &&
                    (selfName == null || !it.profile.name.equals(selfName, ignoreCase = true)) &&
                    !FakePlayerFilter.isFakePlayer(it)
            }
            .distinctBy { it.profile.id }
            .sortedBy { it.profile.name.lowercase() }
        return cachedCandidates
    }

    private fun filteredCandidates(): List<PlayerInfo> {
        val query = stateStore.getInputText("trainer_search").trim()
        val all = candidates()
        if (query.isEmpty()) return all
        return all.filter { it.profile.name.contains(query, ignoreCase = true) }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        // Floating Island Header
        builder.appHeader(
            title = lang("explore_trainers"),
            icon = HdIconAtlas.Icon.SEARCH,
            iconColor = HdColorSpec.Token("primary")
        )

        // Search input row
        builder.row(
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 5f),
            width = HdSizeSpec.Fill
        ) { sb ->
            sb.textInput(
                inputId = "trainer_search",
                placeholder = lang("search_trainers"),
                maxLength = 32
            ) {
                width = HdSizeSpec.Fill
                fontSizeDp = 9.4f
                padding = HdPadding.symmetric(horizontal = 9f, vertical = 5.5f)
            }
        }

        val players = filteredCandidates()
        builder.scroll(
            scrollId = "dm_new_scroll",
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 2f)
        ) { lb ->
            if (players.isEmpty()) {
                lb.column(
                    gap = 10f,
                    crossAlign = HdAlign.CENTER,
                    mainAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(vertical = 60f),
                    width = HdSizeSpec.Fill
                ) { eb ->
                    eb.column(
                        padding = HdPadding.all(10f),
                        background = HdBackground(color = HdColorSpec.Fixed(0x22FFFFFF), radius = 20f)
                    ) { ib ->
                        ib.icon(HdIconAtlas.Icon.USER, size = 20f, color = HdColorSpec.Token("text_primary"))
                    }
                    eb.text(lang("nobody_online"), size = 10f, weight = HdTextLayout.Weight.MEDIUM, color = HdColorSpec.Token("text_primary"), align = HdAlign.CENTER)
                }
            } else {
                players.forEach { info ->
                    lb.card(
                        padding = HdPadding.symmetric(horizontal = 9f, vertical = 7f),
                        hoverHighlight = true,
                        onClick = {
                            playClickSound()
                            Minecraft.getInstance().setScreen(
                                DmThreadScreen(color, smartphoneStack, info.profile.id, info.profile.name)
                            )
                        }
                    ) { cb ->
                        this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_open_conversation").string
                        cb.row(gap = 9f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { rb ->
                            rb.avatar(info.profile.id, info.profile.name, size = 26f)
                            rb.text(info.profile.name, size = 10.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                                width = HdSizeSpec.Weight(1f)
                            }
                            rb.column(
                                width = HdSizeSpec.Fixed(8f),
                                height = HdSizeSpec.Fixed(8f),
                                background = HdBackground(color = HdColorSpec.Token("success"), radius = 4f)
                            ) {}
                        }
                    }
                }
            }
        }

        SocialComponents.buildBottomNav(builder, "discover") { selected ->
            playClickSound()
            when (selected) {
                "feed" -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
                "discover" -> stateStore.scroll("dm_new_scroll").offset = 0f
                "dms" -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.DMS))
                "alerts" -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.NOTIFICATIONS))
            }
        }
    }
}
