package cz.litoj.schlmgr.ui.activity

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.ui.shell.SchlMgrApp
import cz.litoj.schlmgr.ui.viewmodel.ItemRow

/**
 * The app's only shell activity.
	 * - starts the program state (repository, option statics, crash handler) before the first composition reads the database.
	 * - hands the window to [SchlMgrApp]: drawer, navigation, top bars, dialogs, messages — all composed there.
 */
class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Startup.init(this)
		// the row display's settings are the ui's own stored half
		ItemRow.applyStoredOptions()
		// the navigation bar stays black in both palettes — a solid
		// dark scrim keeps it that way under edge-to-edge
		enableEdgeToEdge(
			// the status band is the darkened green in both palettes — the
			// dark style keeps its icons white whatever the system theme does
			statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
			navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
		)
		setContent { SchlMgrApp() }
	}
}
