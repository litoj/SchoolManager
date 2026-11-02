package cz.litoj.schlmgr.ui.screen.test

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.IconSize
import cz.litoj.schlmgr.ui.component.iconRes
import cz.litoj.schlmgr.ui.component.SettingNumberRow
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

/**
 * The test setup screen: top bar, the fresh-entry check and the setup content below.
 * - the check runs over the shared test state.
 * - the two navigation steps, start test and open picker, go through the view model requests.
 *
 * @param openPicker Add source, which navigates to the source picker
 * @param openDrawer the hamburger press, since the shell owns the drawer
 */
@Composable
fun TestSetupScreen(
	viewModel: TestSetupViewModel,
	openPicker: () -> Unit,
	openDrawer: () -> Unit,
) {
	val context = LocalContext.current

	// the fresh-entry check, run once per entry.
	// - the saveable entered flag keeps a rotation from resetting the sources.
	// - the decision lives in the view model.
	var entered by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(viewModel) {
		if (entered) return@LaunchedEffect
		entered = true
		viewModel.onEntered()
	}

	LaunchedEffect(viewModel) {
		viewModel.requests.collect { request ->
			when (request) {
				TestSetupViewModel.Request.StartTest ->
					context.startActivity(Intent(context, TestActivity::class.java))
				TestSetupViewModel.Request.OpenPicker -> openPicker()
			}
		}
	}

	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(onNavigate = openDrawer)
			TopBarTitle(stringResource(R.string.select_test))
		}
		TestSetupContent(viewModel, Modifier.weight(1f).headerShadow())
	}
}

/**
 * The setup rows below the bar: amount and duration fields, the sources and the Start button.
 * - answers live in [TestSetupState], and behaviour in [TestSetupViewModel].
 */
