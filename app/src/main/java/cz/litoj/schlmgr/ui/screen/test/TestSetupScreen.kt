package cz.litoj.schlmgr.ui.screen.test

import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.activity.TestActivity
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.iconRes
import cz.litoj.schlmgr.ui.component.SettingNumberRow
import cz.litoj.schlmgr.ui.component.SettingSwitchRow
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

/**
 * The test setup screen — top bar, the fresh-entry check over the shared
 * test state, the setup content below. The two navigation steps (start
 * test, open picker) go through the view model's requests.
 *
 * @param openPicker 'Add source' — navigate to the source picker
 * @param openDrawer the hamburger press — the shell owns the drawer
 */
@Composable
fun TestSetupScreen(
	viewModel: TestSetupViewModel,
	openPicker: () -> Unit,
	openDrawer: () -> Unit,
) {
	val context = LocalContext.current

	// The fresh-entry check, once per entry — the saveable entered flag keeps
	// a rotation from resetting the sources. The decision lives in the view model.
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
 * The setup rows below the bar — amount/duration fields, the selected
 * sources and the Start button. Answers live in [TestSetupState],
 * behaviour in [TestSetupViewModel].
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
			HairlineDivider()
			SettingNumberRow(
				stringResource(R.string.test_time), TestSetupState.timeText,
				viewModel::setTime,
			) { viewModel.restoreTime(); keyboard?.hide() }
			HairlineDivider()
			// The reverse-test direction — on shows the word and asks for one of
			// its translations — off keeps the default direction.
			SettingSwitchRow(
				stringResource(R.string.test_reverse),
				TestSetupState.reverseTest,
				viewModel::toggleReverse,
			)
			HairlineDivider()
			// The shared list is a SnapshotStateList, so background-thread changes
			// made by the picker's dedup thread recompose this directly.
			LazyColumn(Modifier.weight(1f)) {
				// the add action leads the list, so the sources can grow without
				// scrolling past the ones already picked (the whole hierarchy
				// picked hides it — nothing left to add)
				if (!viewModel.hierarchyPicked()) item { AddSourceTile(viewModel::openPicker) }
				// the chains are dedup-distinct, so each list is its own collision-free key
				items(TestSetupState.list, key = { it }) { chain ->
					SourceRow(chain.toSourceRowUi()) { viewModel.removeSource(chain) }
				}
			}
		}
		Button(
			onClick = viewModel::startTest,
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
				modifier = Modifier.size(30.dp),
			)
			Spacer(Modifier.width(3.dp))
			Text(
				row.displayName,
				style = TextStyle(fontSize = 20.sp, color = DefaultTextColor),
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
 * The add action leading the sources list — a full-width tile with a
 * dashed outline and a centered label, an action rather than a config
 * row.
 */
@Composable
private fun AddSourceTile(onAdd: () -> Unit) {
	val density = LocalDensity.current
	val border = colorResource(R.color.colorPrimaryFg).copy(alpha = 0.5f)
	val corner = with(density) { 8.dp.toPx() }
	// keyed on density — its instance changes with configuration, and the
	// dash lengths must re-scale with it
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
			modifier = Modifier.size(20.dp),
		)
		Spacer(Modifier.width(5.dp))
		Text(
			stringResource(R.string.test_add),
			style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
		)
	}
}
