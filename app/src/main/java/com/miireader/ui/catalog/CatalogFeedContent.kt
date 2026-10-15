package com.miireader.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.miireader.data.opds.CatalogLink
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperButtonStyle
import com.miireader.ui.components.EpaperCard
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.NearBlack

/**
 * A single OPDS feed: its navigable sub-feeds, then its book entries.
 *
 * Navigation links come first because that is how a reader moves through a catalog; book
 * entries follow. Each entry lists its acquisition formats as separate buttons, so a
 * catalog offering both EPUB and PDF does not force a guess.
 */
@Composable
internal fun CatalogFeedContent(
    uiState: CatalogUiState,
    onOpenFeed: (String) -> Unit,
    onAcquisitionSelected: (String, String?, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isFeedEmpty) {
        EpaperEmptyState(
            title = "Nothing in this feed",
            message = "This catalog section has no books or sub-sections to show.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EpaperDimensions.PaddingMedium),
        contentPadding = PaddingValues(
            horizontal = EpaperDimensions.PaddingLarge,
            vertical = EpaperDimensions.PaddingSmall
        )
    ) {
        items(
            items = uiState.navigation,
            key = { "nav-${it.href}" }
        ) { link ->
            NavigationRow(
                link = link,
                onClick = { onOpenFeed(link.href) }
            )
        }

        items(
            items = uiState.entries,
            key = { "entry-${it.title}-${it.hashCode()}" }
        ) { entry ->
            EntryCard(
                entry = entry,
                onAcquisitionSelected = onAcquisitionSelected
            )
        }
    }
}

/** A sub-feed the reader can open. */
@Composable
private fun NavigationRow(
    link: CatalogLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EpaperCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EpaperDimensions.PaddingLarge)
                .heightIn(min = EpaperDimensions.MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = link.title ?: link.href,
                style = MaterialTheme.typography.titleMedium,
                color = NearBlack,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = DarkGray
            )
        }
    }
}

/** One book entry: title, author, summary, and its available download formats. */
@Composable
private fun EntryCard(
    entry: CatalogEntryUi,
    onAcquisitionSelected: (String, String?, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    EpaperCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EpaperDimensions.PaddingLarge)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = NearBlack
            )

            entry.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkGray
                )
            }

            entry.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(modifier = Modifier.width(EpaperDimensions.PaddingSmall))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkGray
                )
            }

            if (entry.acquisitions.isNotEmpty()) {
                Spacer(modifier = Modifier.width(EpaperDimensions.PaddingMedium))
                entry.acquisitions.forEach { link ->
                    EpaperButton(
                        text = formatLabel(link),
                        onClick = { onAcquisitionSelected(link.href, link.mediaType, entry.title, entry.author) },
                        style = EpaperButtonStyle.OUTLINE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = EpaperDimensions.PaddingSmall)
                    )
                }
            }
        }
    }
}

/** A short, human-readable label for an acquisition format. */
private fun formatLabel(link: CatalogLink): String {
    val title = link.title?.takeIf { it.isNotBlank() }
    return title ?: labelForMediaType(link.mediaType)
}

/** Best-effort friendly name for a MIME type, falling back to the raw type. */
private fun labelForMediaType(mediaType: String?): String = when {
    mediaType == null -> "Download"
    mediaType.contains("epub") -> "EPUB"
    mediaType.contains("pdf") -> "PDF"
    mediaType.contains("cbz") -> "Comic (CBZ)"
    else -> mediaType
}
