package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.AppSettings

/**
 * One action of the selection strip and the more menu.
 * - the settings order places the first four on the strip, the rest in the menu.
 * - a connectable action holds no slot of its own, it takes the slot of its target in search.
 */
enum class SelectionAction {
	TEST, CUT, EDIT, DELETE, REFERENCE, FLIP, EXPORT, GOTO;

	/** Cut never runs in a search list, Go-to never in a browse one. */
	fun applies(search: Boolean): Boolean = when (this) {
		CUT -> !search
		GOTO -> search
		else -> true
	}

	/** A connectable action rides a slot of another action, never one of its own. */
	val connectable: Boolean get() = this == GOTO

	companion object {
		fun of(id: String): SelectionAction? = entries.firstOrNull { it.name == id }
	}
}

/** The shared presentation of one action, one source for its icon and texts. */
val SelectionAction.icon: Int
	get() = when (this) {
		SelectionAction.TEST -> R.drawable.ic_test
		SelectionAction.CUT -> R.drawable.ic_cut
		SelectionAction.EDIT -> R.drawable.ic_edit
		SelectionAction.DELETE -> R.drawable.ic_delete
		SelectionAction.REFERENCE -> R.drawable.ic_reference_black
		SelectionAction.FLIP -> R.drawable.ic_flip_black
		SelectionAction.EXPORT -> R.drawable.ic_export_black
		SelectionAction.GOTO -> R.drawable.ic_goto
	}

val SelectionAction.label: Int
	get() = when (this) {
		SelectionAction.TEST -> R.string.select_test
		SelectionAction.CUT -> R.string.cut
		SelectionAction.EDIT -> R.string.edit
		SelectionAction.DELETE -> R.string.delete
		SelectionAction.REFERENCE -> R.string.action_reference
		SelectionAction.FLIP -> R.string.action_flip
		SelectionAction.EXPORT -> R.string.more_export
		SelectionAction.GOTO -> R.string.more_go_to
	}

val SelectionAction.description: Int
	get() = when (this) {
		SelectionAction.TEST -> R.string.setts_act_test
		SelectionAction.CUT -> R.string.setts_act_cut
		SelectionAction.EDIT -> R.string.setts_act_edit
		SelectionAction.DELETE -> R.string.setts_act_delete
		SelectionAction.REFERENCE -> R.string.setts_act_reference
		SelectionAction.FLIP -> R.string.setts_act_flip
		SelectionAction.EXPORT -> R.string.setts_act_export
		SelectionAction.GOTO -> R.string.setts_act_goto
	}

/**
 * The ordered selection actions with the search connections of the connectable ones.
 * - the first four of [order] hold the strip slots, the rest fill the more menu.
 * - a connection takes the target's slot in a search list, the target falls to the menu.
 * @param order the seven placeable actions, a connectable never among them
 * @param connections a connectable to its target, which must hold a strip slot
 */
class ActionConfig(val order: List<SelectionAction>, val connections: Map<SelectionAction, SelectionAction>) {

	/** The strip slots of one list kind, where search substitutes the connected action. */
	fun bar(search: Boolean): List<SelectionAction> =
		order.take(BAR_SLOTS)
			.map { slot -> connections.entries
				.firstOrNull { it.value == slot }?.key
				?.takeIf { it.applies(search) } ?: slot }
			.filter { it.applies(search) }

	/** The more-menu actions of one list kind, in the picked order. */
	fun overflow(search: Boolean): List<SelectionAction> {
		val onBar = bar(search).toSet()
		val placed = order.filter { it !in onBar && it.applies(search) }
		val loose = SelectionAction.entries.filter { it.connectable && it.applies(search) && it !in connections }
		return placed + loose
	}

	/**
	 * The order with one action moved, where a connection of a target that lost its slot clears.
	 */
	fun moved(from: Int, to: Int): ActionConfig {
		val moved = order.toMutableList()
		moved.add(to, moved.removeAt(from))
		val bar = moved.take(BAR_SLOTS)
		return ActionConfig(moved, connections.filter { it.value in bar })
	}

	companion object {

		const val BAR_SLOTS = 4

		val DEFAULT_ORDER = listOf(
			SelectionAction.TEST, SelectionAction.CUT, SelectionAction.EDIT,
			SelectionAction.DELETE, SelectionAction.REFERENCE, SelectionAction.FLIP,
			SelectionAction.EXPORT,
		)

		val DEFAULT_CONNECTIONS = mapOf(SelectionAction.GOTO to SelectionAction.CUT)

		/**
		 * Reads the stored order, where a damaged record falls back to the default.
		 * - a connection with an unknown action or an off-bar target drops.
		 */
		fun load(): ActionConfig {
			val stored = AppSettings.getString(ORDER_KEY, "").split(',').mapNotNull(SelectionAction::of)
			val order = if (stored.size == DEFAULT_ORDER.size && stored.toSet() == DEFAULT_ORDER.toSet())
				stored else DEFAULT_ORDER
			val bar = order.take(BAR_SLOTS)
			val connections = AppSettings.getString(CONNECTIONS_KEY, writeConnections(DEFAULT_CONNECTIONS))
				.split(';').filter { it.isNotEmpty() }
				.mapNotNull { pair ->
					val parts = pair.split('=')
					val from = parts.getOrNull(0)?.let(SelectionAction::of)
					val to = parts.getOrNull(1)?.let(SelectionAction::of)
					if (from?.connectable == true && to != null && to in bar) from to to else null
				}
				.toMap()
			return ActionConfig(order, connections)
		}

		/** Writes the order and the connections, which the loader validates anyway. */
		fun save(order: List<SelectionAction>, connections: Map<SelectionAction, SelectionAction>) {
			AppSettings.set(ORDER_KEY, order.joinToString(",") { it.name })
			AppSettings.set(CONNECTIONS_KEY, writeConnections(connections))
		}

		private fun writeConnections(connections: Map<SelectionAction, SelectionAction>) =
			connections.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

		private const val ORDER_KEY = "selection_actions"
		private const val CONNECTIONS_KEY = "selection_connections"
	}
}
