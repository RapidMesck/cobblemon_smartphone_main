package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.social.FakePlayerFilter
import com.nbp.cobblemon_smartphone.client.social.SocialProfileCache
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.social.SocialTrainerSummary
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Trainer directory built on [HdPhoneScreen] supporting both online and offline trainer
 * discovery, instant HD search, profile viewing, and quick DM thread launch.
 */
class DmNewScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null
) : HdPhoneScreen(color, smartphoneStack, Component.literal("Trainers")) {

    private var onlineOnly = false
    private var lastOnlineUpdate = 0L
    private var cachedOnline: List<SocialTrainerSummary> = emptyList()

    override val viewportMode: ViewportMode = ViewportMode.Large

    override fun init() {
        super.init()
        lastOnlineUpdate = 0L
        SocialProfileCache.requestDirectory()
        stateStore.input("trainer_search")
    }

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
    }

    private fun onlineFallbackCandidates(): List<SocialTrainerSummary> {
        val now = System.currentTimeMillis()
        if (now - lastOnlineUpdate < 500L) return cachedOnline
        lastOnlineUpdate = now

        val minecraft = Minecraft.getInstance()
        val selfId = minecraft.player?.uuid ?: return emptyList()
        val selfName = minecraft.player?.gameProfile?.name
        val connection = minecraft.connection ?: return emptyList()

        val allPlayers = connection.onlinePlayers
        val hasTabFakePlayers = allPlayers.any { FakePlayerFilter.isFakePlayer(it) }
        val source = if (hasTabFakePlayers) allPlayers else connection.listedOnlinePlayers

        cachedOnline = source
            .filter {
                it.profile.id != selfId &&
                    (selfName == null || !it.profile.name.equals(selfName, ignoreCase = true)) &&
                    !FakePlayerFilter.isFakePlayer(it)
            }
            .distinctBy { it.profile.id }
            .sortedBy { it.profile.name.lowercase() }
            .map { SocialTrainerSummary(uuid = it.profile.id, name = it.profile.name, isOnline = true) }
        return cachedOnline
    }

    private fun filteredCandidates(): List<SocialTrainerSummary> {
        val serverDir = SocialProfileCache.getDirectory()
        val base = if (serverDir.isNotEmpty()) serverDir else onlineFallbackCandidates()
        val scoped = if (onlineOnly) base.filter { it.isOnline } else base
        val query = stateStore.getInputText("trainer_search").trim()
        if (query.isEmpty()) return scoped
        return scoped.filter {
            it.name.contains(query, ignoreCase = true) || it.status.contains(query, ignoreCase = true)
        }
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        // Floating Island Header
        builder.appHeader(
            title = lang("explore_trainers"),
            icon = HdIconAtlas.Icon.SEARCH,
            iconColor = HdColorSpec.Token("primary")
        )

        // Filter pills (All / Online) + Search input
        builder.column(
            gap = 4f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f),
            width = HdSizeSpec.Fill
        ) { top ->
            top.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { pills ->
                pills.pill(
                    label = lang("directory_all"),
                    icon = HdIconAtlas.Icon.USERS,
                    active = !onlineOnly,
                    onClick = {
                        playClickSound()
                        onlineOnly = false
                        stateStore.scroll("dm_new_scroll").offset = 0f
                    }
                )
                pills.pill(
                    label = lang("online"),
                    icon = HdIconAtlas.Icon.GLOBE,
                    active = onlineOnly,
                    onClick = {
                        playClickSound()
                        onlineOnly = true
                        stateStore.scroll("dm_new_scroll").offset = 0f
                    }
                )
            }
            top.textInput(
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
                val emptyMsg = when {
                    SocialProfileCache.isDirectoryLoading() -> lang("loading")
                    onlineOnly -> lang("nobody_online")
                    else -> lang("no_trainers_found")
                }
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
                    eb.text(emptyMsg, size = 10f, weight = HdTextLayout.Weight.MEDIUM, color = HdColorSpec.Token("text_primary"), align = HdAlign.CENTER)
                }
            } else {
                players.forEach { trainer ->
                    lb.card(
                        padding = HdPadding.symmetric(horizontal = 9f, vertical = 7f),
                        hoverHighlight = true,
                        onClick = {
                            playClickSound()
                            Minecraft.getInstance().setScreen(
                                SocialProfileScreen(
                                    color = color,
                                    smartphoneStack = smartphoneStack,
                                    targetUuid = trainer.uuid,
                                    fallbackName = trainer.name,
                                    returnScreen = { DmNewScreen(color, smartphoneStack) }
                                )
                            )
                        }
                    ) { cb ->
                        this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_view_profile").string
                        cb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { rb ->
                            rb.avatar(trainer.uuid, trainer.name, trainer.skinUrl, size = 26f)
                            rb.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { tb ->
                                tb.row(gap = 4f, crossAlign = HdAlign.CENTER) { nameRow ->
                                    nameRow.text(trainer.name, size = 10.0f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                                    if (trainer.isMutualFollow) {
                                        nameRow.icon(HdIconAtlas.Icon.STAR_FILLED, size = 9f, color = HdColorSpec.Token("content_gold"))
                                    }
                                }
                                tb.text(
                                    trainer.status.ifBlank { if (trainer.isOnline) lang("online") else lang("offline") },
                                    size = 7.8f,
                                    color = HdColorSpec.Token("text_dark_muted")
                                )
                            }
                            val dotColor = if (trainer.isOnline) HdColorSpec.Token("success") else HdColorSpec.Fixed(0xFF94A3B8.toInt())
                            rb.column(
                                width = HdSizeSpec.Fixed(7f),
                                height = HdSizeSpec.Fixed(7f),
                                background = HdBackground(color = dotColor, radius = 3.5f)
                            ) {}
                            rb.button(
                                icon = HdIconAtlas.Icon.MESSAGE,
                                variant = ButtonNode.Variant.SURFACE,
                                radius = 7f
                            ) {
                                padding = HdPadding.all(4.5f)
                                iconSizeDp = 10.5f
                                tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_open_conversation").string
                                onClick = {
                                    playClickSound()
                                    Minecraft.getInstance().setScreen(
                                        DmThreadScreen(color, smartphoneStack, trainer.uuid, trainer.name)
                                    )
                                }
                            }
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
