package cz.litoj.schlmgr.gui.popup

import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.gui.Controller
import cz.litoj.schlmgr.gui.list.HierarchyItemModel
import cz.litoj.schlmgr.gui.list.TestItemModel

import cz.litoj.schlmgr.gui.engine.objects.Picture

/**
 * The post-test results: success percentage, every item with the correct/incorrect
 * answer and the OK button closing the popup.
 */
class TestResultsPopup(items: ArrayList<TestItemModel>, success: Float) : AbstractPopup(0, true) {

	@Suppress("UNCHECKED_CAST")
	private val list: List<TestItemModel> = items.clone() as List<TestItemModel>
	private val picTest = items[0].sp.t is Picture
	private val successText = Controller.activity.getString(R.string.success_rate, success.toString())

	init {
		create()
	}

	override fun addContent(view: ViewGroup) {
		val compose = ComposeView(view.context)
		(view as FrameLayout).addView(
			compose,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			)
		)
		compose.setContent { TestResultsScreen() }
	}

	@Composable
	private fun TestResultsScreen() {
		Box(Modifier.fillMaxSize().padding(25.dp)) {
			Column(
				Modifier
					.fillMaxWidth()
					.background(Color.White)
					.padding(5.dp)
			) {
				Text(
					successText,
					Modifier
						.fillMaxWidth()
						.padding(top = 5.dp),
					style = TextStyle(fontSize = 25.sp, color = Color(0xE0100000)),
					textAlign = TextAlign.Center,
				)
				Spacer(
					Modifier
						.fillMaxWidth()
						.height(0.7.dp)
						.background(Color(0xFF888888))
				)
				LazyColumn(Modifier.weight(1f)) {
					items(list) { item ->
						if (picTest) PictureResultRow(item) else WordResultRow(item)
					}
				}
			}
			Button(
				onClick = ::dismiss,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 10.dp)
					.height(30.dp),
			) {
				Text("OK", style = TextStyle(fontSize = 15.sp))
			}
		}
	}

	@Composable
	private fun WordResultRow(item: TestItemModel) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
			ResultName(item)
			val trls = item.children.joinToString("\n") {
				HierarchyItemModel.nameParser(it.name)
			}
			Text(
				trls,
				style = TextStyle(fontSize = 15.sp, color = Color(0xE0100000)),
			)
		}
	}

	@Composable
	private fun PictureResultRow(item: TestItemModel) {
		Column(Modifier.fillMaxWidth()) {
			ResultName(item)
			val iim = item.iim ?: return@Column
			Row(Modifier.fillMaxWidth()) {
				ResultPicture(iim, first = true, Modifier.weight(1f))
				if (iim.pic2 != null) ResultPicture(iim, first = false, Modifier.weight(1f))
			}
		}
	}

	@Composable
	private fun ResultName(item: TestItemModel) {
		Text(
			item.sp.t.name,
			style = TextStyle(fontSize = 15.sp, color = Color(0xE0100000)),
		)
		if (item.answer.isNotEmpty()) {
			Text(
				item.answer,
				style = TextStyle(
					fontSize = 15.sp,
					color = if (item.correct) Color(0xFF00AA00) else Color(0xFFAA0000)
				),
			)
		}
	}

	@Composable
	private fun ResultPicture(
		iim: cz.litoj.schlmgr.gui.list.ImageItemModel,
		first: Boolean,
		modifier: Modifier,
	) {
		val pic = if (first) iim.pic1 else iim.pic2 ?: return
		Box(modifier.heightIn(max = 50.dp).padding(5.dp)) {
			(if (first) iim.bm1 else iim.bm2)?.let { bitmap ->
				Image(
					bitmap = bitmap.asImageBitmap(),
					contentDescription = pic.toString(),
					contentScale = ContentScale.Fit,
					modifier = Modifier
						.fillMaxWidth()
						.clickable { FullPicture(pic) },
				)
			}
		}
	}
}
