package cz.cvut.fit.litosjos.features.settings.di

import cz.cvut.fit.litosjos.features.settings.data.SettingsRepository
import cz.cvut.fit.litosjos.features.settings.data.data_preview_api.GithubApiProvider
import cz.cvut.fit.litosjos.features.settings.data.data_preview_api.PreviewRemoteDataSource
import cz.cvut.fit.litosjos.features.settings.data.datastore.SettingsDsSource
import cz.cvut.fit.litosjos.features.settings.data.file_system.SettingsFsSource
import cz.cvut.fit.litosjos.features.settings.presentation.SettingsScreenViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val settingsModule = module {
	single { SettingsDsSource(androidApplication()) }
	single { SettingsFsSource(androidContext()) }
	singleOf(::SettingsRepository)

	single { GithubApiProvider.provide(androidContext()) }
	factoryOf(::PreviewRemoteDataSource)

	viewModelOf(::SettingsScreenViewModel)
}