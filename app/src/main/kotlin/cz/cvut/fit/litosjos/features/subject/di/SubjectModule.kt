package cz.cvut.fit.litosjos.features.subject.di

import cz.cvut.fit.litosjos.features.subject.presentation.SubjectsScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module


val subjectModule = module {
	viewModelOf(::SubjectsScreenViewModel)
}