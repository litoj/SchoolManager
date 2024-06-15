package cz.cvut.fit.litosjos.core.di

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.data.db.ItemDatabase
import cz.cvut.fit.litosjos.core.data.db.ItemLocalDataSource
import cz.cvut.fit.litosjos.core.presentation.item.ItemDialogViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreModule = module {
	singleOf(ItemDatabase::newInstance)
	single { get<ItemDatabase>().itemDao() }
	singleOf(::ItemLocalDataSource)
	singleOf(::ItemRepository)

	viewModelOf(::ItemDialogViewModel)

	singleOf(FirebaseCrashlytics::getInstance)
	singleOf(FirebaseAnalytics::getInstance)
}