package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.client.NotesClientState
import com.nbp.cobblemon_smartphone.client.ui.hd.ButtonNode
import com.nbp.cobblemon_smartphone.client.ui.hd.HdAlign
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBackground
import com.nbp.cobblemon_smartphone.client.ui.hd.HdBuilder
import com.nbp.cobblemon_smartphone.client.ui.hd.HdColorSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdIconAtlas
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPadding
import com.nbp.cobblemon_smartphone.client.ui.hd.HdPhoneScreen
import com.nbp.cobblemon_smartphone.client.ui.hd.HdSizeSpec
import com.nbp.cobblemon_smartphone.client.ui.hd.HdStateStore
import com.nbp.cobblemon_smartphone.client.ui.hd.HdTextLayout
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.util.NotesStorage
import com.nbp.cobblemon_smartphone.util.SmartphoneNote
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * HD Notes app built on [HdPhoneScreen] with search, pinned notes, color tags,
 * coordinate insertion, and automatic server-synced persistence.
 */
class NotesScreen(
    color: SmartphoneColor,
    smartphoneStack: ItemStack? = null,
    scaleOverride: Float? = null
) : HdPhoneScreen(
    color,
    smartphoneStack,
    Component.translatable("cobblemon_smartphone.screen.notes"),
    scaleOverride
) {
    override val viewportMode: ViewportMode = ViewportMode.Large
    override val showBezelHdBadge: Boolean = true

    private var editingNoteId: String? = null
    private var editingIsNew: Boolean = false
    private var editingPinned: Boolean = false
    private var editingColorTag: Int = 0
    private var confirmDeleteId: String? = null
    private var selectedColorFilter: Int = -1 // -1 = all

    override fun onBackPressed() {
        if (confirmDeleteId != null) {
            confirmDeleteId = null
            return
        }
        if (editingNoteId != null) {
            saveCurrentNoteIfNonEmpty(showSavedToast = false)
            closeEditor()
            return
        }
        super.onBackPressed()
    }

    override fun buildContent(theme: SmartphoneTheme, builder: HdBuilder) {
        if (editingNoteId != null) {
            buildEditorView(builder)
        } else {
            buildListView(theme, builder)
        }
    }

    private fun buildListView(theme: SmartphoneTheme, builder: HdBuilder) {
        val allNotes = NotesClientState.getNotes()
        val query = stateStore.getInputText(SEARCH_INPUT_ID)
        val filtered = NotesStorage.filterNotes(allNotes, query).let { list ->
            if (selectedColorFilter >= 0) list.filter { it.colorTag == selectedColorFilter } else list
        }

        builder.appHeader(
            title = tr("cobblemon_smartphone.screen.notes"),
            subtitle = "${allNotes.size} / ${NotesStorage.MAX_NOTES}",
            icon = HdIconAtlas.Icon.EDIT,
            iconColor = HdColorSpec.Token("primary")
        ) { hb ->
            val canCreate = allNotes.size < NotesStorage.MAX_NOTES
            hb.button(
                label = tr("cobblemon_smartphone.notes.new"),
                icon = HdIconAtlas.Icon.PLUS,
                variant = ButtonNode.Variant.PRIMARY,
                fontSize = 8.6f,
                radius = 8f,
                enabled = canCreate,
                onClick = {
                    playClickSound()
                    openNewNote()
                }
            ) {
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4f)
                tooltip = if (canCreate) tr("cobblemon_smartphone.notes.new_tooltip")
                else tr("cobblemon_smartphone.notes.limit_reached")
            }
        }

        // Search + color filter bar
        builder.column(
            gap = 4f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 3f),
            width = HdSizeSpec.Fill
        ) { bar ->
            bar.textInput(
                inputId = SEARCH_INPUT_ID,
                placeholder = tr("cobblemon_smartphone.notes.search"),
                maxLength = 64
            ) {
                fontSizeDp = 8.8f
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f)
            }

            bar.row(gap = 4f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { filterRow ->
                filterRow.pill(
                    label = tr("cobblemon_smartphone.notes.filter_all"),
                    active = selectedColorFilter == -1,
                    onClick = {
                        playClickSound()
                        selectedColorFilter = -1
                    }
                ) {
                    fontSizeDp = 7.6f
                    padding = HdPadding.symmetric(horizontal = 6f, vertical = 2.5f)
                }
                for (tagIdx in 0 until NotesStorage.COLOR_TAG_COUNT) {
                    val active = selectedColorFilter == tagIdx
                    val tagColor = TAG_COLORS[tagIdx]
                    filterRow.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fixed(15f),
                        height = HdSizeSpec.Fixed(15f)
                    ) { dot ->
                        background = HdBackground(
                            color = HdColorSpec.Fixed(tagColor),
                            radius = 7.5f,
                            borderWidth = if (active) 1.8f else 0.7f,
                            borderColor = if (active) HdColorSpec.Token("text_dark") else HdColorSpec.Token("primary_border")
                        )
                        tooltip = tr("cobblemon_smartphone.notes.color.$tagIdx")
                        onClick = {
                            playClickSound()
                            selectedColorFilter = if (selectedColorFilter == tagIdx) -1 else tagIdx
                        }
                        if (active) {
                            dot.icon(HdIconAtlas.Icon.CHECK, size = 8f, color = HdColorSpec.Fixed(0xFFFFFFFF.toInt()))
                        }
                    }
                }
            }
        }

        builder.scroll(
            scrollId = LIST_SCROLL_ID,
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 4f)
        ) { list ->
            if (filtered.isEmpty()) {
                val isFiltering = query.isNotBlank() || selectedColorFilter >= 0
                list.emptyState(
                    title = tr(if (isFiltering) "cobblemon_smartphone.notes.no_results" else "cobblemon_smartphone.notes.empty_title"),
                    description = if (isFiltering) null else tr("cobblemon_smartphone.notes.empty_desc"),
                    icon = HdIconAtlas.Icon.EDIT,
                    actionLabel = if (!isFiltering && allNotes.size < NotesStorage.MAX_NOTES) tr("cobblemon_smartphone.notes.new") else null,
                    actionIcon = HdIconAtlas.Icon.PLUS,
                    onAction = if (!isFiltering && allNotes.size < NotesStorage.MAX_NOTES) {
                        {
                            playClickSound()
                            openNewNote()
                        }
                    } else null
                )
            } else {
                for (note in filtered) {
                    buildNoteCard(theme, list, note)
                }
            }
        }
    }

    private fun buildNoteCard(theme: SmartphoneTheme, list: HdBuilder, note: SmartphoneNote) {
        val accentCol = TAG_COLORS.getOrElse(note.colorTag) { TAG_COLORS[0] }
        list.card(
            gap = 3f,
            padding = HdPadding.symmetric(horizontal = 9f, vertical = 7f),
            radius = 9f,
            shadowBlur = 4f,
            hoverHighlight = true,
            onClick = {
                playClickSound()
                openExistingNote(note)
            }
        ) { card ->
            card.row(gap = 5f, crossAlign = HdAlign.CENTER, width = HdSizeSpec.Fill) { top ->
                // Color tag dot
                top.stack(
                    width = HdSizeSpec.Fixed(7f),
                    height = HdSizeSpec.Fixed(7f)
                ) {
                    background = HdBackground(color = HdColorSpec.Fixed(accentCol), radius = 3.5f)
                }

                if (note.pinned) {
                    top.icon(HdIconAtlas.Icon.STAR_FILLED, size = 9.5f, color = HdColorSpec.Fixed(theme.colors.contentGold))
                }

                val displayTitle = note.title.ifBlank { tr("cobblemon_smartphone.notes.untitled") }
                top.text(
                    text = displayTitle,
                    size = 9.4f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_dark")
                ) {
                    width = HdSizeSpec.Weight(1f)
                }

                // Quick pin toggle (high contrast on both light and dark surfaces)
                top.stack(
                    hAlign = HdAlign.CENTER,
                    vAlign = HdAlign.CENTER,
                    width = HdSizeSpec.Fixed(20f),
                    height = HdSizeSpec.Fixed(20f)
                ) { pinBtn ->
                    background = HdBackground(
                        color = HdColorSpec.Token("surface_alt"),
                        radius = 6f,
                        borderWidth = 0.6f,
                        borderColor = HdColorSpec.Token("primary_border", 0.55f)
                    )
                    hoverBackground = HdBackground(
                        color = HdColorSpec.Token("surface_hover"),
                        radius = 6f,
                        borderWidth = 0.8f,
                        borderColor = HdColorSpec.Token("primary")
                    )
                    tooltip = tr(if (note.pinned) "cobblemon_smartphone.notes.unpin" else "cobblemon_smartphone.notes.pin")
                    onClick = {
                        playClickSound()
                        NotesClientState.togglePin(note.id)
                    }
                    val pinColor = if (note.pinned) HdColorSpec.Fixed(theme.colors.contentGold) else HdColorSpec.Token("text_dark_muted")
                    pinBtn.icon(
                        icon = if (note.pinned) HdIconAtlas.Icon.STAR_FILLED else HdIconAtlas.Icon.STAR,
                        size = 10.5f,
                        color = pinColor
                    )
                }

                // Quick delete (high contrast danger icon on surface)
                top.stack(
                    hAlign = HdAlign.CENTER,
                    vAlign = HdAlign.CENTER,
                    width = HdSizeSpec.Fixed(20f),
                    height = HdSizeSpec.Fixed(20f)
                ) { delBtn ->
                    background = HdBackground(
                        color = HdColorSpec.Token("surface_alt"),
                        radius = 6f,
                        borderWidth = 0.6f,
                        borderColor = HdColorSpec.Token("primary_border", 0.55f)
                    )
                    hoverBackground = HdBackground(
                        color = HdColorSpec.Token("danger", 0.16f),
                        radius = 6f,
                        borderWidth = 0.8f,
                        borderColor = HdColorSpec.Token("danger")
                    )
                    tooltip = tr("cobblemon_smartphone.notes.delete")
                    onClick = {
                        playClickSound()
                        confirmDeleteId = note.id
                    }
                    delBtn.icon(
                        icon = HdIconAtlas.Icon.TRASH,
                        size = 10.5f,
                        color = HdColorSpec.Token("danger")
                    )
                }
            }

            if (note.content.isNotBlank()) {
                val preview = note.content.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString(" • ")
                    .take(120)
                if (preview.isNotEmpty()) {
                    card.text(
                        text = preview,
                        size = 8.1f,
                        color = HdColorSpec.Token("text_dark_muted"),
                        wrap = true
                    ) {
                        width = HdSizeSpec.Fill
                    }
                }
            }

            card.text(
                text = formatTimestamp(note.updatedAt),
                size = 7.2f,
                color = HdColorSpec.Token("text_dark_muted"),
                align = HdAlign.END
            ) {
                width = HdSizeSpec.Fill
            }
        }
    }

    private fun buildEditorView(builder: HdBuilder) {
        builder.appHeader(
            title = tr(if (editingIsNew) "cobblemon_smartphone.notes.new_title" else "cobblemon_smartphone.notes.edit_title"),
            icon = HdIconAtlas.Icon.EDIT,
            iconColor = HdColorSpec.Fixed(TAG_COLORS.getOrElse(editingColorTag) { TAG_COLORS[0] })
        ) { hb ->
            // Insert current player coordinates
            hb.button(
                icon = HdIconAtlas.Icon.COMPASS,
                variant = ButtonNode.Variant.SURFACE,
                radius = 7f,
                onClick = {
                    playClickSound()
                    insertCurrentCoordinates()
                }
            ) {
                width = HdSizeSpec.Fixed(22f)
                height = HdSizeSpec.Fixed(22f)
                padding = HdPadding.ZERO
                iconSizeDp = 11f
                tooltip = tr("cobblemon_smartphone.notes.insert_coords")
            }

            // Pin toggle
            hb.button(
                icon = if (editingPinned) HdIconAtlas.Icon.STAR_FILLED else HdIconAtlas.Icon.STAR,
                variant = if (editingPinned) ButtonNode.Variant.PRIMARY else ButtonNode.Variant.SURFACE,
                radius = 7f,
                onClick = {
                    playClickSound()
                    editingPinned = !editingPinned
                }
            ) {
                width = HdSizeSpec.Fixed(22f)
                height = HdSizeSpec.Fixed(22f)
                padding = HdPadding.ZERO
                iconSizeDp = 11f
                tooltip = tr(if (editingPinned) "cobblemon_smartphone.notes.unpin" else "cobblemon_smartphone.notes.pin")
            }

            // Delete (only for existing note)
            if (!editingIsNew && editingNoteId != null) {
                hb.button(
                    icon = HdIconAtlas.Icon.TRASH,
                    variant = ButtonNode.Variant.DANGER,
                    radius = 7f,
                    onClick = {
                        playClickSound()
                        confirmDeleteId = editingNoteId
                    }
                ) {
                    width = HdSizeSpec.Fixed(22f)
                    height = HdSizeSpec.Fixed(22f)
                    padding = HdPadding.ZERO
                    iconSizeDp = 11f
                    tooltip = tr("cobblemon_smartphone.notes.delete")
                }
            }

            // Save button
            hb.button(
                label = tr("cobblemon_smartphone.notes.save"),
                icon = HdIconAtlas.Icon.CHECK,
                variant = ButtonNode.Variant.PRIMARY,
                fontSize = 8.4f,
                radius = 7f,
                onClick = {
                    playClickSound()
                    if (saveCurrentNoteIfNonEmpty(showSavedToast = true)) {
                        closeEditor()
                    }
                }
            ) {
                height = HdSizeSpec.Fixed(22f)
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 0f)
                iconSizeDp = 10f
            }
        }

        // Editor body
        builder.column(
            gap = 6f,
            padding = HdPadding.symmetric(horizontal = 7f, vertical = 5f),
            width = HdSizeSpec.Fill,
            height = HdSizeSpec.Weight(1f)
        ) { ed ->
            // Color tag picker inside a surface pill for reliable contrast on all themes
            ed.row(
                gap = 6f,
                crossAlign = HdAlign.CENTER,
                padding = HdPadding.symmetric(horizontal = 8f, vertical = 4.5f),
                width = HdSizeSpec.Fill,
                background = HdBackground(
                    color = HdColorSpec.Token("surface"),
                    radius = 9f,
                    borderWidth = 0.8f,
                    borderColor = HdColorSpec.Token("primary_border")
                )
            ) { colorRow ->
                colorRow.text(
                    text = tr("cobblemon_smartphone.notes.color_label"),
                    size = 8.2f,
                    weight = HdTextLayout.Weight.BOLD,
                    color = HdColorSpec.Token("text_dark")
                )
                for (tagIdx in 0 until NotesStorage.COLOR_TAG_COUNT) {
                    val selected = editingColorTag == tagIdx
                    val tagColor = TAG_COLORS[tagIdx]
                    colorRow.stack(
                        hAlign = HdAlign.CENTER,
                        vAlign = HdAlign.CENTER,
                        width = HdSizeSpec.Fixed(16f),
                        height = HdSizeSpec.Fixed(16f)
                    ) { dot ->
                        background = HdBackground(
                            color = HdColorSpec.Fixed(tagColor),
                            radius = 8f,
                            borderWidth = if (selected) 1.8f else 0.7f,
                            borderColor = if (selected) HdColorSpec.Token("text_dark") else HdColorSpec.Token("primary_border")
                        )
                        tooltip = tr("cobblemon_smartphone.notes.color.$tagIdx")
                        onClick = {
                            playClickSound()
                            editingColorTag = tagIdx
                        }
                        if (selected) {
                            dot.icon(HdIconAtlas.Icon.CHECK, size = 8.5f, color = HdColorSpec.Fixed(0xFFFFFFFF.toInt()))
                        }
                    }
                }
            }

            // Title input
            ed.textInput(
                inputId = TITLE_INPUT_ID,
                placeholder = tr("cobblemon_smartphone.notes.title_placeholder"),
                maxLength = NotesStorage.MAX_TITLE_LENGTH
            ) {
                fontSizeDp = 9.8f
                padding = HdPadding.symmetric(horizontal = 9f, vertical = 6f)
            }

            // Content multiline input
            ed.textInput(
                inputId = BODY_INPUT_ID,
                placeholder = tr("cobblemon_smartphone.notes.content_placeholder"),
                maxLength = NotesStorage.MAX_CONTENT_LENGTH,
                multiline = true,
                showCounter = true
            ) {
                width = HdSizeSpec.Fill
                height = HdSizeSpec.Weight(1f)
                fontSizeDp = 9.0f
                padding = HdPadding.all(9f)
            }
        }
    }

    override fun buildModal(theme: SmartphoneTheme): ModalSpec? {
        val targetId = confirmDeleteId ?: return null
        return ModalSpec(
            title = tr("cobblemon_smartphone.notes.delete_confirm_title"),
            widthDp = 240f,
            onClose = { confirmDeleteId = null }
        ) { mb ->
            mb.text(
                text = tr("cobblemon_smartphone.notes.delete_confirm_body"),
                size = 8.8f,
                color = HdColorSpec.Token("text_dark"),
                wrap = true
            ) {
                width = HdSizeSpec.Fill
            }
            mb.row(gap = 8f, width = HdSizeSpec.Fill) { actions ->
                actions.button(
                    label = tr("cobblemon_smartphone.social.cancel"),
                    variant = ButtonNode.Variant.SURFACE,
                    radius = 8f,
                    onClick = {
                        playClickSound()
                        confirmDeleteId = null
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
                actions.button(
                    label = tr("cobblemon_smartphone.notes.delete"),
                    icon = HdIconAtlas.Icon.TRASH,
                    variant = ButtonNode.Variant.DANGER,
                    radius = 8f,
                    onClick = {
                        playClickSound()
                        NotesClientState.delete(targetId)
                        confirmDeleteId = null
                        if (editingNoteId == targetId) {
                            closeEditor()
                        }
                        showToast(
                            tr("cobblemon_smartphone.notes.deleted"),
                            HdStateStore.ToastKind.INFO,
                            HdIconAtlas.Icon.TRASH
                        )
                    }
                ) {
                    width = HdSizeSpec.Weight(1f)
                }
            }
        }
    }

    private fun openNewNote() {
        editingNoteId = UUID.randomUUID().toString()
        editingIsNew = true
        editingPinned = false
        editingColorTag = if (selectedColorFilter >= 0) selectedColorFilter else 0
        setInputContent(TITLE_INPUT_ID, "")
        setInputContent(BODY_INPUT_ID, "")
        stateStore.clearFocus()
    }

    private fun openExistingNote(note: SmartphoneNote) {
        editingNoteId = note.id
        editingIsNew = false
        editingPinned = note.pinned
        editingColorTag = note.colorTag
        setInputContent(TITLE_INPUT_ID, note.title)
        setInputContent(BODY_INPUT_ID, note.content)
        stateStore.clearFocus()
    }

    private fun closeEditor() {
        editingNoteId = null
        editingIsNew = false
        stateStore.clearFocus()
    }

    private fun setInputContent(inputId: String, text: String) {
        val st = stateStore.input(inputId, text)
        st.text = text
        st.cursor = text.length
        st.selectionAnchor = null
        st.scrollOffsetX = 0f
        st.resetBlink()
    }

    private fun insertCurrentCoordinates() {
        val player = Minecraft.getInstance().player ?: return
        val x = player.blockX
        val y = player.blockY
        val z = player.blockZ
        val dim = player.level().dimension().location().path
            .replace('_', ' ')
            .replaceFirstChar { it.uppercaseChar() }
        val snippet = "XYZ: $x, $y, $z ($dim)"

        val st = stateStore.input(BODY_INPUT_ID)
        val current = st.text
        val separator = if (current.isNotEmpty() && !current.endsWith("\n")) "\n" else ""
        val next = (current + separator + snippet).take(NotesStorage.MAX_CONTENT_LENGTH)
        st.text = next
        st.cursor = next.length
        st.selectionAnchor = null
        st.resetBlink()

        showToast(
            tr("cobblemon_smartphone.notes.coords_inserted"),
            HdStateStore.ToastKind.SUCCESS,
            HdIconAtlas.Icon.COMPASS
        )
    }

    private fun saveCurrentNoteIfNonEmpty(showSavedToast: Boolean): Boolean {
        val id = editingNoteId ?: return false
        val title = stateStore.getInputText(TITLE_INPUT_ID).trim()
        val body = stateStore.getInputText(BODY_INPUT_ID)
        if (title.isBlank() && body.isBlank()) {
            if (showSavedToast) {
                showToast(
                    tr("cobblemon_smartphone.notes.empty_warning"),
                    HdStateStore.ToastKind.WARNING,
                    HdIconAtlas.Icon.WARNING
                )
            }
            return false
        }

        val note = SmartphoneNote(
            id = id,
            title = title,
            content = body,
            pinned = editingPinned,
            colorTag = editingColorTag,
            updatedAt = System.currentTimeMillis()
        )
        val ok = NotesClientState.upsert(note)
        if (!ok) {
            showToast(
                tr("cobblemon_smartphone.notes.limit_reached"),
                HdStateStore.ToastKind.ERROR,
                HdIconAtlas.Icon.WARNING
            )
            return false
        }
        if (showSavedToast) {
            showToast(
                tr("cobblemon_smartphone.notes.saved"),
                HdStateStore.ToastKind.SUCCESS,
                HdIconAtlas.Icon.CHECK
            )
        }
        return true
    }

    private fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return try {
            DATE_FORMAT.format(Date(epochMs))
        } catch (_: Exception) {
            ""
        }
    }

    private fun tr(key: String): String = Component.translatable(key).string

    companion object {
        private const val SEARCH_INPUT_ID = "notes_search"
        private const val LIST_SCROLL_ID = "notes_list"
        private const val TITLE_INPUT_ID = "notes_editor_title"
        private const val BODY_INPUT_ID = "notes_editor_body"

        private val DATE_FORMAT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        private val TAG_COLORS = intArrayOf(
            0xFF3A96B6.toInt(), // 0: Default / Cyan
            0xFFEF4444.toInt(), // 1: Red
            0xFFF59E0B.toInt(), // 2: Amber
            0xFF22C55E.toInt(), // 3: Green
            0xFF3B82F6.toInt(), // 4: Blue
            0xFFA855F7.toInt()  // 5: Purple
        )
    }
}
