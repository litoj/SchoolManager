package cz.litoj.schlmgr.ui.popup

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.component.BareTextFieldStyle
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.WideTextField
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import cz.litoj.schlmgr.ui.viewmodel.TranslationValue

/**
 * Type-specific popup content — only words have any, the translations
 * editor. Chapters and notes edit the shared fields alone.
 */
sealed class CreatorContent {

	object Simple : CreatorContent()

	class Word(val initial: List<TranslationRow>) : CreatorContent()
}

/**
 * The item creation/edit popup — creation and editing share the fields. Order
 * changes by dragging in the list and new items append, so placement is the
 * database's business — there is no position picker.
 *
 * The OK listener returns the commit verdict only — the popup closes itself
 * on acceptance, except an Enter commit of a new word, which resets the
 * editor for the next one instead (see [onTranslationNameEnter]).
 */
class CreatorPopup(
	private val kind: Kind,
	val edited: ItemRow?,
	private val content: CreatorContent,
) : DialogSpec {

	/** What the popup does — the header text follows from it at display. */
	enum class Kind { EDIT, NEW_SUBJECT, NEW_CHAPTER, NEW_NOTE, NEW_WORD }

	private var okListener: ((CreatorResult) -> Boolean)? = null

	// Exposed publicly so the call sites read them as name/description.
	var name by mutableStateOf(edited?.item?.name ?: "")
	var description by mutableStateOf(edited?.item?.description ?: "")

	val translations: SnapshotStateList<TranslationRow> =
		((content as? CreatorContent.Word)?.initial
			?: emptyList()).toMutableStateList()
			// A new word always starts with one empty translation ready to type into.
			.apply { if (isEmpty()) add(TranslationRow("", "", null)) }

	/** The existing translations whose rows were removed in the editor. */
	val removedTranslations = mutableListOf<DbItem>()

	// Snapshots of the last committed state, used for the unsaved-changes guard.
	// Vars — an Enter commit of a new word re-baselines them (see [resetForNextWord]).
	private var initialName = name
	private var initialDescription = description
	private var initialRows = translations.map { it.name to it.description }

	init {
		Dialogs.show(this)
	}

	val isDirty: Boolean
		get() = name != initialName || description != initialDescription ||
			removedTranslations.isNotEmpty() ||
			translations.map { it.name to it.description } != initialRows

	override fun canDismissByOutsideTouch(): Boolean = !isDirty

	private val nameFieldFocus = mutableStateMapOf<Long, FocusRequester>()

	private var pendingFocusId by mutableStateOf<Long?>(null)

	private val nameFocus = FocusRequester()

	/**
	 * Enter in the word's name — the first translation follows; a fresh row is
	 * added when the editor holds none (its last one was deleted).
	 */
	@VisibleForTesting
	internal fun onWordNameEnter() {
		val id = translations.firstOrNull()?.id
			?: TranslationRow("", "", null).also(translations::add).id
		focusTranslation(id)
	}

	/**
	 * Enter in a translation's name — the next row follows, the last one
	 * confirms instead (see [notifyOk]).
	 */
	@VisibleForTesting
	internal fun onTranslationNameEnter(index: Int) {
		val next = translations.getOrNull(index + 1)
		if (next != null) focusTranslation(next.id) else notifyOk(fromEnter = true)
	}

	/** Moves the keyboard to a translation's name field, deferred until the row is composed. */
	private fun focusTranslation(id: Long) {
		val focused = runCatching { nameFieldFocus[id]?.requestFocus() }.isSuccess
		if (!focused) pendingFocusId = id
	}

	/** Sets the commit verdict — `false` keeps the popup open; on acceptance the popup closes itself. */
	fun setOkListener(listener: (CreatorResult) -> Boolean) {
		okListener = listener
	}

	fun result() = CreatorResult(
		name = name,
		description = description,
		translations = translations.map { TranslationValue(it.name, it.description, it.source) },
		removedTranslations = removedTranslations.toList(),
	)

	fun dismiss() = Dialogs.dismiss(this)

	/**
	 * Clears the editor for the next word of an Enter-driven run — the popup
	 * stays open, the keyboard moves to the name field.
	 */
	private fun resetForNextWord() {
		name = ""
		description = ""
		translations.clear()
		translations.add(TranslationRow("", "", null))
		removedTranslations.clear()
		// the dropped rows' focus handles are dead — drop them with the rows
		nameFieldFocus.clear()
		// the guard starts over against the fresh state
		initialName = name
		initialDescription = description
		initialRows = translations.map { it.name to it.description }
		// the field is composed whenever the chain can fire — catching keeps
		// the reset safe outside composition (the unit tests)
		runCatching { nameFocus.requestFocus() }
	}

	@Composable
	override fun Content() = CreatorPopupScreen()

	@Composable
	private fun CreatorPopupScreen() {
		// Popup colors mirror the app theme (res/values/colors.xml) instead of being
		// hard-coded here, so a theme change only needs to touch the resource file.
		val textColor = colorResource(R.color.colorPrimaryFg)

		// Outside-dismiss (the dirty-guarded scrim tap) and the keyboard inset
		// handling both live in the shared dialog host.
		PopupCard {
			// Fixed header — title, divider, name and description are always visible.
			Text(
				stringResource(kind.headerRes),
				Modifier
					.fillMaxWidth()
					.padding(top = 5.dp),
				style = TextStyle(fontSize = 25.sp, color = textColor),
				textAlign = TextAlign.Center
			)
			HairlineDivider()
			// Enter in the word name jumps to the first translation's name. The
			// other kinds have no next field — no ImeAction offered.
			BorderedTextField(
				name,
				{ name = it },
				R.string.data_name,
				textColor,
				singleLine = true,
				onImeAction = if (content is CreatorContent.Word) {
					{ onWordNameEnter() }
				} else null,
				focusRequester = nameFocus,
			)
			BorderedTextField(description, { description = it }, R.string.data_desc, textColor)
			// Variable middle — the type-specific editor scroll when it
			// outgrows the (keyboard-resized) window, so the header and the Cancel/OK bar
			// below always stay visible.
			Column(
				Modifier
					.fillMaxWidth()
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
			) {
				when (content) {
					is CreatorContent.Word -> WordEditor(textColor)

					else -> Unit
				}
			}
			HairlineDivider()
			Row(
				Modifier
					.fillMaxWidth()
					.height(50.dp)
			) {
				PopupButton(
					stringResource(R.string.cancel),
					Modifier.weight(1f)
				) { dismiss() }
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) { notifyOk() }
			}
		}
	}

	@Composable
	private fun WordEditor(textColor: Color) {
		// Not a LazyColumn — the enclosing middle section already scrolls, and nesting
		// same-direction scrollables would let the inner one steal the drags.
		Column(Modifier.fillMaxWidth()) {
			translations.forEach { row ->
				TranslationRowEditor(row, textColor)
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
	) {
		// Focus handle for this row's name field, used by the Enter-chain.
		val nameFocus = remember(row.id) { FocusRequester() }
		// registered post-composition — an abandoned recomposition leaves
		// no stale entry behind
		SideEffect { nameFieldFocus[row.id] = nameFocus }
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
		// Same layout as the main frame — the row's start padding aligns the bordered
		// fields' left edge with the main fields, and the delete icon keeps the same
		// margin from the card's right edge so it doesn't hug the popup.
		Column(Modifier.fillMaxWidth()) {
			HairlineDivider()
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
						onImeAction = { onTranslationNameEnter(translations.indexOf(row)) },
						// the last row's Enter confirms — the key reads Done, not Next
						imeAction = if (translations.lastOrNull() == row) ImeAction.Done else ImeAction.Next,
						focusRequester = nameFocus,
						horizontalPadding = 0.dp,
					)
					BorderedTextField(
						row.description,
						{ row.description = it },
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
							// the row's focus handle is dead — drop it with the row
							nameFieldFocus.remove(row.id)
						}
				)
			}
			Spacer(Modifier.height(5.dp))
		}
	}

	@Composable
	private fun BorderedTextField(
		value: String,
		onValueChange: (String) -> Unit,
		@StringRes label: Int,
		textColor: Color,
		singleLine: Boolean = false,
		onImeAction: (() -> Unit)? = null,
		imeAction: ImeAction? = null,
		focusRequester: FocusRequester? = null,
		horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.padding(horizontal = horizontalPadding, vertical = 5.dp)
				.border(0.8.dp, colorResource(R.color.colorControlLine))
				.padding(5.dp)
		) {
			WideTextField(
				value = value,
				onValueChange = onValueChange,
				fontSize = 17.sp,
				style = BareTextFieldStyle(textColor = textColor, cursorColor = textColor, underline = false),
				focusRequester = focusRequester,
				imeAction = imeAction
					?: if (onImeAction != null) ImeAction.Next else ImeAction.Default,
				hint = stringResource(label),
				singleLine = singleLine,
				onImeAction = onImeAction,
			)
		}
	}

	@VisibleForTesting
	internal fun notifyOk(fromEnter: Boolean = false) {
		// one shot per ACCEPTED commit — a double-tap must not commit twice,
		// but a refused one (the popup stays open) must leave the door open
		val listener = okListener ?: return
		if (!listener(result())) return
		if (fromEnter && kind == Kind.NEW_WORD) resetForNextWord()
		else {
			okListener = null
			dismiss()
		}
	}
}

private val CreatorPopup.Kind.headerRes: Int
	get() = when (this) {
		CreatorPopup.Kind.EDIT -> R.string.edit
		CreatorPopup.Kind.NEW_SUBJECT -> R.string.new_mch
		CreatorPopup.Kind.NEW_CHAPTER -> R.string.new_chapter
		CreatorPopup.Kind.NEW_NOTE -> R.string.new_note
		CreatorPopup.Kind.NEW_WORD -> R.string.new_word
	}
