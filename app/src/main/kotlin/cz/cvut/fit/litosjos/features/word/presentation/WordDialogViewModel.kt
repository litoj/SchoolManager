package cz.cvut.fit.litosjos.features.word.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.features.word.data.WordRepository
import cz.cvut.fit.litosjos.features.word.domain.Word
import kotlinx.coroutines.launch

class WordDialogViewModel(
	private val update: Boolean, private val repository: WordRepository
) : ViewModel() {
	fun save(item: Word, onSuccess: () -> Unit) {
		if (item.isValid()) viewModelScope.launch {
			if (update) repository.update(item)
			else repository.insert(item)
			onSuccess()
		}
	}
}