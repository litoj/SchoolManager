package cz.cvut.fit.litosjos.core.presentation

import cz.cvut.fit.litosjos.core.domain.Item


sealed class Screens(val route: String) {

	companion object {
		const val ID_KEY = "id"
		fun of(item: Item) = "${item.javaClass.name.lowercase()}/${item.id}"
	}

	data object ChapterDetail : Screens("chapter/{${ID_KEY}}") {
		fun of(id: Int) = "chapter/$id"
	}

	data object SubjectsList : Screens("subjects")
}