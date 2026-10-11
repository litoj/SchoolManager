package cz.litoj.schlmgr.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import cz.litoj.schlmgr.ui.viewmodel.ExplorerScreenState
import cz.litoj.schlmgr.ui.viewmodel.ItemListState
import cz.litoj.schlmgr.ui.viewmodel.SiblingSwitch

/**
 * The explorer screen — top chrome + item list, plus a slot for the
 * bottom action strip. Stateless, the screen collects the state and
 * maps the row intents back through [callbacks].
 * - only a [SiblingSwitch] ([ExplorerScreenState.sibling]) animates the list.
 */
@Composable
fun ExplorerScaffold(
	state: ExplorerScreenState,
	listState: ItemListState,
	onBreadcrumbClick: (Int) -> Unit,
	onInfoClick: () -> Unit,
	callbacks: ItemListCallbacks,
	modifier: Modifier = Modifier,
	actionBar: @Composable () -> Unit = {},
) {
	// The displayed list — browse content and search results alike — comes from
	// the caller. The scaffold renders it as-is, no search side channel.
	Column(modifier.fillMaxSize()) {
		ExplorerTopBar(
			breadcrumbs = state.breadcrumbs,
			onBreadcrumbClick = onBreadcrumbClick,
			info = state.info,
			isInfoExpanded = state.isInfoExpanded,
			onInfoClick = onInfoClick,
		)
		// The slide waits for the incoming list, because the target advances only once a
		// content load lands — a remap republishes without loading and must not
		// trigger it. The list left behind is frozen from the last in-sync frame,
		// so even a same-frame load still freezes the stale list.
		val switch = state.sibling
		var target by remember { mutableStateOf(switch) }
		var lastSyncList by remember { mutableStateOf(listState) }
		var lastSyncStep by remember { mutableIntStateOf(state.contentStep) }
		// post-composition — while in sync, the holder follows the list
		// (during a pending switch it holds the last in-sync pair)
		SideEffect {
			if (target == state.sibling) {
				lastSyncList = listState
				lastSyncStep = state.contentStep
			}
		}
		// captured at the switch recomposition itself — the outgoing pane reads
		// it that very frame, before any effect could assign it
		val frozen = remember(switch) { lastSyncList }
		val frozenStep = remember(switch) { lastSyncStep }
		LaunchedEffect(target, switch, state.contentStep) {
			if (target != switch && state.contentStep != frozenStep) target = switch
		}
		AnimatedContent(
			targetState = target,
			modifier = Modifier.weight(1f),
			transitionSpec = {
				val slide = tween<IntOffset>(250, easing = FastOutSlowInEasing)
				if ((targetState?.dir ?: 1) > 0)
					(slideInHorizontally(slide) { it } + fadeIn()) togetherWith
						(slideOutHorizontally(slide) { -it } + fadeOut())
				else
					(slideInHorizontally(slide) { -it } + fadeIn()) togetherWith
						(slideOutHorizontally(slide) { it } + fadeOut())
			},
			label = "explorer-list",
		) { key ->
			ItemList(
				// the outgoing pane shows the list frozen at its switch
				state = if (key == state.sibling) listState else frozen,
				callbacks = callbacks,
				modifier = Modifier.fillMaxSize(),
				reorderable = state.reorderable,
			)
		}
		actionBar()
	}
}
