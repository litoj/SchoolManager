package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * The source picker of a referenced row living in several containers.
 * - one button per source, labeled with its whole chain.
 * - picking hands the chain to [onPick].
 */
class SourcePickPopup(
	private val paths: List<List<DbItem>>,
	private val onPick: (List<DbItem>) -> Unit,
) : DialogSpec {

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			Text(
				stringResource(R.string.ref_source_pick),
				Modifier.padding(5.dp),
				style = TextStyle(fontSize = 20.sp, color = TextColor),
			)
			HairlineDivider()
			Column(
				Modifier
					.heightIn(max = 400.dp)
					.verticalScroll(rememberScrollState())
			) {
				for (path in paths) {
					Box(
						Modifier
							.fillMaxWidth()
							.height(50.dp)
							.clickable {
								Dialogs.dismiss(this@SourcePickPopup)
								onPick(path)
							},
						contentAlignment = Alignment.Center,
					) {
						Text(
							path.joinToString(" / ") { it.name },
							Modifier.padding(horizontal = 5.dp),
							style = TextStyle(fontSize = 18.sp, color = TextColor),
						)
					}
				}
			}
		}
	}
}
