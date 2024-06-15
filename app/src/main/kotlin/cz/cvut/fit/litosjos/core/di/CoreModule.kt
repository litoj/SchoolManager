package cz.cvut.fit.litosjos.core.di

import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.data.db.ItemDatabase
import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreModule = module {
	single { ItemDatabase.newInstance(androidContext()) }
	single { get<ItemDatabase>().itemDao() }
	singleOf(::ItemLocalDataSource)
	singleOf(::ItemRepository)

	viewModelOf(::ItemDialogViewModel)
}