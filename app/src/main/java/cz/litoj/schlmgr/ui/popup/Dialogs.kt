package cz.litoj.schlmgr.ui.popup

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
 * One dialog of the app: a state holder rendered by [DialogHost] as a Compose
 * [Dialog] — its own window, above everything of the hosting activity, exactly
 * like the former `PopupWindow` shell. The object survives rotation (only the
 * window is recreated by recomposition), so the editing state of e.g. the item
 * creator stays intact with no repaint hooks.
 */
interface DialogSpec {

	/**
	 * Whether a tap on the dimmed area outside the card may close this dialog.
	 * Dialogs guarding unsaved changes override this and return `false` while the
	 * user has made any changes. The back key always closes.
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
 * The app's dialog stack: the process-wide replacement of the former
 * `AbstractPopup.showed` window list. The state is observable, so every
 * activity composing a [DialogHost] renders the same dialogs — whichever one
 * is resumed shows them on top, and a dialog triggered by an activity that
 * then finishes (the test results) simply renders on the next one. Rotation
 * needs no repaint machinery: the stack survives, recomposition re-renders.
 */
object Dialogs {

	private val _stack = MutableStateFlow<List<DialogSpec>>(emptyList())
	val stack: StateFlow<List<DialogSpec>> = _stack.asStateFlow()

	/** Whether any dialog is on screen. */
	val isActive: Boolean get() = _stack.value.isNotEmpty()

	/** Whether a dialog of the class [T] is on the stack (a restarted screen guards its own popup). */
	inline fun <reified T : DialogSpec> isShowing(): Boolean = stack.value.any { it is T }

	/**
	 * Shows the dialog on top of the stack. Safe to call from any thread —
	 * the flow update is atomic and recomposition runs on the main thread.
	 */
	fun show(spec: DialogSpec) {
		_stack.update { it + spec }
	}

	/** Removes the dialog from the stack and notifies it (see [DialogSpec.onDismissed]). */
	fun dismiss(spec: DialogSpec) {
		var wasPresent = false
		_stack.update { stack ->
			if (spec in stack) {
				wasPresent = true
				stack - spec
			} else stack
		}
		if (wasPresent) spec.onDismissed()
	}

	/** Removes every dialog from the stack, top first. */
	fun clear() {
		var old: List<DialogSpec> = emptyList()
		_stack.update { stack ->
			old = stack
			emptyList()
		}
		old.asReversed().forEach { it.onDismissed() }
	}
}

/**
 * Renders the [Dialogs.stack]. Composed at the root of every activity: the
 * dialogs render in their own windows, above the whole activity, no matter
 * which fragment or view tree the host sits in.
 *
 * The scrim handles the outside tap through [DialogSpec.canDismissByOutsideTouch]
 * (the unsaved-changes guard); the back key closes through the dialog window's
 * own dismiss-on-back, reported to [Dialog]'s `onDismissRequest`.
 */
@Composable
fun DialogHost() {
	// Deliberately not lifecycle-aware: a dialog opened before the app goes to
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
