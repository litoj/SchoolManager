package cz.cvut.fit.litosjos.features.word.di

import cz.cvut.fit.litosjos.core.data.db.ItemDatabase
import cz.cvut.fit.litosjos.features.word.data.WordRepository
import cz.cvut.fit.litosjos.features.word.data.db.WordLocalDataSource
import cz.cvut.fit.litosjos.features.word.presentation.WordDialogViewModel
import cz.cvut.fit.litosjos.features.word.presentation.WordListItemViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val wordModule = module {
	single { ItemDatabase.newInstance(androidContext()) }
	single { get<ItemDatabase>().wordDao() }
	singleOf(::WordLocalDataSource)
	singleOf(::WordRepository)
	viewModelOf(::WordListItemViewModel)
	viewModelOf(::WordDialogViewModel)
}