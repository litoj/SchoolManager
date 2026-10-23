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
 * The only shell activity of the app.
 * - starts the program state before the first composition reads the database.
 * - hands the window to [SchlMgrApp].
 */
class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Startup.init(this)
		// the row display options have their own stored half in the ui
		ItemRow.applyStoredOptions()
		// a solid dark scrim keeps the navigation bar black in both palettes
		enableEdgeToEdge(
			// the status band is dark green in both palettes, so the dark style
			// keeps its icons white whatever the system theme does
			statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
			navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
		)
		setContent { SchlMgrApp() }
	}
}
