package com.miireader.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.components.EpaperLoadingIndicator
import com.miireader.ui.components.EpaperTopBar
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.HighContrastErrorText

/**
 * OPDS catalog browser.
 *
 * Shows the saved-catalog list when no feed is open, and a feed's sub-feeds and book
 * entries when one is. The screen keeps a single-item back stack: browsing into a nested
 * feed and back out again is handled here rather than through navigation, so returning to
 * the list never requires a second back press to escape the screen.
 */
@Composable
fun CatalogScreen(
    uiState: CatalogUiState,
    onAddCatalog: (String) -> Unit,
    onRemoveCatalog: (String) -> Unit,
    onOpenFeed: (String) -> Unit,
    onCloseFeed: () -> Unit,
    onRefreshFeed: () -> Unit,
    onClearError: () -> Unit,
    onAcquisitionSelected: (String, String?, String?, String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        CatalogTopBar(
            uiState = uiState,
            onCloseFeed = onCloseFeed,
            onRefreshFeed = onRefreshFeed,
            onNavigateBack = onNavigateBack
        )

        CatalogErrorMessage(
            message = uiState.errorMessage,
            onClearError = onClearError
        )

        when {
            uiState.isAddingCatalog || uiState.isLoadingFeed -> {
                EpaperLoadingIndicator(
                    text = if (uiState.isAddingCatalog) "Checking catalog..." else "Loading feed...",
                    modifier = Modifier.padding(EpaperDimensions.PaddingLarge)
                )
            }

            uiState.isBrowsingFeed -> CatalogFeedContent(
                uiState = uiState,
                onOpenFeed = onOpenFeed,
                onAcquisitionSelected = onAcquisitionSelected
            )

            else -> CatalogListContent(
                uiState = uiState,
                onAddCatalog = onAddCatalog,
                onRemoveCatalog = onRemoveCatalog,
                onOpenFeed = onOpenFeed
            )
        }
    }
}

/**
 * Back behaves contextually: it pops out of the feed being browsed first, and only leaves
 * the screen once the saved-catalog list is showing. This keeps the on-screen back arrow
 * consistent with the hardware back button.
 */
@Composable
private fun CatalogTopBar(
    uiState: CatalogUiState,
    onCloseFeed: () -> Unit,
    onRefreshFeed: () -> Unit,
    onNavigateBack: () -> Unit
) {
    EpaperTopBar(
        title = if (uiState.isBrowsingFeed) uiState.feedTitle ?: "Catalog" else "OPDS Catalogs",
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        navigationIconContentDescription = if (uiState.isBrowsingFeed) {
            "Back to catalog list"
        } else {
            "Back to Bookshelf"
        },
        onNavigationClick = {
            if (uiState.isBrowsingFeed) onCloseFeed() else onNavigateBack()
        },
        actions = {
            if (uiState.isBrowsingFeed) {
                EpaperIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Reload this feed",
                    onClick = onRefreshFeed
                )
            }
        }
    )
}

/** A one-line, non-animated error notice that clears itself on the next action. */
@Composable
private fun CatalogErrorMessage(
    message: String?,
    onClearError: () -> Unit
) {
    if (message == null) return

    Text(
        text = message,
        color = HighContrastErrorText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple animation on e-paper
                onClick = onClearError
            )
            .padding(EpaperDimensions.PaddingLarge)
    )
}
