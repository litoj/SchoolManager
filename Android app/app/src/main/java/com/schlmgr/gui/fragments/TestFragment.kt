package com.schlmgr.gui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import com.schlmgr.gui.components.BareTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment

import com.schlmgr.R
import com.schlmgr.gui.AppSettings
import com.schlmgr.gui.Controller
import com.schlmgr.gui.Controller.ControlListener
import com.schlmgr.gui.activity.SelectItemsActivity
import com.schlmgr.gui.activity.TestActivity
import com.schlmgr.gui.list.SearchItemModel

import com.schlmgr.gui.engine.objects.MainChapter
import com.schlmgr.gui.engine.objects.Picture
import com.schlmgr.gui.engine.objects.templates.TwoSided
import com.schlmgr.gui.engine.testing.Test

import android.graphics.drawable.Drawable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The test setup screen: test type toggle, tested amount and duration fields, the list
 * of selected sources (filled by [SelectItemsActivity]) and the Start button.
 */
class TestFragment : Fragment(), ControlListener {

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		if (!Controller.isActive(this@TestFragment)) {
			list.clear()
			SelectItemsActivity.backLog = null
		}
		setContent { TestSetupScreen() }
	}

	override fun onResume() {
		Controller.setCurrentControl(this, 0, false, false)
		super.onResume()
	}

	@Composable
	private fun TestSetupScreen() {
		val keyboard = LocalSoftwareKeyboardController.current
		Box(Modifier.fillMaxSize()) {
			Column(Modifier.fillMaxSize()) {
				TypeRow()
				Divider()
				NumberRow(
					stringResource(R.string.test_size), amountText,
					{ amountText = it },
					{ restoreAmount(); keyboard?.hide() },
				)
				Divider()
				NumberRow(
					stringResource(R.string.test_time), timeText,
					{ timeText = it },
					{ restoreTime(); keyboard?.hide() },
				)
				Divider()
				AddSourceRow()
				Divider()
				// The shared list is a SnapshotStateList, so background-thread changes
				// made by SelectItemsActivity's dedup thread recompose this directly.
				LazyColumn(Modifier.weight(1f)) {
					items(list, key = { System.identityHashCode(it) }) { sim ->
						SourceRow(sim) { list.remove(sim) }
					}
				}
			}
			Button(
				onClick = ::startTest,
				// The app theme colors (header green background, white text).
				colors = ButtonDefaults.buttonColors(
					backgroundColor = colorResource(R.color.colorPrimaryHeader),
					contentColor = Color.White,
				),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 5.dp)
					.height(40.dp),
			) {
				Text(stringResource(R.string.start))
			}
		}
	}

	/** Test type icon: toggling also drops sources of the other type from the list. */
	@Composable
	private fun TypeRow() {
		Row(
			Modifier
				.fillMaxWidth()
				.height(40.dp)
				.padding(horizontal = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				stringResource(R.string.test_type),
				Modifier
					.weight(1f)
					.padding(top = 5.dp),
				style = TextStyle(fontSize = 24.sp, color = TextColor),
			)
			Image(
				painter = painterResource(if (picTest) R.drawable.ic_pic else R.drawable.ic_word),
				contentDescription = stringResource(R.string.test_type),
				modifier = Modifier
					.size(40.dp)
					.clickable {
						picTest = !picTest
						list.removeAll { it.bd is TwoSided<*> && (it.bd is Picture) != picTest }
					},
				contentScale = ContentScale.Fit,
			)
		}
	}

	@Composable
	private fun NumberRow(
		label: String, value: String,
		onValueChange: (String) -> Unit,
		onFocusLost: () -> Unit,
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.height(40.dp)
				.padding(horizontal = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				label,
				Modifier
					.weight(1f)
					.padding(top = 5.dp),
				style = TextStyle(fontSize = 24.sp, color = TextColor),
			)
				// Same font size as the row label; the component enforces its own square
				// minimum width, so no fixed width is needed here anymore.
				BareTextField(
					value = value,
					onValueChange = onValueChange,
					fontSize = 24.sp,
					keyboardType = KeyboardType.Number,
					onFocusLost = onFocusLost,
				)
		}
	}

	/** One selected source: its icon, name and a remove button. */
	@Composable
	private fun SourceRow(sim: SearchItemModel, onRemove: () -> Unit) {
		Row(
			Modifier
				.fillMaxWidth()
				.padding(top = 3.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Row(
				Modifier
					.weight(1f)
					.padding(horizontal = 3.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
					// The models carry pre-scaled drawables; render them at a fixed 30dp.
				Image(
					painter = rememberDrawablePainter(sim.ic),
					contentDescription = null,
					modifier = Modifier.size(30.dp),
				)
				Spacer(Modifier.width(3.dp))
				Text(
					sim.bd.name,
					style = TextStyle(fontSize = 20.sp, color = TextColor),
				)
			}
			Image(
				painter = painterResource(R.drawable.ic_cancel_round),
				contentDescription = stringResource(R.string.delete),
				modifier = Modifier
					.size(30.dp)
					.clickable(onClick = onRemove),
			)
		}
	}

	private fun startTest() {
		if (list.isEmpty()) return
		control?.let { if (it.isAlive) runCatching { it.join() } }
		startActivity(Intent(requireContext(), TestActivity::class.java))
	}

	private fun openSelectItems() {
		// The whole hierarchy can't be added twice; nothing to select then.
		if (list.any { it.bd is MainChapter }) return
		startActivity(Intent(requireContext(), SelectItemsActivity::class.java))
	}

	private fun restoreAmount() {
		if (amountText.isEmpty()) amountText = Test.getAmount().toString()
	}

	private fun restoreTime() {
		if (timeText.isEmpty()) timeText = Test.getDefaultTime().toString()
	}

	@Composable
	private fun AddSourceRow() {
		Row(
			Modifier
				.fillMaxWidth()
				.height(30.dp)
				.padding(horizontal = 5.dp)
				.clickable(onClick = ::openSelectItems),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Image(
				painter = painterResource(R.drawable.ic_add),
				contentDescription = null,
				modifier = Modifier.size(20.dp),
			)
			Text(
				stringResource(R.string.test_add),
				Modifier.padding(start = 3.dp),
				style = TextStyle(fontSize = 20.sp, color = TextColor),
			)
		}
	}

	@Composable
	private fun Divider() {
		Spacer(
			Modifier
				.fillMaxWidth()
				.height(0.6.dp)
				.background(Color(0x58888888))
		)
	}

	companion object {

		@JvmField
		var control: Thread? = null

		/** The test type picked on this screen. */
		@JvmField
		var picTest = AppSettings.getBool("defaultTestTypePicture", false)

		/**
		 * The sources selected for the test. A [androidx.compose.runtime.snapshots.SnapshotStateList]
		 * so mutations from [SelectItemsActivity] (including its background dedup thread)
		 * recompose the setup screen without any adapter notify.
		 */
		@JvmField
		val list = ArrayList<SearchItemModel>().toMutableStateList()

		/** Answer to "how many items" — read by TestActivity when starting a test. */
		var amountText by mutableStateOf(Test.getAmount().toString())
			private set

		/** Answer to "how long per item" — read by TestActivity when starting a test. */
		var timeText by mutableStateOf(Test.getDefaultTime().toString())
			private set
	}
}

private val TextColor = Color(0xDD000000)

/** Renders a pre-scaled [Drawable] as a painter, applying its bounds to the canvas. */
@Composable
private fun rememberDrawablePainter(drawable: Drawable): Painter = remember(drawable) {
	val width = maxOf(1, drawable.intrinsicWidth)
	val height = maxOf(1, drawable.intrinsicHeight)
	object : Painter() {
		override val intrinsicSize = Size(width.toFloat(), height.toFloat())

		override fun DrawScope.onDraw() {
			// Render at the painter's size so the icon isn't stretched to the whole layout.
			drawable.setBounds(0, 0, width, height)
			// nativeCanvas is the backing android.graphics.Canvas.
			drawable.draw(drawContext.canvas.nativeCanvas)
		}
	}
}
