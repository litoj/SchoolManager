package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import cz.litoj.schlmgr.ui.component.CrumbChain
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * The source picker of a referenced row living in several containers.
 * - one button per source, its chain rendered like the titlebar's breadcrumbs.
 * - the chains show the descent below the searched chapter — not the
 *   absolute chain from the subject root.
 * - picking hands the whole chain to [onPick].
 */
class SourcePickPopup(
	private val paths: List<List<DbItem>>,
	private val origin: List<DbItem>,
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
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
			) {
				paths.forEachIndexed { index, path ->
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
						CrumbChain(
							path.relativeTo(origin).map { it.name },
							TextColor,
							modifier = Modifier.padding(horizontal = 5.dp),
						)
					}
					if (index < paths.lastIndex) HairlineDivider()
				}
			}
		}
	}
}

/**
 * The chain as shown against the search's [origin] — the descent below the
 * searched chapter, with the chapter itself dropped. The location equal to the
 * searched chapter shows itself; a chain from outside the origin's subject
 * shows whole.
 */
internal fun List<DbItem>.relativeTo(origin: List<DbItem>): List<DbItem> {
	if (origin.isEmpty()) return this
	if (take(origin.size) != origin) return this
	val below = drop(origin.size)
	return below.ifEmpty { listOf(last()) }
}
