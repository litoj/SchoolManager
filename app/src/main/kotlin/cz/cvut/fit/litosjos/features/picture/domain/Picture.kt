package cz.cvut.fit.litosjos.features.picture.domain

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.isValid

data class Picture(
	val base: Item,
	val uri: String,
)

fun Picture.isValid() = base.isValid() && uri.isNotBlank()