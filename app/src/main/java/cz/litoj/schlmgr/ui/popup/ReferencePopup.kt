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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * Pre-fork reference picker, the items referencing the word, in no
 * particular order. Kept rows follow the new word, X-ed ones keep the
 * original.
 * - carries a pending decision — a scrim tap cannot drop it (the back key still can).
 */
class ReferencePopup(
	references: List<DbItem>,
) : DialogSpec {

	private var okListener: ((List<Int>) -> Unit)? = null

	private val following = references.toMutableStateList()

	override fun canDismissByOutsideTouch() = false

	init {
		Dialogs.show(this)
	}

	fun setOkListener(listener: (List<Int>) -> Unit) {
		okListener = listener
	}

	fun dismiss() = Dialogs.dismiss(this)

	@Composable
	override fun Content() = ReferencePopupScreen()

	@Composable
	private fun ReferencePopupScreen() {
		val textColor = TextColor
		PopupCard {
			Text(
				stringResource(R.string.reference_fork),
				Modifier
					.fillMaxWidth()
					.padding(top = 5.dp),
				style = TextStyle(fontSize = 25.sp, color = textColor),
				textAlign = TextAlign.Center
			)
			HairlineDivider()
			// the list scrolls when it outgrows the card. The button row stays visible
			Column(
				Modifier
					.fillMaxWidth()
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
			) {
				following.forEach { item -> ReferenceRow(item, textColor) }
			}
			HairlineDivider()
			Row(
				Modifier
					.fillMaxWidth()
					.height(50.dp)
			) {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) { dismiss() }
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) {
				// one shot — a double-tap must not commit the fork twice
				val listener = okListener ?: return@PopupButton
				okListener = null
				listener(following.map { it.id })
			}
			}
		}
	}

	@Composable
	private fun ReferenceRow(item: DbItem, textColor: Color) {
		Column(Modifier.fillMaxWidth()) {
			HairlineDivider()
			Row(
				Modifier
					.fillMaxWidth()
					.padding(start = 20.dp, end = 20.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					item.name,
					Modifier
						.weight(1f)
						.padding(vertical = 10.dp),
					style = TextStyle(fontSize = 17.sp, color = textColor),
				)
				Icon(
					painterResource(R.drawable.ic_cancel_round),
					contentDescription = null,
					tint = Color.Unspecified,
					modifier = Modifier
						.padding(start = 12.5.dp)
						.size(30.dp)
						.clickable { following.remove(item) }
				)
			}
			Spacer(Modifier.height(5.dp))
		}
	}
}
