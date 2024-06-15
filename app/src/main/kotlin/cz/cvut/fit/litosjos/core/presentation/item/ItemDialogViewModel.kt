package cz.cvut.fit.litosjos.core.presentation.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.domain.Item
import kotlinx.coroutines.launch

class ItemDialogViewModel(
	private val update: Boolean,
	private val repository: ItemRepository
) : ViewModel() {

	fun save(item: Item, onSuccess: () -> Unit) {
		if (item.isValid()) viewModelScope.launch {
			if (update) repository.update(item)
			else repository.insert(item)
			onSuccess()
		}
	}
}