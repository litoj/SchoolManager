package cz.cvut.fit.litosjos.features.picture.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.features.picture.data.PictureRepository
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import kotlinx.coroutines.launch

class PictureDialogViewModel(
	private val update: Boolean, private val repository: PictureRepository
) : ViewModel() {
	fun save(item: Picture, onSuccess: () -> Unit) {
		if (item.isValid()) viewModelScope.launch {
			if (update) repository.update(item)
			else repository.insert(item)
			onSuccess()
		}
	}
}