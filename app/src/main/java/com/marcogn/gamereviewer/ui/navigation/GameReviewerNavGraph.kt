package com.marcogn.gamereviewer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.marcogn.gamereviewer.ui.detail.DetailScreen
import com.marcogn.gamereviewer.ui.form.ReviewFormScreen
import com.marcogn.gamereviewer.ui.library.LibraryScreen

@Composable
fun GameReviewerNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Library) {
        composable<Destination.Library> {
            LibraryScreen(
                onReviewClick = { id -> navController.navigate(Destination.Detail(id)) },
                onAddClick = { navController.navigate(Destination.Form()) },
            )
        }
        composable<Destination.Detail> {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Destination.Form(id)) },
                onDeleted = {
                    navController.popBackStack(Destination.Library, inclusive = false)
                },
            )
        }
        composable<Destination.Form> {
            ReviewFormScreen(
                onSaved = { id ->
                    navController.navigate(Destination.Detail(id)) {
                        popUpTo(Destination.Library)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
