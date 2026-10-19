package cz.litoj.schlmgr.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The floating New-action button — bottom right, a rounded square in the
 * title bar's colouring. The screen drives [visible] with the list's scroll
 * (down hides, up shows) and offers the [kinds] of the open screen.
 * - one offered kind opens its creator when the press releases — a click and a drag alike.
 * - several kinds open a circular palette around the button: dragging onto an icon commits it, as does clicking it.
 * - a plain tap leaves the palette standing; a tap outside the icons, or on the button again, closes it.
 */
@Composable
fun NewItemFab(
	kinds: List<RowKind>,
	visible: Boolean,
	onPick: (RowKind) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (kinds.isEmpty()) return
	// the button's center in root coordinates — the palette circles it
	var center by remember { mutableStateOf(Offset.Zero) }
	var root by remember { mutableStateOf(Offset.Zero) }
	// the palette lives while the finger holds the button, and stays
	// standing after a plain tap released it
	var pressed by remember { mutableStateOf(false) }
	var standing by remember { mutableStateOf(false) }
	// the icon the finger's drag hovers — commits on the release
	var hovered by remember { mutableIntStateOf(-1) }
	val radius = with(LocalDensity.current) { PaletteRadius.toPx() }
	val dragThreshold = with(LocalDensity.current) { DragThreshold.toPx() }

	fun commit(index: Int) {
		standing = false
		onPick(kinds[index])
	}

	Box(modifier.onGloballyPositioned { root = it.positionInRoot() }) {
		// a hidden button must not leave its palette floating — the icons
		// follow the button's visibility
		if ((pressed || standing) && visible && kinds.size > 1) {
			if (standing && !pressed) {
				// a tap anywhere outside the icons closes the standing palette
				Box(
					Modifier.matchParentSize().clickable(
						interactionSource = remember { MutableInteractionSource() },
						indication = null,
					) { standing = false },
				)
			}
			kinds.forEachIndexed { i, kind ->
				val at = center - root + paletteOffset(i, kinds.size, radius)
				Image(
					painterResource(kind.iconRes),
					contentDescription = null,
					modifier = Modifier
						.offset {
							IntOffset(
								(at.x - IconSize.toPx() / 2).roundToInt(),
								(at.y - IconSize.toPx() / 2).roundToInt(),
							)
						}
						.size(IconSize)
						// the hovered icon carries the header colour, the rest
						// the strip's grey wash
						.background(
							if (i == hovered) ExplorerColors.header else ExplorerColors.actionBar,
							CircleShape,
						)
						.padding(IconPadding)
						// the standing palette's icons take plain taps
						.then(if (standing && !pressed) {
							Modifier.clickable(
								interactionSource = remember { MutableInteractionSource() },
								indication = null,
							) { commit(i) }
						} else Modifier),
				)
			}
		}
		AnimatedVisibility(
			visible,
			modifier = Modifier.align(Alignment.BottomEnd),
			enter = fadeIn() + slideInVertically { it / 2 },
			exit = fadeOut() + slideOutVertically { it / 2 },
		) {
			Box(
				Modifier
					.padding(FabMargin)
					.size(FabSize)
					.shadow(FabElevation, RoundedCornerShape(FabCorner))
					.background(ExplorerColors.header, RoundedCornerShape(FabCorner))
					.onGloballyPositioned {
						center = it.positionInRoot() +
							Offset(it.size.width / 2f, it.size.height / 2f)
					}
					// the kinds list is a fresh instance per caller recomposition —
					// the count is the stable key the gesture depends on
					.pointerInput(kinds.size) {
						awaitEachGesture {
							val down = awaitFirstDown(requireUnconsumed = false)
							down.consume()
							if (kinds.size == 1) {
								// one kind — the release commits it, wherever
								// the finger travelled
								waitForRelease(down.id)
								onPick(kinds[0])
								return@awaitEachGesture
							}
							pressed = true
							hovered = -1
							var pick = -1
							try {
								while (true) {
									val change = nextChange(down.id) ?: continue
									if (!change.pressed) {
										pick = hovered
										break
									}
									change.consume()
									// the drag runs from the press point — wherever on the
									// button the finger landed, only the movement counts
									hovered = paletteHover(
										change.position.x - down.position.x,
										change.position.y - down.position.y,
										kinds.size,
										dragThreshold,
									)
								}
							} finally {
								pressed = false
								hovered = -1
							}
							if (pick >= 0) commit(pick)
							// a plain tap toggles the standing palette
							else standing = !standing
						}
					},
			) {
				Image(
					painterResource(R.drawable.ic_add),
					contentDescription = null,
					colorFilter = ColorFilter.tint(ExplorerColors.onHeader),
					modifier = Modifier.align(Alignment.Center).size(FabIconSize),
				)
			}
		}
	}
}

