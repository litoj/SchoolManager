package cz.litoj.schlmgr.ui.popup

import androidx.annotation.VisibleForTesting
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import cz.litoj.schlmgr.ui.viewmodel.TranslationValue

/** Only words carry type-specific content, in the translations editor. */
sealed class CreatorContent {

	object Simple : CreatorContent()

	class Word(val initial: List<TranslationRow>) : CreatorContent()
}

/**
 * The item creation/edit popup, where creation and editing share the fields.
 * - order changes by dragging in the list and new items append.
 * - so placement is the business of the database, and there is no position picker.
 * - the OK listener returns the commit verdict only.
 * - the popup closes itself on acceptance, except a new word confirmed by the Enter chain.
 * - that one renews the editor for the next word, see [resetForNextWord].
 * - the OK button always closes.
 */
class CreatorPopup(
	private val kind: Kind,
	val edited: ItemRow?,
	private val content: CreatorContent,
) : DialogSpec {

	/** What the popup does, from which the header text follows at display. */
	enum class Kind { EDIT, NEW_SUBJECT, NEW_CHAPTER, NEW_NOTE, NEW_WORD }

	private var okListener: ((CreatorResult) -> Boolean)? = null

	/**
	 * The whole content of the editor, built by one construction path, see [freshEditor].
	 * - a renewal replaces the instance instead of wiping fields, see [resetForNextWord].
	 * - so nothing of the committed word survives it.
	 * - the screen keys the composed fields on the instance, so they are rebuilt with it too.
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
				// a new word always starts with one empty translation ready to type into.
				.apply { if (isEmpty()) add(TranslationRow("", "", null)) }

		val removedTranslations = mutableListOf<DbItem>()

		// snapshots of the last committed state, used for the unsaved-changes guard.
		private val initialName = name
		private val initialDescription = description
		private val initialRows = translations.map { it.name to it.description }

		val isDirty: Boolean
			get() = name != initialName || description != initialDescription ||
				removedTranslations.isNotEmpty() ||
				translations.map { it.name to it.description } != initialRows

		// the row-focus plumbing of the Enter chain, held by the editor.
		// so a renewal drops the dead handles with the rows they belonged to
		val nameFieldFocus = mutableStateMapOf<Long, FocusRequester>()
		var pendingFocusId by mutableStateOf<Long?>(null)
	}

	/**
	 * One construction path of the editor, for the first word of a run and its renewals.
	 * - the renewals come from [resetForNextWord].
	 */
	private fun freshEditor(focusNameOnOpen: Boolean = false) = EditorState(
		focusNameOnOpen,
		edited,
		(content as? CreatorContent.Word)?.initial ?: emptyList(),
	)

	private var editor by mutableStateOf(freshEditor())

	// exposed publicly so the call sites read them as name/description.
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

	/** The name Enter of a note lands here, since its body is the description. */
	private val descriptionFocus = FocusRequester()

	private val isNote = kind == Kind.NEW_NOTE ||
		(kind == Kind.EDIT && edited?.item?.type == ItemKind.NOTE)

	/**
	 * A fresh row is added when the editor holds none, since its last one was deleted.
	 */
	@VisibleForTesting
	internal fun onWordNameEnter() {
		val id = translations.firstOrNull()?.id
			?: TranslationRow("", "", null).also(translations::add).id
		focusTranslation(id)
	}

	/**
	 * A note commits through the OK button only, since its body is the description.
	 */
	@VisibleForTesting
	internal fun onNoteNameEnter() {
		// the field is composed whenever Enter can fire.
		// catching keeps the call safe outside composition, as in the unit tests
		runCatching { descriptionFocus.requestFocus() }
	}

	/**
	 * The last row confirms instead, see [notifyOk].
	 * - a new word renews the editor for the next one, and everything else closes.
	 */
	@VisibleForTesting
	internal fun onTranslationNameEnter(index: Int) {
		val next = translations.getOrNull(index + 1)
		if (next != null) focusTranslation(next.id) else notifyOk(renew = true)
	}

	/** Moves the keyboard to the name field of a translation, deferred until it composes. */
	private fun focusTranslation(id: Long) {
		val focused = runCatching { editor.nameFieldFocus[id]?.requestFocus() }.isSuccess
		if (!focused) editor.pendingFocusId = id
	}

	/**
	 * A false verdict keeps the popup open.
	 * - an accepted commit closes the popup, except a new word confirmed by the Enter chain.
	 */
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
	 * A renewal goes through the same path as the first word.
	 * - a run holds no edited row.
	 * - the keyboard lands in the name field, and the popup stays open.
	 */
	private fun resetForNextWord() {
		editor = freshEditor(focusNameOnOpen = true)
	}

	@Composable
	override fun Content() = CreatorPopupScreen()

	@Composable
	private fun CreatorPopupScreen() {
		// the popup colours mirror the app theme in res/values/colors.xml, not hard-coded values.
		// so a theme change only needs to touch the resource file.
		val textColor = colorResource(R.color.colorPrimaryFg)

		// the editor instance is the identity of the content.
		// - a renewal, see [resetForNextWord], rebuilds the whole subtree.
		// - so the next word gets fresh field components, not the committed ones.
		key(editor) {
			// a renewed editor opens with the keyboard in its name field.
			// the deferred effect lets the fresh subtree compose first
			LaunchedEffect(editor.focusNameOnOpen) {
				if (editor.focusNameOnOpen) runCatching { nameFocus.requestFocus() }
			}
			// the outside dismiss and the keyboard inset handling live in the shared dialog host.
			PopupCard {
				// the header is fixed, so title, divider, name and description stay visible.
				PopupTitle(stringResource(kind.headerRes))
				HairlineDivider()
				// the Enter key commits on the last name field, for the simple kinds this one.
				// a note is the exception, since its body is the description.
				// so its name Enter jumps there and the commit stays on the OK button.
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
				// the variable middle holds the type-specific editor.
				// - it scrolls when it outgrows the window, which the keyboard resizes.
				// - so the header and the Cancel/OK bar below always stay visible.
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
				PopupButtonRow {
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
		// not a LazyColumn, since the enclosing middle section already scrolls.
		// nesting same-direction scrollables would let the inner one steal the drags.
		Column(Modifier.fillMaxWidth()) {
			translations.forEach { row ->
				// keyed by the row, since rows come and go without a renewal too.
				// a slot shift must not hand one row the field state of another row.
				key(row.id) { TranslationRowEditor(row, textColor) }
			}
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
		val nameFocus = remember(row.id) { FocusRequester() }
		// registered post-composition, so an abandoned recomposition leaves no stale entry behind
		SideEffect { editor.nameFieldFocus[row.id] = nameFocus }
		// keyed on pendingFocusId too, so re-targeting an already-composed row re-runs the effect.
		// - so focus really moves there, instead of falling through to the description field.
		// - newly appended rows are focused as soon as they compose.
		LaunchedEffect(row.id, editor.pendingFocusId) {
			if (editor.pendingFocusId == row.id) {
				editor.pendingFocusId = null
				runCatching { nameFocus.requestFocus() }
			}
		}
		// the layout matches the main frame, so the start padding aligns the bordered fields.
		// - the delete icon keeps the same margin from the right edge of the card.
		// - so it does not hug the popup
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
						// the Enter of the last row confirms, so the key reads Done, not Next
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
							// the focus handle of the row is dead, so drop it with the row
							editor.nameFieldFocus.remove(row.id)
						}
				)
			}
			Spacer(Modifier.height(5.dp))
		}
	}

	@VisibleForTesting
	internal fun notifyOk(renew: Boolean = false) {
		// the closing commit is one shot, so a double-tap must not commit twice.
		// - a refused commit leaves the popup open, so the door stays open.
		// - the renew of a new word keeps the listener for the next word of the run.
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
