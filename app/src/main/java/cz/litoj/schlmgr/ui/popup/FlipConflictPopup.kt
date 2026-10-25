package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipConflict
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.IconSize

/**
 * The conflict popup of the flip, one row per relation that spans the flip
 * set and the outside, reusing the creator row fields. The round rules live
 * in the flip_conflicts_help text, the one user-facing copy.
 */
class FlipConflictPopup(conflicts: List<FlipConflict>) : DialogSpec {

	/**
	 * The creator row fields over the conflict row, which is the commit identity.
	 */
	@Stable
	internal class ConflictRow(val source: DbItem) {
		var name by mutableStateOf(source.name)
		var description by mutableStateOf(source.description)
	}

	/** One editable row per conflict, so the source items stay untouched until commit. */
	internal val rows: SnapshotStateList<ConflictRow> = conflicts
		.map { ConflictRow(it.row) }
		.toMutableStateList()

	/** The dropped rows, kept apart so the commit can detach them. */
	private val removed = mutableListOf<DbItem>()

	private var okListener: ((List<FlipResolution>) -> Boolean)? = null

	/** The round closed without an OK, so the host drops the flip run. */
	var onCanceled: (() -> Unit)? = null

	/** Snapshot of each row at start, for the unsaved-changes guard. */
	private val initialRows = rows.map { it.name to it.description }

	init {
		Dialogs.show(this)
	}

	val isDirty: Boolean
		get() = removed.isNotEmpty() || rows.map { it.name to it.description } != initialRows

	override fun canDismissByOutsideTouch(): Boolean = !isDirty

	fun setOkListener(listener: (List<FlipResolution>) -> Boolean) {
		okListener = listener
	}

	private fun result() = rows.map { FlipResolution(it.source.id, false, it.name, it.description) } +
		removed.map { FlipResolution(it.id, true, it.name, it.description) }

	/** The remove button and the tests share this path, so it stays internal. */
	internal fun remove(row: ConflictRow) {
		removed.add(row.source)
		rows.remove(row)
	}

	fun dismiss() = Dialogs.dismiss(this)

	// the OK path clears the listener before the dismiss, so a set listener means a cancel
	override fun onDismissed() {
		if (okListener != null) onCanceled?.invoke()
	}

	private fun showHelp() {
		InfoPopup(R.string.help, R.string.flip_conflicts_help)
	}

	/** A false verdict from the listener keeps the popup open, so a refusal can be corrected. */
	internal fun notifyOk(): Boolean {
		val listener = okListener ?: return true
		if (!listener(result())) return false
		okListener = null
		dismiss()
		return true
	}

	@Composable
	override fun Content() {
		val textColor = colorResource(R.color.colorPrimaryFg)
		PopupCard {
			PopupTitle(stringResource(R.string.flip_conflicts_title)) {
				Icon(
					painterResource(R.drawable.ic_help),
					contentDescription = stringResource(R.string.help),
					tint = textColor,
					modifier = Modifier
						.align(Alignment.CenterEnd)
						.padding(end = 10.dp)
						.size(IconSize)
						.clickable { showHelp() }
				)
			}
			HairlineDivider()
			// not a LazyColumn, since same-direction scrollables would steal drags.
			Column(
				Modifier
					.fillMaxWidth()
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
			) {
				rows.forEach { row ->
					// key by conflict row: a removal must not hand one row
					// the field state of another row
					key(row.source.id) { ConflictRowEditor(row, textColor) }
				}
			}
			HairlineDivider()
			PopupButtonRow {
				PopupButton(
					stringResource(R.string.cancel),
					Modifier.weight(1f)
				) { dismiss() }
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.flip_conflicts_ok), Modifier.weight(1f)) { notifyOk() }
			}
		}
	}

	/** A creator translation row without the Enter-chain, because OK commits the round. */
	@Composable
	private fun ConflictRowEditor(row: ConflictRow, textColor: Color) {
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
						// a word row edits a name, a translation row a translation
						if (row.source.type == ItemKind.WORD) R.string.data_name
						else R.string.data_translate,
						textColor,
						singleLine = true,
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
						.size(IconSize)
						.clickable { remove(row) }
				)
			}
			Spacer(Modifier.height(5.dp))
		}
	}
}
