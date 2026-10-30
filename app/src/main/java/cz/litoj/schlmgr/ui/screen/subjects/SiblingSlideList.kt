package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import cz.litoj.schlmgr.ui.component.ItemList
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.viewmodel.ExplorerScreenState
import cz.litoj.schlmgr.ui.viewmodel.ItemListState

/**
 * The list of the subjects screen with its sibling slide.
 * - the slide waits for the incoming list: the target advances only after a load lands.
 * - a remap republishes without loading, so it must not trigger the slide.
 * - the list left behind is frozen from the last in-sync frame.
 * - the outgoing pane drives its own list state, since one LazyListState
 *   cannot drive two live lists.
 */
@Composable
internal fun SiblingSlideList(
	state: ExplorerScreenState,
	listState: ItemListState,
	callbacks: ItemListCallbacks,
	scrollState: LazyListState,
	modifier: Modifier,
) {
	var target by remember { mutableStateOf(state.sibling) }
	var lastSyncList by remember { mutableStateOf(listState) }
	var lastSyncStep by remember { mutableIntStateOf(state.contentStep) }
	// post-composition: while in sync, the holder follows the list, and during
	// a pending switch it keeps the last in-sync pair
	SideEffect {
		if (target == state.sibling) {
			lastSyncList = listState
			lastSyncStep = state.contentStep
		}
	}
	// captured at the switch recomposition itself, because the outgoing pane
	// reads it that very frame, before any effect could assign it
	val frozen = remember(state.sibling) { lastSyncList }
	val frozenStep = remember(state.sibling) { lastSyncStep }
	LaunchedEffect(target, state.sibling, state.contentStep) {
		if (target != state.sibling && state.contentStep != frozenStep) target = state.sibling
	}
	AnimatedContent(
		targetState = target,
		modifier = modifier,
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
		if (key == state.sibling) {
			ItemList(
				state = listState,
				callbacks = callbacks,
				modifier = Modifier.fillMaxSize(),
				reorderable = state.reorderable,
				listState = scrollState,
			)
		} else {
			val frozenScroll = rememberLazyListState()
			// the frozen pane keeps the scroll of the switch frame: its state
			// jumps there on the frame it first runs
			LaunchedEffect(frozenScroll) {
				frozenScroll.scrollToItem(
					scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
			}
			ItemList(
				state = frozen,
				callbacks = callbacks,
				modifier = Modifier.fillMaxSize(),
				reorderable = state.reorderable,
				listState = frozenScroll,
			)
		}
	}
}
