package cz.cvut.fit.litosjos.features.chapter.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.Screens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChapterScreenViewModel(
	savedStateHandle: SavedStateHandle,
	private val repository: ItemRepository,
) : ViewModel() {

	private val _state = MutableStateFlow(
		ChapterDetailScreenState(
			Item(id = savedStateHandle[Screens.ID_KEY]!!), emptyList()
		)
	)
	val state = _state.asStateFlow()

	init {
		viewModelScope.launch {
			val source = repository.get(state.value.source.id)!!
			repository.getByParent(state.value.source.id)
				.collectLatest { _state.value = ChapterDetailScreenState(source, it) }
		}
	}

	fun deleteChild(item: Item) {
		viewModelScope.launch {
			repository.delete(item.id)
		}
	}
}

data class ChapterDetailScreenState(
	val source: Item, val items: List<Item>
)
