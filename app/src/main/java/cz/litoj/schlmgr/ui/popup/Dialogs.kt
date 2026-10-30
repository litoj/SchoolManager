package cz.litoj.schlmgr.ui.popup

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.litoj.schlmgr.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The keyboard inset the dialog host pads with, held while the keyboard is away.
 * - a pause drops the keyboard, the held inset keeps the layout put.
 * - the resume holds it further until the live inset returns, so the card
 *   does not collapse and rise again.
 * - a close of the keyboard while resumed follows the live inset at once.
 */
@Stable
internal class StickyImePad {
	var paused by mutableStateOf(false)
		private set
	var awaiting by mutableStateOf(false)
		private set
	var held by mutableIntStateOf(0)
		private set

	/** The live inset of the last frame, written by [follow]. */
	var live by mutableIntStateOf(0)
		private set

	/** The pad of the current frame, derived so an unchanged pad recomposes nothing. */
	val pad: Int by derivedStateOf { padFor(live) }

	fun pause() {
		paused = true
	}

	/** Nothing was held keeps the live inset in the lead right away. */
	fun resume() {
		paused = false
		awaiting = held > 0
	}

	/** The pad to render for the live inset of the current frame. */
	fun padFor(ime: Int): Int = if (paused || awaiting) held else ime

	/** Captures the live inset while it leads, after a resume only once it returns. */
	fun follow(ime: Int) {
		live = ime
		when {
			paused -> {}
			awaiting -> if (ime > 0) {
				awaiting = false
				held = ime
			}
			else -> held = ime
		}
	}
}

/**
 * One dialog of the app: a state holder rendered by [DialogHost] as a Compose [Dialog].
 * - it gets its own window above the hosting activity.
 * - it survives rotation, since only the window re-creates, so editor state stays intact.
 */
interface DialogSpec {

	/**
	 * Unsaved-changes guards return false once edited.
	 * - the back key always closes.
	 */
	fun canDismissByOutsideTouch(): Boolean = true

	/**
	 * Called after the dialog left the screen, whatever dismissed it.
	 * - that covers the back key, a scrim tap and a button.
	 * - it is the place to consume one-time data.
	 */
	fun onDismissed() {}

	@Composable
	fun Content()
}

/**
 * The dialog stack of the app: the process-wide dialog list as observable state.
 * - every activity composing a [DialogHost] renders the same dialogs.
 * - rotation needs no repaint machinery, since the stack survives and recomposition re-renders.
 * - an activity may finish with its dialog still on the stack.
 * - the host beneath in the task renders it, see the test results popup.
 */
object Dialogs {

	private val _stack = MutableStateFlow<List<DialogSpec>>(emptyList())
	val stack: StateFlow<List<DialogSpec>> = _stack.asStateFlow()

	inline fun <reified T : DialogSpec> isShowing(): Boolean = stack.value.any { it is T }

	/** The flow update is atomic, so any thread may call it. */
	fun show(spec: DialogSpec) {
		_stack.update { it + spec }
	}

	fun dismiss(spec: DialogSpec) {
		// the removal decision reads the pre-update value, never a side effect of an update lambda.
		// a lambda may re-run under contention, so the CAS retries until it wins.
		while (true) {
			val current = _stack.value
			if (spec !in current) return
			if (_stack.compareAndSet(current, current - spec)) {
				spec.onDismissed()
				return
			}
		}
	}

	/** Removes every dialog from the stack, top first, for tests only. */
	@VisibleForTesting
	fun clear() {
		while (true) {
			val current = _stack.value
			if (current.isEmpty()) return
			if (_stack.compareAndSet(current, emptyList())) {
				current.asReversed().forEach { it.onDismissed() }
				return
			}
		}
	}
}

/**
 * Renders the [Dialogs.stack] in its own windows above the activity. The scrim
 * handles outside taps, the dialog window handles back.
 */
@Composable
fun DialogHost() {
	// deliberately not lifecycle-aware, since a dialog opened before the app goes to the
	// background must stay on screen when it comes back
	val stack by Dialogs.stack.collectAsState()
	stack.forEach { spec ->
		key(spec) {
			Dialog(
				onDismissRequest = { Dialogs.dismiss(spec) },
				properties = DialogProperties(
					// the scrim below decides, guarding dirty dialogs
					dismissOnClickOutside = false,
					usePlatformDefaultWidth = false,
					decorFitsSystemWindows = false,
				),
			) {
				DialogScrim(spec)
			}
		}
	}
}

/**
 * The scrim of one dialog, padded by the keyboard inset.
 * - a pause drops the keyboard, a [StickyImePad] keeps the card layout put
 *   until the resume brings the keyboard back.
 */
@Composable
private fun DialogScrim(spec: DialogSpec) {
	val sticky = remember { StickyImePad() }
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_PAUSE -> sticky.pause()
				Lifecycle.Event.ON_RESUME -> sticky.resume()
				else -> {}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	val density = LocalDensity.current
	val ime = WindowInsets.ime.getBottom(density)
	SideEffect { sticky.follow(ime) }
	Box(
		Modifier
			.fillMaxSize()
			.padding(bottom = with(density) { sticky.pad.toDp() })
			.background(colorResource(R.color.colorScrim))
			.pointerInput(spec) {
				detectTapGestures {
					if (spec.canDismissByOutsideTouch()) Dialogs.dismiss(spec)
				}
			}
	) {
		spec.Content()
	}
}
