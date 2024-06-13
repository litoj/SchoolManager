package cz.cvut.fit.litosjos.core.domain

data class Item(
	val id: Int = 0, // -1 would create literally item with id=-1
	val parentId: Int? = null,
	val name: String = "",
	val description: String = "",
	val passedTests: Int = 0,
	val failedTests: Int = 0,
	val type: ItemType = ItemType.SUBJECT,
)

fun Item.isValid() = name.isNotBlank()