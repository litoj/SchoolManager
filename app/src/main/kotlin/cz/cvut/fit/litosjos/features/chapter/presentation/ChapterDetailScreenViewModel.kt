package cz.cvut.fit.litosjos.features.chapter.presentation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.features.settings.data.SettingsRepository
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChapterDetailScreenViewModel(
	savedStateHandle: SavedStateHandle,
	private val itemRepository: ItemRepository,
	settingsRepository: SettingsRepository
) : ViewModel() {

	private val _state = MutableStateFlow(
		ChapterDetailScreenState(
			Item(id = savedStateHandle[Screens.ID_KEY]!!), emptyList(), Settings(), null
		)
	)
	val state = _state.asStateFlow()

	init {
		viewModelScope.launch {
			_state.update { it.copy(source = itemRepository.get(state.value.source.id)!!) }
		}
		viewModelScope.launch {
			itemRepository.getByParent(state.value.source.id).collectLatest {
				Log.d("", "items ${state.value.settings}")
				_state.update { value -> value.copy(items = it) }
			}
		}
		viewModelScope.launch {
			settingsRepository.getSettings().collectLatest {
				Log.d("", "settings $it")
				_state.update { value -> value.copy(settings = it) }
			}
		}
	}

	fun toggleAll(on: Boolean) {
		if (state.value.settings.toggleAll) {
			_state.update { it.copy(settings = it.settings.copy(showTranslated = on)) }
		}
	}

	fun updateDialog(dialog: (@Composable () -> Unit)?) {
		_state.update { it.copy(dialog = dialog) }
	}

	fun deleteChild(item: Item) {
		viewModelScope.launch {
			itemRepository.delete(item.id)
		}
	}
}

data class ChapterDetailScreenState(
	val source: Item,
	val items: List<Item>,
	val settings: Settings,
	val dialog: (@Composable () -> Unit)?
)
