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
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
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
        onAuthorClick: () -> Unit,
        onLikeClick: () -> Unit,
        onCommentClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onReportClick: () -> Unit,
        onHashtagClick: (String) -> Unit,
        onPokemonDetailsClick: (PokemonAttachment) -> Unit,
        onVotePoll: (Int) -> Unit,
        onLoadPhoto: (java.util.UUID) -> Unit
    ) {
        builder.card(gap = 7f, padding = HdPadding.all(10f), onClick = onOpenPost) { cb ->
            // Header row
            cb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hb ->
                hb.row(gap = 8f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Weight(1f)) { ab ->
                    this.onClick = onAuthorClick
                    ab.avatar(post.authorUuid, post.authorName, post.skinUrl, size = 24f)
                    ab.column(gap = 1f, width = HdSizeSpec.Weight(1f)) { nb ->
                        nb.text(post.authorName, size = 10.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))
                        nb.text("Trainer", size = 7.8f, color = HdColorSpec.Token("text_dark_muted"))
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
                buildPokemonAttachmentBox(cb, att, colors, floatingState) {
                    onPokemonDetailsClick(att)
                }
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
        compact: Boolean = false,
        onShowDetails: () -> Unit
    ) {
        val boxSize = if (compact) 46f else 56f

        builder.row(
            gap = 9f,
            crossAlign = HdAlign.CENTER,
            padding = HdPadding.all(5f),
            width = HdSizeSpec.Fill,
            background = HdBackground(
                color = HdColorSpec.Token("surface_alt"),
                radius = 9f,
                borderWidth = 0.8f,
                borderColor = HdColorSpec.Token("primary_border")
            )
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

            rb.column(gap = 3f, width = HdSizeSpec.Weight(1f)) { ib ->
                ib.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { topRow ->
                    val speciesLabel = speciesName(attachment.species)
                    val fullName = attachment.nickname?.let { "$it ($speciesLabel)" } ?: speciesLabel
                    topRow.text(fullName, size = if (compact) 9.4f else 10.2f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                        width = HdSizeSpec.Weight(1f)
                    }
                    when (attachment.gender?.uppercase()) {
                        "MALE" -> topRow.text("♂", size = 10.5f, weight = HdTextLayout.Weight.BOLD, color = GENDER_MALE_COLOR)
                        "FEMALE" -> topRow.text("♀", size = 10.5f, weight = HdTextLayout.Weight.BOLD, color = GENDER_FEMALE_COLOR)
                    }
                }
                ib.text("Lv. ${attachment.level}", size = 8.8f, weight = HdTextLayout.Weight.MEDIUM, color = HdColorSpec.Token("text_dark_muted"))
                if (hasPokemonDetails(attachment)) {
                    ib.button(lang("show_more"), variant = ButtonNode.Variant.SURFACE, fontSize = 8.4f, radius = 9f) {
                        padding = HdPadding.symmetric(horizontal = 9f, vertical = 3.5f)
                        onClick = onShowDetails
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

    fun buildPokemonModalSpec(
        attachment: PokemonAttachment,
        onClose: () -> Unit
    ): HdPhoneScreen.ModalSpec {
        val speciesLabel = speciesName(attachment.species)
        val pokemonLabel = attachment.nickname?.let { "$it ($speciesLabel)" } ?: speciesLabel
        return HdPhoneScreen.ModalSpec(
            title = lang("pokemon_details"),
            widthDp = 278f,
            onClose = onClose
        ) { mb ->
            mb.text(pokemonLabel, size = 11f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark"))

            if (attachment.types.isNotEmpty()) {
                val typeNames = attachment.types.joinToString(" / ") {
                    Component.translatable("cobblemon.type.${it.lowercase()}").string
                }
                mb.text("${lang("types")}: $typeNames", size = 9.4f, color = HdColorSpec.Token("text_dark_muted"), wrap = true) {
                    width = HdSizeSpec.Fill
                }
            }
            attachment.ability?.let {
                mb.text("${lang("ability")}: ${Component.translatable(it).string}", size = 9.4f, color = HdColorSpec.Token("text_dark_muted"))
            }
            attachment.nature?.let {
                mb.text("${lang("nature")}: ${Component.translatable(it).string}", size = 9.4f, color = HdColorSpec.Token("text_dark_muted"))
            }

            val hasIvs = attachment.ivs.size == 6
            val hasEvs = attachment.evs.size == 6
            if (hasIvs || hasEvs) {
                mb.divider()
                mb.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { sr ->
                    sr.spacer(w = 24f)
                    STAT_LABELS.forEachIndexed { idx, stat ->
                        sr.text(stat, size = 8.6f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Fixed(STAT_COLORS[idx]), align = HdAlign.CENTER) {
                            width = HdSizeSpec.Weight(1f)
                        }
                    }
                }
                if (hasIvs) {
                    mb.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { ir ->
                        ir.text("IV", size = 9f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                            width = HdSizeSpec.Fixed(24f)
                        }
                        attachment.ivs.take(6).forEachIndexed { idx, v ->
                            ir.text(v.toString(), size = 9f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Fixed(STAT_COLORS[idx]), align = HdAlign.CENTER) {
                                width = HdSizeSpec.Weight(1f)
                            }
                        }
                    }
                }
                if (hasEvs) {
                    mb.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { er ->
                        er.text("EV", size = 9f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                            width = HdSizeSpec.Fixed(24f)
                        }
                        attachment.evs.take(6).forEachIndexed { idx, v ->
                            er.text(v.toString(), size = 9f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Fixed(STAT_COLORS[idx]), align = HdAlign.CENTER) {
                                width = HdSizeSpec.Weight(1f)
                            }
                        }
                    }
                }
            }
        }
    }
}
