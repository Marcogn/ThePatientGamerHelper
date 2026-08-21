package com.marcogn.thepatientgamerhelper.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
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

// A NavBackStackEntry only reaches RESUMED once its enter/exit transition animation has
// fully completed and it is settled on top of the back stack (see NavHost/AnimatedContent
// docs). Gating every navigate()/popBackStack() call behind this check on the *specific*
// entry that owns the callback is the officially recommended fix for a fast double-tap
// (e.g. back then immediately tapping another destination) landing on a screen that is
// still being composed/torn down mid-transition instead of the intended one.
private fun NavBackStackEntry.lifecycleIsResumed() =
    lifecycle.currentState == Lifecycle.State.RESUMED

private const val NAV_ANIM_DURATION_MS = 300

private val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        animationSpec = tween(NAV_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION_MS))
}
private val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(
        animationSpec = tween(NAV_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> -fullWidth / 4 },
    ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION_MS))
}
private val navPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        animationSpec = tween(NAV_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> -fullWidth / 4 },
    ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION_MS))
}
private val navPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(
        animationSpec = tween(NAV_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION_MS))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThePatientGamerHelperNavGraph(navController: NavHostController = rememberNavController()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val navigateFromDrawer: (Destination) -> Unit = { destination ->
        // Guards against a drawer tap landing while the current screen is still mid
        // transition (see lifecycleIsResumed() above) - same race as forward/back taps.
        if (navController.currentBackStackEntry?.lifecycleIsResumed() != false) {
            scope.launch { drawerState.close() }
            navController.navigate(destination) {
                popUpTo(Destination.Home) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
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
        NavHost(
            navController = navController,
            startDestination = Destination.Home,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition,
        ) {
            composable<Destination.Home> { entry ->
                HomeScreen(
                    onMenuClick = openDrawer,
                    onReviewsClick = { if (entry.lifecycleIsResumed()) navController.navigate(Destination.Library) },
                    onBacklogClick = { if (entry.lifecycleIsResumed()) navController.navigate(Destination.Backlog) },
                    onStatsClick = { if (entry.lifecycleIsResumed()) navController.navigate(Destination.Stats) },
                )
            }
            composable<Destination.Library> { entry ->
                LibraryScreen(
                    onMenuClick = openDrawer,
                    onReviewClick = { id ->
                        if (entry.lifecycleIsResumed()) navController.navigate(Destination.Detail(id))
                    },
                    onAddClick = { if (entry.lifecycleIsResumed()) navController.navigate(Destination.Form()) },
                )
            }
            composable<Destination.Stats> {
                StatsScreen(onMenuClick = openDrawer)
            }
            composable<Destination.Settings> { entry ->
                SettingsScreen(
                    onBack = {
                        // Not a plain popBackStack(): that destroys the entry outright, clearing
                        // SettingsViewModel's in-memory Drive login state (signedInEmail) on every
                        // single visit, since Settings is reachable only from the drawer and its
                        // back arrow is the only way to leave it. Mirrors navigateFromDrawer's
                        // popUpTo/saveState/restoreState so the entry (and its login state) is
                        // preserved across drawer round-trips, same as Library/Backlog/Stats.
                        if (entry.lifecycleIsResumed()) {
                            navController.navigate(Destination.Home) {
                                popUpTo(Destination.Home) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
            composable<Destination.Detail> { entry ->
                DetailScreen(
                    onBack = { if (entry.lifecycleIsResumed()) navController.popBackStack() },
                    onEdit = { id -> if (entry.lifecycleIsResumed()) navController.navigate(Destination.Form(id)) },
                    onDeleted = {
                        if (entry.lifecycleIsResumed()) {
                            navController.popBackStack(Destination.Library, inclusive = false)
                        }
                    },
                )
            }
            composable<Destination.Form> { backStackEntry ->
                val route = backStackEntry.toRoute<Destination.Form>()
                ReviewFormScreen(
                    onSaved = { id ->
                        if (backStackEntry.lifecycleIsResumed()) {
                            navController.navigate(Destination.Detail(id)) {
                                popUpTo(Destination.Library)
                            }
                        }
                    },
                    onCancel = {
                        if (backStackEntry.lifecycleIsResumed()) {
                            if (route.backlogItemId != null) {
                                // Opened from the backlog's "want to write a review?" prompt: leaving
                                // (with or without an implicit draft save, see ReviewFormViewModel.onBackPressed)
                                // should land on the reviews library, not back into the Backlog stack it came from.
                                // Deliberately NOT saveState = true here: this popUpTo always pops the
                                // Backlog -> BacklogListDetail -> BacklogItemDetail -> Form chain we're
                                // discarding on purpose, and NavController's saveState/restoreState keys
                                // saved back-stack "islands" by the id of the *first* entry above the
                                // popUpTo target (Backlog here) with a write-once guard per key. Saving it
                                // poisoned the drawer's "Backlog" entry: the next drawer tap to Backlog
                                // (restoreState = true in navigateFromDrawer) restored this exact stale
                                // chain wholesale, landing back on this Form/Detail screen instead of the
                                // Backlog list, popping straight back past it on the next back press. See
                                // REG-13 in docs/test-plan.md.
                                navController.navigate(Destination.Library) {
                                    popUpTo(Destination.Home)
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                navController.popBackStack()
                            }
                        }
                    },
                )
            }
            composable<Destination.Backlog> { entry ->
                BacklogScreen(
                    onMenuClick = openDrawer,
                    onListClick = { listId ->
                        if (entry.lifecycleIsResumed()) {
                            navController.navigate(Destination.BacklogListDetail(listId))
                        }
                    },
                    onItemClick = { itemId ->
                        if (entry.lifecycleIsResumed()) {
                            navController.navigate(Destination.BacklogItemDetail(itemId))
                        }
                    },
                )
            }
            composable<Destination.BacklogListDetail> { backStackEntry ->
                val listId = backStackEntry.toRoute<Destination.BacklogListDetail>().listId
                BacklogListDetailScreen(
                    onBack = { if (backStackEntry.lifecycleIsResumed()) navController.popBackStack() },
                    onAddItemClick = {
                        if (backStackEntry.lifecycleIsResumed()) {
                            navController.navigate(Destination.BacklogItemForm(listId))
                        }
                    },
                    onItemClick = { itemId ->
                        if (backStackEntry.lifecycleIsResumed()) {
                            navController.navigate(Destination.BacklogItemDetail(itemId))
                        }
                    },
                )
            }
            composable<Destination.BacklogItemForm> { entry ->
                BacklogItemFormScreen(
                    onSaved = { if (entry.lifecycleIsResumed()) navController.popBackStack() },
                    onCancel = { if (entry.lifecycleIsResumed()) navController.popBackStack() },
                )
            }
            composable<Destination.BacklogItemDetail> { entry ->
                BacklogItemDetailScreen(
                    onBack = { if (entry.lifecycleIsResumed()) navController.popBackStack() },
                    onEdit = { itemId, listId ->
                        if (entry.lifecycleIsResumed()) {
                            navController.navigate(Destination.BacklogItemForm(listId, itemId))
                        }
                    },
                    onDeleted = { if (entry.lifecycleIsResumed()) navController.popBackStack() },
                    onWriteReview = { itemId ->
                        if (entry.lifecycleIsResumed()) {
                            navController.navigate(Destination.Form(backlogItemId = itemId)) { launchSingleTop = true }
                        }
                    },
                    onOpenReview = { reviewId ->
                        if (entry.lifecycleIsResumed()) navController.navigate(Destination.Detail(reviewId))
                    },
                )
            }
        }
    }
}
