package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdCanvas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColors
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.client.ui.hd.TabsNode
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.SocialPollView
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeColors
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared HD UI components reused across every Social screen ([SocialScreen],
 * [SocialPostDetailScreen], [DmThreadScreen], [DmNewScreen]).
 */
object SocialComponents {
    val LIKED_COLOR = HdColorSpec.Fixed(0xFFFF5C9A.toInt())
    val GENDER_MALE_COLOR = HdColorSpec.Fixed(0xFF408CFF.toInt())
    val GENDER_FEMALE_COLOR = HdColorSpec.Fixed(0xFFFF5C9A.toInt())

    private val STAT_LABELS = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
    private val STAT_COLORS = listOf(
        0xFF2EAD5B.toInt(),
        0xFFE34B4B.toInt(),
        0xFF82C91E.toInt(),
        0xFFFF8C32.toInt(),
        0xFFE0B928.toInt(),
        0xFF3F7FE5.toInt()
    )

    fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    fun speciesName(id: String): String {
        val loc = ResourceLocation.tryParse(id) ?: return id.substringAfter(':')
        return PokemonSpecies.getByIdentifier(loc)?.name ?: id.substringAfter(':')
    }

    fun relativeTime(timestamp: Long): String {
        val seconds = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> lang("now")
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3600}h"
            else -> "${seconds / 86_400}d"
        }
    }

    fun canDeletePost(post: SocialPostView): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return post.authorUuid == player.uuid || player.hasPermissions(2)
    }

    fun hasPokemonDetails(attachment: PokemonAttachment): Boolean =
        attachment.ivs.size == 6 ||
            attachment.evs.size == 6 ||
            attachment.ability != null ||
            attachment.nature != null ||
            attachment.types.isNotEmpty()

    fun buildBottomNav(
        builder: HdBuilder,
        activeId: String,
        onSelect: (String) -> Unit
    ) {
        builder.tabs(
            items = listOf(
                TabsNode.TabItem("feed", lang("tab_feed"), HdIconAtlas.Icon.FEED),
                TabsNode.TabItem("discover", "DM+", HdIconAtlas.Icon.SEARCH),
                TabsNode.TabItem("dms", lang("tab_dms"), HdIconAtlas.Icon.MESSAGE, hasBadge = SocialDmCache.unreadTotal > 0),
                TabsNode.TabItem("alerts", lang("tab_notifications"), HdIconAtlas.Icon.BELL, hasBadge = SocialNotificationCache.unreadCount > 0)
            ),
            activeId = activeId,
            style = TabsNode.Style.FLOATING_DOCK,
            onSelect = onSelect
        )
    }

    fun buildPostCard(
        builder: HdBuilder,
        post: SocialPostView,
        colors: SmartphoneThemeColors,
        floatingState: FloatingState,
        onOpenPost: (() -> Unit)? = null,
        onAuthorClick: (() -> Unit)? = null,
        onLikeClick: () -> Unit,
        onCommentClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onReportClick: () -> Unit,
        onHashtagClick: (String) -> Unit,
        onVotePoll: (Int) -> Unit,
        onLoadPhoto: (java.util.UUID) -> Unit,
        isPinned: Boolean = false,
        onPinClick: (() -> Unit)? = null
    ) {
        builder.card(gap = 7f, padding = HdPadding.all(10f), onClick = onOpenPost) { cb ->
            if (isPinned) {
                cb.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { pb ->
                    pb.icon(HdIconAtlas.Icon.STAR_FILLED, size = 9.5f, color = HdColorSpec.Token("content_gold"))
                    pb.text(
                        lang("pinned_post"),
                        size = 7.8f,
                        weight = HdTextLayout.Weight.BOLD,
                        color = HdColorSpec.Token("content_gold")
                    )
                }
            }

            // Header row
            cb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hb ->
                hb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Weight(1f)) { ab ->
                    if (onAuthorClick != null) {
                        this.onClick = onAuthorClick
                        this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_view_profile").string
                    }
                    ab.avatar(post.authorUuid, post.authorName, post.skinUrl, size = 24f)
                    ab.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { nb ->
                        nb.text(post.authorName, size = 10.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                        nb.text(
                            post.authorStatus.ifBlank { lang("trainer_role") },
                            size = 7.8f,
                            color = HdColorSpec.Token("text_dark_muted")
                        )
                    }
                }
                hb.text(relativeTime(post.timestamp), size = 8.6f, color = HdColorSpec.Token("text_dark_muted"))
            }

            // Body text
            if (post.text.isNotBlank()) {
                cb.text(
                    text = post.text,
                    size = 9.8f,
                    color = HdColorSpec.Token("text_dark"),
                    wrap = true,
                    socialMarkup = true,
                    onHashtagClick = onHashtagClick
                ) {
                    width = HdSizeSpec.Fill
                }
            }

            // Pokemon attachment
            post.attachment?.let { att ->
                buildPokemonAttachmentBox(cb, att, colors, floatingState)
            }

            // Photo attachment
            post.photo?.let { photo ->
                val maxW = 286f
                val maxH = 128f
                val s = if (photo.width > 0 && photo.height > 0) {
                    min(maxW / photo.width.toFloat(), maxH / photo.height.toFloat())
                } else 1f
                val pw = if (photo.width > 0) (photo.width * s).coerceAtLeast(16f) else maxW
                val ph = if (photo.height > 0) (photo.height * s).coerceAtLeast(16f) else maxH

                val tex = SocialPhotoClient.texture(photo.id)
                if (tex != null) {
                    cb.row(mainAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { pb ->
                        pb.image(tex, pw, ph, radius = 8f, filter = HdCanvas.TextureFilter.LINEAR) {
                            this.borderWidth = 0.8f
                            this.borderColor = HdColorSpec.Token("primary_border")
                        }
                    }
                } else {
                    cb.column(
                        mainAlign = HdAlign.CENTER,
                        crossAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fill,
                        height = HdSizeSpec.Fixed(ph.coerceAtMost(90f)),
                        background = HdBackground(
                            color = HdColorSpec.Token("surface_alt"),
                            radius = 8f,
                            borderWidth = 0.8f,
                            borderColor = HdColorSpec.Token("primary_border")
                        )
                    ) { pb ->
                        val lbl = lang(if (SocialPhotoClient.isRequested(photo.id)) "photo_loading" else "photo_load")
                        pb.button(lbl, icon = HdIconAtlas.Icon.IMAGE, variant = ButtonNode.Variant.SURFACE, fontSize = 8.8f) {
                            onClick = { onLoadPhoto(photo.id) }
                        }
                    }
                }
            }

            // Poll
            post.poll?.let { poll ->
                buildPollBox(cb, poll, onVotePoll)
            }

            cb.divider()

            // Footer
            cb.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { fb ->
                val likeCol = if (post.likedByMe) LIKED_COLOR else HdColorSpec.Token("text_dark_muted")
                val likeBg = if (post.likedByMe) HdColorSpec.Fixed(0x1FFF5C9A) else HdColorSpec.TRANSPARENT
                fb.row(
                    gap = 4f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f),
                    background = HdBackground(color = likeBg, radius = 9f)
                ) { lb ->
                    this.onClick = onLikeClick
                    this.hoverBackground = HdBackground(color = HdColorSpec.Fixed(0x1A3A96B6), radius = 9f)
                    this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.${if (post.likedByMe) "social_unlike" else "social_like"}").string
                    lb.icon(if (post.likedByMe) HdIconAtlas.Icon.HEART_FILLED else HdIconAtlas.Icon.HEART, size = 12f, color = likeCol)
                    lb.text(post.likeCount.toString(), size = 9.2f, weight = HdTextLayout.Weight.BOLD, color = likeCol)
                }

                fb.row(
                    gap = 4f,
                    crossAlign = HdAlign.CENTER,
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f)
                ) { cm ->
                    this.onClick = onCommentClick
                    this.hoverBackground = HdBackground(color = HdColorSpec.Fixed(0x1A3A96B6), radius = 9f)
                    this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_comments").string
                    cm.icon(HdIconAtlas.Icon.MESSAGE, size = 12f, color = HdColorSpec.Token("text_dark_muted"))
                    cm.text(post.commentCount.toString(), size = 9.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted"))
                }

                fb.spacer(weight = 1f)

                if (onPinClick != null) {
                    val pinColor = if (isPinned) HdColorSpec.Token("content_gold") else HdColorSpec.Token("text_dark_muted")
                    fb.row(
                        gap = 3f,
                        crossAlign = HdAlign.CENTER,
                        padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f)
                    ) { pb ->
                        this.onClick = onPinClick
                        this.hoverBackground = HdBackground(color = HdColorSpec.Fixed(0x1AF2C14E), radius = 9f)
                        this.tooltip = lang(if (isPinned) "unpin_post" else "pin_post")
                        pb.icon(
                            if (isPinned) HdIconAtlas.Icon.STAR_FILLED else HdIconAtlas.Icon.STAR,
                            size = 11f,
                            color = pinColor
                        )
                    }
                }

                if (canDeletePost(post)) {
                    fb.row(
                        gap = 3f,
                        crossAlign = HdAlign.CENTER,
                        padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f)
                    ) { db ->
                        this.onClick = onDeleteClick
                        this.hoverBackground = HdBackground(color = HdColorSpec.Fixed(0x1AEF4444), radius = 9f)
                        this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_delete").string
                        db.icon(HdIconAtlas.Icon.TRASH, size = 11f, color = HdColorSpec.Token("text_dark_muted"))
                        db.text(lang("delete"), size = 8.8f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark_muted"))
                    }
                } else {
                    fb.row(
                        padding = HdPadding.symmetric(horizontal = 6f, vertical = 3f)
                    ) { rb ->
                        this.onClick = onReportClick
                        this.hoverBackground = HdBackground(color = HdColorSpec.Fixed(0x1AEF4444), radius = 9f)
                        this.tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_report").string
                        rb.icon(HdIconAtlas.Icon.FLAG, size = 11.5f, color = HdColorSpec.Token("text_dark_muted"))
                    }
                }
            }
        }
    }

    fun buildPokemonAttachmentBox(
        builder: HdBuilder,
        attachment: PokemonAttachment,
        colors: SmartphoneThemeColors,
        floatingState: FloatingState,
        compact: Boolean = false
    ) {
        val boxSize = if (compact) 44f else 54f
        val hasIvs = attachment.ivs.size == 6
        val hasEvs = attachment.evs.size == 6

        builder.column(
            gap = if (compact) 4f else 5f,
            padding = HdPadding.all(if (compact) 5f else 6f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface_alt"),
                radius = 9f,
                borderWidth = 0.8f,
                borderColor = HdColorSpec.Token("primary_border")
            )
        ) { card ->
            card.row(
                gap = if (compact) 7f else 9f,
                crossAlign = HdAlign.CENTER,
                width = HdSizeSpec.Fill
            ) { rb ->
                // Model box
                rb.stack(
                    hAlign = HdAlign.CENTER,
                    vAlign = HdAlign.CENTER,
                    width = HdSizeSpec.Fixed(boxSize),
                    height = HdSizeSpec.Fixed(boxSize)
                ) { sb ->
                    this.background = HdBackground(
                        color = HdColorSpec.Fixed(HdColors.mix(colors.headerNavy, colors.primary, 0.35f)),
                        radius = 8f,
                        borderWidth = 0.7f,
                        borderColor = HdColorSpec.Fixed(0x33FFFFFF)
                    )
                    sb.pokemonView(
                        speciesId = attachment.species,
                        aspects = attachment.aspects,
                        size = boxSize - 2f,
                        floatingState = floatingState
                    )
                }

                rb.column(gap = if (compact) 2f else 2.5f, width = HdSizeSpec.Weight(1f)) { ib ->
                    ib.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { topRow ->
                        val speciesLabel = speciesName(attachment.species)
                        val fullName = attachment.nickname?.let { "$it ($speciesLabel)" } ?: speciesLabel
                        topRow.text(
                            fullName,
                            size = if (compact) 9.2f else 10.0f,
                            weight = HdTextLayout.Weight.BOLD,
                            color = HdColorSpec.Token("text_dark")
                        ) {
                            width = HdSizeSpec.Weight(1f)
                        }
                        when (attachment.gender?.uppercase()) {
                            "MALE" -> topRow.text("♂", size = 10.2f, weight = HdTextLayout.Weight.BOLD, color = GENDER_MALE_COLOR)
                            "FEMALE" -> topRow.text("♀", size = 10.2f, weight = HdTextLayout.Weight.BOLD, color = GENDER_FEMALE_COLOR)
                        }
                    }

                    ib.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { metaRow ->
                        metaRow.text(
                            "Lv. ${attachment.level}",
                            size = if (compact) 8.0f else 8.6f,
                            weight = HdTextLayout.Weight.MEDIUM,
                            color = HdColorSpec.Token("text_dark_muted")
                        )
                        attachment.types.forEach { type ->
                            metaRow.row(
                                padding = HdPadding.symmetric(horizontal = if (compact) 4f else 5f, vertical = 1f),
                                background = HdBackground(
                                    color = HdColorSpec.Fixed(PokeInfoDetailScreen.typeColor(type)),
                                    radius = 4f
                                )
                            ) { pill ->
                                pill.text(
                                    text = Component.translatable("cobblemon.type.${type.lowercase()}").string,
                                    size = if (compact) 6.8f else 7.2f,
                                    weight = HdTextLayout.Weight.BOLD,
                                    color = HdColorSpec.Fixed(0xFFFFFFFF.toInt())
                                )
                            }
                        }
                    }

                    attachment.ability?.let { ab ->
                        ib.text(
                            "${lang("ability")}: ${Component.translatable(ab).string}",
                            size = if (compact) 7.4f else 8.0f,
                            color = HdColorSpec.Token("text_dark_muted"),
                            wrap = true
                        ) {
                            width = HdSizeSpec.Fill
                        }
                    }
                    attachment.nature?.let { nat ->
                        ib.text(
                            "${lang("nature")}: ${Component.translatable(nat).string}",
                            size = if (compact) 7.4f else 8.0f,
                            color = HdColorSpec.Token("text_dark_muted"),
                            wrap = true
                        ) {
                            width = HdSizeSpec.Fill
                        }
                    }
                }
            }

            if (hasIvs || hasEvs) {
                card.divider()
                val labelW = if (compact) 18f else 22f
                val statHeaderSize = if (compact) 7.0f else 7.6f
                val statValSize = if (compact) 7.4f else 8.0f
                card.column(gap = 2f, width = HdSizeSpec.Fill) { statsCol ->
                    statsCol.row(gap = 2f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { sr ->
                        sr.spacer(w = labelW)
                        STAT_LABELS.forEachIndexed { idx, stat ->
                            sr.text(
                                stat,
                                size = statHeaderSize,
                                weight = HdTextLayout.Weight.BOLD,
                                color = HdColorSpec.Fixed(STAT_COLORS[idx]),
                                align = HdAlign.CENTER
                            ) {
                                width = HdSizeSpec.Weight(1f)
                            }
                        }
                    }
                    if (hasIvs) {
                        statsCol.row(gap = 2f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { ir ->
                            ir.text("IV", size = statValSize, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                                width = HdSizeSpec.Fixed(labelW)
                            }
                            attachment.ivs.take(6).forEachIndexed { idx, v ->
                                ir.text(
                                    v.toString(),
                                    size = statValSize,
                                    weight = HdTextLayout.Weight.BOLD,
                                    color = HdColorSpec.Fixed(STAT_COLORS[idx]),
                                    align = HdAlign.CENTER
                                ) {
                                    width = HdSizeSpec.Weight(1f)
                                }
                            }
                        }
                    }
                    if (hasEvs) {
                        statsCol.row(gap = 2f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { er ->
                            er.text("EV", size = statValSize, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                                width = HdSizeSpec.Fixed(labelW)
                            }
                            attachment.evs.take(6).forEachIndexed { idx, v ->
                                er.text(
                                    v.toString(),
                                    size = statValSize,
                                    weight = HdTextLayout.Weight.BOLD,
                                    color = HdColorSpec.Fixed(STAT_COLORS[idx]),
                                    align = HdAlign.CENTER
                                ) {
                                    width = HdSizeSpec.Weight(1f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun buildPollBox(
        builder: HdBuilder,
        poll: SocialPollView,
        onVote: (Int) -> Unit
    ) {
        builder.column(
            gap = 5f,
            padding = HdPadding.all(9f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface_alt"),
                radius = 9f,
                borderWidth = 0.8f,
                borderColor = HdColorSpec.Token("primary_border")
            )
        ) { pb ->
            pb.text(poll.question, size = 9.6f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"), wrap = true) {
                width = HdSizeSpec.Fill
            }

            val hasVoted = poll.userVotedOption != null
            val total = poll.totalVotes

            poll.options.forEachIndexed { idx, optText ->
                val isMyVote = poll.userVotedOption == idx
                val count = poll.voteCounts.getOrElse(idx) { 0 }
                val pct = if (total > 0) count.toFloat() / total else 0f
                val pctStr = "${(pct * 100f).roundToInt()}%"

                if (!hasVoted) {
                    pb.button(optText, variant = ButtonNode.Variant.SURFACE, fontSize = 9.2f, radius = 7f) {
                        width = HdSizeSpec.Fill
                        padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
                        onClick = { onVote(idx) }
                    }
                } else {
                    val borderCol = if (isMyVote) HdColorSpec.Token("gold") else HdColorSpec.Token("primary_border")
                    pb.stack(
                        hAlign = HdAlign.START,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fill
                    ) { ob ->
                        this.height = HdSizeSpec.Fixed(21f)
                        this.background = HdBackground(
                            color = HdColorSpec.Token("surface"),
                            radius = 7f,
                            borderWidth = if (isMyVote) 1.1f else 0.7f,
                            borderColor = borderCol
                        )
                        this.onClick = { onVote(idx) }

                        if (pct > 0f) {
                            ob.progressBar(pct, height = 20f) {
                                this.trackColor = HdColorSpec.TRANSPARENT
                                this.borderColor = HdColorSpec.TRANSPARENT
                                this.fillColor = HdColorSpec.Token(if (isMyVote) "primary" else "primary_border", alpha = if (isMyVote) 0.34f else 0.20f)
                                this.radius = 6.5f
                            }
                        }

                        ob.row(
                            gap = 4f,
                            crossAlign = HdAlign.CENTER,
                            padding = HdPadding.symmetric(horizontal = 7f),
                            width = HdSizeSpec.Fill
                        ) { lr ->
                            if (isMyVote) {
                                lr.icon(HdIconAtlas.Icon.CHECK, size = 10.5f, color = HdColorSpec.Token("content_gold"))
                            }
                            lr.text(
                                optText,
                                size = 9.2f,
                                weight = if (isMyVote) HdTextLayout.Weight.BOLD else HdTextLayout.Weight.REGULAR,
                                color = HdColorSpec.Token("text_dark")
                            ) {
                                width = HdSizeSpec.Weight(1f)
                            }
                            lr.text(
                                "$pctStr ($count)",
                                size = 8.2f,
                                weight = HdTextLayout.Weight.BOLD,
                                color = if (isMyVote) HdColorSpec.Token("content_gold") else HdColorSpec.Token("text_dark_muted")
                            )
                        }
                    }
                }
            }

            pb.text("${poll.totalVotes} ${lang("poll_votes")}", size = 8.2f, color = HdColorSpec.Token("text_dark_muted"))
        }
    }
}
