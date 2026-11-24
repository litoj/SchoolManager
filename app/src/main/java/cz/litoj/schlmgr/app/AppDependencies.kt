package cz.litoj.schlmgr.app

import android.content.Context
import cz.litoj.schlmgr.data.CounterService
import cz.litoj.schlmgr.data.DataExchangeService
import cz.litoj.schlmgr.data.FlipRunner
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.ItemEditor
import cz.litoj.schlmgr.data.ItemTransferService
import cz.litoj.schlmgr.data.db.ItemDatabase

/** Services shared by the activities of one application process. */
class AppDependencies(val appContext: Context, database: ItemDatabase? = null) {
	val settings = Settings(appContext)
	val userMessages = UserMessages(appContext)
	val graphQueries by lazy(LazyThreadSafetyMode.NONE) { GraphQueries(checkNotNull(database)) }
	val dataExchange by lazy(LazyThreadSafetyMode.NONE) { DataExchangeService(checkNotNull(database)) }
	val counterService by lazy(LazyThreadSafetyMode.NONE) { CounterService(checkNotNull(database)) }
	val itemEditor by lazy(LazyThreadSafetyMode.NONE) { ItemEditor(checkNotNull(database)) }
	val itemTransfer by lazy(LazyThreadSafetyMode.NONE) { ItemTransferService(checkNotNull(database)) }
	val flipper by lazy(LazyThreadSafetyMode.NONE) { FlipRunner(checkNotNull(database)) }
}
