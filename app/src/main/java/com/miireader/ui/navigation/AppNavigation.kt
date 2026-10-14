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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miireader.di.ViewModelFactory
import com.miireader.ui.bookshelf.BookshelfScreen
import com.miireader.ui.bookshelf.BookshelfViewModel
import com.miireader.ui.catalog.CatalogScreen
import com.miireader.ui.reader.ReaderScreen
import com.miireader.ui.reader.ReaderViewModel
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
                    navController.navigate(Screen.Reader.create(bookId))
                },
                onImportEpubClick = {
                    // Handled by the SAF launcher inside BookshelfScreen.
                },
                onImportUriSelected = viewModel::importBook,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCatalog = {
                    navController.navigate(Screen.Catalog.route)
                },
                onClearError = viewModel::clearError
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument(Screen.Reader.ARG_BOOK_ID) { type = NavType.StringType }
            )
        ) { entry ->
            val bookId = entry.arguments?.getString(Screen.Reader.ARG_BOOK_ID).orEmpty()
            val viewModel: ReaderViewModel = viewModel(
                key = "reader-$bookId",
                factory = viewModelFactory.readerViewModelFactory(bookId)
            )
            val uiState by viewModel.uiState.collectAsState()
            val session by viewModel.session.collectAsState()

            ReaderScreen(
                uiState = uiState,
                session = session,
                onBack = { navController.popBackStack() },
                onNavigatorReady = viewModel::onNavigatorReady,
                onNavigatorDisposed = viewModel::onNavigatorDisposed,
                onToggleToolbar = viewModel::toggleToolbar,
                onPreviousPage = viewModel::goBackward,
                onNextPage = viewModel::goForward,
                onToggleBookmark = viewModel::toggleBookmarkForCurrentPosition,
                onTocItemSelected = viewModel::goToTocItem,
                onBookmarkSelected = viewModel::goToBookmark,
                onRemoveBookmark = viewModel::removeBookmark,
                onFontSizeChanged = viewModel::onFontSizeChanged,
                onReaderThemeChanged = viewModel::onReaderThemeChanged
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
