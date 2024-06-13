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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cz.cvut.fit.litosjos.MainActivity
import cz.cvut.fit.litosjos.R
import cz.cvut.fit.litosjos.core.data.ItemRepository
import cz.cvut.fit.litosjos.core.domain.Item
import cz.cvut.fit.litosjos.core.domain.ItemType
import cz.cvut.fit.litosjos.core.presentation.Screens
import cz.cvut.fit.litosjos.core.presentation.item.ListItemSharedViewModel
import cz.cvut.fit.litosjos.features.settings.SettingsActivity
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

@Composable
// BackPress: close dialog, first press toast, second press exit
fun HandleBackPress(hasDialog: Boolean, hideDialog: () -> Unit) {
	var showToast by remember { mutableStateOf(false) }
	if (showToast) {
		// a lot simpler than: https://developer.android.com/develop/ui/compose/components/snackbar
		Toast.makeText(
			LocalContext.current, stringResource(R.string.double_back_press), Toast.LENGTH_SHORT
		).show()
		showToast = false
	}

	var lastBackPress by remember { mutableLongStateOf(0L) }
	if (lastBackPress == -1L) (LocalContext.current as MainActivity).finish()
	BackHandler {
		if (hasDialog) hideDialog()
		else if (System.currentTimeMillis() - lastBackPress > 2000) {
			lastBackPress = System.currentTimeMillis()
			showToast = true
		} else lastBackPress = -1L // https://stackoverflow.com/a/67402808
	}
}

@Composable
fun SubjectsScreen(navController: NavController) {
	val repository = getKoin().get<ItemRepository>()
	val scope = rememberCoroutineScope()
	val subjects by repository.getByParent(null)
		.collectAsStateWithLifecycle(initialValue = emptyList())

	val sharedViewModel: ListItemSharedViewModel = koinViewModel()
	val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()

	val dialogUpdater =
		{ it: (@Composable () -> Unit)? -> sharedViewModel.update(sharedState.copy(dialog = it)) }
	val onOpen = { item: Item -> navController.navigate(Screens.ChapterDetail.of(item.id)) }
	val onDelete: (Item) -> Unit = { item -> scope.launch { repository.delete(item.id) } }

	HandleBackPress(hasDialog = sharedState.dialog != null, hideDialog = { dialogUpdater(null) })

	val context = LocalContext.current

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
			IconButton(onClick = {
				dialogUpdater { SubjectDialog(item = Item(type = ItemType.SUBJECT)) { dialogUpdater(null) } }
			}) {
				Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_item))
			}
		})
	}) {
		sharedState.dialog?.invoke()

		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(it)
		) {
			items(count = subjects.size, key = { idx -> subjects[idx].id }) { idx ->
				SubjectListItem(
					item = subjects[idx],
					settings = sharedState.settings,
					dialogUpdater = dialogUpdater,
					onOpen = onOpen,
					onDelete = onDelete,
				)
			}
		}
	}
}