package cz.litoj.schlmgr.ui.activity

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.ui.viewmodel.ItemRow

/**
 * The shared cold start of the app activities, before the first composition.
 * - the process state first, so no read races the database build.
 * - the row display options carry their own stored half in the ui.
 */
internal fun ComponentActivity.startAppShell() {
	Startup.init(this)
	ItemRow.applyStoredOptions()
	// a solid dark scrim keeps the navigation bar black in both palettes
	enableEdgeToEdge(
		// the status band is dark green in both palettes, so the dark style
		// keeps its icons white whatever the system theme does
		statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
		navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
	)
}
