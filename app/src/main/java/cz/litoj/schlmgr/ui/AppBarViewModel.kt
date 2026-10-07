package cz.litoj.schlmgr.ui

import android.view.MenuItem
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The app-bar state shared by the shell activity and the current navigation
 * destination: which options menu the destination uses, whether the search
 * button is visible, and whether it currently acts as the select-all toggle
 * (rows selected).
 *
 * The shell observes [state] and renders its `Toolbar` views from it; the
 * destination writes through [setDestination]. A destination change resets
 * the select-all form — the explorer re-asserts it when its rows are selected.
 * Requests made during fragment inflation (before the shell views exist)
 * converge through the flow, the way the former `Controller.applyBarState`
 * race fix did.
 */
class AppBarViewModel : ViewModel() {

	/**
	 * The actions the shell dispatches to the current destination: menu item
	 * clicks, the select-all toggle and the search submit. Back is not part
	 * of it — each destination registers its own `OnBackPressedCallback`
	 * with the dispatcher.
	 */
	interface DestinationActions {
		/** The user picked an item of the 'more options' menu. */
		fun onMenuItemClick(item: MenuItem): Boolean = false

		/** The bar's select-all button, shown while rows are selected. */
		fun onSelectAllToggle() {}

		/** The user submitted the app-bar search field's query. */
		fun onSearchSubmit(query: String) {}
	}

	data class State(
		/** The destination's options menu resource, `0` for none. */
		val menuRes: Int = 0,
		/** Whether the search button is visible at all. */
		val searchVisible: Boolean = false,
		/**
		 * Whether the search button currently acts as the select-all toggle:
		 * while rows are selected the button runs the destination's select-all
		 * action instead of opening the search field. The 'more options'
		 * button keeps its menu then, so the menu stays reachable.
		 */
		val selectAsSelectAll: Boolean = false,
	)

	private val _state = MutableStateFlow(State())
	val state: StateFlow<State> = _state.asStateFlow()

	/** The bar state applied right now — for one-shot reads outside a collector. */
	val current: State get() = _state.value

	private val _destination = MutableStateFlow<DestinationActions?>(null)

	/** The current destination's actions — for one-shot reads outside a collector. */
	val currentDestination: DestinationActions? get() = _destination.value

	/**
	 * Registers the shown destination: its [DestinationActions] and bar
	 * state. A `null` [actions] destination (or one whose actions are all
	 * default) simply receives nothing from the shell.
	 */
	fun setDestination(actions: DestinationActions?, menuRes: Int, searchVisible: Boolean) {
		_destination.value = actions
		_state.update {
			State(menuRes = menuRes, searchVisible = searchVisible)
		}
	}

	/** Swaps the options menu without resetting the select-all form. */
	fun setMenu(menuRes: Int) {
		_state.update { it.copy(menuRes = menuRes) }
	}

	/** Shows or hides the search button without resetting the select-all form. */
	fun setSearchVisible(visible: Boolean) {
		_state.update { it.copy(searchVisible = visible) }
	}

	/** Swaps the search button between its search and select-all form. */
	fun setSelectAsSelectAll(on: Boolean) {
		_state.update { it.copy(selectAsSelectAll = on) }
	}
}
