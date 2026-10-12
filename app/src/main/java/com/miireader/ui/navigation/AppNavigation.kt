package com.miireader.ui.navigation

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.miireader.di.ViewModelFactory
import com.miireader.ui.bookshelf.BookshelfScreen
import com.miireader.ui.bookshelf.BookshelfViewModel
import com.miireader.ui.catalog.CatalogScreen
import com.miireader.ui.settings.SettingsScreen
import com.miireader.ui.settings.SettingsViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModelFactory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Bookshelf.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None }, // Instant transition for e-paper
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Screen.Bookshelf.route) {
            val viewModel: BookshelfViewModel = viewModel(factory = viewModelFactory)
            val uiState by viewModel.uiState.collectAsState()

            BookshelfScreen(
                uiState = uiState,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onToggleSearch = viewModel::onToggleSearch,
                onFilterSelected = viewModel::onFilterSelected,
                onSortOrderSelected = viewModel::onSortOrderSelected,
                onBookClick = { bookId ->
                    Toast.makeText(context, "Opening reader for book will be enabled in Phase 3", Toast.LENGTH_SHORT).show()
                },
                onImportEpubClick = {
                    Toast.makeText(context, "SAF File Import will be connected in Phase 2", Toast.LENGTH_SHORT).show()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCatalog = {
                    navController.navigate(Screen.Catalog.route)
                },
                onClearError = viewModel::clearError
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val uiState by viewModel.uiState.collectAsState()

            SettingsScreen(
                uiState = uiState,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onWarmBackgroundChanged = viewModel::onWarmBackgroundChanged,
                onFontSizeChanged = viewModel::onFontSizeChanged,
                onAnimationsToggled = viewModel::onAnimationsToggled,
                onRefreshIntervalChanged = viewModel::onRefreshIntervalChanged,
                onSortOrderChanged = viewModel::onSortOrderChanged,
                onTriggerFullRefresh = {
                    viewModel.onTriggerFullRefresh()
                    Toast.makeText(context, "Full refresh requested", Toast.LENGTH_SHORT).show()
                }
            )
        }

        composable(Screen.Catalog.route) {
            CatalogScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
