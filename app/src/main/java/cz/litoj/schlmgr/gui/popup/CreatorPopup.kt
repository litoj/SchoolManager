package cz.litoj.schlmgr.gui.popup

import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.gui.engine.objects.Word
import cz.litoj.schlmgr.gui.list.HierarchyItemModel

/**
 * Describes the type-specific part of the [CreatorPopup] — everything besides the shared
 * header, name, description and (edit-only) position picker.
 */
sealed class CreatorContent {

    /** Name + description only, e.g. a main chapter. */
    object Simple : CreatorContent()

    /** A chapter: offers the "chapter file" checkbox. */
    class Chapter(val initialFile: Boolean) : CreatorContent()

    /** A word: offers the translations editor. */
    class Word(val initial: List<TranslationRow>) : CreatorContent()

    companion object {
        /** Java-friendly access to [Simple] (avoids `Simple.INSTANCE`). */
        @JvmField
        val SIMPLE: CreatorContent = Simple
    }
}

/**
 * One editable translation row of the word editor.
 *
 * @param source the existing translation being edited, or `null` for a newly added one
 */
@Stable
class TranslationRow(name: String, desc: String, val source: Word?) {

    val id: Long = nextId++

    var name by mutableStateOf(name)
    var desc by mutableStateOf(desc)

    private companion object {
        var nextId = 0L
    }
}

/**
 * The one-line height the position picker is clamped to; the surrounding tall drum is
 * clipped to this so the picker reads as a single line like the text fields beside it.
 */
private val OneLineHeight = 40.dp

/**
 * The item creation/edit screen, rendered with Compose inside the [AbstractPopup] shell
 * (dimming, outside-click dirty-guard and back handling preserved).
 *
 * Two modes:
 *  - **Creation** — name, description and the type-specific editor; no position picker.
 *  - **Editing** — additionally always shows the horizontal position picker, pre-filled
 *    with the edited item's current position.
 *
 * Tapping the empty space outside the card only closes the popup when nothing was changed
 * (see [canDismissByOutsideTouch]).
 *
 * @param header the popup title (e.g. "New chapter" / "Edit")
 * @param edited the item model being edited, or `null` when creating a new one
 * @param initialPosition current 1-based position of the edited item
 * @param maxPosition the highest position the edited item can be moved to
 * @param content the type-specific editor
 */
