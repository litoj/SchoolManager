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
import cz.cvut.fit.litosjos.features.settings.presentation.SettingsScreen
import cz.cvut.fit.litosjos.features.subject.presentation.SubjectsScreen


@Composable
fun Navigation() {
	val navController = rememberNavController()
	NavHost(
		navController = navController,
		startDestination = Screens.SubjectsList.route,
		modifier = Modifier.fillMaxSize()
	) {
		composable(route = Screens.SubjectsList.route) {
			SubjectsScreen(navController = navController)
		}

		composable(
			route = Screens.ChapterDetail.route,
			arguments = listOf(navArgument(ID_KEY) { type = NavType.IntType }),
		) {
			ChapterScreen(navController = navController)
		}
	}
}
