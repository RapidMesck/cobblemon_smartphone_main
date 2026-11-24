package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.canDeletePost
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.lang
import com.nbp.cobblemon_smartphone.client.gui.SocialComponents.relativeTime
import com.nbp.cobblemon_smartphone.client.social.SocialCommentCache
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
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
import com.nbp.cobblemon_smartphone.client.ui.hd.TextInputNode
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.AddCommentPacket
import com.nbp.cobblemon_smartphone.network.packet.DeleteCommentPacket
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.ReportContentPacket
import com.nbp.cobblemon_smartphone.network.packet.VotePollPacket
import com.nbp.cobblemon_smartphone.social.PokemonAttachment
import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.social.SocialCommentView
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Expanded single post view, full comment list, and HD comment composer built on [HdPhoneScreen].
 */
class SocialPostDetailScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    private var post: SocialPostView
) : HdPhoneScreen(color, smartphoneStack, Component.literal("Post Details")) {

    private var pokemonPopup: PokemonAttachment? = null
    private val posableState = FloatingState()

    override val viewportMode: ViewportMode = ViewportMode.Large

    override fun init() {
        super.init()
        SocialCommentCache.registerListener(post.id) {}
        SocialCommentCache.request(post.id)
    }

    override fun removed() {
        SocialCommentCache.unregisterListener(post.id)
        super.removed()
    }

    override fun onBackPressed() {
        Minecraft.getInstance().setScreen(SocialScreen(color, smartphoneStack))
    }

    override fun buildModal(theme: SmartphoneTheme): ModalSpec? =
        pokemonPopup?.let { att ->
            SocialComponents.buildPokemonModalSpec(att) { pokemonPopup = null }
        }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        SocialFeedCache.posts().find { it.id == post.id }?.let { post = it }

        // Floating Island Header
        builder.appHeader(
            title = lang("post_detail"),
            subtitle = post.authorName,
            icon = HdIconAtlas.Icon.FEED,
            iconColor = HdColorSpec.Token("primary")
        )

        // Scrollable post + comments
        builder.scroll(
            scrollId = "post_detail_scroll_${post.id}",
            gap = 7f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 6f)
        ) { sb ->
            SocialComponents.buildPostCard(
                builder = sb,
                post = post,
                colors = theme.colors,
                floatingState = posableState,
                onOpenPost = null,
                onAuthorClick = {
                    val me = Minecraft.getInstance().player
                    if (me == null || post.authorUuid != me.uuid) {
                        playClickSound()
                        Minecraft.getInstance().setScreen(DmThreadScreen(color, smartphoneStack, post.authorUuid, post.authorName))
                    }
                },
                onLikeClick = {
                    playClickSound()
                    val newLiked = !post.likedByMe
                    post = post.copy(
                        likedByMe = newLiked,
                        likeCount = (post.likeCount + if (newLiked) 1 else -1).coerceAtLeast(0)
                    )
                    SocialFeedCache.toggleLikeLocally(post.id)
                    LikePostPacket(post.id, newLiked).sendToServer()
                },
                onCommentClick = {},
                onDeleteClick = {
                    if (canDeletePost(post)) {
                        playClickSound()
                        DeletePostPacket(post.id).sendToServer()
                        onBackPressed()
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
                    onBackPressed()
                },
                onPokemonDetailsClick = { att ->
                    playClickSound()
                    pokemonPopup = att
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

            val comments = SocialCommentCache.commentsFor(post.id)
            sb.row(
                gap = 6f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 4f, vertical = 2f),
                width = HdSizeSpec.Fill
            ) { ch ->
                ch.text(
                    "${lang("comments")} (${comments.size})",
                    size = 9.4f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_primary")
                )
            }

            if (comments.isEmpty()) {
                sb.card(padding = HdPadding.all(10f), shadowBlur = 3f) { eb ->
                    eb.text(
                        lang("no_comments"),
                        size = 9f,
                        color = HdColorSpec.Token("text_dark_muted"),
                        align = HdAlign.CENTER
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
            } else {
                comments.forEach { comment ->
                    buildCommentCard(sb, comment)
                }
            }
        }

        // Floating comment input bar at bottom
        builder.column(
            padding = HdPadding.only(left = 7f, right = 7f, top = 3f, bottom = 7f),
            width = HdSizeSpec.Fill
        ) { wrap ->
            wrap.row(
                gap = 6f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.all(5f),
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
            ) { ib ->
                val maxLen = CobblemonSmartphone.config.social.maxCommentLength
                ib.textInput(
                    inputId = "comment_input_${post.id}",
                    placeholder = lang("comment_placeholder"),
                    maxLength = maxLen,
                    onSubmit = { trySendComment() }
                ) {
                    width = HdSizeSpec.Weight(1f)
                    fontSizeDp = 9.2f
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 5f)
                }
                ib.button(
                    icon = HdIconAtlas.Icon.SEND,
                    variant = ButtonNode.Variant.PRIMARY,
                    radius = 8f
                ) {
                    padding = HdPadding.symmetric(horizontal = 8f, vertical = 5.5f)
                    iconSizeDp = 12f
                    tooltip = lang("send")
                    onClick = { trySendComment() }
                }
            }
        }
    }

    private fun buildCommentCard(sb: HdBuilder, comment: SocialCommentView) {
        sb.card(gap = 4f, padding = HdPadding.all(8f), radius = 9f, shadowBlur = 4f) { cb ->
            cb.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { hb ->
                hb.row(gap = 6f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Weight(1f)) { ab ->
                    this.onClick = {
                        val me = Minecraft.getInstance().player
                        if (me == null || comment.authorUuid != me.uuid) {
                            playClickSound()
                            Minecraft.getInstance().setScreen(DmThreadScreen(color, smartphoneStack, comment.authorUuid, comment.authorName))
                        }
                    }
                    ab.avatar(comment.authorUuid, comment.authorName, comment.skinUrl, size = 18f)
                    ab.text(comment.authorName, size = 9.4f, weight = HdTextLayout.Weight.BOLD, color = HdColorSpec.Token("text_dark")) {
                        width = HdSizeSpec.Weight(1f)
                    }
                }
                hb.text(relativeTime(comment.timestamp), size = 8f, color = HdColorSpec.Token("text_dark_muted"))

                if (canDeleteComment(comment)) {
                    hb.button(icon = HdIconAtlas.Icon.TRASH, variant = ButtonNode.Variant.GHOST, radius = 5f) {
                        padding = HdPadding.all(2.5f)
                        iconSizeDp = 10f
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_delete").string
                        onClick = {
                            playClickSound()
                            DeleteCommentPacket(post.id, comment.id).sendToServer()
                        }
                    }
                } else {
                    hb.button(icon = HdIconAtlas.Icon.FLAG, variant = ButtonNode.Variant.GHOST, radius = 5f) {
                        padding = HdPadding.all(2.5f)
                        iconSizeDp = 10f
                        tooltip = Component.translatable("cobblemon_smartphone.tooltip.social_report").string
                        onClick = {
                            playClickSound()
                            ReportContentPacket(ReportTargetType.COMMENT, comment.id, "Inappropriate content").sendToServer()
                            Minecraft.getInstance().player?.displayClientMessage(
                                Component.translatable("message.nbp.social.report_sent"),
                                false
                            )
                        }
                    }
                }
            }
            cb.text(comment.text, size = 9.2f, color = HdColorSpec.Token("text_dark"), wrap = true) {
                width = HdSizeSpec.Fill
            }
        }
    }

    private fun trySendComment() {
        val st = stateStore.input("comment_input_${post.id}")
        val text = st.text.trim()
        if (text.isEmpty()) return
        playClickSound()
        AddCommentPacket(post.id, text).sendToServer()
        st.text = ""
        st.cursor = 0
        st.selectionAnchor = null
        st.resetBlink()
    }

    private fun canDeleteComment(comment: SocialCommentView): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return comment.authorUuid == player.uuid || player.hasPermissions(2)
    }
}