class CreatorPopup(
    private val header: String,
    val edited: HierarchyItemModel?,
    initialPosition: Int,
    val maxPosition: Int,
    private val content: CreatorContent,
) : AbstractPopup(0, true) {

    private var okListener: Runnable? = null
    private var cancelListener: Runnable? = null

    // Exposed publicly so the (still Java) call sites read them as getName()/getDesc()/…
    var name by mutableStateOf(edited?.bd?.name ?: "")
    var desc by mutableStateOf(
        if (edited != null) edited.bd.getDesc(edited.parent) ?: "" else ""
    )
    var position by mutableIntStateOf(initialPosition)

    var isChapterFile by mutableStateOf(
        (content as? CreatorContent.Chapter)?.initialFile ?: false
    )

    val translations: SnapshotStateList<TranslationRow> =
        ((content as? CreatorContent.Word)?.initial
            ?: emptyList()).toMutableStateList()
            // A new word always starts with one empty translation ready to type into.
            .apply { if (isEmpty()) add(TranslationRow("", "", null)) }

    val removedTranslations = mutableListOf<Word>()

    // Snapshots taken at open time, used for the unsaved-changes guard.
    private val initialName = name
    private val initialDesc = desc
    private val initialPositionSnapshot = initialPosition
    private val initialChapterFile = isChapterFile
    private val initialRows = translations.map { it.name to it.desc }

    init {
        create()
    }

    /** `true` once the user has changed anything in this popup. */
    val isDirty: Boolean
        get() = name != initialName || desc != initialDesc ||
            (edited != null && position != initialPositionSnapshot) ||
            isChapterFile != initialChapterFile ||
            removedTranslations.isNotEmpty() ||
            translations.map { it.name to it.desc } != initialRows

    override fun canDismissByOutsideTouch(): Boolean = !isDirty

    /** Focus handles for each translation row's name field, keyed by [TranslationRow.id]. */
    private val nameFieldFocus = mutableStateMapOf<Long, FocusRequester>()

    /** Set when a row was just appended and should grab focus once composed. */
    private var pendingFocusId by mutableStateOf<Long?>(null)

    /**
     * Moves focus to the name field of the translation after [fromIndex] (the main word
     * name field passes `-1`), appending a fresh row when currently on the last one.
     */
    private fun focusNextTranslation(fromIndex: Int) {
        val next = translations.getOrNull(fromIndex + 1)
        if (next != null) {
            val focused =
                runCatching { nameFieldFocus[next.id]?.requestFocus() }.isSuccess
            if (!focused) pendingFocusId = next.id
        } else {
            val row = TranslationRow("", "", null)
            translations.add(row)
            pendingFocusId = row.id
        }
    }

    override fun onWindowCreated(pw: PopupWindow) {
        // Resize the popup window above the keyboard so the Cancel/OK bar always stays
        // visible while typing. Compose's imePadding is unreliable in popup windows.
        pw.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    fun setOkListener(listener: Runnable) {
        okListener = listener
    }

    override fun addContent(view: ViewGroup) {
        val compose = ComposeView(view.context).apply {
            // Default strategy (dispose on detach) — the popup is short-lived and recreated;
            // tying composition disposal to the activity lifecycle would leak it.
            setContent { CreatorPopupScreen() }
        }
        (view as FrameLayout).addView(
            compose,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    @Composable
    private fun CreatorPopupScreen() {
        // Popup colors mirror the app theme (res/values/colors.xml) instead of being
        // hard-coded here, so a theme change only needs to touch the resource file.
        val cardColor = colorResource(R.color.colorPrimaryBg)
        val textColor = colorResource(R.color.colorPrimaryFg)
        val dividerColor = Color(0x88888888)
        val accentColor = colorResource(R.color.colorPrimaryHeader)

        // Outside-dismiss: tapping the dimmed area around the card closes the popup, unless
        // the dirty-guard blocks it. The ComposeView consumes all touches, so the View-layer
        // listener in AbstractPopup never fires — handle it here in Compose instead. The
        // card is a child of this Box, so it consumes its own taps first; this handler only
        // sees taps that landed on the backdrop.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (canDismissByOutsideTouch()) dismiss()
                    }
                }
        ) {
            Column(
                Modifier
                    .padding(25.dp)
                    .fillMaxWidth()
                    .background(cardColor)
            ) {
                // Fixed header: title, divider, name and description are always visible.
                Text(
                    header,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    style = TextStyle(fontSize = 25.sp, color = textColor),
                    textAlign = TextAlign.Center
                )
                ThinDivider(0.7.dp, dividerColor)
                // Enter in the word name jumps to the first translation's name (or adds one).
                BorderedTextField(
                    name,
                    { name = it },
                    R.string.data_name,
                    textColor,
                    singleLine = true,
                    onImeAction = {
                        if (content is CreatorContent.Word) focusNextTranslation(
                            -1
                        )
                    }
                )
                BorderedTextField(desc, { desc = it }, R.string.data_desc, textColor)
                // Variable middle: position picker + type-specific editor scroll when they
                // outgrow the (keyboard-resized) window, so the header and the Cancel/OK bar
                // below always stay visible.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (edited != null) PositionPicker(textColor, dividerColor)
                    when (content) {
                        is CreatorContent.Chapter -> ChapterEditor(
                            textColor,
                            accentColor
                        )

                        is CreatorContent.Word -> WordEditor(
                            textColor,
                            dividerColor
                        )

                        else -> Unit
                    }
                }
                ThinDivider(0.7.dp, dividerColor)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    PopupTextButton(
                        stringResource(R.string.cancel),
                        textColor
                    ) { notifyCancel() }
                    Box(
                        Modifier
                            .width(0.5.dp)
                            .padding(vertical = 10.dp)
                            .fillMaxHeight()
                            .background(dividerColor)
                    )
                    PopupTextButton("OK", textColor) { notifyOk() }
                }
            }
        }
    }

    @Composable
    private fun ChapterEditor(textColor: Color, accentColor: Color) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChapterFile,
                onCheckedChange = { isChapterFile = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                ),
            )
            Text(
                stringResource(R.string.chapter_file),
                Modifier.padding(start = 4.dp),
                style = TextStyle(fontSize = 16.sp, color = textColor)
            )
        }
    }

    @Composable
    private fun WordEditor(textColor: Color, dividerColor: Color) {
        // Not a LazyColumn: the enclosing middle section already scrolls, and nesting
        // same-direction scrollables would let the inner one steal the drags.
        Column(Modifier.fillMaxWidth()) {
            translations.forEach { row ->
                TranslationRowEditor(row, textColor, dividerColor)
            }
            // Just the centered add icon — no label, no separator above it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { translations.add(TranslationRow("", "", null)) }
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_word),
                    tint = Color.Unspecified,
                )
            }
        }
    }

    @Composable
    private fun TranslationRowEditor(
        row: TranslationRow,
        textColor: Color,
        dividerColor: Color
    ) {
        // Focus handle for this row's name field, used by the Enter-chain.
        val nameFocus = remember(row.id) { FocusRequester() }
        nameFieldFocus[row.id] = nameFocus
        // Keyed on pendingFocusId as well so that re-targeting an already-composed row
        // re-runs the effect and actually moves focus there (otherwise IME-Next falls
        // through to the description field). Newly appended rows are focused as soon as
        // they are composed.
        LaunchedEffect(row.id, pendingFocusId) {
            if (pendingFocusId == row.id) {
                pendingFocusId = null
                runCatching { nameFocus.requestFocus() }
            }
        }
        // Same layout as the main frame: the row's start padding aligns the bordered
        // fields' left edge with the main fields, and the delete icon keeps the same
        // margin from the card's right edge so it doesn't hug the popup.
        Column(Modifier.fillMaxWidth()) {
            ThinDivider(0.6.dp, dividerColor)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 5.dp)
                ) {
                    BorderedTextField(
                        row.name,
                        { row.name = it },
                        R.string.data_translate,
                        textColor,
                        singleLine = true,
                        onImeAction = { focusNextTranslation(translations.indexOf(row)) },
                        focusRequester = nameFocus,
                        horizontalPadding = 0.dp,
                    )
                    BorderedTextField(
                        row.desc,
                        { row.desc = it },
                        R.string.data_desc,
                        textColor,
                        horizontalPadding = 0.dp
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_cancel_round),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 12.5.dp)
                        .size(30.dp)
                        .clickable {
                            row.source?.let { removedTranslations.add(it) }
                            translations.remove(row)
                        }
                )
            }
            Spacer(Modifier.height(5.dp))
        }
    }

    /**
     * A compact single-line row: the "Position" label with a drum-style [NumberPicker]
     * next to it, separated from the type-specific editor below by a divider. The drum
     * is clamped to one-line height so only the current value is visible; flings still
     * walk through the whole 1..[maxPosition] range.
     */
    @Composable
    private fun PositionPicker(textColor: Color, dividerColor: Color) {
        Column(
            Modifier
                .padding(top = 5.dp)
                .fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.position),
                    Modifier.weight(1f),
                    style = TextStyle(fontSize = 18.sp, color = textColor),
                )
                // The NumberPicker always renders a 3-slot wheel; center the tall drum in a
                // one-line window clipped with clipToBounds so only the middle, current
                // value peeks through and the half-cut neighbors are gone.
                Box(
                    Modifier
                        .height(OneLineHeight)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { context ->
                            NumberPicker(context).apply {
                                minValue = 1
                                maxValue = this@CreatorPopup.maxPosition
                                wrapSelectorWheel = false
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setOnValueChangedListener { _, _, newVal ->
                                    position = newVal
                                }
                            }
                        },
                        update = { picker ->
                            if (picker.value != position) picker.value = position
                        },
                        // 3x the window => clips exactly to the middle (selected) slot.
                        modifier = Modifier.height(OneLineHeight * 3),
                    )
                }
            }
            ThinDivider(1.dp, dividerColor, topPadding = 5.dp)
        }
    }

    /**
     * The bordered text field replicating the old `outline_gray` drawable: a thin gray
     * border with inner padding, a hint shown when empty, no floating label.
     */
    @Composable
    private fun BorderedTextField(
        value: String,
        onValueChange: (String) -> Unit,
        @StringRes label: Int,
        textColor: Color,
        singleLine: Boolean = false,
        onImeAction: (() -> Unit)? = null,
        focusRequester: FocusRequester? = null,
        horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    ) {
        val fontSize = 17.sp
        val style = TextStyle(fontSize = fontSize, color = textColor)
        val state = rememberTextFieldState(value)
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(
            value
        )
        LaunchedEffect(state) {
            snapshotFlow { state.text.toString() }.collect(onValueChange)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 5.dp)
                .border(0.8.dp, Color(0x88888888))
                .padding(5.dp)
        ) {
            if (state.text.isEmpty()) {
                Text(
                    stringResource(label),
                    style = style.copy(color = textColor.copy(alpha = 0.5f)),
                )
            }
            BasicTextField(
                state = state,
                lineLimits = if (singleLine) TextFieldLineLimits.SingleLine
                else TextFieldLineLimits.Default,
                textStyle = style,
                cursorBrush = SolidColor(textColor),
                keyboardOptions = KeyboardOptions(
                    imeAction =
                        if (onImeAction != null) ImeAction.Next else ImeAction.Default
                ),
                // NOTE: give the non-null action a name — inside the SAM lambda the
                // implicit `it` is `performDefaultAction`, so the naive `let { …{ it() } }`
                // would just run the default focus move (landing on the description field)
                // instead of our Enter chain. Covers both the soft Next key and hardware
                // Enter (single-line fields route Enter here).
                onKeyboardAction = onImeAction?.let { action ->
                    KeyboardActionHandler { action() }
                },
                modifier = (if (focusRequester != null) Modifier.focusRequester(
                    focusRequester
                )
                else Modifier).fillMaxWidth()
            )
        }
    }

    @Composable
    private fun RowScope.PopupTextButton(
        text: String,
        textColor: Color,
        onClick: () -> Unit
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = TextStyle(fontSize = 18.sp, color = textColor))
        }
    }

    @Composable
    private fun ThinDivider(
        thickness: androidx.compose.ui.unit.Dp,
        color: Color,
        topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .height(thickness)
                .background(color)
        )
    }

    private fun notifyCancel() {
        cancelListener?.run()
        dismiss()
    }

    private fun notifyOk() {
        okListener?.run()
    }
}
