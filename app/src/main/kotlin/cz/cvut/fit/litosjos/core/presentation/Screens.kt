package cz.cvut.fit.litosjos.core.presentation

import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.ItemType


sealed class Screens(val route: String) {

	companion object {
		const val ID_KEY = "id"
		fun of(item: Item) = "${item.type.name.lowercase()}/${item.id}"
	}

	data object ChapterDetail : Screens("${ItemType.CHAPTER.name.lowercase()}/{${ID_KEY}}") {
		fun of(id: Int) = "${ItemType.CHAPTER.name.lowercase()}/$id"
	}

	data object SubjectsList : Screens("subjects")
}