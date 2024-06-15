package cz.cvut.fit.litosjos

import android.app.Application
import cz.cvut.fit.litosjos.core.di.coreModule
import cz.cvut.fit.litosjos.features.chapter.di.chapterModule
import cz.cvut.fit.litosjos.features.picture.di.pictureModule
import cz.cvut.fit.litosjos.features.settings.di.settingsModule
import cz.cvut.fit.litosjos.features.subject.di.subjectModule
import cz.cvut.fit.litosjos.features.word.di.wordModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
	override fun onCreate() {
		super.onCreate()
		startKoin {
			androidContext(this@App)
			modules(coreModule, settingsModule, subjectModule, chapterModule, wordModule, pictureModule)
		}
	}
}