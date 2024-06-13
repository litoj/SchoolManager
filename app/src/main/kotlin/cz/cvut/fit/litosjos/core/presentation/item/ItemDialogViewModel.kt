package cz.cvut.fit.litosjos.core.presentation.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.isValid
import cz.cvut.fit.litosjos.core.presentation.Screens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ItemDialogViewModel(
	item: Item, savedStateHandle: SavedStateHandle, private val repository: ItemRepository
) : ViewModel() {

	private val isNew = !item.isValid()
	private val _state = MutableStateFlow(
		if (isNew) item.copy(parentId = savedStateHandle[Screens.ID_KEY])
		else item
	)

	val state = _state.asStateFlow()

	fun updateState(item: Item) {
		_state.value = item
	}

	fun save(onSuccess: () -> Unit) {
		val item = state.value
		if (item.isValid()) viewModelScope.launch {
			if (isNew) {
				repository.insert(item)
				// reset the viewModel, because koin will reuse it for other creations
				_state.value = Item(parentId = item.parentId, type = item.type)
			} else repository.update(item)
			onSuccess()
		}
	}
}