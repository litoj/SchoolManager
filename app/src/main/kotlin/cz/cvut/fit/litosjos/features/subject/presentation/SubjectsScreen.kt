package cz.cvut.fit.litosjos.features.subject.presentation

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.cvut.fit.litosjos.MainActivity
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.features.settings.SettingsActivity
import cz.cvut.fit.litosjos.features.subject.domain.Subject
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
// BackPress: close dialog, first press toast, second press exit
fun HandleBackPress() {
	var state by remember { mutableStateOf("idle") }
	if (state == "toast") {
		// a lot simpler than: https://developer.android.com/develop/ui/compose/components/snackbar
		Toast.makeText(
			LocalContext.current, stringResource(R.string.double_back_press), Toast.LENGTH_SHORT
		).show()
		state = "first"
	}

	LaunchedEffect(key1 = state) {
		if (state == "first") {
			delay(2000)
			state = "idle"
		}
	}
	if (state == "end") (LocalContext.current as MainActivity).finish()
	BackHandler {
		if (state == "idle") state = "toast"
		else if (state == "first") state = "end"
	}
}

@Composable
fun SubjectsScreen(navigate: (route: String) -> Unit) {

	val viewModel: SubjectsScreenViewModel = koinViewModel()
	val state by viewModel.state.collectAsStateWithLifecycle()
	val context = LocalContext.current

	var creating by rememberSaveable { mutableStateOf(false) }
	if (creating) SubjectDialog(item = Subject()) { creating = false }

	HandleBackPress()

	Scaffold(topBar = {
		TopAppBar(title = {
			Text(
				text = stringResource(R.string.subjects), style = MaterialTheme.typography.headlineMedium
			)
		}, actions = {
			IconButton(onClick = {
				ContextCompat.startActivity(context, Intent(context, SettingsActivity::class.java), null)
			}) {
				Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
			}
			IconButton(onClick = { creating = true }) {
				Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_item))
			}
		})
	}) {
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(it)
		) {
			items(count = state.items.size, key = { idx -> state.items[idx].id }) { idx ->
				SubjectListItem(
					item = state.items[idx],
					settings = state.settings,
					onOpen = { navigate(Screens.ChapterDetail.of(state.items[idx].id)) },
					onDelete = { viewModel.deleteChild(state.items[idx]) },
				)
			}
		}
	}
}