@Composable
private fun TestSetupContent(viewModel: TestSetupViewModel, modifier: Modifier = Modifier) {
	val keyboard = LocalSoftwareKeyboardController.current
	Box(modifier.fillMaxSize()) {
		Column(Modifier.fillMaxSize()) {
			SettingNumberRow(
				stringResource(R.string.test_size), TestSetupState.amountText,
				viewModel::setAmount,
			) { viewModel.restoreAmount(); keyboard?.hide() }
			// the pool clamps the amount, so the caption names what is really asked
			if (TestSetupState.poolCount > 0) {
				RowCaption(pluralStringResource(R.plurals.test_available, TestSetupState.poolCount, TestSetupState.poolCount))
			}
			HairlineDivider()
			SettingNumberRow(
				stringResource(R.string.test_time), TestSetupState.timeText,
				viewModel::setTime,
			) { viewModel.restoreTime(); keyboard?.hide() }
			// the caption derives the run length, since the row itself holds only one word's share
			val total = TestSetupState.timeTotal()
			RowCaption(stringResource(R.string.test_total_time, total.perWord, total.count, total.total))
			HairlineDivider()
			DirectionRow(TestSetupState.reverseTest, viewModel::toggleReverse)
			HairlineDivider()
			Text(
				stringResource(R.string.test_sources_header),
				Modifier.padding(start = 8.dp, top = 2.dp),
				style = TextStyle(fontSize = 15.sp, color = DefaultTextColor),
			)
			// the shared list is a SnapshotStateList.
			// so a background-thread change from the picker dedup recomposes this directly
			LazyColumn(Modifier.weight(1f)) {
				// the add action leads the list, so sources grow without scrolling.
				// a fully picked hierarchy hides it, since nothing is left to add
				if (!viewModel.hierarchyPicked()) item { AddSourceTile(viewModel::openPicker) }
				// the chains are dedup-distinct, so each list is its own collision-free key
				items(TestSetupState.list, key = { it }) { chain ->
					SourceRow(chain.toSourceRowUi()) { viewModel.removeSource(chain) }
				}
			}
		}
		if (!TestSetupState.hasSources()) {
			Text(
				stringResource(R.string.test_start_blocked),
				Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 50.dp),
				style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
			)
		}
		Button(
			onClick = viewModel::startTest,
			enabled = TestSetupState.hasSources(),
			colors = ButtonDefaults.buttonColors(
				backgroundColor = colorResource(R.color.colorPrimaryHeader),
				contentColor = colorResource(R.color.colorOnHeader),
				disabledBackgroundColor = colorResource(R.color.colorPrimaryHeader).copy(alpha = 0.4f),
				disabledContentColor = colorResource(R.color.colorOnHeader).copy(alpha = 0.6f),
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

/** The small caption line under a setting row. */
@Composable
private fun RowCaption(text: String) {
	Text(
		text,
		Modifier.padding(start = 8.dp),
		style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
	)
}

/** The two directions of the run, where one chosen chip names both sides. */
@Composable
private fun DirectionRow(reverse: Boolean, onToggle: () -> Unit) {
	Column(Modifier.fillMaxWidth().padding(5.dp)) {
		Text(stringResource(R.string.test_direction), style = TextStyle(fontSize = 18.sp, color = DefaultTextColor))
		Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
			DirectionChip(stringResource(R.string.test_dir_normal), !reverse, Modifier.weight(1f), onToggle)
			DirectionChip(stringResource(R.string.test_dir_reverse), reverse, Modifier.weight(1f), onToggle)
		}
	}
}

/** One side of the direction, where a tap on an unselected chip flips the direction. */
@Composable
private fun DirectionChip(label: String, selected: Boolean, modifier: Modifier, onToggle: () -> Unit) {
	Box(
		modifier
			.padding(end = 5.dp)
			.clip(RoundedCornerShape(5.dp))
			.background(if (selected) colorResource(R.color.colorPrimaryHeader) else colorResource(R.color.colorSubBackground))
			.clickable(onClick = onToggle)
			.padding(vertical = 4.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			label,
			style = TextStyle(fontSize = 15.sp, color = if (selected) colorResource(R.color.colorOnHeader) else DefaultTextColor),
		)
	}
}

@Composable
private fun SourceRow(row: SourceRowUi, onRemove: () -> Unit) {
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
				painter = painterResource(row.kind.iconRes),
				contentDescription = null,
				modifier = Modifier.size(IconSize),
			)
			Spacer(Modifier.width(3.dp))
			Text(
				row.displayName,
				style = TextStyle(fontSize = 20.sp, color = DefaultTextColor),
			)
			Spacer(Modifier.width(5.dp))
			Text(
				pluralStringResource(R.plurals.test_source_words, row.words, row.words),
				style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
			)
		}
		Image(
			painter = painterResource(R.drawable.ic_cancel_round),
			contentDescription = stringResource(R.string.delete),
			modifier = Modifier
				.size(IconSize)
				.clickable(onClick = onRemove),
		)
	}
}

/**
 * The add action leading the sources list: a full-width tile with a dashed outline.
 * - it holds a centered label, since it is an action rather than a config row.
 */
@Composable
private fun AddSourceTile(onAdd: () -> Unit) {
	val density = LocalDensity.current
	val border = colorResource(R.color.colorPrimaryFg).copy(alpha = 0.5f)
	val corner = with(density) { 8.dp.toPx() }
	// keyed on density, since its instance changes with configuration.
	// the dash lengths must re-scale with it
	val stroke = remember(density) {
		Stroke(
			width = with(density) { 1.dp.toPx() },
			pathEffect = with(density) {
				PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 8.dp.toPx()), 0f)
			},
		)
	}
	Row(
		Modifier
			.fillMaxWidth()
			.padding(5.dp)
			.drawBehind {
				drawRoundRect(color = border, style = stroke, cornerRadius = CornerRadius(corner))
			}
			.clickable(onClick = onAdd)
			.padding(6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.Center,
	) {
		Image(
			painter = painterResource(R.drawable.ic_add),
			contentDescription = null,
			modifier = Modifier.size(IconSize),
		)
		Spacer(Modifier.width(5.dp))
		Text(
			stringResource(R.string.test_add),
			style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
		)
	}
}
