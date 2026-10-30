package cz.litoj.schlmgr.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.screen.importer.ImportTargetScreen
import cz.litoj.schlmgr.ui.screen.importer.ImportTargetViewModel
import cz.litoj.schlmgr.ui.shell.StatusBarBand

/**
 * The share and open-with entry: a picked file lands through the import mover.
 * - the target screen owns the explorer and the import action.
 * - the activity only hands the incoming URI over and finishes after Done or Cancel.
 */
class ImportActivity : ComponentActivity() {

	private val importUri = mutableStateOf<Uri?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startAppShell()
		importUri.value = sharedUri(intent)
		if (importUri.value == null) finish()
		setContent {
			val uri = importUri.value ?: return@setContent
			// keyed by the uri, so a second share swaps the mover of the open document
			val vm: ImportTargetViewModel = viewModel(key = uri.toString()) {
				ImportTargetViewModel(uri)
			}
			Box(Modifier.fillMaxSize()) {
				ImportTargetScreen(
					vm,
					onExit = { finish() },
					onDone = { path -> openMain(path) },
				)
				StatusBarBand()
			}
			DialogHost()
		}
	}

	/** A second share while this mover is open swaps the document, see the view model key. */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		importUri.value = sharedUri(intent) ?: importUri.value
	}

	/** Done hands the destination to the main screen, which reloads and scrolls to the rows. */
	private fun openMain(path: List<Int>) {
		startActivity(
			Intent(this, MainActivity::class.java).apply {
				// the mover lives in the task of the sharing app, so NEW_TASK
				// routes the main activity of the app's own task
				addFlags(
					Intent.FLAG_ACTIVITY_NEW_TASK or
						Intent.FLAG_ACTIVITY_CLEAR_TOP or
						Intent.FLAG_ACTIVITY_SINGLE_TOP
				)
				putIntegerArrayListExtra(MainActivity.EXTRA_PATH, ArrayList(path))
				putExtra(MainActivity.EXTRA_SCROLL, true)
			},
		)
		finish()
	}

	/** ACTION_VIEW carries the data URI, ACTION_SEND carries the stream extra. */
	private fun sharedUri(intent: Intent?): Uri? {
		if (intent == null) return null
		if (intent.data != null) return intent.data
		if (Build.VERSION.SDK_INT >= 33)
			return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
		// a hostile sender may put any parcelable in the extra
		@Suppress("DEPRECATION")
		return intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
	}
}
