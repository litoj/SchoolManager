package cz.cvut.fit.litosjos.features.word.domain

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.isValid

typealias Translation = String

data class Word(
	val base: Item,
	val translations: Translation,
)

fun Word.isValid() = base.isValid() && translations.isNotBlank()