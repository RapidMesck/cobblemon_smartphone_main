package com.nbp.cobblemon_smartphone.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.systems.RenderSystem
import com.nbp.cobblemon_smartphone.client.social.SocialFeedCache
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.social.SocialProfileCache
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.network.packet.ChallengeBattlePacket
import com.nbp.cobblemon_smartphone.network.packet.ChallengeTradePacket
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.ToggleFollowPacket
import com.nbp.cobblemon_smartphone.network.packet.UpdateProfilePacket
import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeRenderer
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import java.util.UUID

/**
 * Screen displaying a player's profile: bio, stats, partner Pokémon showcase, and their posts.
 */
class SocialProfileScreen(
    private val color: SmartphoneColor,
    private val smartphoneStack: ItemStack?,
    private val profileUuid: UUID,
    private val initialName: String
) : Screen(Component.literal("Profile")) {

    private var screenX = 0
    private var screenY = 0
    private var scrollY = 0
    private var maxScroll = 0
    private var editingBio = false
    private lateinit var bioInput: EditBox
    private var pickingPartner = false

    private val posableState = FloatingState()
    private var partnerModel: RenderablePokemon? = null
    private var cachedSpecies: String? = null

    private val theme get() = SmartphoneThemeHelper.resolveCurrentTheme(smartphoneStack)
    private val NAME_COLOR get() = theme.colors.textPrimary
    private val CONTENT_TEXT get() = theme.colors.textDark
    private val CONTENT_DIM get() = theme.colors.textDarkMuted
    private val MUTED_COLOR get() = theme.colors.textMuted
    private val ACCENT_COLOR get() = theme.colors.primary
    private val BUTTON_BORDER_COLOR get() = theme.colors.primaryBorder
    private val SECTION_CONTENT_BG get() = theme.colors.surface
    private val ROW_HOVER_BG get() = theme.colors.surfaceHover
    private val DANGER_COLOR get() = theme.colors.danger
    private val ONLINE_COLOR = 0xFF43A968.toInt()
    private val OFFLINE_COLOR = 0xFF888888.toInt()

    private val isOwnProfile: Boolean
        get() = Minecraft.getInstance().player?.uuid == profileUuid

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        screenX = (width - GUI_WIDTH) / 2
        screenY = (height - GUI_HEIGHT) / 2
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color

        bioInput = EditBox(
            font,
            screenX + CONTENT_X + 4,
            screenY + 84,
            CONTENT_WIDTH - 8,
            14,
            Component.empty()
        )
        bioInput.setMaxLength(150)
        bioInput.setBordered(true)
        bioInput.setTextColor(0xFF333333.toInt())
        val currentProfile = SocialProfileCache.getProfile(profileUuid)
        if (currentProfile != null) bioInput.value = currentProfile.bio
        addRenderableWidget(bioInput)

        SocialProfileCache.registerListener(profileUuid) { profile, _ ->
            if (!editingBio) bioInput.value = profile.bio
            updatePartnerModel(profile.favoriteSpecies, profile.favoriteAspects)
            scrollY = scrollY.coerceIn(0, maxScroll)
        }
        SocialProfileCache.request(profileUuid)
    }

    override fun removed() {
        SocialProfileCache.unregisterListener(profileUuid)
        SmartphoneHelper.contextSmartphone = null
        SmartphoneHelper.contextColor = null
        super.removed()
    }

    private fun updatePartnerModel(speciesId: String?, aspects: Set<String>) {
        if (speciesId == null) {
            partnerModel = null
            cachedSpecies = null
            return
        }
        if (partnerModel == null || cachedSpecies != speciesId) {
            val species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(speciesId))
            partnerModel = species?.let { RenderablePokemon(it, aspects) }
            cachedSpecies = speciesId
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = guiGraphics.pose()
        SmartphoneThemeRenderer.renderLargeTheme(guiGraphics, theme, screenX, screenY)
        blitk(
            matrixStack = matrices,
            texture = theme.getLargeFrameTexture(color.getLargeScreenTexture()),
            x = screenX,
            y = screenY,
            width = GUI_WIDTH,
            height = GUI_HEIGHT
        )

        renderHeader(guiGraphics, mouseX, mouseY)

        val profile = SocialProfileCache.getProfile(profileUuid)
        val posts = SocialProfileCache.getPosts(profileUuid)

        val profileHeaderHeight = measureProfileHeader(profile)
        val postsTitleHeight = 16
        val postsHeight = posts.sumOf { measureCard(it) + CARD_GAP }
        val totalHeight = profileHeaderHeight + postsTitleHeight + postsHeight + 8

        val viewportHeight = LIST_END_Y - LIST_START_Y
        maxScroll = (totalHeight - viewportHeight).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(0, maxScroll)

        guiGraphics.enableScissor(
            screenX + CONTENT_X,
            screenY + LIST_START_Y,
            screenX + CONTENT_X + CONTENT_WIDTH,
            screenY + LIST_END_Y
        )

        var cursorY = screenY + LIST_START_Y - scrollY

        // 1. Profile Header Box
        renderProfileHeader(guiGraphics, profile, screenX + CONTENT_X, cursorY, mouseX, mouseY)
        cursorY += profileHeaderHeight + 6

        // 2. Posts Header
        val postsTitle = "${lang("user_posts").format(profile?.name ?: initialName)} (${posts.size})"
        guiGraphics.drawString(font, postsTitle, screenX + CONTENT_X + 2, cursorY + 2, NAME_COLOR, false)
        cursorY += postsTitleHeight

        // 3. Posts List
        if (posts.isEmpty()) {
            val emptyMsg = lang("empty")
            guiGraphics.drawString(font, emptyMsg, screenX + CONTENT_X + 4, cursorY + 4, CONTENT_DIM, false)
        } else {
            posts.forEach { post ->
                val cardH = measureCard(post)
                renderCard(guiGraphics, post, screenX + CONTENT_X, cursorY, mouseX, mouseY, cardH)
                cursorY += cardH + CARD_GAP
            }
        }

        guiGraphics.disableScissor()

        if (maxScroll > 0) renderScrollbar(guiGraphics)

        if (pickingPartner) renderPartnerPickerModal(guiGraphics, mouseX, mouseY)
        renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun measureProfileHeader(profile: SocialProfileView?): Int {
        var h = 48 // base info + counters
        if (profile?.bio?.isNotBlank() == true || editingBio) {
            val lines = font.split(Component.literal(profile?.bio ?: ""), CONTENT_WIDTH - 12)
            h += (lines.size * font.lineHeight).coerceAtLeast(14) + 6
        }
        if (profile?.favoriteSpecies != null || isOwnProfile) {
            h += 42 // Partner pokemon card
        }
        if (!isOwnProfile && profile != null) {
            h += 20 // Action bar: Battle | Trade | Chat
        }
        return h
    }

    private fun renderProfileHeader(
        guiGraphics: GuiGraphics,
        profile: SocialProfileView?,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val h = measureProfileHeader(profile)
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, h, colors = theme.colors)

        // Player Head
        val headSize = 20
        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(profileUuid, profile?.name ?: initialName, profile?.skinUrl),
            x + 6,
            y + 6,
            headSize
        )

        // Name & Online Indicator
        val displayName = profile?.name ?: initialName
        guiGraphics.drawString(font, displayName, x + 30, y + 6, NAME_COLOR, false)

        val isOnline = profile?.isOnline ?: false
        val statusColor = if (isOnline) ONLINE_COLOR else OFFLINE_COLOR
        guiGraphics.fill(x + 30, y + 17, x + 34, y + 21, statusColor)
        val statusText = lang(if (isOnline) "online" else "offline")
        guiGraphics.drawString(font, statusText, x + 37, y + 15, CONTENT_DIM, false)

        // Action Buttons (Follow / Edit Bio / DM)
        if (!isOwnProfile && profile != null) {
            val followText = lang(if (profile.isFollowedByMe) "unfollow" else "follow")
            val btnW = font.width(followText) + 8
            val btnX = x + CONTENT_WIDTH - btnW - 6
            val btnY = y + 5
            val hovered = mouseX in btnX..(btnX + btnW) && mouseY in btnY..(btnY + 14)
            val btnBg = if (profile.isFollowedByMe) (if (hovered) DANGER_COLOR else theme.colors.surface) else (if (hovered) theme.colors.primaryHover else ACCENT_COLOR)
            guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + 14, BUTTON_BORDER_COLOR)
            guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + 13, btnBg)
            guiGraphics.drawString(font, followText, btnX + 4, btnY + 3, if (profile.isFollowedByMe && !hovered) CONTENT_TEXT else 0xFFFFFFFF.toInt(), false)
        } else if (isOwnProfile) {
            val editBtnText = lang(if (editingBio) "save_bio" else "edit_bio")
            val btnW = font.width(editBtnText) + 8
            val btnX = x + CONTENT_WIDTH - btnW - 6
            val btnY = y + 5
            val hovered = mouseX in btnX..(btnX + btnW) && mouseY in btnY..(btnY + 14)
            guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + 14, BUTTON_BORDER_COLOR)
            guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + 13, if (hovered) theme.colors.primaryHover else ACCENT_COLOR)
            guiGraphics.drawString(font, editBtnText, btnX + 4, btnY + 3, 0xFFFFFFFF.toInt(), false)
        }

        // Stats: Posts | Followers | Following
        val statsY = y + 29
        val postCount = profile?.postCount ?: 0
        val followers = profile?.followerCount ?: 0
        val following = profile?.followingCount ?: 0
        val statsStr = "${lang("posts_count").format(postCount)}  •  ${lang("followers").format(followers)}  •  ${lang("following").format(following)}"
        guiGraphics.drawString(font, statsStr, x + 6, statsY, CONTENT_DIM, false)

        var cursorY = y + 42

        // Bio section
        if (editingBio) {
            bioInput.setX(x + 6)
            bioInput.setY(cursorY)
            bioInput.visible = true
            bioInput.render(guiGraphics, mouseX, mouseY, 0f)
            cursorY += 18
        } else {
            bioInput.visible = false
            val bio = profile?.bio ?: ""
            if (bio.isNotBlank()) {
                val lines = font.split(Component.literal(bio), CONTENT_WIDTH - 12)
                lines.forEach { line ->
                    guiGraphics.drawString(font, line, x + 6, cursorY, CONTENT_TEXT, false)
                    cursorY += font.lineHeight
                }
                cursorY += 4
            }
        }

        // Partner Pokémon Showcase Card
        if (profile?.favoriteSpecies != null || isOwnProfile) {
            renderPartnerCard(guiGraphics, profile, x + 6, cursorY, mouseX, mouseY)
            cursorY += 40
        }

        // Gameplay Interaction Bar (Battle | Trade | Chat)
        if (!isOwnProfile && profile != null) {
            renderActionBar(guiGraphics, profile, x + 6, cursorY, mouseX, mouseY)
        }
    }

    private fun renderActionBar(guiGraphics: GuiGraphics, profile: SocialProfileView, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val totalW = CONTENT_WIDTH - 12
        val gap = 4
        val btnW = (totalW - gap * 2) / 3
        val btnH = 14

        // 1. Battle Challenge Button
        val bHovered = mouseX in x..(x + btnW) && mouseY in y..(y + btnH)
        val bBg = if (bHovered) theme.colors.primaryHover else SECTION_CONTENT_BG
        guiGraphics.fill(x, y, x + btnW, y + btnH, bBg)
        guiGraphics.fill(x, y, x + btnW, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y + btnH - 1, x + btnW, y + btnH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y, x + 1, y + btnH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x + btnW - 1, y, x + btnW, y + btnH, BUTTON_BORDER_COLOR)
        val bLabel = "⚔ " + lang("battle_action")
        val bTrimmed = font.plainSubstrByWidth(bLabel, btnW - 4)
        guiGraphics.drawString(font, bTrimmed, x + (btnW - font.width(bTrimmed)) / 2, y + 3, if (bHovered) NAME_COLOR else CONTENT_TEXT, false)

        // 2. Trade Request Button
        val tx = x + btnW + gap
        val tHovered = mouseX in tx..(tx + btnW) && mouseY in y..(y + btnH)
        val tBg = if (tHovered) theme.colors.primaryHover else SECTION_CONTENT_BG
        guiGraphics.fill(tx, y, tx + btnW, y + btnH, tBg)
        guiGraphics.fill(tx, y, tx + btnW, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(tx, y + btnH - 1, tx + btnW, y + btnH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(tx, y, tx + 1, y + btnH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(tx + btnW - 1, y, tx + btnW, y + btnH, BUTTON_BORDER_COLOR)
        val tLabel = "⇄ " + lang("trade_action")
        val tTrimmed = font.plainSubstrByWidth(tLabel, btnW - 4)
        guiGraphics.drawString(font, tTrimmed, tx + (btnW - font.width(tTrimmed)) / 2, y + 3, if (tHovered) NAME_COLOR else CONTENT_TEXT, false)

        // 3. Message / Chat Button
        val cx = tx + btnW + gap
        val mHovered = mouseX in cx..(cx + btnW) && mouseY in y..(y + btnH)
        val mBg = if (mHovered) theme.colors.primaryHover else SECTION_CONTENT_BG
        guiGraphics.fill(cx, y, cx + btnW, y + btnH, mBg)
        guiGraphics.fill(cx, y, cx + btnW, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(cx, y + btnH - 1, cx + btnW, y + btnH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(cx, y, cx + 1, y + btnH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(cx + btnW - 1, y, cx + btnW, y + btnH, BUTTON_BORDER_COLOR)
        val mLabel = "✉ " + lang("message_action")
        val mTrimmed = font.plainSubstrByWidth(mLabel, btnW - 4)
        guiGraphics.drawString(font, mTrimmed, cx + (btnW - font.width(mTrimmed)) / 2, y + 3, if (mHovered) NAME_COLOR else CONTENT_TEXT, false)
    }

    private fun renderPartnerCard(
        guiGraphics: GuiGraphics,
        profile: SocialProfileView?,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val cardW = CONTENT_WIDTH - 12
        val cardH = 36
        guiGraphics.fill(x, y, x + cardW, y + cardH, SECTION_CONTENT_BG)
        guiGraphics.fill(x, y, x + cardW, y + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y + cardH - 1, x + cardW, y + cardH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x, y, x + 1, y + cardH, BUTTON_BORDER_COLOR)
        guiGraphics.fill(x + cardW - 1, y, x + cardW, y + cardH, BUTTON_BORDER_COLOR)

        val modelBoxSize = 32
        val modelX = x + 2
        val modelY = y + 2

        val partner = partnerModel
        if (partner != null) {
            val matrices = guiGraphics.pose()
            matrices.pushPose()
            matrices.translate((modelX + 16).toDouble(), (modelY + 28).toDouble(), 0.0)
            drawProfilePokemon(
                partner, matrices, Quaternionf().rotateY(Math.toRadians(30.0).toFloat()),
                PoseType.PROFILE, posableState, 0f, 16f
            )
            matrices.popPose()

            val species = profile?.favoriteSpecies?.let { PokemonSpecies.getByIdentifier(ResourceLocation.parse(it)) }
            val name = species?.name ?: lang("favorite_pokemon")
            guiGraphics.drawString(font, name, x + 38, y + 8, NAME_COLOR, false)

            val tagLabel = lang("favorite_pokemon")
            guiGraphics.drawString(font, tagLabel, x + 38, y + 20, ACCENT_COLOR, false)

            if (profile?.favoriteAspects?.contains("shiny") == true) {
                guiGraphics.drawString(font, "★", x + cardW - 14, y + 6, 0xFFEAB308.toInt(), false)
            }
        } else {
            val label = lang("favorite_pokemon")
            guiGraphics.drawString(font, label, x + 8, y + 8, CONTENT_TEXT, false)
            if (isOwnProfile) {
                val chooseText = lang("choose_partner")
                guiGraphics.drawString(font, "« $chooseText »", x + 8, y + 20, ACCENT_COLOR, false)
            }
        }

        if (isOwnProfile) {
            val changeHovered = mouseX in (x + cardW - 20)..x + cardW && mouseY in y..(y + 14)
            SocialUi.drawIcon(guiGraphics, SocialUi.Icon.PLUS, x + cardW - 14, y + cardH - 14, if (changeHovered) ACCENT_COLOR else CONTENT_DIM)
        }
    }

    private fun renderPartnerPickerModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val pw = 140
        val ph = 60
        val px = screenX + (GUI_WIDTH - pw) / 2
        val py = screenY + (GUI_HEIGHT - ph) / 2

        guiGraphics.fill(px, py, px + pw, py + ph, SECTION_CONTENT_BG)
        guiGraphics.fill(px, py, px + pw, py + 1, BUTTON_BORDER_COLOR)
        guiGraphics.fill(px, py + ph - 1, px + pw, py + ph, BUTTON_BORDER_COLOR)
        guiGraphics.fill(px, py, px + 1, py + ph, BUTTON_BORDER_COLOR)
        guiGraphics.fill(px + pw - 1, py, px + pw, py + ph, BUTTON_BORDER_COLOR)

        guiGraphics.drawString(font, lang("choose_partner"), px + 8, py + 6, NAME_COLOR, false)
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.CLOSE, px + pw - 12, py + 6, CONTENT_DIM)

        val slots = CobblemonClient.storage.party.slots
        var slotX = px + 8
        slots.forEachIndexed { index, pokemon ->
            val slotHovered = mouseX in slotX..(slotX + 18) && mouseY in (py + 24)..(py + 44)
            guiGraphics.fill(slotX, py + 24, slotX + 18, py + 44, if (slotHovered) ROW_HOVER_BG else SECTION_CONTENT_BG)
            guiGraphics.fill(slotX, py + 24, slotX + 18, py + 25, BUTTON_BORDER_COLOR)
            guiGraphics.fill(slotX, py + 43, slotX + 18, py + 44, BUTTON_BORDER_COLOR)
            guiGraphics.fill(slotX, py + 24, slotX + 1, py + 44, BUTTON_BORDER_COLOR)
            guiGraphics.fill(slotX + 17, py + 24, slotX + 18, py + 44, BUTTON_BORDER_COLOR)

            if (pokemon != null) {
                val icon = pokemon.species.name.take(2).uppercase()
                guiGraphics.drawString(font, icon, slotX + 4, py + 29, ACCENT_COLOR, false)
            } else {
                guiGraphics.drawString(font, "-", slotX + 7, py + 29, CONTENT_DIM, false)
            }
            slotX += 21
        }
    }

    private fun renderCard(
        guiGraphics: GuiGraphics,
        p: SocialPostView,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        height: Int
    ) {
        SocialUi.surface(guiGraphics, x, y, CONTENT_WIDTH, height, colors = theme.colors)
        var cursorY = y + 4
        val textX = x + 4

        PlayerFaceRenderer.draw(
            guiGraphics,
            PlayerHeads.skinFor(p.authorUuid, p.authorName, p.skinUrl),
            textX,
            cursorY,
            8
        )
        guiGraphics.drawString(font, p.authorName, textX + 12, cursorY + 1, NAME_COLOR, false)

        val age = relativeTime(p.timestamp)
        guiGraphics.drawString(font, age, x + CONTENT_WIDTH - 4 - font.width(age), cursorY + 1, CONTENT_DIM, false)
        cursorY += 12

        guiGraphics.fill(textX, cursorY, x + CONTENT_WIDTH - 4, cursorY + 1, 0x22000000)
        cursorY += 4

        if (p.text.isNotBlank()) {
            val lines = font.split(Component.literal(p.text), CONTENT_WIDTH - 8)
            lines.forEach { line ->
                guiGraphics.drawString(font, line, textX, cursorY, CONTENT_TEXT, false)
                cursorY += font.lineHeight
            }
            cursorY += 3
        }

        val footerY = y + height - 16
        guiGraphics.fill(textX, footerY - 2, x + CONTENT_WIDTH - 4, footerY - 1, 0x22000000)

        // Like icon & count
        val heartHovered = mouseX in textX..(textX + 24) && mouseY in (footerY + 1)..(footerY + 14)
        val heartColor = if (p.likedByMe) 0xFFD84A4A.toInt() else if (heartHovered) ACCENT_COLOR else CONTENT_DIM
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.HEART, textX + 1, footerY + 2, heartColor)
        guiGraphics.drawString(font, p.likeCount.toString(), textX + 12, footerY + 2, heartColor, false)

        // Comment icon & count
        val likeW = 12 + font.width(p.likeCount.toString())
        val commentX = textX + likeW + 8
        val commentHovered = mouseX in commentX..(commentX + 24) && mouseY in (footerY + 1)..(footerY + 14)
        val commentColor = if (commentHovered) ACCENT_COLOR else CONTENT_DIM
        SocialUi.drawIcon(guiGraphics, SocialUi.Icon.MESSAGE, commentX, footerY + 2, commentColor)
        guiGraphics.drawString(font, p.commentCount.toString(), commentX + 11, footerY + 2, commentColor, false)
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        SmartphoneBatteryRenderer.render(guiGraphics, screenX, screenY, isLargeScreen = true, smartphoneStack)
        guiGraphics.fill(screenX + CONTENT_X, screenY + 24, screenX + CONTENT_X + CONTENT_WIDTH, screenY + LIST_START_Y, theme.colors.headerNavy)

        val backHovered = isInBack(mouseX, mouseY)
        guiGraphics.drawString(
            font,
            lang("back"),
            screenX + CONTENT_X + 4,
            screenY + TITLE_Y + 2,
            if (backHovered) theme.colors.gold else MUTED_COLOR,
            false
        )

        val title = lang(if (isOwnProfile) "my_profile" else "profile")
        guiGraphics.drawString(
            font,
            title,
            screenX + CONTENT_X + (CONTENT_WIDTH - font.width(title)) / 2,
            screenY + TITLE_Y + 2,
            NAME_COLOR,
            false
        )
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics) {
        val barX = screenX + CONTENT_X + CONTENT_WIDTH + 1
        val barY = screenY + LIST_START_Y
        val barH = LIST_END_Y - LIST_START_Y
        guiGraphics.fill(barX, barY, barX + 3, barY + barH, BUTTON_BORDER_COLOR)

        val thumbH = ((barH.toFloat() / (barH + maxScroll)) * barH).toInt().coerceAtLeast(10)
        val thumbY = barY + ((scrollY.toFloat() / maxScroll) * (barH - thumbH)).toInt()
        guiGraphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, ACCENT_COLOR)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        if (pickingPartner) {
            val pw = 140
            val ph = 60
            val px = screenX + (GUI_WIDTH - pw) / 2
            val py = screenY + (GUI_HEIGHT - ph) / 2
            if (mx in (px + pw - 14)..(px + pw) && my in py..(py + 14)) {
                playClickSound()
                pickingPartner = false
                return true
            }
            var slotX = px + 8
            val slots = CobblemonClient.storage.party.slots
            slots.indices.forEach { index ->
                if (mx in slotX..(slotX + 18) && my in (py + 24)..(py + 44)) {
                    playClickSound()
                    UpdateProfilePacket(bioInput.value, index).sendToServer()
                    pickingPartner = false
                    return true
                }
                slotX += 21
            }
            return true
        }

        if (isInBack(mx, my)) {
            playClickSound()
            minecraft?.setScreen(SocialScreen(color, smartphoneStack))
            return true
        }

        val profile = SocialProfileCache.getProfile(profileUuid)
        val headerH = measureProfileHeader(profile)
        val cardX = screenX + CONTENT_X
        val headerY = screenY + LIST_START_Y - scrollY

        // Follow / Unfollow button & Action Bar (Battle / Trade / Chat)
        if (!isOwnProfile && profile != null) {
            val followText = lang(if (profile.isFollowedByMe) "unfollow" else "follow")
            val btnW = font.width(followText) + 8
            val btnX = cardX + CONTENT_WIDTH - btnW - 6
            val btnY = headerY + 5
            if (mx in btnX..(btnX + btnW) && my in btnY..(btnY + 14)) {
                playClickSound()
                ToggleFollowPacket(profileUuid).sendToServer()
                return true
            }

            // Gameplay Interaction Bar (Battle | Trade | Chat)
            var actionY = headerY + 42
            val bio = profile.bio
            if (bio.isNotBlank()) {
                val lines = font.split(Component.literal(bio), CONTENT_WIDTH - 12)
                actionY += lines.size * font.lineHeight + 4
            }
            if (profile.favoriteSpecies != null) {
                actionY += 40
            }

            val totalW = CONTENT_WIDTH - 12
            val gap = 4
            val aBtnW = (totalW - gap * 2) / 3
            val aBtnH = 14
            val ax = cardX + 6

            if (my in actionY..(actionY + aBtnH)) {
                // 1. Battle Challenge
                if (mx in ax..(ax + aBtnW)) {
                    playClickSound()
                    ChallengeBattlePacket(profileUuid).sendToServer()
                    return true
                }
                // 2. Trade Request
                val tx = ax + aBtnW + gap
                if (mx in tx..(tx + aBtnW)) {
                    playClickSound()
                    ChallengeTradePacket(profileUuid).sendToServer()
                    return true
                }
                // 3. Message / DM
                val cx = tx + aBtnW + gap
                if (mx in cx..(cx + aBtnW)) {
                    playClickSound()
                    minecraft?.setScreen(DmThreadScreen(color, smartphoneStack, profileUuid, profile.name))
                    return true
                }
            }
        } else if (isOwnProfile) {
            val editBtnText = lang(if (editingBio) "save_bio" else "edit_bio")
            val btnW = font.width(editBtnText) + 8
            val btnX = cardX + CONTENT_WIDTH - btnW - 6
            val btnY = headerY + 5
            if (mx in btnX..(btnX + btnW) && my in btnY..(btnY + 14)) {
                playClickSound()
                if (editingBio) {
                    UpdateProfilePacket(bioInput.value, -1).sendToServer()
                    editingBio = false
                } else {
                    editingBio = true
                }
                return true
            }

            // Partner Pokémon button
            val partnerCardY = headerY + 42 + (if (bioInput.value.isNotBlank() && !editingBio) font.split(Component.literal(bioInput.value), CONTENT_WIDTH - 12).size * font.lineHeight + 4 else 0)
            if (mx in (cardX + 6)..(cardX + CONTENT_WIDTH - 6) && my in partnerCardY..(partnerCardY + 36)) {
                playClickSound()
                pickingPartner = true
                return true
            }
        }

        // Post card clicks
        val posts = SocialProfileCache.getPosts(profileUuid)
        var cursorY = headerY + headerH + 22
        posts.forEach { post ->
            val cardH = measureCard(post)
            if (mx in cardX..(cardX + CONTENT_WIDTH) && my in cursorY..(cursorY + cardH)) {
                playClickSound()
                minecraft?.setScreen(SocialPostDetailScreen(color, smartphoneStack, post))
                return true
            }
            cursorY += cardH + CARD_GAP
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) { // Escape
            if (pickingPartner) {
                pickingPartner = false
                return true
            }
            if (editingBio) {
                editingBio = false
                return true
            }
            minecraft?.setScreen(SocialScreen(color, smartphoneStack))
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (maxScroll > 0) {
            scrollY = (scrollY - (verticalAmount * 14).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun measureCard(p: SocialPostView): Int {
        var h = 20
        if (p.text.isNotBlank()) h += font.split(Component.literal(p.text), CONTENT_WIDTH - 8).size * font.lineHeight + 4
        h += 16
        return h
    }

    private fun isInBack(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (screenX + CONTENT_X + 2)..(screenX + CONTENT_X + 36) && mouseY in (screenY + TITLE_Y)..(screenY + TITLE_Y + 12)

    private fun relativeTime(timestamp: Long): String {
        val diffSec = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
        return when {
            diffSec < 60 -> lang("now")
            diffSec < 3600 -> "${diffSec / 60}m"
            diffSec < 86400 -> "${diffSec / 3600}h"
            diffSec < 172800 -> lang("one_day_ago")
            else -> lang("days_ago").format(diffSec / 86400)
        }
    }

    private fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (isInBack(mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable("cobblemon_smartphone.tooltip.social_back_to_social"), mouseX, mouseY)
        }
    }

    private fun lang(key: String): String = Component.translatable("cobblemon_smartphone.social.$key").string

    private fun playClickSound() {
        Minecraft.getInstance().player?.playSound(CobblemonSounds.GUI_CLICK, 0.5f, 1.0f)
    }

    companion object {
        const val GUI_WIDTH = 211
        const val GUI_HEIGHT = 207
        const val CONTENT_X = 27
        const val CONTENT_WIDTH = 157
        const val TITLE_Y = 28
        const val LIST_START_Y = 46
        const val LIST_END_Y = 194
        const val CARD_GAP = 4
    }
}
