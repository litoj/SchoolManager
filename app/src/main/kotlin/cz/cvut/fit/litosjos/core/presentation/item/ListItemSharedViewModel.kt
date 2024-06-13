package cz.cvut.fit.litosjos.core.presentation.item

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.features.settings.data.SettingsRepository
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ListItemSharedViewModel(settingsRepository: SettingsRepository) : ViewModel() {

	private val _state = MutableStateFlow(ListItemSharedState())
	val state = _state.asStateFlow()

	init {
		viewModelScope.launch {
			settingsRepository.getSettings()
				.collectLatest { _state.value = state.value.copy(settings = it) }
		}
	}

	fun update(newState: ListItemSharedState) {
		_state.value = newState
	}
}

data class ListItemSharedState(
	val settings: Settings = Settings(),
	val dialog: (@Composable () -> Unit)? = null,
)