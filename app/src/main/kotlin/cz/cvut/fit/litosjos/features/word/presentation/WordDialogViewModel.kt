package cz.cvut.fit.litosjos.features.word.presentation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.features.word.data.WordRepository
import cz.cvut.fit.litosjos.features.word.domain.Word
import cz.cvut.fit.litosjos.features.word.domain.isValid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class WordDialogViewModel(
	item: Word, savedStateHandle: SavedStateHandle, private val repository: WordRepository
) : ViewModel() {

	private val isNew = !item.isValid()
	private val _state = MutableStateFlow(
		if (isNew) item.copy(base = item.base.copy(parentId = savedStateHandle[Screens.ID_KEY]))
		else item
	)

//	private val ts = System.currentTimeMillis()
//	init { // TODO: figure out why does this get called again on the first edit, yet ItemViewModel is fine
//		Log.d("WordDialogViewModel", "init($ts): $item")
//	}

	val state = _state.asStateFlow()

	fun updateState(item: Word) {
//		Log.d("WordDialogViewModel", "update($ts): $item")
		_state.value = item
	}

	fun save(onSuccess: () -> Unit) {
		val item = state.value
//		Log.d("WordDialogViewModel", "save($ts): $item")
		if (item.isValid()) viewModelScope.launch {
			if (isNew) {
				repository.insert(item)
				// reset the viewModel, because koin will reuse it for other creations
				_state.value = Word(Item(parentId = item.base.parentId, type = item.base.type), "")
			} else repository.update(item)
			onSuccess()
		}
	}
}