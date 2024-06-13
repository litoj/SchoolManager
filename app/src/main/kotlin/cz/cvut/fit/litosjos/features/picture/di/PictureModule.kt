package cz.cvut.fit.litosjos.features.picture.di

import cz.cvut.fit.litosjos.core.data.db.ItemDatabase
import cz.cvut.fit.litosjos.features.picture.data.PictureRepository
import cz.cvut.fit.litosjos.features.picture.data.db.PictureLocalDataSource
import cz.cvut.fit.litosjos.features.picture.presentation.PictureDialogViewModel
import cz.cvut.fit.litosjos.features.picture.presentation.PictureListItemViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val pictureModule = module {
	single { ItemDatabase.newInstance(androidContext()) }
	single { get<ItemDatabase>().pictureDao() }
	single { PictureLocalDataSource(get(), get()) }
	single { PictureRepository(get()) }

	viewModelOf(::PictureDialogViewModel)
	viewModelOf(::PictureListItemViewModel)
}