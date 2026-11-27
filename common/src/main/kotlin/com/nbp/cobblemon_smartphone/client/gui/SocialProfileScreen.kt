package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.canDeletePost
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.client.social.MutedPlayers
import com.nbp.cobblemon_smartphone.client.social.SocialClientSession
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.social.SocialProfileCache
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColors
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.compat.voicechat.VoiceChatBridge
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.network.packet.ChallengeBattlePacket
import com.nbp.cobblemon_smartphone.network.packet.ChallengeTradePacket
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.MutePlayerPacket
import com.nbp.cobblemon_smartphone.network.packet.PinPostPacket
import com.nbp.cobblemon_smartphone.network.packet.ReportContentPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestProfileListPacket
import com.nbp.cobblemon_smartphone.network.packet.ToggleFollowPacket
import com.nbp.cobblemon_smartphone.network.packet.UpdateProfilePacket
import com.nbp.cobblemon_smartphone.network.packet.VotePollPacket
import com.nbp.cobblemon_smartphone.social.CallStatus
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.social.SocialProfile
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import com.nbp.cobblemon_smartphone.social.SocialTrainerSummary
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Trainer profile screen built on the HD [HdPhoneScreen] UI engine.
 * Supports viewing & editing own profile (bio, partner Pokémon, status, type banner,
 * team showcase, privacy, pinned post) as well as interacting with other trainers.
 */
class SocialProfileScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    private val targetUuid: UUID,
    private val fallbackName: String = "",
    private val returnScreen: (() -> Screen)? = null
) : HdPhoneScreen(color, smartphoneStack, Component.literal("Profile")) {

    enum class ProfileTab { POSTS, PHOTOS, TEAM, FOLLOWERS, FOLLOWING }

    private var activeTab = ProfileTab.POSTS
    private var editing = false
    private var editFavoriteSlot = -1 // -1 no change, 0..5 party slot, -2 clear
    private var editBannerType = ""
    private var editSyncTeamAction = 0 // 0 no change, 1 sync party, -1 clear
    private var editShowTeam = true
    private var editAcceptInteractions = true
    private var editDmMutualOnly = false
    private var expandedTeamIndex: Int? = null

    private val partnerModelState = FloatingState()
    private val postModelState = FloatingState()
    private val teamModelState = FloatingState()

    private val isOwnProfile: Boolean
        get() = Minecraft.getInstance().player?.uuid == targetUuid

    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    override fun init() {
        super.init()
        SocialProfileCache.request(targetUuid)
    }

    override fun onBackPressed() {
        if (editing) {
            editing = false
            return
        }
        val prev = returnScreen?.invoke()
        Minecraft.getInstance().setScreen(prev ?: SocialScreen(color, smartphoneStack))
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        val profile = SocialProfileCache.getProfile(targetUuid)
        val displayName = profile?.name?.ifBlank { fallbackName } ?: fallbackName.ifBlank { lang("profile") }

        builder.appHeader(
            title = if (isOwnProfile) lang("my_profile") else displayName,
            subtitle = if (editing) lang("edit_profile") else (profile?.status?.ifBlank { lang("trainer_role") } ?: lang("profile")),
            icon = HdIconAtlas.Icon.USER,
            iconColor = HdColorSpec.Token("primary")
        ) { hb ->
            if (isOwnProfile && profile != null) {
                if (editing) {
                    hb.button(
                        label = lang("save_bio"),
                        icon = HdIconAtlas.Icon.CHECK,
                        variant = ButtonNode.Variant.PRIMARY,
                        fontSize = 8.2f,
                        radius = 8f
                    ) {
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
                        iconSizeDp = 10f
                        onClick = { saveProfile() }
                    }
                } else {
                    hb.button(
                        icon = HdIconAtlas.Icon.EDIT,
                        variant = ButtonNode.Variant.SURFACE,
                        radius = 8f
                    ) {
                        padding = HdPadding.all(4.5f)
                        iconSizeDp = 11f
                        tooltip = lang("edit_profile")
                        onClick = {
                            playClickSound()
                            enterEditMode(profile)
                        }
                    }
                }
            }
        }

        builder.scroll(
            scrollId = "profile_scroll_${targetUuid}_${editing}_${activeTab.name}",
            gap = 7f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 6f)
        ) { sb ->
            if (profile == null) {
                buildEmptyState(sb, HdIconAtlas.Icon.USER, lang("loading"))
                return@scroll
            }

            if (editing && isOwnProfile) {
                buildEditForm(sb, profile, theme)
            } else {
                buildTrainerCard(sb, profile, theme)
                buildTabBar(sb, profile)
                when (activeTab) {
                    ProfileTab.POSTS -> buildPostsTab(sb, profile, theme, photosOnly = false)
                    ProfileTab.PHOTOS -> buildPostsTab(sb, profile, theme, photosOnly = true)
                    ProfileTab.TEAM -> buildTeamAndStatsTab(sb, profile, theme)
                    ProfileTab.FOLLOWERS -> buildTrainerListTab(sb, RequestProfileListPacket.ListType.FOLLOWERS)
                    ProfileTab.FOLLOWING -> buildTrainerListTab(sb, RequestProfileListPacket.ListType.FOLLOWING)
                }
            }
        }

        SocialComponents.buildBottomNav(builder, "") { selected ->
            playClickSound()
            when (selected) {
                "feed" -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
                "discover" -> Minecraft.getInstance().setScreen(DmNewScreen(color, smartphoneStack))
                "dms" -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.DMS))
                "alerts" -> Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.NOTIFICATIONS))
            }
        }
    }

    private fun buildTrainerCard(sb: HdBuilder, profile: SocialProfileView, theme: SmartphoneTheme) {
        val bannerColor = if (profile.bannerType.isNotBlank()) {
            PokeInfoDetailScreen.typeColor(profile.bannerType)
        } else {
            theme.colors.primary
        }

        sb.card(gap = 7f, padding = HdPadding.all(0f), radius = 12f) { card ->
            // Colored Type Banner Header Strip
            card.row(
                gap = 6f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 10f, vertical = 6f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Fixed(bannerColor),
                    radius = 11f
                )
            ) { banner ->
                banner.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Weight(1f)) { left ->
                    val statusColor = if (profile.isOnline) HdColorSpec.Token("success") else HdColorSpec.Fixed(0xFF94A3B8.toInt())
                    left.column(
                        width = HdSizeSpec.Fixed(7f),
                        height = HdSizeSpec.Fixed(7f),
                        background = HdBackground(color = statusColor, radius = 3.5f)
                    ) {}
                    left.text(
                        if (profile.isOnline) lang("online") else lang("offline"),
                        size = 7.8f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Fixed(0xFFFFFFFF.toInt())
                    )
                    if (profile.isMutualFollow && !isOwnProfile) {
                        left.row(
                            gap = 2f,
                            crossAlign = HdAlign.CENTER,
                            padding = HdPadding.symmetric(horizontal = 5f, vertical = 1f),
                            background = HdBackground(color = HdColorSpec.Fixed(0x38000000), radius = 6f)
                        ) { mb ->
                            mb.icon(HdIconAtlas.Icon.STAR_FILLED, size = 7.5f, color = HdColorSpec.Token("gold"))
                            mb.text(lang("mutual_badge"), size = 7.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Fixed(0xFFFFFFFF.toInt()))
                        }
                    }
                }
                if (profile.bannerType.isNotBlank()) {
                    banner.row(
                        padding = HdPadding.symmetric(horizontal = 5f, vertical = 1.5f),
                        background = HdBackground(color = HdColorSpec.Fixed(0x38000000), radius = 5f)
                    ) { tb ->
                        tb.text(
                            Component.translatable("cobblemon.type.${profile.bannerType.lowercase()}").string,
                            size = 7.2f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = HdColorSpec.Fixed(0xFFFFFFFF.toInt())
                        )
                    }
                }
            }

            // Main profile body inside card
            card.column(gap = 7f, padding = HdPadding.only(left = 10f, right = 10f, top = 2f, bottom = 10f), width = HdSizeSpec.Fill) { body ->
                body.row(gap = 9f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { topRow ->
                    topRow.avatar(profile.uuid, profile.name, profile.skinUrl, size = 38f)

                    topRow.column(gap = 2f, width = HdSizeSpec.Weight(1f)) { info ->
                        info.text(
                            profile.name.ifBlank { fallbackName.ifBlank { lang("profile") } },
                            size = 11.2f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = HdColorSpec.Token("text_dark")
                        )
                        info.text(
                            profile.status.ifBlank { lang("trainer_role") },
                            size = 8.4f,
                            weight = HdTextLayout.Weight.MEDIUM,
                            color = HdColorSpec.Token("primary")
                        )
                    }

                    // 3D Partner Pokémon showcase
                    profile.favoriteSpecies?.let { species ->
                        topRow.stack(
                            hAlign = HdAlign.CENTER,
                            vAlign = HdAlign.CENTER,
                            width = HdSizeSpec.Fixed(48f),
                            height = HdSizeSpec.Fixed(48f)
                        ) { pbox ->
                            this.background = HdBackground(
                                color = HdColorSpec.Fixed(HdColors.mix(theme.colors.headerNavy, bannerColor, 0.38f)),
                                radius = 9f,
                                borderWidth = 0.8f,
                                borderColor = HdColorSpec.Fixed(0x44FFFFFF)
                            )
                            this.tooltip = "${lang("favorite_pokemon")}: ${SocialComponents.speciesName(species)}"
                            pbox.pokemonView(
                                speciesId = species,
                                aspects = profile.favoriteAspects,
                                size = 44f,
                                floatingState = partnerModelState
                            )
                        }
                    }
                }

                // Bio
                body.text(
                    text = profile.bio.ifBlank { lang("no_bio") },
                    size = 9.0f,
                    color = if (profile.bio.isBlank()) HdColorSpec.Token("text_dark_muted") else HdColorSpec.Token("text_dark"),
                    wrap = true
                ) {
                    width = HdSizeSpec.Fill
                }

                // Clickable counters row
                body.row(
                    gap = 4f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(horizontal = 4f, vertical = 5f),
                    width = HdSizeSpec.Fill,
                    background = HdBackground(
                        color = HdColorSpec.Token("surface_alt"),
                        radius = 8f,
                        borderWidth = 0.7f,
                        borderColor = HdColorSpec.Token("primary_border", 0.5f)
                    )
                ) { stats ->
                    buildCounterChip(stats, profile.postCount.toString(), lang("posts_label"), activeTab == ProfileTab.POSTS) {
                        switchTab(ProfileTab.POSTS)
                    }
                    buildCounterChip(stats, profile.followerCount.toString(), lang("followers_label"), activeTab == ProfileTab.FOLLOWERS) {
                        switchTab(ProfileTab.FOLLOWERS)
                    }
                    buildCounterChip(stats, profile.followingCount.toString(), lang("following_label"), activeTab == ProfileTab.FOLLOWING) {
                        switchTab(ProfileTab.FOLLOWING)
                    }
                }

                // Action buttons
                if (isOwnProfile) {
                    body.button(
                        label = lang("edit_profile"),
                        icon = HdIconAtlas.Icon.EDIT,
                        variant = ButtonNode.Variant.PRIMARY,
                        fontSize = 8.8f,
                        radius = 8f
                    ) {
                        width = HdSizeSpec.Fill
                        padding = HdPadding.symmetric(horizontal = 10f, vertical = 5f)
                        iconSizeDp = 10.5f
                        onClick = {
                            playClickSound()
                            enterEditMode(profile)
                        }
                    }
                } else {
                    buildOtherPlayerActions(body, profile)
                }
            }
        }
    }

    private fun buildCounterChip(
        parent: HdBuilder,
        value: String,
        label: String,
        active: Boolean,
        onClick: () -> Unit
    ) {
        parent.column(
            gap = 1f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 4f, vertical = 2f),
            width = HdSizeSpec.Weight(1f),
            background = if (active) HdBackground(color = HdColorSpec.Token("primary", 0.16f), radius = 6f) else null
        ) { col ->
            this.onClick = {
                playClickSound()
                onClick()
            }
            col.text(value, size = 10.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
            col.text(label, size = 7.4f, color = HdColorSpec.Token("text_dark_muted"))
        }
    }

    private fun buildOtherPlayerActions(body: HdBuilder, profile: SocialProfileView) {
        val isFollowed = profile.isFollowedByMe
        val isMutual = profile.isMutualFollow
        val caps = SocialClientSession.capabilities

        // Row 1: Follow + DM + Call + Mute
        body.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row1 ->
            val followLabel = if (isFollowed) "✓ ${lang("feed_following")}" else "+ ${lang("follow")}"
            row1.button(
                label = followLabel,
                variant = if (isFollowed) ButtonNode.Variant.SURFACE else ButtonNode.Variant.PRIMARY,
                fontSize = 8.4f,
                radius = 8f
            ) {
                width = HdSizeSpec.Weight(1f)
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
                tooltip = Component.translatable(
                    if (isFollowed) "cobblemon_smartphone.tooltip.social_unfollow" else "cobblemon_smartphone.tooltip.social_follow"
                ).string
                onClick = {
                    playClickSound()
                    ToggleFollowPacket(targetUuid).sendToServer()
                }
            }

            val dmAllowed = !profile.dmMutualOnly || isMutual
            row1.button(
                label = lang("tab_dms"),
                icon = HdIconAtlas.Icon.MESSAGE,
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 8.4f,
                radius = 8f,
                enabled = dmAllowed
            ) {
                width = HdSizeSpec.Weight(1f)
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
                iconSizeDp = 10f
                tooltip = if (dmAllowed) {
                    Component.translatable("cobblemon_smartphone.tooltip.social_open_conversation").string
                } else {
                    lang("dm_mutual_only_hint")
                }
                onClick = {
                    playClickSound()
                    Minecraft.getInstance().setScreen(
                        DmThreadScreen(color, smartphoneStack, profile.uuid, profile.name)
                    )
                }
            }

            if (callAvailable()) {
                val activeCall = CallState.isBusyWith(targetUuid)
                row1.button(
                    icon = HdIconAtlas.Icon.PHONE,
                    variant = if (activeCall) ButtonNode.Variant.DANGER else ButtonNode.Variant.SURFACE,
                    radius = 8f
                ) {
                    padding = HdPadding.all(4.5f)
                    iconSizeDp = 11f
                    val callTipKey = if (activeCall || (CallState.status == CallStatus.OUTGOING && CallState.otherUuid == targetUuid)) {
                        "social_call_end"
                    } else {
                        "social_call_start"
                    }
                    tooltip = Component.translatable("cobblemon_smartphone.tooltip.$callTipKey").string
                    onClick = {
                        playClickSound()
                        when {
                            CallState.isBusyWith(targetUuid) ->
                                CallActionPacket(CallActionPacket.Action.HANGUP, targetUuid).sendToServer()
                            CallState.isIdle() ->
                                CallActionPacket(CallActionPacket.Action.START, targetUuid).sendToServer()
                        }
                    }
                }
            }

            val muted = MutedPlayers.contains(targetUuid)
            row1.button(
                icon = HdIconAtlas.Icon.BELL,
                variant = if (muted) ButtonNode.Variant.DANGER else ButtonNode.Variant.SURFACE,
                radius = 8f
            ) {
                padding = HdPadding.all(4.5f)
                iconSizeDp = 11f
                tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_mute_player").string
                onClick = {
                    playClickSound()
                    MutePlayerPacket(targetUuid, MutedPlayers.toggle(targetUuid)).sendToServer()
                }
            }
        }

        // Row 2: Battle & Trade (when mutual follow + enabled)
        val canBattle = isMutual && caps.battleEnabled && profile.acceptInteractions
        val canTrade = isMutual && caps.tradeEnabled && profile.acceptInteractions
        if (canBattle || canTrade) {
            body.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row2 ->
                if (canBattle) {
                    row2.button(
                        label = lang("battle_action"),
                        icon = HdIconAtlas.Icon.SWORDS,
                        variant = ButtonNode.Variant.SURFACE,
                        fontSize = 8.4f,
                        radius = 8f
                    ) {
                        width = HdSizeSpec.Weight(1f)
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
                        iconSizeDp = 10f
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_battle_challenge").string
                        onClick = {
                            playClickSound()
                            ChallengeBattlePacket(targetUuid).sendToServer()
                        }
                    }
                }
                if (canTrade) {
                    row2.button(
                        label = lang("trade_action"),
                        icon = HdIconAtlas.Icon.TRADE,
                        variant = ButtonNode.Variant.SURFACE,
                        fontSize = 8.4f,
                        radius = 8f
                    ) {
                        width = HdSizeSpec.Weight(1f)
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
                        iconSizeDp = 10f
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_trade_challenge").string
                        onClick = {
                            playClickSound()
                            ChallengeTradePacket(targetUuid).sendToServer()
                        }
                    }
                }
            }
        } else if (isMutual && !profile.acceptInteractions && (caps.battleEnabled || caps.tradeEnabled)) {
            body.text(
                lang("interactions_private_hint"),
                size = 7.6f,
                color = HdColorSpec.Token("text_dark_muted"),
                align = HdAlign.CENTER
            ) {
                width = HdSizeSpec.Fill
            }
        }
    }

    private fun buildTabBar(sb: HdBuilder, profile: SocialProfileView) {
        sb.row(
            gap = 4f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(horizontal = 4f, vertical = 4f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface"),
                radius = 10f,
                borderWidth = 0.8f,
                borderColor = HdColorSpec.Token("primary_border")
            )
        ) { tb ->
            tb.pill(
                label = lang("posts_label"),
                icon = HdIconAtlas.Icon.FEED,
                active = activeTab == ProfileTab.POSTS,
                onClick = { playClickSound(); switchTab(ProfileTab.POSTS) }
            )
            tb.pill(
                label = lang("photos_tab"),
                icon = HdIconAtlas.Icon.IMAGE,
                active = activeTab == ProfileTab.PHOTOS,
                onClick = { playClickSound(); switchTab(ProfileTab.PHOTOS) }
            )
            tb.pill(
                label = lang("team_stats_tab"),
                icon = HdIconAtlas.Icon.SPARKLES,
                active = activeTab == ProfileTab.TEAM,
                onClick = { playClickSound(); switchTab(ProfileTab.TEAM) }
            )
        }
    }

    private fun buildPostsTab(sb: HdBuilder, profile: SocialProfileView, theme: SmartphoneTheme, photosOnly: Boolean) {
        val allPosts = SocialProfileCache.getPosts(targetUuid)
        val posts = if (photosOnly) allPosts.filter { it.photo != null } else allPosts

        if (posts.isEmpty()) {
            val msg = if (photosOnly) lang("no_photos") else lang("no_posts_profile")
            buildEmptyState(sb, if (photosOnly) HdIconAtlas.Icon.IMAGE else HdIconAtlas.Icon.FEED, msg)
            return
        }

        posts.forEach { post ->
            val isPinned = !photosOnly && profile.pinnedPostId > 0L && post.id == profile.pinnedPostId
            SocialComponents.buildPostCard(
                builder = sb,
                post = post,
                colors = theme.colors,
                floatingState = postModelState,
                onOpenPost = {
                    playClickSound()
                    Minecraft.getInstance().setScreen(SocialPostDetailScreen(color, smartphoneStack, post))
                },
                onAuthorClick = null,
                onLikeClick = {
                    playClickSound()
                    SocialFeedCache.toggleLikeLocally(post.id)
                    val liked = SocialProfileCache.toggleLikeLocally(targetUuid, post.id) ?: !post.likedByMe
                    LikePostPacket(post.id, liked).sendToServer()
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
                    Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack, initialTab = SocialScreen.Tab.FEED))
                },
                onVotePoll = { optIdx ->
                    playClickSound()
                    VotePollPacket(post.id, optIdx).sendToServer()
                },
                onLoadPhoto = { photoId ->
                    playClickSound()
                    SocialPhotoClient.request(photoId)
                },
                isPinned = isPinned,
                onPinClick = if (isOwnProfile) {
                    {
                        playClickSound()
                        PinPostPacket(post.id).sendToServer()
                    }
                } else null
            )
        }
    }

    private fun buildTeamAndStatsTab(sb: HdBuilder, profile: SocialProfileView, theme: SmartphoneTheme) {
        // 1. Trainer Stats Card
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hdr ->
                hdr.icon(HdIconAtlas.Icon.SPARKLES, size = 11f, color = HdColorSpec.Token("primary"))
                hdr.text(lang("trainer_stats"), size = 9.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
            }

            card.grid(columns = 2, colGap = 6f, rowGap = 6f, width = HdSizeSpec.Fill) { grid ->
                buildStatBox(grid, lang("stat_pokedex"), "${profile.pokedexCaught} / ${profile.pokedexSeen}", HdColorSpec.Token("primary"))
                buildStatBox(grid, lang("stat_battles"), "${profile.battleWins}W - ${profile.battleLosses}L", HdColorSpec.Fixed(0xFFE34B4B.toInt()))
                buildStatBox(grid, lang("stat_trades"), profile.tradesCompleted.toString(), HdColorSpec.Fixed(0xFF2EAD5B.toInt()))
                buildStatBox(grid, lang("stat_shinies"), "★ ${profile.shiniesCaught}  ✦ ${profile.legendariesCaught}", HdColorSpec.Token("content_gold"))
            }
        }

        // 2. Team Showcase Card
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hdr ->
                hdr.icon(HdIconAtlas.Icon.STAR_FILLED, size = 11f, color = HdColorSpec.Token("content_gold"))
                hdr.text(lang("team_showcase"), size = 9.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                    width = HdSizeSpec.Weight(1f)
                }
                if (isOwnProfile) {
                    hdr.button(
                        label = lang("sync_party"),
                        icon = HdIconAtlas.Icon.REFRESH,
                        variant = ButtonNode.Variant.PRIMARY,
                        fontSize = 7.6f,
                        radius = 7f
                    ) {
                        padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f)
                        iconSizeDp = 9f
                        onClick = {
                            playClickSound()
                            UpdateProfilePacket(
                                bio = profile.bio,
                                favoritePartySlot = -1,
                                status = profile.status,
                                bannerType = profile.bannerType,
                                syncTeamAction = 1,
                                showTeam = profile.showTeam,
                                acceptInteractions = profile.acceptInteractions,
                                dmMutualOnly = profile.dmMutualOnly
                            ).sendToServer()
                        }
                    }
                }
            }

            if (!isOwnProfile && !profile.showTeam) {
                card.text(
                    lang("team_private"),
                    size = 8.6f,
                    color = HdColorSpec.Token("text_dark_muted"),
                    align = HdAlign.CENTER
                ) {
                    width = HdSizeSpec.Fill
                }
            } else if (profile.teamShowcase.isEmpty()) {
                card.text(
                    lang("team_empty"),
                    size = 8.6f,
                    color = HdColorSpec.Token("text_dark_muted"),
                    align = HdAlign.CENTER
                ) {
                    width = HdSizeSpec.Fill
                }
            } else {
                // 3-column mini showcase tiles; clicking expands full stats box below
                card.grid(columns = 3, colGap = 5f, rowGap = 5f, width = HdSizeSpec.Fill) { grid ->
                    profile.teamShowcase.forEachIndexed { idx, mon ->
                        val selected = expandedTeamIndex == idx
                        grid.column(
                            gap = 2f,
                            crossAlign = HdAlign.CENTER,
                            padding = HdPadding.all(5f),
                            width = HdSizeSpec.Fill,
                            background = HdBackground(
                                color = if (selected) HdColorSpec.Token("primary", 0.16f) else HdColorSpec.Token("surface_alt"),
                                radius = 8f,
                                borderWidth = if (selected) 1.1f else 0.7f,
                                borderColor = if (selected) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border")
                            )
                        ) { tile ->
                            this.onClick = {
                                playClickSound()
                                expandedTeamIndex = if (selected) null else idx
                            }
                            tile.pokemonView(
                                speciesId = mon.species,
                                aspects = mon.aspects,
                                size = 36f,
                                floatingState = teamModelState
                            )
                            tile.text(
                                mon.nickname ?: SocialComponents.speciesName(mon.species),
                                size = 7.6f,
                                weight = HdTextLayout.Weight.BOLD,
                                color = HdColorSpec.Token("text_dark"),
                                align = HdAlign.CENTER
                            ) {
                                width = HdSizeSpec.Fill
                            }
                            tile.text(
                                "Lv.${mon.level}",
                                size = 7.0f,
                                color = HdColorSpec.Token("text_dark_muted")
                            )
                        }
                    }
                }

                // Expanded detail box for selected team Pokémon
                expandedTeamIndex?.let { idx ->
                    profile.teamShowcase.getOrNull(idx)?.let { selectedMon ->
                        SocialComponents.buildPokemonAttachmentBox(
                            builder = card,
                            attachment = selectedMon,
                            colors = theme.colors,
                            floatingState = teamModelState,
                            compact = true
                        )
                    }
                }
            }
        }
    }

    private fun buildStatBox(parent: HdBuilder, label: String, value: String, accent: HdColorSpec) {
        parent.column(
            gap = 2f,
            padding = HdPadding.symmetric(horizontal = 8f, vertical = 6f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface_alt"),
                radius = 8f,
                borderWidth = 0.7f,
                borderColor = HdColorSpec.Token("primary_border", 0.5f)
            )
        ) { box ->
            box.text(label, size = 7.4f, color = HdColorSpec.Token("text_dark_muted"))
            box.text(value, size = 9.8f, weight = HdTextLayout.Weight.BOLD, color = accent)
        }
    }

    private fun buildTrainerListTab(sb: HdBuilder, listType: RequestProfileListPacket.ListType) {
        val list = SocialProfileCache.getList(targetUuid, listType)
        if (list.isEmpty()) {
            val loading = SocialProfileCache.isListLoading(targetUuid, listType)
            val msg = if (loading) lang("loading") else lang(
                if (listType == RequestProfileListPacket.ListType.FOLLOWERS) "no_followers" else "no_following"
            )
            buildEmptyState(sb, HdIconAtlas.Icon.USERS, msg)
            return
        }

        list.forEach { trainer ->
            buildTrainerSummaryCard(sb, trainer)
        }
    }

    private fun buildTrainerSummaryCard(sb: HdBuilder, trainer: SocialTrainerSummary) {
        sb.card(
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
                        returnScreen = { SocialProfileScreen(color, smartphoneStack, targetUuid, fallbackName, returnScreen) }
                    )
                )
            }
        ) { cb ->
            this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_view_profile").string
            cb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { rb ->
                rb.avatar(trainer.uuid, trainer.name, trainer.skinUrl, size = 26f)
                rb.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { tb ->
                    tb.text(trainer.name, size = 10f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                    tb.text(
                        trainer.status.ifBlank { lang("trainer_role") },
                        size = 7.8f,
                        color = HdColorSpec.Token("text_dark_muted")
                    )
                }
                if (trainer.isMutualFollow) {
                    rb.icon(HdIconAtlas.Icon.STAR_FILLED, size = 10f, color = HdColorSpec.Token("content_gold"))
                }
                val dotColor = if (trainer.isOnline) HdColorSpec.Token("success") else HdColorSpec.Fixed(0xFF94A3B8.toInt())
                rb.column(
                    width = HdSizeSpec.Fixed(7f),
                    height = HdSizeSpec.Fixed(7f),
                    background = HdBackground(color = dotColor, radius = 3.5f)
                ) {}
            }
        }
    }

    private fun buildEditForm(sb: HdBuilder, profile: SocialProfileView, theme: SmartphoneTheme) {
        // 1. Bio & Status
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(lang("edit_bio"), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("primary"))
            card.textInput(
                inputId = BIO_INPUT_ID,
                placeholder = lang("bio_placeholder"),
                maxLength = SocialProfile.MAX_BIO_LENGTH
            ) {
                width = HdSizeSpec.Fill
                fontSizeDp = 9.0f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 5.5f)
            }

            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))

            card.text(lang("trainer_status"), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("primary"))
            card.textInput(
                inputId = STATUS_INPUT_ID,
                placeholder = lang("status_placeholder"),
                maxLength = SocialProfile.MAX_STATUS_LENGTH
            ) {
                width = HdSizeSpec.Fill
                fontSizeDp = 9.0f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 5.5f)
            }

            card.flowRow(hGap = 4f, vGap = 4f, width = HdSizeSpec.Fill) { presets ->
                STATUS_PRESET_KEYS.forEach { key ->
                    val presetText = lang(key)
                    presets.button(presetText, variant = ButtonNode.Variant.SURFACE, fontSize = 7.4f, radius = 6f) {
                        padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f)
                        onClick = {
                            playClickSound()
                            stateStore.input(STATUS_INPUT_ID).apply {
                                text = presetText.take(SocialProfile.MAX_STATUS_LENGTH)
                                cursor = text.length
                            }
                        }
                    }
                }
            }
        }

        // 2. Profile Banner Color (Pokémon Type)
        sb.card(gap = 6f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(lang("banner_theme"), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("primary"))
            card.flowRow(hGap = 4f, vGap = 4f, width = HdSizeSpec.Fill) { flow ->
                val defaultSelected = editBannerType.isBlank()
                flow.row(
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f),
                    background = HdBackground(
                        color = HdColorSpec.Token("primary"),
                        radius = 6f,
                        borderWidth = if (defaultSelected) 1.4f else 0.5f,
                        borderColor = if (defaultSelected) HdColorSpec.Fixed(0xFFFFFFFF.toInt()) else HdColorSpec.Fixed(0x33000000)
                    )
                ) { chip ->
                    this.onClick = {
                        playClickSound()
                        editBannerType = ""
                    }
                    chip.text(lang("banner_default"), size = 7.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Fixed(0xFFFFFFFF.toInt()))
                }

                BANNER_TYPES.forEach { type ->
                    val selected = editBannerType.equals(type, ignoreCase = true)
                    flow.row(
                        padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f),
                        background = HdBackground(
                            color = HdColorSpec.Fixed(PokeInfoDetailScreen.typeColor(type)),
                            radius = 6f,
                            borderWidth = if (selected) 1.4f else 0.5f,
                            borderColor = if (selected) HdColorSpec.Fixed(0xFFFFFFFF.toInt()) else HdColorSpec.Fixed(0x33000000)
                        )
                    ) { chip ->
                        this.onClick = {
                            playClickSound()
                            editBannerType = type
                        }
                        chip.text(
                            Component.translatable("cobblemon.type.$type").string,
                            size = 7.2f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = HdColorSpec.Fixed(0xFFFFFFFF.toInt())
                        )
                    }
                }
            }
        }

        // 3. Partner Pokémon Picker (from Party)
        sb.card(gap = 6f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hdr ->
                hdr.text(lang("favorite_pokemon"), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("primary")) {
                    width = HdSizeSpec.Weight(1f)
                }
                hdr.button(
                    label = lang("clear_partner"),
                    variant = if (editFavoriteSlot == -2) ButtonNode.Variant.DANGER else ButtonNode.Variant.SURFACE,
                    fontSize = 7.4f,
                    radius = 6f
                ) {
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f)
                    onClick = {
                        playClickSound()
                        editFavoriteSlot = -2
                    }
                }
            }

            val slots = CobblemonClient.storage.party.slots
            card.grid(columns = 3, colGap = 5f, rowGap = 5f, width = HdSizeSpec.Fill) { grid ->
                for (i in 0 until 6) {
                    val mon = slots.getOrNull(i)
                    val selected = editFavoriteSlot == i
                    grid.column(
                        gap = 2f,
                        crossAlign = HdAlign.CENTER,
                        mainAlign = HdAlign.CENTER,
                        padding = HdPadding.all(5f),
                        width = HdSizeSpec.Fill,
                        background = HdBackground(
                            color = if (selected) HdColorSpec.Token("primary", 0.18f) else HdColorSpec.Token("surface_alt"),
                            radius = 8f,
                            borderWidth = if (selected) 1.2f else 0.7f,
                            borderColor = if (selected) HdColorSpec.Token("primary") else HdColorSpec.Token("primary_border")
                        )
                    ) { tile ->
                        if (mon != null) {
                            this.onClick = {
                                playClickSound()
                                editFavoriteSlot = i
                            }
                            tile.pokemonView(
                                speciesId = mon.species.resourceIdentifier.toString(),
                                aspects = mon.aspects,
                                size = 34f,
                                floatingState = partnerModelState
                            )
                            tile.text(
                                mon.nickname?.string ?: mon.species.name,
                                size = 7.4f,
                                weight = HdTextLayout.Weight.BOLD,
                                color = HdColorSpec.Token("text_dark"),
                                align = HdAlign.CENTER
                            ) {
                                width = HdSizeSpec.Fill
                            }
                        } else {
                            tile.text("-", size = 9f, color = HdColorSpec.Token("text_dark_muted"))
                        }
                    }
                }
            }
        }

        // 4. Team Showcase Sync
        sb.card(gap = 6f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(lang("team_showcase"), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("primary"))
            card.row(gap = 6f, width = HdSizeSpec.Fill) { row ->
                row.button(
                    label = lang("sync_party"),
                    icon = HdIconAtlas.Icon.REFRESH,
                    variant = if (editSyncTeamAction == 1) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SURFACE,
                    fontSize = 8.0f,
                    radius = 7f
                ) {
                    width = HdSizeSpec.Weight(1f)
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f)
                    iconSizeDp = 9.5f
                    onClick = {
                        playClickSound()
                        editSyncTeamAction = if (editSyncTeamAction == 1) 0 else 1
                    }
                }
                row.button(
                    label = lang("clear_team"),
                    icon = HdIconAtlas.Icon.TRASH,
                    variant = if (editSyncTeamAction == -1) ButtonNode.Variant.DANGER else ButtonNode.Variant.SURFACE,
                    fontSize = 8.0f,
                    radius = 7f
                ) {
                    width = HdSizeSpec.Weight(1f)
                    padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f)
                    iconSizeDp = 9.5f
                    onClick = {
                        playClickSound()
                        editSyncTeamAction = if (editSyncTeamAction == -1) 0 else -1
                    }
                }
            }
        }

        // 5. Privacy Controls
        sb.card(gap = 7f, padding = HdPadding.all(10f), radius = 11f) { card ->
            card.text(lang("privacy_section"), size = 8.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("primary"))
            buildPrivacyToggle(card, lang("privacy_show_team"), lang("privacy_show_team_desc"), editShowTeam) {
                playClickSound()
                editShowTeam = it
            }
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildPrivacyToggle(card, lang("privacy_interactions"), lang("privacy_interactions_desc"), editAcceptInteractions) {
                playClickSound()
                editAcceptInteractions = it
            }
            card.divider(thickness = 0.6f, color = HdColorSpec.Token("primary_border", 0.35f))
            buildPrivacyToggle(card, lang("privacy_dm_mutual"), lang("privacy_dm_mutual_desc"), editDmMutualOnly) {
                playClickSound()
                editDmMutualOnly = it
            }
        }

        // Save / Cancel Row
        sb.row(gap = 6f, width = HdSizeSpec.Fill) { actions ->
            actions.button(
                label = lang("cancel"),
                variant = ButtonNode.Variant.SURFACE,
                fontSize = 8.8f,
                radius = 8f
            ) {
                width = HdSizeSpec.Weight(1f)
                padding = HdPadding.symmetric(horizontal = 10f, vertical = 5.5f)
                onClick = {
                    playClickSound()
                    editing = false
                }
            }
            actions.button(
                label = lang("save_bio"),
                icon = HdIconAtlas.Icon.CHECK,
                variant = ButtonNode.Variant.PRIMARY,
                fontSize = 8.8f,
                radius = 8f
            ) {
                width = HdSizeSpec.Weight(1f)
                padding = HdPadding.symmetric(horizontal = 10f, vertical = 5.5f)
                iconSizeDp = 10.5f
                onClick = { saveProfile() }
            }
        }
    }

    private fun buildPrivacyToggle(
        parent: HdBuilder,
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        parent.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { row ->
            row.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { col ->
                col.text(title, size = 8.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                col.text(subtitle, size = 7.2f, color = HdColorSpec.Token("text_dark_muted"), wrap = true) {
                    width = HdSizeSpec.Fill
                }
            }
            row.switch(checked = checked, onToggle = onToggle)
        }
    }

    private fun enterEditMode(profile: SocialProfileView) {
        editing = true
        editFavoriteSlot = -1
        editBannerType = profile.bannerType
        editSyncTeamAction = 0
        editShowTeam = profile.showTeam
        editAcceptInteractions = profile.acceptInteractions
        editDmMutualOnly = profile.dmMutualOnly

        stateStore.input(BIO_INPUT_ID).apply {
            text = profile.bio
            cursor = text.length
        }
        stateStore.input(STATUS_INPUT_ID).apply {
            text = profile.status
            cursor = text.length
        }
    }

    private fun saveProfile() {
        playClickSound()
        val bio = stateStore.getInputText(BIO_INPUT_ID).trim().take(SocialProfile.MAX_BIO_LENGTH)
        val status = stateStore.getInputText(STATUS_INPUT_ID).trim().take(SocialProfile.MAX_STATUS_LENGTH)
        UpdateProfilePacket(
            bio = bio,
            favoritePartySlot = editFavoriteSlot,
            status = status,
            bannerType = editBannerType,
            syncTeamAction = editSyncTeamAction,
            showTeam = editShowTeam,
            acceptInteractions = editAcceptInteractions,
            dmMutualOnly = editDmMutualOnly
        ).sendToServer()
        editing = false
    }

    private fun switchTab(next: ProfileTab) {
        activeTab = next
        expandedTeamIndex = null
        if (next == ProfileTab.FOLLOWERS) {
            SocialProfileCache.requestList(targetUuid, RequestProfileListPacket.ListType.FOLLOWERS)
        } else if (next == ProfileTab.FOLLOWING) {
            SocialProfileCache.requestList(targetUuid, RequestProfileListPacket.ListType.FOLLOWING)
        }
    }

    private fun callAvailable(): Boolean {
        if (!VoiceChatBridge.isModPresent || !SocialClientSession.capabilities.callsEnabled) return false
        val player = Minecraft.getInstance().player ?: return false
        return SmartphoneHelper.getSmartphone(player) != null
    }

    private fun buildEmptyState(sb: HdBuilder, icon: HdIconAtlas.Icon, message: String) {
        sb.column(
            gap = 8f,
            crossAlign = HdAlign.CENTER,
            mainAlign = HdAlign.CENTER,
            padding = HdPadding.symmetric(vertical = 44f),
            width = HdSizeSpec.Fill
        ) { eb ->
            eb.column(
                padding = HdPadding.all(10f),
                background = HdBackground(color = HdColorSpec.Fixed(0x22FFFFFF), radius = 20f)
            ) { ib ->
                ib.icon(icon, size = 18f, color = HdColorSpec.Token("text_primary"))
            }
            eb.text(message, size = 9.6f, weight = HdTextLayout.Weight.MEDIUM, color = HdColorSpec.Token("text_primary"), align = HdAlign.CENTER)
        }
    }

    companion object {
        private const val BIO_INPUT_ID = "profile_edit_bio"
        private const val STATUS_INPUT_ID = "profile_edit_status"

        private val STATUS_PRESET_KEYS = listOf(
            "status_preset_trades",
            "status_preset_battle",
            "status_preset_shiny",
            "status_preset_breeding",
            "status_preset_exploring"
        )

        private val BANNER_TYPES = listOf(
            "fire", "water", "grass", "electric", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock",
            "ghost", "dragon", "dark", "steel", "fairy", "normal"
        )
    }
}
