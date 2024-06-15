package cz.cvut.fit.litosjos.features.subject.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.features.settings.data.SettingsRepository
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.subject.domain.Subject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubjectsScreenViewModel(
	private val itemRepository: ItemRepository, settingsRepository: SettingsRepository
) : ViewModel() {

	private val _state = MutableStateFlow(SubjectsScreenState(emptyList(), Settings()))
	val state = _state.asStateFlow()

	init {
		viewModelScope.launch {
			itemRepository.getByParent(null)
				.collectLatest { _state.update { value -> value.copy(items = it.map { it as Subject }) } }
		}
		viewModelScope.launch {
			settingsRepository.getSettings()
				.collectLatest { _state.update { value -> value.copy(settings = it) } }
		}
	}

	fun deleteChild(item: Item) = viewModelScope.launch { itemRepository.delete(item.id) }
}

data class SubjectsScreenState(val items: List<Subject>, val settings: Settings)
