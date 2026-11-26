package cz.litoj.schlmgr.ui.popup

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.SettingSwitchRow

/**
 * The export door: the file and where it lands.
 * - it opens with the choice of the last export, so a repeat is two taps.
 *
 * @param onSave the save dialog of the chosen format
 * @param onShare the share sheet of the chosen format
 */
class ExportPopup(
	private val settings: Settings,
	private val onSave: (ExportFormat) -> Unit,
	private val onShare: (ExportFormat) -> Unit,
) : DialogSpec {

	/** The shown format, the stored one when it names a real format. */
	var format by mutableStateOf(storedFormat(settings))
		private set

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			PopupTitle(stringResource(R.string.more_export))
			HairlineDivider()
			PopupScrollBody {
				SettingSwitchRow(
					stringResource(R.string.export_omit_history),
					checked = format == ExportFormat.WORD,
					onToggle = ::toggleFormat,
					caption = stringResource(noteRes(format)),
				)
			}
			HairlineDivider()
			// share takes the right edge, where every popup waits for its action.
			// testers missed it on the left
			PopupButtonRow {
				PopupButton(stringResource(R.string.export_save), Modifier.weight(1f)) { pickSave() }
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.action_share), Modifier.weight(1f)) { pickShare() }
			}
		}
	}

	/** The format of the next export, the switch of the sheet sets it. */
	internal fun toggleFormat() {
		format = if (format == ExportFormat.WORD) ExportFormat.JSON else ExportFormat.WORD
	}

	@VisibleForTesting
	internal fun pickSave() = go(onSave)

	@VisibleForTesting
	internal fun pickShare() = go(onShare)

	/** The choice outlives the sheet, so the next export opens on the same format. */
	private fun go(then: (ExportFormat) -> Unit) {
		settings.set(FORMAT_KEY, format.name)
		Dialogs.dismiss(this)
		then(format)
	}

	companion object {
		/** The choice the last export used, the word file when nothing was stored. */
		private fun storedFormat(settings: Settings): ExportFormat =
			ExportFormat.entries.firstOrNull {
				it.name == settings.getString(FORMAT_KEY, ExportFormat.WORD.name)
			} ?: ExportFormat.WORD

		private fun noteRes(format: ExportFormat) =
			if (format == ExportFormat.WORD) R.string.export_note_word else R.string.export_note_json

		private const val FORMAT_KEY = "exportFormat"
	}
}
