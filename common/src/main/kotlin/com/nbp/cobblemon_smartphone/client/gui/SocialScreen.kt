package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.canDeletePost
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.relativeTime
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialMute
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
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
import com.nbp.cobblemon_smartphone.client.ui.hd.HdUiPreferences
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.ReportContentPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveSocialMutePacket
import com.nbp.cobblemon_smartphone.network.packet.VotePollPacket
import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.social.SocialNotificationPrefs
import com.nbp.cobblemon_smartphone.social.SocialNotificationType
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * The Social feed, DMs directory, and notifications screen built on the HD [HdPhoneScreen] UI engine.
 */
class SocialScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    startOnDms: Boolean = false,
    initialTab: Tab = if (startOnDms) Tab.DMS else Tab.FEED,
    scaleOverride: Float? = null,
    styleOverride: com.nbp.cobblemon_smartphone.client.ui.hd.HdUiStyle? = null,
    initialNotifSubTab: NotificationSubTab = NotificationSubTab.ALERTS
) : HdPhoneScreen(color, smartphoneStack, Component.literal("Social"), scaleOverride, styleOverride) {

    enum class Tab { FEED, DMS, NOTIFICATIONS }
    enum class NotificationSubTab { ALERTS, SETTINGS }

    private var tab = initialTab
    private var notifSubTab = initialNotifSubTab
    private val posableState = FloatingState()

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    override fun init() {
        super.init()
        if (SocialFeedCache.isEmpty()) SocialFeedCache.refresh()
        if (tab == Tab.DMS) SocialDmCache.refreshThreads()
        SocialNotificationCache.refresh()
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        buildHeader(builder)

        builder.scroll(
            scrollId = "social_main_scroll_${tab.name}",
            gap = 8f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 6f),
            onNearBottom = {
                if (tab == Tab.FEED) SocialFeedCache.loadMore()
                else if (tab == Tab.DMS) SocialDmCache.loadMoreThreads()
            }
        ) { sb ->
            when (tab) {
                Tab.FEED -> buildFeedContent(sb, theme)
                Tab.DMS -> buildDmThreadsContent(sb)
                Tab.NOTIFICATIONS -> buildNotificationsContent(sb)
            }
        }

        val activeNavId = when (tab) {
            Tab.FEED -> "feed"
            Tab.DMS -> "dms"
            Tab.NOTIFICATIONS -> "alerts"
        }
        SocialComponents.buildBottomNav(builder, activeNavId) { selected ->
            playClickSound()
            when (selected) {
                "feed" -> switchTab(Tab.FEED)
                "discover" -> Minecraft.getInstance().setScreen(DmNewScreen(color, smartphoneStack))
                "dms" -> switchTab(Tab.DMS)
                "alerts" -> switchTab(Tab.NOTIFICATIONS)
            }
        }
    }

    private fun buildHeader(builder: HdBuilder) {
        builder.column(
            padding = HdPadding.only(left = 7f, right = 7f, top = 6f, bottom = 2f),
            width = HdSizeSpec.Fill
        ) { wrap ->
            wrap.row(
                gap = 5f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 7f, vertical = 5.5f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 12f,
                    borderWidth = 0.85f,
                    borderColor = HdColorSpec.Token("primary_border"),
                    shadowBlur = 6f,
                    shadowOffsetY = 2f,
                    shadowColor = HdColorSpec.Fixed(0x28000000)
                )
            ) { hb ->
                if (tab == Tab.FEED) {
                    val following = SocialFeedCache.followingOnly
                    hb.pill(
                        label = lang("feed_global"),
                        icon = HdIconAtlas.Icon.GLOBE,
                        active = !following,
                        onClick = {
                            playClickSound()
                            SocialFeedCache.setFilter(false)
                            stateStore.scroll("social_main_scroll_FEED").offset = 0f
                        }
                    ) {
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_feed_global").string
                    }
                    hb.pill(
                        label = lang("feed_following"),
                        icon = HdIconAtlas.Icon.USERS,
                        active = following,
                        onClick = {
                            playClickSound()
                            SocialFeedCache.setFilter(true)
                            stateStore.scroll("social_main_scroll_FEED").offset = 0f
                        }
                    ) {
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_feed_following").string
                    }

                    val tag = SocialFeedCache.hashtag
                    if (tag != null) {
                        hb.button("#$tag ×", variant = ButtonNode.Variant.PRIMARY, fontSize = 8.2f, radius = 10f) {
                            padding = HdPadding.symmetric(horizontal = 7f, vertical = 3.5f)
                            tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_clear_hashtag").string
                            onClick = {
                                playClickSound()
                                SocialFeedCache.clearHashtag()
                                stateStore.scroll("social_main_scroll_FEED").offset = 0f
                            }
                        }
                    }
                    hb.spacer(weight = 1f)
                } else if (tab == Tab.NOTIFICATIONS) {
                    hb.pill(
                        label = lang("tab_notifications"),
                        icon = HdIconAtlas.Icon.BELL,
                        active = notifSubTab == NotificationSubTab.ALERTS,
                        onClick = {
                            playClickSound()
                            notifSubTab = NotificationSubTab.ALERTS
                            stateStore.scroll("social_main_scroll_NOTIFICATIONS").offset = 0f
                        }
                    )
                    hb.pill(
                        label = lang("notif_settings_tab"),
                        icon = HdIconAtlas.Icon.SETTINGS,
                        active = notifSubTab == NotificationSubTab.SETTINGS,
                        onClick = {
                            playClickSound()
                            notifSubTab = NotificationSubTab.SETTINGS
                            stateStore.scroll("social_main_scroll_NOTIFICATIONS").offset = 0f
                        }
                    )
                    hb.spacer(weight = 1f)
                } else {
                    val unread = SocialDmCache.unreadTotal
                    val sub = if (unread > 0) "$unread ${lang("new_alerts")}" else "${SocialDmCache.threads().size} threads"
                    hb.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fixed(22f),
                        height = HdSizeSpec.Fixed(22f)
                    ) { icBox ->
                        background = HdBackground(color = HdColorSpec.Token("primary", 0.16f), radius = 11f)
                        icBox.icon(HdIconAtlas.Icon.MESSAGE, size = 12f, color = HdColorSpec.Token("primary"))
                    }
                    hb.column(gap = 0.5f, width = HdSizeSpec.Weight(1f)) { tb ->
                        tb.text(lang("tab_dms"), size = 10.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                        tb.text(sub, size = 7.4f, color = HdColorSpec.Token("text_dark_muted"))
                    }
                }

                // Compose button (+)
                if (tab != Tab.NOTIFICATIONS) {
                    hb.button(
                        icon = HdIconAtlas.Icon.PLUS,
                        variant = ButtonNode.Variant.PRIMARY,
                        radius = 9f
                    ) {
                        padding = HdPadding.all(4.5f)
                        iconSizeDp = 11f
                        tooltip = Component.translatable(
                            "cobblemon_smartphone.tooltip.${if (tab == Tab.FEED) "social_new_post" else "social_new_message"}"
                        ).string
                        onClick = {
                            playClickSound()
                            val next = if (tab == Tab.FEED) {
                                SocialComposeScreen(color, smartphoneStack)
                            } else {
                                DmNewScreen(color, smartphoneStack)
                            }
                            Minecraft.getInstance().setScreen(next)
                        }
                    }
                }
            }
        }
    }

    private fun buildFeedContent(sb: HdBuilder, theme: SmartphoneTheme) {
        val posts = SocialFeedCache.posts()
        if (posts.isEmpty()) {
            val msg = when {
                SocialFeedCache.loading -> lang("loading")
                SocialFeedCache.followingOnly -> lang("empty_following")
                else -> lang("empty")
            }
            buildEmptyState(sb, HdIconAtlas.Icon.FEED, msg)
            return
        }

        posts.forEach { post ->
            SocialComponents.buildPostCard(
                builder = sb,
                post = post,
                colors = theme.colors,
                floatingState = posableState,
                onOpenPost = {
                    playClickSound()
                    Minecraft.getInstance().setScreen(SocialPostDetailScreen(color, smartphoneStack, post))
                },
                onAuthorClick = {
                    val me = Minecraft.getInstance().player
                    if (me == null || post.authorUuid != me.uuid) {
                        playClickSound()
                        Minecraft.getInstance().setScreen(DmThreadScreen(color, smartphoneStack, post.authorUuid, post.authorName))
                    }
                },
                onLikeClick = {
                    playClickSound()
                    val liked = SocialFeedCache.toggleLikeLocally(post.id)
                    if (liked != null) LikePostPacket(post.id, liked).sendToServer()
                },
                onCommentClick = {
                    playClickSound()
                    Minecraft.getInstance().setScreen(SocialPostDetailScreen(color, smartphoneStack, post))
                },
                onDeleteClick = {
                    if (canDeletePost(post)) {
                        playClickSound()
                        DeletePostPacket(post.id).sendToServer()
                    }
                },
                onReportClick = {
                    playClickSound()
                    ReportContentPacket(ReportTargetType.POST, post.id, "Inappropriate content").sendToServer()
                    Minecraft.getInstance().player?.displayClientMessage(
                        Component.translatable("message.nbp.social.report_sent"),
                        false
                    )
                },
                onHashtagClick = { tag ->
                    playClickSound()
                    SocialFeedCache.setHashtag(tag)
                    stateStore.scroll("social_main_scroll_FEED").offset = 0f
                },
                onVotePoll = { optIdx ->
                    playClickSound()
                    VotePollPacket(post.id, optIdx).sendToServer()
                },
                onLoadPhoto = { photoId ->
                    playClickSound()
                    SocialPhotoClient.request(photoId)
                }
            )
        }
    }

    private fun buildDmThreadsContent(sb: HdBuilder) {
        val threads = SocialDmCache.threads()
        if (threads.isEmpty()) {
            buildEmptyState(sb, HdIconAtlas.Icon.MESSAGE, if (SocialDmCache.loading) lang("loading") else lang("no_threads"))
            return
        }

        threads.forEach { thread ->
            sb.card(
                padding = HdPadding.symmetric(horizontal = 9f, vertical = 8f),
                hoverHighlight = true,
                onClick = {
                    playClickSound()
                    Minecraft.getInstance().setScreen(DmThreadScreen(color, smartphoneStack, thread.otherUuid, thread.otherName))
                }
            ) { cb ->
                this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_open_conversation").string
                cb.row(gap = 9f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { rb ->
                    rb.avatar(thread.otherUuid, thread.otherName, thread.skinUrl, size = 28f)
                    rb.column(gap = 2f, width = HdSizeSpec.Weight(1f)) { tb ->
                        tb.text(thread.otherName, size = 10.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                        tb.text(thread.preview, size = 9f, color = HdColorSpec.Token("text_dark_muted")) {
                            width = HdSizeSpec.Fill
                        }
                    }
                    if (thread.unreadCount > 0) {
                        rb.column(
                            padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f),
                            background = HdBackground(color = HdColorSpec.Token("danger"), radius = 8f)
                        ) { bb ->
                            bb.text(thread.unreadCount.toString(), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_primary"))
                        }
                    } else {
                        rb.icon(HdIconAtlas.Icon.MESSAGE, size = 13f, color = HdColorSpec.Token("text_dark_muted"))
                    }
                }
            }
        }
    }

    private fun buildNotificationsContent(sb: HdBuilder) {
        if (notifSubTab == NotificationSubTab.SETTINGS) {
            buildNotificationSettings(sb)
            return
        }

        val notifications = SocialNotificationCache.notifications()
        if (notifications.isEmpty()) {
            buildEmptyState(sb, HdIconAtlas.Icon.BELL, if (SocialNotificationCache.loading) lang("loading") else lang("no_notifications"))
            return
        }

        if (SocialNotificationCache.unreadCount > 0) {
            sb.row(mainAlign = HdAlign.END, width = HdSizeSpec.Fill) { rb ->
                rb.button(lang("mark_all_read"), variant = ButtonNode.Variant.SURFACE, fontSize = 8.4f, radius = 10f) {
                    padding = HdPadding.symmetric(horizontal = 9f, vertical = 4f)
                    onClick = {
                        playClickSound()
                        SocialNotificationCache.markAllRead()
                    }
                }
            }
        }

        notifications.forEach { notif ->
            sb.card(
                padding = HdPadding.symmetric(horizontal = 9f, vertical = 8f),
                hoverHighlight = true,
                onClick = {
                    playClickSound()
                    SocialNotificationCache.markRead(notif.id)
                    when (notif.type) {
                        SocialNotificationType.FOLLOW -> Minecraft.getInstance().setScreen(
                            DmThreadScreen(color, smartphoneStack, notif.actorUuid, notif.actorName)
                        )
                        SocialNotificationType.LIKE,
                        SocialNotificationType.COMMENT,
                        SocialNotificationType.MENTION -> {
                            val target = SocialFeedCache.posts().find { it.id == notif.referenceId }
                            if (target != null) {
                                Minecraft.getInstance().setScreen(SocialPostDetailScreen(color, smartphoneStack, target))
                            } else {
                                Minecraft.getInstance().setScreen(DmThreadScreen(color, smartphoneStack, notif.actorUuid, notif.actorName))
                            }
                        }
                    }
                }
            ) { cb ->
                val tipKey = when (notif.type) {
                    SocialNotificationType.FOLLOW -> "social_open_conversation"
                    else -> "social_view_post"
                }
                this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.$tipKey").string

                cb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { rb ->
                    if (!notif.read) {
                        rb.column(
                            width = HdSizeSpec.Fixed(3f),
                            height = HdSizeSpec.Fixed(26f),
                            background = HdBackground(color = HdColorSpec.Token("primary"), radius = 1.5f)
                        ) {}
                    }
                    rb.stack(hAlign = HdAlign.END, vAlign = HdAlign.END, width = HdSizeSpec.Fixed(30f), height = HdSizeSpec.Fixed(30f)) { sbIcon ->
                        sbIcon.avatar(notif.actorUuid, notif.actorName, notif.skinUrl, size = 27f)
                        val badgeIcon = when (notif.type) {
                            SocialNotificationType.LIKE -> HdIconAtlas.Icon.HEART_FILLED
                            SocialNotificationType.COMMENT -> HdIconAtlas.Icon.MESSAGE
                            SocialNotificationType.FOLLOW -> HdIconAtlas.Icon.USER
                            SocialNotificationType.MENTION -> HdIconAtlas.Icon.BELL
                        }
                        val badgeCol = when (notif.type) {
                            SocialNotificationType.LIKE -> SocialComponents.LIKED_COLOR
                            SocialNotificationType.COMMENT -> HdColorSpec.Token("primary")
                            SocialNotificationType.FOLLOW -> HdColorSpec.Token("content_gold")
                            SocialNotificationType.MENTION -> HdColorSpec.Token("primary")
                        }
                        sbIcon.column(
                            padding = HdPadding.all(2.5f),
                            background = HdBackground(
                                color = HdColorSpec.Token("surface"),
                                radius = 7f,
                                borderWidth = 0.8f,
                                borderColor = HdColorSpec.Token("primary_border")
                            )
                        ) { bb ->
                            bb.icon(badgeIcon, size = 8f, color = badgeCol)
                        }
                    }

                    rb.column(gap = 2f, width = HdSizeSpec.Weight(1f)) { tb ->
                        tb.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { top ->
                            top.text(notif.actorName, size = 10f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                                width = HdSizeSpec.Weight(1f)
                            }
                            top.text(relativeTime(notif.timestamp), size = 8.2f, color = HdColorSpec.Token("text_dark_muted"))
                        }
                        val desc = when (notif.type) {
                            SocialNotificationType.LIKE -> lang("notif_liked_desc")
                            SocialNotificationType.COMMENT -> "\"${notif.message}\""
                            SocialNotificationType.FOLLOW -> lang("notif_followed_desc")
                            SocialNotificationType.MENTION -> "${lang("notif_mentioned_desc")}: \"${notif.message}\""
                        }
                        tb.text(desc, size = 8.8f, color = HdColorSpec.Token("text_dark_muted")) {
                            width = HdSizeSpec.Fill
                        }
                    }
                }
            }
        }
    }

    private fun buildNotificationSettings(sb: HdBuilder) {
        val prefs = SocialMute.prefs
        val save: ((SocialNotificationPrefs) -> SocialNotificationPrefs) -> Unit = { transform ->
            playClickSound()
            val updated = SocialMute.update(transform)
            SaveSocialMutePacket(updated).sendToServer()
        }

        // Section 1: General (Do Not Disturb & Sounds)
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(
                lang("notif_settings_general"),
                size = 8.4f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("primary")
            )
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.BELL,
                iconColor = if (prefs.doNotDisturb) HdColorSpec.Token("danger") else HdColorSpec.Token("primary"),
                title = lang("notif_pref_dnd"),
                subtitle = lang("notif_pref_dnd_desc"),
                checked = prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(doNotDisturb = v) } }
            )
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.SPARKLES,
                iconColor = HdColorSpec.Token("primary"),
                title = lang("notif_pref_sounds"),
                subtitle = lang("notif_pref_sounds_desc"),
                checked = prefs.sounds,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(sounds = v) } }
            )
        }

        // Section 2: Direct Messages & Voice Calls
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(
                lang("notif_settings_comms"),
                size = 8.4f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("primary")
            )
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.MESSAGE,
                iconColor = HdColorSpec.Token("primary"),
                title = lang("notif_pref_dms"),
                subtitle = lang("notif_pref_dms_desc"),
                checked = prefs.dms,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(dms = v) } }
            )
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.PHONE,
                iconColor = HdColorSpec.Token("primary"),
                title = lang("notif_pref_calls"),
                subtitle = lang("notif_pref_calls_desc"),
                checked = prefs.calls,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(calls = v) } }
            )
        }

        // Section 3: Social Interactions
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(
                lang("notif_settings_social"),
                size = 8.4f,
                weight = HdTextLayout.Weight.BOLD,
                color = HdColorSpec.Token("primary")
            )
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.HEART_FILLED,
                iconColor = SocialComponents.LIKED_COLOR,
                title = lang("notif_pref_likes"),
                subtitle = lang("notif_pref_likes_desc"),
                checked = prefs.likes,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(likes = v) } }
            )
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.MESSAGE,
                iconColor = HdColorSpec.Token("primary"),
                title = lang("notif_pref_comments"),
                subtitle = lang("notif_pref_comments_desc"),
                checked = prefs.comments,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(comments = v) } }
            )
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.USER,
                iconColor = HdColorSpec.Token("content_gold"),
                title = lang("notif_pref_follows"),
                subtitle = lang("notif_pref_follows_desc"),
                checked = prefs.follows,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(follows = v) } }
            )
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildNotifToggleRow(
                parent = card,
                icon = HdIconAtlas.Icon.BELL,
                iconColor = HdColorSpec.Token("primary"),
                title = lang("notif_pref_mentions"),
                subtitle = lang("notif_pref_mentions_desc"),
                checked = prefs.mentions,
                enabled = !prefs.doNotDisturb,
                onToggle = { v -> save { it.copy(mentions = v) } }
            )
        }
    }

    private fun buildNotifToggleRow(
        parent: HdBuilder,
        icon: HdIconAtlas.Icon,
        iconColor: HdColorSpec,
        title: String,
        subtitle: String,
        checked: Boolean,
        enabled: Boolean = true,
        onToggle: (Boolean) -> Unit
    ) {
        parent.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
            row.stack(
                hAlign = HdAlign.CENTER,
                vAlign = HdAlign.CENTER,
                width = HdSizeSpec.Fixed(22f),
                height = HdSizeSpec.Fixed(22f)
            ) { ic ->
                background = HdBackground(
                    color = if (enabled) HdColorSpec.Token("primary", 0.13f) else HdColorSpec.Token("surface_alt"),
                    radius = 11f
                )
                ic.icon(icon, size = 11f, color = if (enabled) iconColor else HdColorSpec.Token("text_dark_muted"))
            }
            row.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { col ->
                col.text(
                    title,
                    size = 9.2f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = if (enabled) HdColorSpec.Token("text_dark") else HdColorSpec.Token("text_dark_muted")
                )
                col.text(
                    subtitle,
                    size = 7.4f,
                    color = HdColorSpec.Token("text_dark_muted")
                )
            }
            row.switch(
                checked = checked,
                enabled = enabled,
                onToggle = onToggle
            )
        }
    }

    private fun buildEmptyState(sb: HdBuilder, icon: HdIconAtlas.Icon, message: String) {
        sb.column(
            gap = 10f,
            crossAlign = HdAlign.CENTER,
            mainAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(vertical = 70f),
            width = HdSizeSpec.Fill
        ) { eb ->
            eb.column(
                padding = HdPadding.all(10f),
                background = HdBackground(color = HdColorSpec.Fixed(0x22FFFFFF), radius = 20f)
            ) { ib ->
                ib.icon(icon, size = 20f, color = HdColorSpec.Token("text_primary"))
            }
            eb.text(message, size = 10f, weight = HdTextLayout.Weight.MEDIUM, color = HdColorSpec.Token("text_primary"), align = HdAlign.CENTER)
        }
    }

    private fun switchTab(next: Tab) {
        if (tab == next) return
        tab = next
        if (next == Tab.DMS) SocialDmCache.refreshThreads()
        if (next == Tab.NOTIFICATIONS) SocialNotificationCache.refresh()
    }
}
