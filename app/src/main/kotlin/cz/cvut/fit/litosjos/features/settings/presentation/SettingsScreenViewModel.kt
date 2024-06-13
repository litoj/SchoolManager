package cz.cvut.fit.litosjos.features.settings.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.ItemType
import cz.cvut.fit.litosjos.features.picture.domain.Picture
import cz.cvut.fit.litosjos.features.settings.data.SettingsRepository
import cz.cvut.fit.litosjos.features.settings.data.data_preview_api.PreviewRemoteDataSource
import cz.cvut.fit.litosjos.features.settings.domain.PreviewData
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.word.domain.Word
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val DEBOUNCE = 500L

class SettingsScreenViewModel(
	private val previewSource: PreviewRemoteDataSource,
	private val repository: SettingsRepository,
) : ViewModel() {

	private val _state: MutableStateFlow<SettingsScreenState> = MutableStateFlow(
		SettingsScreenState(
			previewQuery = "JosefLitos",
			previewData = PreviewData(
				parent = Item(),
				chapter = Item(type = ItemType.CHAPTER),
				picture = Picture(Item(type = ItemType.PICTURE), ""),
				word = Word(Item(type = ItemType.WORD), ""),
			),
			settings = Settings(),
		)
	)

	val state = _state.asStateFlow()

	init {
		viewModelScope.launch {
			repository.getSettings().collectLatest(::update)
		}
		query(state.value.previewQuery)
	}

	private var lastQuery = 0L

	fun query(username: String) {
		viewModelScope.launch {
			_state.value = state.value.copy(previewQuery = username)
			lastQuery = System.currentTimeMillis()
			delay(DEBOUNCE)
			if (System.currentTimeMillis() >= lastQuery + DEBOUNCE) adaptDescription(
				state.value.copy(
					previewData = previewSource.getUser(username)
				)
			)
		}
	}

	// generate maximum viewable description line length
	private fun adaptDescription(state: SettingsScreenState) {
		_state.value = state.copy(
			previewData = state.previewData.copy(
				picture = state.previewData.picture.copy(
					base = state.previewData.picture.base.copy(
						description = if (state.settings.descriptionLineCount == 0 || state.settings.descriptionLineCount > 20) ""
						else {
							val list = mutableListOf("1.")
							for (i in 2..state.settings.descriptionLineCount) list += "$i."
							list.joinToString("\n")
						}
					)
				),
			)
		)
	}

	fun export(uri: Uri) {
		repository.exportSettings(state.value.settings, uri)
	}

	fun import(uri: Uri) {
		_state.value = state.value.copy(settings = repository.importSettings(uri, state.value.settings))
	}

	fun update(settings: Settings) = adaptDescription(state.value.copy(settings = settings))

	fun save() = viewModelScope.launch { repository.updateSettings(state.value.settings) }
}

data class SettingsScreenState(
	val previewQuery: String,
	val previewData: PreviewData,
	val settings: Settings,
)