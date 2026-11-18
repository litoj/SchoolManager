package cz.litoj.schlmgr.ui.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.popup.DialogSpec
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.PopupButton
import cz.litoj.schlmgr.ui.popup.PopupButtonRow
import cz.litoj.schlmgr.ui.popup.PopupCard
import cz.litoj.schlmgr.ui.popup.PopupScrollBody
import cz.litoj.schlmgr.ui.popup.PopupTitle
import cz.litoj.schlmgr.ui.popup.VerticalButtonSeparator

internal const val NOTIFICATION_PROMPT_SEEN = "notificationPromptSeen"

/** Whether this launch must explain the permission before Android can ask. */
internal fun shouldAskNotificationPermission(sdk: Int, granted: Boolean, asked: Boolean): Boolean =
	sdk >= Build.VERSION_CODES.TIRAMISU && !granted && !asked

/**
 * Explains progress notifications on the first start, then hands the ask to Android.
 * - a later launch never repeats the explanation.
 * - Not now leaves the app usable without progress notifications.
 */
@Composable
fun NotificationPermissionPrompt() {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) NotificationPermissionPrompt33()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun NotificationPermissionPrompt33() {
	val context = LocalContext.current
	val request = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) {}
	LaunchedEffect(context) {
		val granted = ContextCompat.checkSelfPermission(
			context, Manifest.permission.POST_NOTIFICATIONS,
		) == PackageManager.PERMISSION_GRANTED
		if (!shouldAskNotificationPermission(
				Build.VERSION.SDK_INT,
				granted,
				AppSettings.getBool(NOTIFICATION_PROMPT_SEEN, false),
			)
		) return@LaunchedEffect
		AppSettings.set(NOTIFICATION_PROMPT_SEEN, true)
		NotificationRationalePopup {
			request.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}
}

/** The pre-permission explanation, in the same card chrome as every other dialog. */
private class NotificationRationalePopup(private val onAllow: () -> Unit) : DialogSpec {

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			PopupTitle(stringResource(R.string.notification_permission_title))
			HairlineDivider()
			PopupScrollBody(stringResource(R.string.notification_permission_body))
			HairlineDivider()
			PopupButtonRow {
				PopupButton(
					stringResource(R.string.notification_permission_later),
					Modifier.weight(1f),
				) { Dialogs.dismiss(this@NotificationRationalePopup) }
				VerticalButtonSeparator()
				PopupButton(
					stringResource(R.string.notification_permission_allow),
					Modifier.weight(1f),
				) {
					Dialogs.dismiss(this@NotificationRationalePopup)
					onAllow()
				}
			}
		}
	}
}
