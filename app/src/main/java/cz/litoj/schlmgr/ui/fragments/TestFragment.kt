package cz.litoj.schlmgr.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import cz.litoj.schlmgr.ui.components.BareTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.ui.AppBarViewModel
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.explorer.model.ItemRow

/**
 * The test setup screen: tested amount and duration fields, the list of selected
 * sources (filled by [SelectItemsFragment]) and the Start button.
 */
class TestFragment : Fragment(), AppBarViewModel.DestinationActions {
	private val appBar: AppBarViewModel by activityViewModels()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		// A return from the picker must keep the chosen sources: `openedPicker`
		// is set when this screen launches the picker and consumed here. Only a
		// genuine fresh entry (e.g. the drawer) resets the setup — returning
		// from the running test keeps a TestFragment the registered destination,
		// which is exactly the case that must keep the sources too.
		val fromPicker = openedPicker
		openedPicker = false
		// 'Prepare test' from a selection fills the list the same way — it must
		// survive too.
		val fromSelection = openedWithSelection
		openedWithSelection = false
		if (!fromPicker && !fromSelection && appBar.currentDestination !is TestFragment) {
			list.clear()
			SelectItemsFragment.backLog = null
		}
		setContent { TestSetupScreen() }
	}

	override fun onResume() {
		appBar.setDestination(this, 0, false)
		super.onResume()
	}

	@Composable
	private fun TestSetupScreen() {
		val keyboard = LocalSoftwareKeyboardController.current
		Box(Modifier.fillMaxSize()) {
			Column(Modifier.fillMaxSize()) {
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
				ReverseRow()
				Divider()
				// The shared list is a SnapshotStateList, so background-thread changes
				// made by SelectItemsFragment's dedup thread recompose this directly.
				LazyColumn(Modifier.weight(1f)) {
					// the add action leads the list: the sources can grow without
					// scrolling past the ones already picked
					// (the whole hierarchy is picked — nothing left to add)
					if (list.none { it.size == 1 }) item { AddSourceTile() }
					items(list, key = { System.identityHashCode(it) }) { chain ->
						SourceRow(chain) { list.remove(chain) }
					}
				}
			}
			Button(
				onClick = ::startTest,
				// The app theme colors (header green background, white text).
				colors = ButtonDefaults.buttonColors(
					backgroundColor = colorResource(R.color.colorPrimaryHeader),
					contentColor = colorResource(R.color.colorOnHeader),
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

	@Composable
	private fun NumberRow(
		label: String, value: String,
		onValueChange: (String) -> Unit,
		onFocusLost: () -> Unit,
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.padding(5.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				label,
				Modifier.weight(1f),
				style = TextStyle(fontSize = 18.sp, color = TextColor),
			)
				// Same font size as the row label; the component enforces its own square
				// minimum width, so no fixed width is needed here anymore.
				BareTextField(
					value = value,
					onValueChange = onValueChange,
					fontSize = 18.sp,
					keyboardType = KeyboardType.Number,
					onFocusLost = onFocusLost,
				)
		}
	}

	/**
	 * The reverse-test switch: when on, the test shows the word and asks for one of
	 * its translations; when off, it keeps the default direction. The whole row
	 * toggles, like the settings switch rows.
	 */
	@Composable
	private fun ReverseRow() {
		Row(
			Modifier
				.fillMaxWidth()
				.clickable(onClick = ::toggleReverseTest)
				.padding(5.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				stringResource(R.string.test_reverse),
				Modifier.weight(1f),
				style = TextStyle(fontSize = 18.sp, color = TextColor),
			)
			Switch(
				checked = reverseTest,
				onCheckedChange = null,
				colors = SwitchDefaults.colors(
					checkedThumbColor = colorResource(R.color.colorOnHeader),
					checkedTrackColor = colorResource(R.color.colorPrimaryHeader),
				),
			)
		}
	}

	/** One selected source: its icon, name and a remove button. */
	@Composable
	private fun SourceRow(chain: List<DbItem>, onRemove: () -> Unit) {
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
				Image(
					painter = painterResource(iconFor(chain)),
					contentDescription = null,
					modifier = Modifier.size(30.dp),
				)
				Spacer(Modifier.width(3.dp))
				Text(
					ItemRow.nameParser(chain.last().name),
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

	/**
	 * The source icon matches the row the item was picked as: a chain of length 1
	 * is a whole subject, anything else by its type.
	 */
	private fun iconFor(chain: List<DbItem>): Int = when {
		chain.size == 1 -> R.drawable.ic_subject
		chain.last().type == ItemKind.WORD -> R.drawable.ic_word
		else -> R.drawable.ic_chapter
	}

	private fun startTest() {
		if (list.isEmpty()) return
		control?.let { if (it.isAlive) runCatching { it.join() } }
		startActivity(Intent(requireContext(), TestActivity::class.java))
	}

	private fun openSelectItems() {
		// The whole hierarchy can't be added twice; nothing to select then.
		if (list.any { it.size == 1 }) return
		openedPicker = true
		findNavController().navigate(R.id.select_items)
	}

	private fun restoreAmount() {
		if (amountText.isEmpty()) amountText = TestEngine.amount.toString()
	}

	private fun restoreTime() {
		if (timeText.isEmpty()) timeText = TestEngine.getDefaultTime().toString()
	}

	/**
	 * The add action, leading the sources list: a full-width tile with a dashed
	 * outline and a centered label, clearly an action rather than a config row.
	 */
	@Composable
	private fun AddSourceTile() {
		val density = LocalDensity.current
		val border = colorResource(R.color.colorPrimaryFg).copy(alpha = 0.5f)
		val corner = with(density) { 8.dp.toPx() }
		val stroke = remember {
			Stroke(
				width = with(density) { 1.dp.toPx() },
				pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
			)
		}
		Row(
			Modifier
				.fillMaxWidth()
				.padding(5.dp)
				.drawBehind {
					drawRoundRect(color = border, style = stroke, cornerRadius = CornerRadius(corner))
				}
				.clickable(onClick = ::openSelectItems)
				.padding(6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.Center,
		) {
			Image(
				painter = painterResource(R.drawable.ic_add),
				contentDescription = null,
				modifier = Modifier.size(20.dp),
			)
			Spacer(Modifier.width(5.dp))
			Text(
				stringResource(R.string.test_add),
				style = TextStyle(fontSize = 18.sp, color = TextColor),
			)
		}
	}

	@Composable
	private fun Divider() {
		Spacer(
			Modifier
				.fillMaxWidth()
				.height(0.6.dp)
				.background(colorResource(R.color.colorSourceDivider))
		)
	}

	companion object {

		@JvmField
		var control: Thread? = null

		/**
		 * Set when this screen launches the picker and consumed by its `onCreateView`:
		 * a return from the picker must keep [list] — only a genuine fresh entry
		 * (e.g. the drawer) resets the setup.
		 */
		@JvmField
		var openedPicker = false

		/**
		 * Set when a host prepares the sources straight from a selection (MainFragment's
		 * 'Prepare test') and consumed by `onCreateView`, so the filled [list] survives
		 * like a picker return.
		 */
		@JvmField
		var openedWithSelection = false

		/**
		 * One selected row's source chain: root first, the picked item last. A search row
		 * carries its hit's chain already; a browse row's chain is rebuilt from the parent
		 * it was selected under — an item with several parents uses that parent.
		 */
		fun chainOf(row: ItemRow): List<DbItem> {
			row.path?.let { return it + row.item }
			val parentId = row.parentId ?: return listOf(row.item)
			return ItemRepository.pathTo(parentId) + row.item
		}

		/**
		 * The legacy dedup pass of the `TFrag test item control` thread: a chain that sits
		 * inside another picked chain's item is covered by it, and the same chain twice is
		 * one source. Callable from any thread — the setup list is a snapshot state list,
		 * so its removals recompose the screen straight away.
		 */
		fun dedup(list: MutableList<List<DbItem>>) {
			var pos = 0
			while (pos < list.size - 1) {
				val chain = list[pos]
				var i = list.size - 1
				var removedPos = false
				while (i > pos) {
					val other = list[i]
					if (within(chain, other) && chain.size < other.size) {
						// `other` sits inside chain's item — the ancestor alone covers it
						list.removeAt(i)
					} else if (within(other, chain)) {
						// chain sits inside `other`'s item, or is the very same chain — keep the other
						list.removeAt(pos)
						removedPos = true
						break
					}
					i--
				}
				if (!removedPos) pos++
			}
		}

		/** Whether [shorter] equals the first [shorter.size] elements of [longer], by row id. */
		private fun within(shorter: List<DbItem>, longer: List<DbItem>): Boolean {
			if (shorter.size > longer.size) return false
			for (i in shorter.indices) if (shorter[i].id != longer[i].id) return false
			return true
		}

		/**
		 * The sources selected for the test: each entry is one picked item's container
		 * chain (root first, the picked item last — the TestEntry path shape), built
		 * by [SelectItemsFragment]. A [androidx.compose.runtime.snapshots.SnapshotStateList]
		 * so mutations from [SelectItemsFragment] (including its background dedup thread)
		 * recompose the setup screen without any adapter notify.
		 */
		@JvmField
		val list = ArrayList<List<DbItem>>().toMutableStateList()

		/** Answer to "how many items" — read by TestActivity when starting a test. */
		var amountText by mutableStateOf(TestEngine.amount.toString())
			private set

		/** Answer to "how long per item" — read by TestActivity when starting a test. */
		var timeText by mutableStateOf(TestEngine.getDefaultTime().toString())
			private set

		/**
		 * Whether the test runs in the reverse direction (see [TestEngine.isReversed]).
		 * Restored from the engine, which [cz.litoj.schlmgr.app.Startup] fills from the
		 * persisted setting.
		 */
		var reverseTest by mutableStateOf(TestEngine.isReversed)
			private set
	}

	/** Flips the reverse-test direction and persists it at once. */
	private fun toggleReverseTest() {
		reverseTest = !reverseTest
		TestEngine.isReversed = reverseTest
		AppSettings.set("reverseTest", reverseTest)
	}
}

private val TextColor @Composable get() = colorResource(R.color.colorPrimaryFg)
