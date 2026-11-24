package cz.litoj.schlmgr.app

import android.content.Context

/** Context access for code outside [AppDependencies]. */
object GlobalDependencies {

	/** The application context, safe to hold for the whole process, also from a crashing thread. */
	lateinit var appContext: Context
}
