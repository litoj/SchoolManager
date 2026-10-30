package cz.litoj.schlmgr.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import cz.litoj.schlmgr.ui.shell.MainLaunchTarget
import cz.litoj.schlmgr.ui.shell.SchlMgrApp

/**
 * The only shell activity of the app.
 * - starts the program state before the first composition reads the database.
 * - hands the window to [SchlMgrApp].
 */
class MainActivity : ComponentActivity() {

	private val launchTarget = mutableStateOf<MainLaunchTarget?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startAppShell()
		launchTarget.value = consumeLaunchTarget(intent)
		setContent {
			SchlMgrApp(launchTarget.value) { launchTarget.value = null }
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		launchTarget.value = consumeLaunchTarget(intent)
	}

	companion object {
		const val EXTRA_PATH = "main_path"
		const val EXTRA_SCROLL = "main_scroll"

		/**
		 * Reads the import destination and clears its extras.
		 * - a rotation recreates the activity on the same intent, so the target
		 *   must not fire twice.
		 */
		internal fun consumeLaunchTarget(intent: Intent): MainLaunchTarget? {
			val path = intent.getIntegerArrayListExtra(EXTRA_PATH)
			val scroll = intent.getBooleanExtra(EXTRA_SCROLL, false)
			intent.removeExtra(EXTRA_PATH)
			intent.removeExtra(EXTRA_SCROLL)
			if (path == null) return null
			return MainLaunchTarget(path.toList(), scroll)
		}
	}
}
