package com.schlmgr.gui.popup

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.PopupWindow

import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import com.schlmgr.gui.Controller

import java.util.LinkedList

/**
 * The shared shell for all popup dialogs: a fullscreen (dimmed) [PopupWindow] centered
 * over the current activity, surviving screen rotation by re-running [create].
 *
 * Subclasses with XML layouts pass the resource id; Compose-based popups pass `0` (the
 * default content root is an empty [FrameLayout] from [mkView]) and add a [androidx.compose.ui.platform.ComposeView]
 * with their content in [addContent] — see [CreatorPopup] for the pattern.
 */
abstract class AbstractPopup protected constructor(
	private val resId: Int,
	private val onlyMain: Boolean,
) {

	private val creator = Runnable { create() }

	private var backBtnDismiss = true
	private var pw: PopupWindow? = null

	/**
	 * Builds the popup's content view when no layout resource is used ([resId] == `0`).
	 */
	protected open fun mkView(context: Context): View = FrameLayout(context)

	/**
	 * Called with the [PopupWindow] after it is created, before it is shown.
	 * Subclasses may adjust window parameters (e.g. soft input mode) here.
	 */
	protected open fun onWindowCreated(pw: PopupWindow) {}

	fun dismiss() = dismiss(true)

	fun dismiss(forever: Boolean) {
		backBtnDismiss = false
		pw?.dismiss()
		pw = null
		showed.remove(this)
		isActive = false
		if (forever) Controller.removePopupRepaint(creator)
		onDismiss(forever)
	}

	/**
	 * Called right after this popup was dismissed, so a subclass may react (e.g. consume a
	 * one-time message). [forever] mirrors the dismissal: it is `false` when the popup will be
	 * re-created later (e.g. rotation) and `true` when it won't be shown again.
	 */
	protected open fun onDismiss(forever: Boolean) {}

	protected fun create() {
		isActive = true
		isShowing = true
		val host: ComponentActivity = if (onlyMain) Controller.activity
		else Controller.currentActivity ?: return
		host.runOnUiThread {
			val view: ViewGroup = if (resId != 0)
				host.layoutInflater.inflate(resId, null) as ViewGroup
			else mkView(host) as ViewGroup
			// Focusable so text fields inside the popup can receive keyboard input. The
			// "empty space around the card" is part of the (fullscreen) content root, so
			// its taps are handled deterministically by the root touch listener below
			// instead of any PopupWindow outside-dismiss mechanism.
			val window = PopupWindow(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, true)
			onWindowCreated(window)
			window.setOnDismissListener {
				isShowing = false
				if (backBtnDismiss) host.onBackPressedDispatcher.onBackPressed()
			}
			window.setBackgroundDrawable(ColorDrawable(0x90000000.toInt()))
			// Tapping the dimmed area outside the card closes the popup, unless it guards
			// unsaved changes (see canDismissByOutsideTouch()).
			view.setOnTouchListener { _, event ->
				if (event.actionMasked == MotionEvent.ACTION_DOWN && canDismissByOutsideTouch()) {
					dismiss()
					true
				} else false
			}
			// A PopupWindow lives in its own window, detached from the activity's view tree,
			// so Compose content cannot find the ViewTree owners the way a ComposeView inside
			// an activity can. Provide them explicitly (the hosting activity implements all
			// three), both on the content root and on the popup's decor view.
			view.setViewTreeLifecycleOwner(host)
			view.setViewTreeSavedStateRegistryOwner(host)
			view.setViewTreeViewModelStoreOwner(host)
			addContent(view)
			showed.add(this)
			window.showAtLocation(view, Gravity.CENTER, 0, 0)
			// The decor view only exists after showing; the window recomposer resolves the
			// owner starting from this root, so it must carry the owners too.
			val decor = view.rootView
			decor.setViewTreeLifecycleOwner(host)
			decor.setViewTreeSavedStateRegistryOwner(host)
			decor.setViewTreeViewModelStoreOwner(host)
			pw = window
		}
	}

	/**
	 * Determines whether tapping the empty space outside the popup may close it.
	 * Popups guarding unsaved changes override this and return `false` while the
	 * user has made any changes.
	 */
	protected open fun canDismissByOutsideTouch(): Boolean = true

	protected abstract fun addContent(view: ViewGroup)

	companion object {

		private val showed = LinkedList<AbstractPopup>()

		// Real static fields so the (still Java) activity call sites can read them directly.
		@JvmField
		var isActive = false

		@JvmField
		var isShowing = false

		/**
		 * Will display again.
		 */
		@JvmStatic
		fun clean() {
			for (ap in showed.toList()) ap.dismiss(false)
			showed.clear()
		}

		/**
		 * Won't be displayed again.
		 */
		@JvmStatic
		fun clear() {
			for (ap in showed.toList()) ap.dismiss(true)
			showed.clear()
		}
	}
}
