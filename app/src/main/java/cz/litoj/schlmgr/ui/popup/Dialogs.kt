package cz.litoj.schlmgr.ui.popup

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.litoj.schlmgr.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One dialog of the app — a state holder rendered by [DialogHost] as a Compose
 * [Dialog] — its own window above the hosting activity.
 * - survives rotation (only the window re-creates), so editor state stays
 *   intact with no repaint hooks.
 */
interface DialogSpec {

	/**
	 * Whether an outside tap on the dimmed area may close this dialog.
	 * - unsaved-changes guards return `false` once edited.
	 * - the back key always closes.
	 */
	fun canDismissByOutsideTouch(): Boolean = true

	/**
	 * Called after the dialog left the screen, whatever dismissed it (back key,
	 * scrim tap, a button) — the place to consume one-time data.
	 */
	fun onDismissed() {}

	@Composable
	fun Content()
}

/**
 * The app's dialog stack — the process-wide dialog list as observable state.
 * - every activity composing a [DialogHost] renders the same dialogs.
 *   Rotation needs no repaint machinery (the stack survives, recomposition
 *   re-renders).
 */
object Dialogs {

	private val _stack = MutableStateFlow<List<DialogSpec>>(emptyList())
	val stack: StateFlow<List<DialogSpec>> = _stack.asStateFlow()

	inline fun <reified T : DialogSpec> isShowing(): Boolean = stack.value.any { it is T }

	/**
	 * Shows the dialog on top of the stack, from any thread (the flow
	 * update is atomic).
	 */
	fun show(spec: DialogSpec) {
		_stack.update { it + spec }
	}

	/** Removes the dialog from the stack and notifies it (see [DialogSpec.onDismissed]). */
	fun dismiss(spec: DialogSpec) {
		// The removal decision reads the pre-update value, never a side effect of an
		// update lambda (which may re-run under contention) — CAS retries until it wins.
		while (true) {
			val current = _stack.value
			if (spec !in current) return
			if (_stack.compareAndSet(current, current - spec)) {
				spec.onDismissed()
				return
			}
		}
	}

	/** Removes every dialog from the stack, top first — tests only. */
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
	// Deliberately not lifecycle-aware — a dialog opened before the app goes to
	// the background must stay on screen when it comes back.
	val stack by Dialogs.stack.collectAsState()
	stack.forEach { spec ->
		key(spec) {
			Dialog(
				onDismissRequest = { Dialogs.dismiss(spec) },
				properties = DialogProperties(
					dismissOnClickOutside = false, // the scrim below decides, guarding dirty dialogs
					usePlatformDefaultWidth = false,
					decorFitsSystemWindows = false,
				),
			) {
				Box(
					Modifier
						.fillMaxSize()
						.imePadding()
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
		}
	}
}
