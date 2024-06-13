package cz.cvut.fit.litosjos.features.settings.domain

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.word.domain.Word


data class PreviewData(
	val parent: Item,
	val chapter: Item,
	val picture: Picture,
	val word: Word,
)