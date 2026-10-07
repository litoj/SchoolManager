package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * The app's base activity: edge-to-edge set-up and the back dispatch the
 * shell hooks into. Dialogs need no care here anymore — they render through
 * [cz.litoj.schlmgr.ui.popup.DialogHost] in their own windows and survive
 * rotation with the dialog state itself.
 */
open class BaseActivity : AppCompatActivity() {

	private val backCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			onUserBackPressed()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		onBackPressedDispatcher.addCallback(this, backCallback)
		// the transient user-message snackbars — installed by every activity
		// hosting the shell (see TestActivity too)
		showUserMessages()
		// Edge-to-edge is enforced for apps targeting Android 15+; opt in consistently on
		// older versions too so the insets handling applies everywhere.
		WindowCompat.setDecorFitsSystemWindows(window, false)
	}

	/**
	 * Replaces the deprecated [android.app.Activity.onBackPressed].
	 * Subclasses override this instead of `onBackPressed()`.
	 */
	protected open fun onUserBackPressed() {
		goBack()
	}

	/**
	 * Hands the back event over to the system (this activity's callback is
	 * temporarily disabled), so the predictive back animations (back-to-home,
	 * cross-activity) can run. Also a destination's way to decline the back
	 * event and let the shell proceed.
	 *
	 * A destination calling this while handling the event must pass its own
	 * callback as [declining]: the re-dispatch consults the newest enabled
	 * callback first — the declining one — which would consume the event again
	 * and recurse goBack ↔ the handler until the stack overflows.
	 */
	fun goBack(declining: OnBackPressedCallback? = null) {
		declining?.isEnabled = false
		backCallback.isEnabled = false
		try {
			onBackPressedDispatcher.onBackPressed()
		} finally {
			backCallback.isEnabled = true
			declining?.isEnabled = true
		}
	}
}
