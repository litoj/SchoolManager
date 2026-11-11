package cz.litoj.schlmgr.app

import android.content.Context
import android.provider.Settings

/**
 * The animation decision of the phone, which Compose ignores.
 * - the window and animator scales of the developer options stand for the setting.
 * - a scale of zero means the user switched the animations off.
 */
object SystemAnimation {

	/** Whether the phone allows animation, read per use so a change applies at once. */
	fun enabled(ctx: Context) =
		scale(ctx, Settings.Global.WINDOW_ANIMATION_SCALE) > 0f &&
			scale(ctx, Settings.Global.ANIMATOR_DURATION_SCALE) > 0f

	private fun scale(ctx: Context, key: String) =
		Settings.Global.getFloat(ctx.contentResolver, key, 1f)
}
