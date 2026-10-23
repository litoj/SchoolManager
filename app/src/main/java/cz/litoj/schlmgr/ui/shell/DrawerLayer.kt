package cz.litoj.schlmgr.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val DrawerWidth = 250.dp

private val ScrimColor = Color(0x99000000)

/**
 * The modal drawer of the app, drawn by the shell.
 * - it holds the content, a full-screen close scrim and the 250dp panel.
 * - it is not the M2 ModalDrawer, whose gesture surface would eat the close taps.
 */
@Composable
fun DrawerLayer(
	state: DrawerLayerState,
	drawerContent: @Composable () -> Unit,
	content: @Composable () -> Unit,
) {
	val drag = remember { DraggableState { state.dragBy(it) } }
	Box(Modifier.fillMaxSize()) {
		// the content takes no drawer gestures.
		// - the drawer opens only through the bar icon.
		// - the horizontal swipe of the explorer, the sibling switch, stays with the content
		Box(Modifier.fillMaxSize()) { content() }

		// the drawer owns its back press while it is open.
		// - it sits after the content, so it outranks the back ladder of every screen.
		// - the press closes the topmost state instead of acting behind the drawer.
		// - derivedStateOf keeps the raw reads to one flip per drag, not one per frame
		val open by remember { derivedStateOf { state.isOpen } }
		val scrimmed by remember { derivedStateOf { state.scrimFraction > 0f } }
		BackHandler(enabled = open) { state.close() }

		// the scrim exists only while the panel is out.
		// a tap anywhere beside the panel closes it, not only the far edge
		if (scrimmed) {
			Box(
				Modifier
					.fillMaxSize()
					.graphicsLayer { alpha = state.scrimFraction }
					.background(ScrimColor)
					.clickable(
						interactionSource = remember { MutableInteractionSource() },
						indication = null,
					) { state.close() }
					.draggable(drag, Orientation.Horizontal, onDragStopped = { state.settle(it) })
			)
		}

		Box(
			Modifier
				.offset { IntOffset(state.offset.value.roundToInt(), 0) }
				.fillMaxHeight()
				.width(DrawerWidth)
				.draggable(drag, Orientation.Horizontal, onDragStopped = { state.settle(it) })
		) { drawerContent() }
	}
}

/**
 * The drawer position, the x offset of the panel.
 * - it animates between the closed park at -[DrawerWidth] and the open rest at 0.
 * - a drag snaps along, and then [settle] picks the nearer anchor or the flung one.
 */
@Composable
fun rememberDrawerLayerState(): DrawerLayerState {
	val density = LocalDensity.current
	val scope = rememberCoroutineScope()
	return remember { DrawerLayerState(scope, with(density) { DrawerWidth.toPx() }) }
}

class DrawerLayerState internal constructor(
	private val scope: CoroutineScope,
	private val panelWidth: Float,
) {
	internal val offset = Animatable(-panelWidth)

	val isOpen: Boolean get() = offset.targetValue == 0f

	val scrimFraction: Float get() = offset.value / panelWidth + 1f

	fun open() {
		scope.launch { offset.animateTo(0f, DrawerSpec) }
	}

	fun close() {
		scope.launch { offset.animateTo(-panelWidth, DrawerSpec) }
	}

	private var dragJob: Job? = null

	internal fun dragBy(delta: Float) {
		// one live drag job, so a newer delta cancels the pending snap.
		// it applies the fresh total
		dragJob?.cancel()
		dragJob = scope.launch { offset.snapTo((offset.value + delta).coerceIn(-panelWidth, 0f)) }
	}

	/**
	 * Ends a drag.
	 * - a fling settles its way, and a slow drag takes the nearer anchor.
	 * - a speed above panelWidth px/s counts as a fling, otherwise the halfway rule applies.
	 */
	internal fun settle(velocity: Float) {
		val fling = panelWidth // px/s, since a modest fling overtakes the halfway rule
		val target = when {
			velocity > fling -> 0f
			velocity < -fling -> -panelWidth
			offset.value > -panelWidth / 2 -> 0f
			else -> -panelWidth
		}
		scope.launch { offset.animateTo(target, DrawerSpec) }
	}
}

private val DrawerSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
