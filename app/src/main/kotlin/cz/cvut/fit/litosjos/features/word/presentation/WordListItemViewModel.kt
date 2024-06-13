package cz.cvut.fit.litosjos.features.word.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedState
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedViewModel
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.word.data.WordRepository
import cz.cvut.fit.litosjos.features.word.domain.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WordListItemViewModel(
	item: Item,
	private val sharedViewModel: ListItemSharedViewModel,
	private val repository: WordRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(
		WordListItemState(Settings(), Word(item, ""), false)
	)
	val state get() = _state.asStateFlow()

	init {
		viewModelScope.launch {
			_state.value = state.value.copy(word = repository.getBy(item.id)!!)

			sharedViewModel.state.collectLatest {
				_state.value = state.value.copy(
					settings = it.settings,
					showTranslated = if (it.settings.toggleAll) it.settings.showTranslated
					else state.value.showTranslated
				)
			}
		}
	}

	fun toggleTranslated(on: Boolean = !state.value.showTranslated) {
		if (state.value.settings.toggleAll) sharedViewModel.update(
			ListItemSharedState(settings = sharedViewModel.state.value.settings.copy(showTranslated = on))
		)

		_state.value = state.value.copy(showTranslated = on)
	}
}

data class WordListItemState(
	val settings: Settings, val word: Word, val showTranslated: Boolean
)