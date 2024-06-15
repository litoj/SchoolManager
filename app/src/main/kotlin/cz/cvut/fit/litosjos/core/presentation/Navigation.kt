package cz.cvut.fit.litosjos.core.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.cvut.fit.litosjos.core.presentation.Screens.Companion.ID_KEY
import cz.cvut.fit.litosjos.features.chapter.presentation.ChapterScreen
import cz.cvut.fit.litosjos.features.subject.presentation.SubjectsScreen

@Composable
fun Navigation() {
	val navController = rememberNavController()
	val onNavigate: (String) -> Unit = {// FIXME: why doesn't this fix fastclicking/doubleopen
		if (!navController.popBackStack(it, inclusive = false)) navController.navigate(it)
	}
	NavHost(
		navController = navController,
		startDestination = Screens.SubjectsList.route,
		modifier = Modifier.fillMaxSize()
	) {
		composable(route = Screens.SubjectsList.route) {
			SubjectsScreen(onNavigate)
		}

		composable(
			route = Screens.ChapterDetail.route,
			arguments = listOf(navArgument(ID_KEY) { type = NavType.IntType }),
		) {
			ChapterScreen(onNavigate)
		}
	}
}
