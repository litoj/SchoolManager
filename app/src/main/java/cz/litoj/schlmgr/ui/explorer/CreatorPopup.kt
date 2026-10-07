package cz.litoj.schlmgr.ui.explorer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import cz.litoj.schlmgr.ui.popup.DialogSpec
import cz.litoj.schlmgr.ui.popup.Dialogs

/**
 * Describes the type-specific part of the [CreatorPopup] — everything besides the shared
 * header, name and description fields.
 *
 * Only words have one: the translations editor. The former chapter variant (the
 * file-chapter checkbox) is gone with the engine's `SaveChapter` — every chapter is a
 * database chapter now, edited with name + description + position alone.
 */
sealed class CreatorContent {

    /** Name + description only: subjects, chapters and notes. */
    object Simple : CreatorContent()

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
 * @param source the existing translation ([DbItem]) being edited, or `null` for a newly
 * added one
 */
@Stable
class TranslationRow(name: String, desc: String, val source: DbItem?) {

    val id: Long = nextId++

    var name by mutableStateOf(name)
    var desc by mutableStateOf(desc)

    private companion object {
        var nextId = 0L
    }
}

/**
 * The item creation/edit screen, rendered through the shared dialog host
 * (dimming, outside-click dirty-guard and back handling preserved).
 *
 * Creation and editing share the same fields: name, description and the
 * type-specific editor. Items are reordered by dragging them in the list, so
 * there is no position picker.
 *
 * Tapping the empty space outside the card only closes the dialog when nothing was
 * changed (see [canDismissByOutsideTouch]).
 *
 * @param header the dialog title (e.g. "New chapter" / "Edit")
 * @param edited the item row being edited, or `null` when creating a new one
 * @param insertPosition 1-based slot the created item is inserted at (the list
 *   end by default); never read when [edited] is not `null`
 * @param content the type-specific editor
 */
class CreatorPopup(
    private val header: String,
    val edited: ItemRow?,
    insertPosition: Int,
    private val content: CreatorContent,
) : DialogSpec {

    private var okListener: Runnable? = null
    private var cancelListener: Runnable? = null

    // Exposed publicly so the call sites read them as name/desc/insert position.
    var name by mutableStateOf(edited?.item?.name ?: "")
    var desc by mutableStateOf(edited?.item?.description ?: "")
    var position by mutableIntStateOf(insertPosition)

    val translations: SnapshotStateList<TranslationRow> =
        ((content as? CreatorContent.Word)?.initial
            ?: emptyList()).toMutableStateList()
            // A new word always starts with one empty translation ready to type into.
            .apply { if (isEmpty()) add(TranslationRow("", "", null)) }

    /** The existing translations whose rows were removed in the editor. */
    val removedTranslations = mutableListOf<DbItem>()

    // Snapshots taken at open time, used for the unsaved-changes guard.
    private val initialName = name
    private val initialDesc = desc
    private val initialRows = translations.map { it.name to it.desc }

    init {
        Dialogs.show(this)
    }

    /** `true` once the user has changed anything in this dialog. */
    val isDirty: Boolean
        get() = name != initialName || desc != initialDesc ||
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

    fun setOkListener(listener: Runnable) {
        okListener = listener
    }

    fun dismiss() = Dialogs.dismiss(this)

    @Composable
    override fun Content() = CreatorPopupScreen()

    @Composable
    private fun CreatorPopupScreen() {
        // Popup colors mirror the app theme (res/values/colors.xml) instead of being
        // hard-coded here, so a theme change only needs to touch the resource file.
        val cardColor = colorResource(R.color.colorPrimaryBg)
        val textColor = colorResource(R.color.colorPrimaryFg)
        val dividerColor = colorResource(R.color.colorControlLine)

        // Outside-dismiss (the dirty-guarded scrim tap) and the keyboard inset
        // handling both live in the shared dialog host.
        Box(Modifier.fillMaxSize()) {
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
                // Variable middle: the type-specific editor scroll when it
                // outgrows the (keyboard-resized) window, so the header and the Cancel/OK bar
                // below always stay visible.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (content) {
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
                .border(0.8.dp, colorResource(R.color.colorControlLine))
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
    ) {
        Box(
            Modifier
                .fillMaxWidth()
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
