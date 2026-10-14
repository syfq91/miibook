package com.miireader.ui.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.miireader.core.model.ReaderTheme
import com.miireader.ui.components.EpaperDivider
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.components.EpaperLoadingIndicator
import com.miireader.ui.components.EpaperTopBar
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.NearBlack

/**
 * Unwraps [ContextWrapper] layers to find the hosting activity, because
 * `LocalContext.current` is not guaranteed to be the activity itself.
 */
tailrec fun Context.findFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> error("No FragmentActivity found in the context chain")
}

/**
 * The reader screen: the navigator page, a hideable top toolbar, and an always-present
 * bottom bar with the page-turn controls.
 *
 * The toolbar's slot is always composed — it simply holds nothing when hidden — so
 * toggling it never disposes and re-creates the navigator, which would lose the page
 * currently on screen.
 */
@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    session: ReaderSession?,
    onBack: () -> Unit,
    onNavigatorReady: (ReaderNavigator) -> Unit,
    onNavigatorDisposed: () -> Unit,
    onToggleToolbar: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTocItemSelected: (TocItem) -> Unit,
    onBookmarkSelected: (BookmarkItem) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onReaderThemeChanged: (ReaderTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    var panel by remember { mutableStateOf<ReaderPanel?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Always composed so toggling the toolbar does not shift later siblings.
            Box(modifier = Modifier.fillMaxWidth()) {
                if (uiState.toolbarVisible) {
                    EpaperTopBar(
                        title = uiState.chapterTitle.ifBlank { uiState.bookTitle },
                        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                        navigationIconContentDescription = "Back to library",
                        onNavigationClick = onBack,
                        actions = {
                            EpaperIconButton(
                                icon = if (uiState.isCurrentPositionBookmarked) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                contentDescription = if (uiState.isCurrentPositionBookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Add bookmark"
                                },
                                onClick = onToggleBookmark
                            )
                            EpaperIconButton(
                                icon = Icons.AutoMirrored.Filled.ListIcon,
                                contentDescription = "Contents",
                                onClick = { panel = ReaderPanel.CONTENTS }
                            )
                            EpaperIconButton(
                                icon = Icons.Outlined.FormatSize,
                                contentDescription = "Reading appearance",
                                onClick = { panel = ReaderPanel.APPEARANCE }
                            )
                        }
                    )
                }
            }

            EpaperDivider()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.errorMessage != null -> EpaperEmptyState(
                        title = "Can't open this book",
                        message = uiState.errorMessage,
                        actionButtonText = "Back to library",
                        onActionClick = onBack,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    uiState.isLoading || session == null -> EpaperLoadingIndicator(
                        text = "Opening book...",
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> EpubNavigatorHost(
                        session = session,
                        fontSizeSp = uiState.fontSizeSp,
                        readerTheme = uiState.readerTheme,
                        onNavigatorReady = onNavigatorReady,
                        onNavigatorDisposed = onNavigatorDisposed,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            EpaperDivider()

            ReaderBottomBar(
                progressPercent = uiState.progressPercent,
                chapterTitle = uiState.chapterTitle,
                toolbarVisible = uiState.toolbarVisible,
                controlsEnabled = !uiState.isLoading && uiState.errorMessage == null,
                onToggleToolbar = onToggleToolbar,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage
            )
        }

        when (panel) {
            ReaderPanel.CONTENTS -> ContentsPanel(
                toc = uiState.toc,
                onItemSelected = {
                    panel = null
                    onTocItemSelected(it)
                },
                onShowBookmarks = { panel = ReaderPanel.BOOKMARKS },
                onClose = { panel = null }
            )

            ReaderPanel.BOOKMARKS -> BookmarksPanel(
                bookmarks = uiState.bookmarks,
                onBookmarkSelected = {
                    panel = null
                    onBookmarkSelected(it)
                },
                onRemoveBookmark = onRemoveBookmark,
                onClose = { panel = null }
            )

            ReaderPanel.APPEARANCE -> AppearancePanel(
                fontSizeSp = uiState.fontSizeSp,
                onFontSizeChanged = onFontSizeChanged,
                readerTheme = uiState.readerTheme,
                onReaderThemeChanged = onReaderThemeChanged,
                onClose = { panel = null }
            )

            null -> Unit
        }
    }
}

@Composable
private fun ReaderBottomBar(
    progressPercent: Int,
    chapterTitle: String,
    toolbarVisible: Boolean,
    controlsEnabled: Boolean,
    onToggleToolbar: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EpaperIconButton(
            icon = if (toolbarVisible) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (toolbarVisible) "Hide toolbar" else "Show toolbar",
            onClick = onToggleToolbar
        )

        EpaperIconButton(
            icon = Icons.Filled.ChevronLeft,
            contentDescription = "Previous page",
            enabled = controlsEnabled,
            onClick = onPreviousPage
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.titleMedium,
                color = NearBlack
            )
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.labelMedium,
                color = DarkGray,
                maxLines = 1
            )
        }

        EpaperIconButton(
            icon = Icons.Filled.ChevronRight,
            contentDescription = "Next page",
            enabled = controlsEnabled,
            onClick = onNextPage
        )
    }
}
