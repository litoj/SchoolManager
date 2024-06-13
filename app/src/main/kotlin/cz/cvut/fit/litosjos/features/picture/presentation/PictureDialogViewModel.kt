package cz.cvut.fit.litosjos.features.picture.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.features.picture.data.PictureRepository
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.picture.domain.isValid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class PictureDialogViewModel(
	item: Picture, savedStateHandle: SavedStateHandle, private val repository: PictureRepository
) : ViewModel() {

	private val isNew = !item.isValid()
	private val _state = MutableStateFlow(
		if (isNew) item.copy(base = item.base.copy(parentId = savedStateHandle[Screens.ID_KEY]))
		else item
	)

	val state = _state.asStateFlow()

	fun updateState(item: Picture) {
		_state.value = item
	}

	fun save(onSuccess: () -> Unit) {
		val item = state.value
		if (item.isValid()) viewModelScope.launch {
			if (isNew) {
				repository.insert(item)
				// reset the viewModel, because koin will reuse it for other creations
				_state.value = Picture(Item(parentId = item.base.parentId, type = item.base.type), "")
			} else repository.update(item)
			onSuccess()
		}
	}
}