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
