package cz.litoj.schlmgr.ui.popup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * A read-only explanation dialog, shown above the dialog it explains.
 */
class InfoPopup(@param:StringRes private val title: Int, @param:StringRes private val body: Int) : DialogSpec {

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			PopupTitle(stringResource(title))
			HairlineDivider()
			PopupScrollBody(stringResource(body))
			HairlineDivider()
			PopupButtonRow {
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) { Dialogs.dismiss(this@InfoPopup) }
			}
		}
	}
}
