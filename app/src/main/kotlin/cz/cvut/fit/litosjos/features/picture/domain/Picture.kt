package cz.cvut.fit.litosjos.features.picture.domain

import cz.cvut.fit.litosjos.core.domain.Item

class Picture(base: Item = Item(), val uri: String = "") : Item(base) {
	fun copy(base: Item = this, uri: String = this.uri) = Picture(base, uri)
	override fun isValid() = super.isValid() && uri.isNotBlank()
}