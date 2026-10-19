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
import androidx.compose.runtime.key
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
import cz.litoj.schlmgr.data.db.ItemKind
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
 * on acceptance, except a new word confirmed through the Enter chain, which
 * renews the editor for the next one instead (see [resetForNextWord]); the
 * OK button always closes.
 */
class CreatorPopup(
	private val kind: Kind,
	val edited: ItemRow?,
	private val content: CreatorContent,
) : DialogSpec {

	/** What the popup does — the header text follows from it at display. */
	enum class Kind { EDIT, NEW_SUBJECT, NEW_CHAPTER, NEW_NOTE, NEW_WORD }

	private var okListener: ((CreatorResult) -> Boolean)? = null

	/**
	 * The editor's whole content, built by the one construction path (see
	 * [freshEditor]) — a renewal replaces the instance (see
	 * [resetForNextWord]) instead of wiping fields, so nothing of the
	 * committed word survives it: the screen keys the composed fields on
	 * the instance, so they are rebuilt with it too.
	 */
	@Stable
	private class EditorState(
		val focusNameOnOpen: Boolean,
		edited: ItemRow?,
		rows: List<TranslationRow>,
	) {
		var name by mutableStateOf(edited?.item?.name ?: "")
		var description by mutableStateOf(edited?.item?.description ?: "")

		val translations: SnapshotStateList<TranslationRow> =
			rows.toMutableStateList()
				// A new word always starts with one empty translation ready to type into.
				.apply { if (isEmpty()) add(TranslationRow("", "", null)) }

		/** The existing translations whose rows were removed in the editor. */
		val removedTranslations = mutableListOf<DbItem>()

		// Snapshots of the last committed state, used for the unsaved-changes guard.
		private val initialName = name
		private val initialDescription = description
		private val initialRows = translations.map { it.name to it.description }

		val isDirty: Boolean
			get() = name != initialName || description != initialDescription ||
				removedTranslations.isNotEmpty() ||
				translations.map { it.name to it.description } != initialRows

		// The Enter chain's row-focus plumbing — held by the editor, so a
		// renewal drops the dead handles with the rows they belonged to.
		val nameFieldFocus = mutableStateMapOf<Long, FocusRequester>()
		var pendingFocusId by mutableStateOf<Long?>(null)
	}

	/** One construction path of the editor — the first word of a run and every renewed one (see [resetForNextWord]). */
	private fun freshEditor(focusNameOnOpen: Boolean = false) = EditorState(
		focusNameOnOpen,
		edited,
		(content as? CreatorContent.Word)?.initial ?: emptyList(),
	)

	private var editor by mutableStateOf(freshEditor())

	// Exposed publicly so the call sites read them as name/description.
	var name: String
		get() = editor.name
		set(value) { editor.name = value }
	var description: String
		get() = editor.description
		set(value) { editor.description = value }

	val translations: SnapshotStateList<TranslationRow> get() = editor.translations

	/** The existing translations whose rows were removed in the editor. */
	val removedTranslations: MutableList<DbItem> get() = editor.removedTranslations

	init {
		Dialogs.show(this)
	}

	val isDirty: Boolean get() = editor.isDirty

	override fun canDismissByOutsideTouch(): Boolean = !isDirty

	private val nameFocus = FocusRequester()

	/** A note's name Enter lands here — its body is the description. */
	private val descriptionFocus = FocusRequester()

	/** Whether the popup edits a note — its name Enter targets the description (see [onNoteNameEnter]). */
	private val isNote = kind == Kind.NEW_NOTE ||
		(kind == Kind.EDIT && edited?.item?.type == ItemKind.NOTE)

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
	 * Enter in a note's name — the description follows. A note commits
	 * through the OK button only: its body is the description, typed over
	 * newlines.
	 */
	@VisibleForTesting
	internal fun onNoteNameEnter() {
		// the field is composed whenever Enter can fire — catching keeps the
		// call safe outside composition (the unit tests)
		runCatching { descriptionFocus.requestFocus() }
	}

	/**
	 * Enter in a translation's name — the next row follows, the last one
	 * confirms instead (see [notifyOk]): a new word renews the editor for
	 * the next one, everything else closes.
	 */
	@VisibleForTesting
	internal fun onTranslationNameEnter(index: Int) {
		val next = translations.getOrNull(index + 1)
		if (next != null) focusTranslation(next.id) else notifyOk(renew = true)
	}

	/** Moves the keyboard to a translation's name field, deferred until the row is composed. */
	private fun focusTranslation(id: Long) {
		val focused = runCatching { editor.nameFieldFocus[id]?.requestFocus() }.isSuccess
		if (!focused) editor.pendingFocusId = id
	}

	/** Sets the commit verdict — `false` keeps the popup open; an accepted commit closes the popup, except a new word confirmed through the Enter chain, which renews (see [resetForNextWord]). */
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
	 * Rebuilds the editor for the next word of a creation run — a fresh
	 * [EditorState] through the same construction path as the first word
	 * (a run holds no edited row), with the keyboard landing in the name
	 * field. The popup stays open.
	 */
	private fun resetForNextWord() {
		editor = freshEditor(focusNameOnOpen = true)
	}

	@Composable
	override fun Content() = CreatorPopupScreen()

	@Composable
	private fun CreatorPopupScreen() {
		// Popup colors mirror the app theme (res/values/colors.xml) instead of being
		// hard-coded here, so a theme change only needs to touch the resource file.
		val textColor = colorResource(R.color.colorPrimaryFg)

		// The editor instance is the content's identity — the renewal (see
		// [resetForNextWord]) rebuilds the whole subtree, so the next word
		// gets fresh field components instead of the committed word's ones.
		key(editor) {
			// A renewed editor opens with the keyboard in its name field — the
			// deferred effect lets the fresh subtree compose first.
			LaunchedEffect(editor.focusNameOnOpen) {
				if (editor.focusNameOnOpen) runCatching { nameFocus.requestFocus() }
			}
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
				// Enter commits on the last name field in the view: this name for
				// the simple kinds, the last translation for words (the word name
				// chains there). A note is the exception — its body is the
				// description, so its name Enter jumps there and the commit
				// stays on the OK button.
				BorderedTextField(
					name,
					{ name = it },
					R.string.data_name,
					textColor,
					singleLine = true,
					onImeAction = when {
						content is CreatorContent.Word -> ({ onWordNameEnter() })
						isNote -> ({ onNoteNameEnter() })
						else -> ({ notifyOk() })
					},
					imeAction = if (content is CreatorContent.Word || isNote) ImeAction.Next else ImeAction.Done,
					focusRequester = nameFocus,
				)
				BorderedTextField(
					description,
					{ description = it },
					R.string.data_desc,
					textColor,
					focusRequester = descriptionFocus,
				)
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
	}

	@Composable
	private fun WordEditor(textColor: Color) {
		// Not a LazyColumn — the enclosing middle section already scrolls, and nesting
		// same-direction scrollables would let the inner one steal the drags.
		Column(Modifier.fillMaxWidth()) {
			translations.forEach { row ->
				// Keyed by the row: rows come and go without a renewal too, and
				// a slot shift must not hand one row another row's field state.
				key(row.id) { TranslationRowEditor(row, textColor) }
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
		SideEffect { editor.nameFieldFocus[row.id] = nameFocus }
		// Keyed on pendingFocusId as well so that re-targeting an already-composed row
		// re-runs the effect and actually moves focus there (otherwise IME-Next falls
		// through to the description field). Newly appended rows are focused as soon as
		// they are composed.
		LaunchedEffect(row.id, editor.pendingFocusId) {
			if (editor.pendingFocusId == row.id) {
				editor.pendingFocusId = null
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
							editor.nameFieldFocus.remove(row.id)
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
	internal fun notifyOk(renew: Boolean = false) {
		// the closing commit is one shot — a double-tap must not commit twice,
		// but a refused one (the popup stays open) must leave the door open.
		// A new word's renew keeps the listener for the next word of the run.
		val listener = okListener ?: return
		if (!listener(result())) return
		if (renew && kind == Kind.NEW_WORD) resetForNextWord()
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
