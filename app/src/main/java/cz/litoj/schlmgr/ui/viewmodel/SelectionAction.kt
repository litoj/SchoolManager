package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.app.Settings

/**
 * One action of the selection strip and the more menu.
 * - direct slots fill in the picked order, as width allows.
 * - Cut applies to a browse list, Go-to to a search list.
 */
enum class SelectionAction(val action: ExplorerAction) {
	TEST(ExplorerAction.TEST),
	CUT(ExplorerAction.CUT),
	EDIT(ExplorerAction.EDIT),
	DELETE(ExplorerAction.DELETE),
	REFERENCE(ExplorerAction.REFERENCE),
	FLIP(ExplorerAction.FLIP),
	EXPORT(ExplorerAction.EXPORT),
	GOTO(ExplorerAction.GOTO);

	/** Cut never runs in a search list, Go-to never in a browse one. */
	fun applies(search: Boolean): Boolean = when (this) {
		CUT -> !search
		GOTO -> search
		else -> true
	}

	companion object {
		fun of(id: String): SelectionAction? = entries.firstOrNull { it.name == id }
	}
}

/** The shared presentation of one action, one source for its icon and texts. */
val SelectionAction.icon: Int get() = action.icon

val SelectionAction.label: Int get() = action.label

val SelectionAction.description: Int get() = action.description

/**
 * The ordered selection actions.
 * - each list kind takes as many applicable actions as fit on its strip, the rest fill the menu.
 * - Go-to applies only in search, so it never takes a browse slot and vice versa for Cut.
 */
class ActionConfig(val order: List<SelectionAction>) {

	/** The strip slots of one list kind, in the picked order. */
	fun bar(search: Boolean, direct: Int): List<SelectionAction> = applicable(search).take(direct)

	/** The more-menu actions of one list kind, in the picked order. */
	fun overflow(search: Boolean, direct: Int): List<SelectionAction> = applicable(search).drop(direct)

	fun actionCount(search: Boolean): Int = order.count { it.applies(search) }

	private fun applicable(search: Boolean): List<SelectionAction> =
		order.filter { it.applies(search) }

	/** The order with one action moved. */
	fun moved(from: Int, to: Int): ActionConfig {
		val moved = order.toMutableList()
		moved.add(to, moved.removeAt(from))
		return ActionConfig(moved)
	}

	companion object {

		val DEFAULT_ORDER = listOf(
			SelectionAction.TEST, SelectionAction.CUT, SelectionAction.GOTO,
			SelectionAction.EDIT, SelectionAction.DELETE, SelectionAction.REFERENCE,
			SelectionAction.FLIP, SelectionAction.EXPORT,
		)

		/** The order before Go-to became a normal search-only action. */
		private val LEGACY_ORDER = listOf(
			SelectionAction.TEST, SelectionAction.CUT, SelectionAction.EDIT,
			SelectionAction.DELETE, SelectionAction.REFERENCE, SelectionAction.FLIP,
			SelectionAction.EXPORT,
		)

		/**
		 * Reads the stored order, where a damaged record falls back to the default.
		 * - a stored legacy order gains Go-to after Cut, so search keeps its place.
		 */
		fun load(settings: Settings): ActionConfig {
			val stored = settings.getString(ORDER_KEY, "").split(',').mapNotNull(SelectionAction::of)
			val order = when {
				stored.size == DEFAULT_ORDER.size && stored.toSet() == DEFAULT_ORDER.toSet() -> stored
				stored.size == LEGACY_ORDER.size && stored.toSet() == LEGACY_ORDER.toSet() ->
					stored.toMutableList().apply {
						add(indexOf(SelectionAction.CUT) + 1, SelectionAction.GOTO)
					}
				else -> DEFAULT_ORDER
			}
			return ActionConfig(order)
		}

		/** Writes the order, which the loader validates anyway. */
		fun save(settings: Settings, order: List<SelectionAction>) {
			settings.set(ORDER_KEY, order.joinToString(",") { it.name })
		}

		private const val ORDER_KEY = "selection_actions"
	}
}
