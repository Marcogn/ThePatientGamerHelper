package com.marcogn.thepatientgamerhelper.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.ui.backlog.BacklogItemDetailScreen
import com.marcogn.thepatientgamerhelper.ui.backlog.BacklogItemFormScreen
import com.marcogn.thepatientgamerhelper.ui.backlog.BacklogListDetailScreen
import com.marcogn.thepatientgamerhelper.ui.backlog.BacklogScreen
import com.marcogn.thepatientgamerhelper.ui.detail.DetailScreen
import com.marcogn.thepatientgamerhelper.ui.form.ReviewFormScreen
import com.marcogn.thepatientgamerhelper.ui.home.HomeScreen
import com.marcogn.thepatientgamerhelper.ui.library.LibraryScreen
import com.marcogn.thepatientgamerhelper.ui.settings.SettingsScreen
import com.marcogn.thepatientgamerhelper.ui.stats.StatsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThePatientGamerHelperNavGraph(navController: NavHostController = rememberNavController()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val navigateFromDrawer: (Destination) -> Unit = { destination ->
        scope.launch { drawerState.close() }
        navController.navigate(destination) {
            popUpTo(Destination.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_drawer_reviews)) },
                    icon = { Icon(Icons.Filled.RateReview, contentDescription = null) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Library) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_drawer_backlog)) },
                    icon = { Icon(Icons.Filled.ViewList, contentDescription = null) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Backlog) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_drawer_stats)) },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Stats) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_title)) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Settings) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Destination.Home) {
            composable<Destination.Home> {
                HomeScreen(
                    onMenuClick = openDrawer,
                    onReviewsClick = { navController.navigate(Destination.Library) },
                    onBacklogClick = { navController.navigate(Destination.Backlog) },
                    onStatsClick = { navController.navigate(Destination.Stats) },
                )
            }
            composable<Destination.Library> {
                LibraryScreen(
                    onMenuClick = openDrawer,
                    onReviewClick = { id -> navController.navigate(Destination.Detail(id)) },
                    onAddClick = { navController.navigate(Destination.Form()) },
                )
            }
            composable<Destination.Stats> {
                StatsScreen(onMenuClick = openDrawer)
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
            composable<Destination.Form> { backStackEntry ->
                val route = backStackEntry.toRoute<Destination.Form>()
                ReviewFormScreen(
                    onSaved = { id ->
                        navController.navigate(Destination.Detail(id)) {
                            popUpTo(Destination.Library)
                        }
                    },
                    onCancel = {
                        if (route.backlogItemId != null) {
                            // Opened from the backlog's "vuoi scrivere una recensione?" prompt: leaving
                            // (with or without an implicit draft save, see ReviewFormViewModel.onBackPressed)
                            // should land on Recensioni, not back into the Backlog stack it came from.
                            navController.navigate(Destination.Library) {
                                popUpTo(Destination.Home) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable<Destination.Backlog> {
                BacklogScreen(
                    onMenuClick = openDrawer,
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
                    onWriteReview = { itemId ->
                        navController.navigate(Destination.Form(backlogItemId = itemId)) { launchSingleTop = true }
                    },
                    onOpenReview = { reviewId -> navController.navigate(Destination.Detail(reviewId)) },
                )
            }
        }
    }
}
