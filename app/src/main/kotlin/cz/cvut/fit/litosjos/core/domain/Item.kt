package cz.cvut.fit.litosjos.core.domain

open class Item(
	val id: Int = 0,
	val parentId: Int? = null,
	val name: String = "",
	val description: String = "",
	val passedTests: Int = 0,
	val failedTests: Int = 0,
) {
	constructor(src: Item) : this(
		id = src.id,
		parentId = src.parentId,
		name = src.name,
		description = src.description,
		passedTests = src.passedTests,
		failedTests = src.failedTests,
	)

	open fun copy(
		id: Int = this.id,
		parentId: Int? = this.parentId,
		name: String = this.name,
		description: String = this.description,
		passedTests: Int = this.passedTests,
		failedTests: Int = this.failedTests,
	) = Item(
		id = id,
		parentId = parentId,
		name = name,
		description = description,
		passedTests = passedTests,
		failedTests = failedTests,
	)

	open fun isValid() = name.isNotBlank()
}