package cz.cvut.fit.litosjos.features.chapter.di

import cz.cvut.fit.litosjos.features.chapter.presentation.ChapterScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val chapterModule = module {
	viewModelOf(::ChapterScreenViewModel)
}