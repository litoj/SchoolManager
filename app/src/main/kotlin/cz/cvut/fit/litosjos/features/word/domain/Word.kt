package cz.cvut.fit.litosjos.features.word.domain

import cz.cvut.fit.litosjos.core.domain.Item

typealias Translation = String // NOTE: this is for future change to a list+separate entity

class Word(base: Item = Item(), val translations: Translation = "") : Item(base) {
	fun copy(base: Item = this, translations: Translation = this.translations) =
		Word(base, translations)

	override fun isValid() = super.isValid() && translations.isNotBlank()
}