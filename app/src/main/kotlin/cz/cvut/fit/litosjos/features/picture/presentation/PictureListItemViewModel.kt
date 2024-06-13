package cz.cvut.fit.litosjos.features.picture.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedState
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedViewModel
import cz.cvut.fit.litosjos.features.picture.data.PictureRepository
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PictureListItemViewModel(
	item: Item,
	private val sharedViewModel: ListItemSharedViewModel,
	private val repository: PictureRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(
		PictureListItemState(
			sharedViewModel.state.value.settings,
			Picture(item, ""),
			sharedViewModel.state.value.settings.showTranslated
		)
	)
	val state get() = _state.asStateFlow()

	init {
		viewModelScope.launch {
			_state.value = state.value.copy(picture = repository.getBy(item.id)!!)

			sharedViewModel.state.collectLatest {
				_state.value = state.value.copy(
					settings = it.settings, showImage = if (it.settings.toggleAll) it.settings.showTranslated
					else state.value.showImage
				)
			}
		}
	}

	fun toggleImage(on: Boolean = !state.value.showImage) {
		if (state.value.settings.toggleAll) sharedViewModel.update(
			ListItemSharedState(settings = sharedViewModel.state.value.settings.copy(showTranslated = on))
		)

		_state.value = state.value.copy(showImage = on)
	}
}

data class PictureListItemState(
	val settings: Settings, val picture: Picture, val showImage: Boolean
)