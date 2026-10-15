package com.miireader.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperButtonStyle
import com.miireader.ui.components.EpaperCard
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.components.EpaperProgressBar
import com.miireader.ui.components.EpaperTopBar
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.HighContrastErrorText
import com.miireader.ui.theme.NearBlack

/**
 * Download queue screen: downloads in flight, finished ones that can be opened, and
 * failures that can be retried.
 *
 * Everything here is static — the progress bar is a solid fill that is redrawn as bytes
 * arrive, with no animated indicator, so a slow e-paper refresh never lags behind the UI.
 */
@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onRetryDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onOpenDownload: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        EpaperTopBar(
            title = "Downloads",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationIconContentDescription = "Back to Bookshelf",
            onNavigationClick = onNavigateBack
        )

        DownloadsErrorMessage(
            message = uiState.errorMessage,
            onClearError = onClearError
        )

        if (uiState.downloads.isEmpty()) {
            EpaperEmptyState(
                title = "No downloads",
                message = "Books you download from OPDS catalogs will appear here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(EpaperDimensions.PaddingMedium),
                contentPadding = PaddingValues(
                    horizontal = EpaperDimensions.PaddingLarge,
                    vertical = EpaperDimensions.PaddingSmall
                )
            ) {
                if (uiState.activeDownloads.isNotEmpty()) {
                    item(key = "header-active") {
                        SectionHeader(title = "Downloading")
                    }
                    items(
                        items = uiState.activeDownloads,
                        key = { it.id }
                    ) { download ->
                        ActiveDownloadRow(
                            download = download,
                            onCancel = { onCancelDownload(download.id) }
                        )
                    }
                }

                if (uiState.completedDownloads.isNotEmpty()) {
                    item(key = "header-completed") {
                        SectionHeader(title = "Completed")
                    }
                    items(
                        items = uiState.completedDownloads,
                        key = { it.id }
                    ) { download ->
                        CompletedDownloadRow(
                            download = download,
                            onRemove = { onRemoveDownload(download.id) },
                            onOpen = download.bookId?.let { bookId -> { onOpenDownload(bookId) } }
                        )
                    }
                }

                if (uiState.failedDownloads.isNotEmpty()) {
                    item(key = "header-failed") {
                        SectionHeader(title = "Failed")
                    }
                    items(
                        items = uiState.failedDownloads,
                        key = { it.id }
                    ) { download ->
                        FailedDownloadRow(
                            download = download,
                            onRetry = { onRetryDownload(download.id) },
                            onRemove = { onRemoveDownload(download.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = NearBlack,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EpaperDimensions.PaddingSmall)
    )
}

/** A download currently in progress. */
@Composable
private fun ActiveDownloadRow(
    download: DownloadItemUi,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    EpaperCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = NearBlack
                    )
                    download.displaySubtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkGray
                        )
                    }
                }

                EpaperIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Cancel download",
                    onClick = onCancel
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Downloading... ${download.progressPercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NearBlack
                )
                Text(
                    text = download.formattedProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkGray
                )
            }

            EpaperProgressBar(
                progress = download.progress,
                modifier = Modifier.padding(top = EpaperDimensions.PaddingSmall)
            )
        }
    }
}

/** A finished download that can be opened in the reader. */
@Composable
private fun CompletedDownloadRow(
    download: DownloadItemUi,
    onRemove: () -> Unit,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    EpaperCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EpaperDimensions.MinTouchTarget),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = EpaperDimensions.PaddingMedium)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = NearBlack
                )
                download.displaySubtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGray
                    )
                }
            }

            EpaperIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Remove from history",
                onClick = onRemove
            )
        }
    }
}

/** A failed download, with its error and a retry action. */
@Composable
private fun FailedDownloadRow(
    download: DownloadItemUi,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    EpaperCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = NearBlack
                )
                download.displaySubtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGray
                    )
                }

                download.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastErrorText,
                        modifier = Modifier.padding(top = EpaperDimensions.PaddingSmall)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(EpaperDimensions.PaddingSmall)
            ) {
                EpaperButton(
                    text = "Retry",
                    onClick = onRetry,
                    style = EpaperButtonStyle.OUTLINE,
                    modifier = Modifier.width(EpaperDimensions.MinTouchTarget)
                )
                EpaperIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Remove from history",
                    onClick = onRemove
                )
            }
        }
    }
}

/** A one-line, non-animated error notice that clears itself on the next action. */
@Composable
private fun DownloadsErrorMessage(
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