/** Waits for [pointer] to lift — consuming the moves so nothing below scrolls. */
private suspend fun AwaitPointerEventScope.waitForRelease(pointer: PointerId) {
	while (true) {
		val change = nextChange(pointer) ?: continue
		change.consume()
		if (!change.pressed) return
	}
}

/** The tracked pointer's next change, or null while another finger acts. */
private suspend fun AwaitPointerEventScope.nextChange(pointer: PointerId): PointerInputChange? =
	awaitPointerEvent().changes.firstOrNull { it.id == pointer }

/** The button — a rounded square of the title bar's colour. Every size below derives from it. */
internal val FabSize = 50.dp
/** The palette icons' cell — the button's own size, the type icon at its kind's spot. */
private val IconSize = FabSize
/** The icons drawn inside the button and the cells — the room the padding leaves. */
private val IconPadding = 10.dp
private val FabIconSize = FabSize - IconPadding * 2
/** The palette icons sit one button-size off the button's own edge. */
internal val PaletteRadius = FabSize * 2
private val FabCorner = 10.dp
internal val FabMargin = 15.dp
private val FabElevation = 6.dp

/**
 * True while the list idles or scrolls up — the New button hides on the
 * way down the way a browser's search bar does, and returns on the way up.
 */
@Composable
internal fun rememberScrollUpVisible(list: LazyListState): Boolean {
	var shown by remember { mutableStateOf(true) }
	LaunchedEffect(list) {
		// a packed scroll position — the index times a bound no single
		// item's offset can reach, so the two move as one number
		var previous = 0L
		snapshotFlow { list.firstVisibleItemIndex.toLong() * 100_000L + list.firstVisibleItemScrollOffset }
			.collect { position ->
				if (position < previous) shown = true else if (position > previous) shown = false
				previous = position
			}
	}
	return shown
}

/**
 * The arc the palette icons fan over — from straight up to straight left,
 * away from the button's bottom-right corner.
 */
private const val PaletteStart = 90f
private const val PaletteArc = 90f
/**
 * A finger that moved less than half the button from its press point is a
 * tap, not a drag — beyond it the direction picks, however short and
 * wherever the finger sits.
 */
private val DragThreshold = FabSize / 2

/**
 * The palette icon's center offset from the button's center — [index] of
 * [count], fanned over the arc (see [PaletteStart]).
 */
internal fun paletteOffset(index: Int, count: Int, radius: Float): Offset {
	val step = if (count > 1) PaletteArc / (count - 1) else 0f
	val angle = Math.toRadians((PaletteStart + index * step).toDouble())
	return Offset(
		(radius * cos(angle)).toFloat(),
		(-radius * sin(angle)).toFloat(),
	)
}

/**
 * The hovered palette icon for a drag of `[dx, dy]` from the press point —
 * the icon whose sector the direction falls into. The corner icons are the
 * easy reaches, so every middle one claims two thirds of the gap to each
 * neighbour; the space behind the arc splits at its midpoint between the
 * two ends. `-1` while the drag stays under [threshold] — a tap, not a
 * direction.
 */
internal fun paletteHover(dx: Float, dy: Float, count: Int, threshold: Float): Int {
	if (hypot(dx, dy) < threshold) return -1
	if (count == 1) return 0
	// compose's y grows down — negate for the math angle, up is 90°; the
	// wrap keeps the left direction at 180° instead of atan2's -180°
	val angle = ((Math.toDegrees(atan2(-dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
	val step = PaletteArc / (count - 1)
	// the sector boundary at the gap's start plus [share] of it — the
	// corner icons weigh one, the middle ones two
	fun boundary(i: Int): Float {
		if (i == count)
			// the wrap behind the arc — the ends' circular midpoint
			return PaletteStart + step * (count - 1) + (360f - PaletteArc) / 2
		fun weight(i: Int) = if (i == 0 || i == count - 1) 1f else 2f
		val share = weight(i - 1) / (weight(i - 1) + weight(i))
		return PaletteStart + step * (i - 1) + step * share
	}
	// icon 0 owns from behind the arc around to its boundary with icon 1
	if (angle < boundary(1) || angle >= boundary(count)) return 0
	for (i in 1 until count) if (angle < boundary(i + 1)) return i
	return count - 1
}
