package cz.cvut.fit.litosjos.features.settings.domain

import cz.cvut.fit.litosjos.features.chapter.domain.Chapter
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.subject.domain.Subject
import cz.cvut.fit.litosjos.features.word.domain.Word


data class PreviewData(
	val parent: Subject,
	val chapter: Chapter,
	val picture: Picture,
	val word: Word,
)