package com.marcogn.gamereviewer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.marcogn.gamereviewer.ui.backlog.BacklogItemDetailScreen
import com.marcogn.gamereviewer.ui.backlog.BacklogItemFormScreen
import com.marcogn.gamereviewer.ui.backlog.BacklogListDetailScreen
import com.marcogn.gamereviewer.ui.backlog.BacklogScreen
import com.marcogn.gamereviewer.ui.detail.DetailScreen
import com.marcogn.gamereviewer.ui.form.ReviewFormScreen
import com.marcogn.gamereviewer.ui.library.LibraryScreen
import com.marcogn.gamereviewer.ui.settings.SettingsScreen
import com.marcogn.gamereviewer.ui.stats.StatsScreen

@Composable
fun GameReviewerNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Library) {
        composable<Destination.Library> {
            LibraryScreen(
                onReviewClick = { id -> navController.navigate(Destination.Detail(id)) },
                onAddClick = { navController.navigate(Destination.Form()) },
                onStatsClick = { navController.navigate(Destination.Stats) },
                onSettingsClick = { navController.navigate(Destination.Settings) },
                onBacklogClick = { navController.navigate(Destination.Backlog) },
            )
        }
        composable<Destination.Stats> {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable<Destination.Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
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
        composable<Destination.Backlog> {
            BacklogScreen(
                onBack = { navController.popBackStack() },
                onListClick = { listId -> navController.navigate(Destination.BacklogListDetail(listId)) },
                onItemClick = { itemId -> navController.navigate(Destination.BacklogItemDetail(itemId)) },
            )
        }
        composable<Destination.BacklogListDetail> { backStackEntry ->
            val listId = backStackEntry.toRoute<Destination.BacklogListDetail>().listId
            BacklogListDetailScreen(
                onBack = { navController.popBackStack() },
                onAddItemClick = { navController.navigate(Destination.BacklogItemForm(listId)) },
                onItemClick = { itemId -> navController.navigate(Destination.BacklogItemDetail(itemId)) },
            )
        }
        composable<Destination.BacklogItemForm> {
            BacklogItemFormScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable<Destination.BacklogItemDetail> {
            BacklogItemDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { itemId, listId -> navController.navigate(Destination.BacklogItemForm(listId, itemId)) },
                onDeleted = { navController.popBackStack() },
                onWriteReview = { itemId -> navController.navigate(Destination.Form(backlogItemId = itemId)) },
            )
        }
    }
}
