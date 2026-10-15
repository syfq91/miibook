package com.miireader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miireader.core.model.ReaderTheme
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperButtonStyle
import com.miireader.ui.components.EpaperDivider
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.components.EpaperTopBar
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.NearBlack
import java.text.DateFormat
import java.util.Date

/** Which full-screen reader panel, if any, is covering the page. */
enum class ReaderPanel { CONTENTS, BOOKMARKS, APPEARANCE }

/** Preference bounds, mirroring `UserPreferencesRepository.setFontSizeSp`. */
private const val MIN_FONT_SIZE_SP = 12
private const val MAX_FONT_SIZE_SP = 36

/** Rows are inset by a base padding plus one step per nesting level. */
private fun depthPadding(depth: Int) = (16 + depth * 16).dp

@Composable
fun ContentsPanel(
    toc: List<TocItem>,
    onItemSelected: (TocItem) -> Unit,
    onShowBookmarks: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        EpaperTopBar(
            title = "Contents",
            navigationIcon = Icons.Filled.Close,
            navigationIconContentDescription = "Close contents",
            onNavigationClick = onClose,
            actions = {
                EpaperIconButton(
                    icon = Icons.Filled.Bookmark,
                    contentDescription = "Bookmarks",
                    onClick = onShowBookmarks
                )
            }
        )

        if (toc.isEmpty()) {
            EpaperEmptyState(
                title = "No contents",
                message = "This book does not contain a table of contents.",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items = toc, key = { it.key }) { item ->
                    ReaderRow(
                        modifier = Modifier.padding(start = depthPadding(item.depth)),
                        title = item.title,
                        onClick = { onItemSelected(item) }
                    )
                    EpaperDivider()
                }
            }
        }
    }
}

@Composable
fun BookmarksPanel(
    bookmarks: List<BookmarkItem>,
    onBookmarkSelected: (BookmarkItem) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        EpaperTopBar(
            title = "Bookmarks",
            navigationIcon = Icons.Filled.Close,
            navigationIconContentDescription = "Close bookmarks",
            onNavigationClick = onClose
        )

        if (bookmarks.isEmpty()) {
            EpaperEmptyState(
                title = "No bookmarks",
                message = "Use the bookmark button while reading to save your place here.",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items = bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderRow(
                            title = bookmark.chapterTitle?.takeIf { it.isNotBlank() }
                                ?: bookmark.title,
                            subtitle = DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT
                            ).format(Date(bookmark.createdAt)),
                            onClick = { onBookmarkSelected(bookmark) },
                            modifier = Modifier.weight(1f)
                        )
                        EpaperIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Remove bookmark",
                            onClick = { onRemoveBookmark(bookmark.id) }
                        )
                    }
                    EpaperDivider()
                }
            }
        }
    }
}

@Composable
fun AppearancePanel(
    fontSizeSp: Int,
    onFontSizeChanged: (Int) -> Unit,
    readerTheme: ReaderTheme,
    onReaderThemeChanged: (ReaderTheme) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        EpaperTopBar(
            title = "Reading appearance",
            navigationIcon = Icons.Filled.Close,
            navigationIconContentDescription = "Close reading appearance",
            onNavigationClick = onClose
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(EpaperDimensions.PaddingXLarge)
        ) {
            SectionLabel("Text size")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                EpaperIconButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Decrease text size",
                    enabled = fontSizeSp > MIN_FONT_SIZE_SP,
                    onClick = { onFontSizeChanged(fontSizeSp - 1) }
                )
                Text(
                    text = "$fontSizeSp sp",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NearBlack,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                EpaperIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Increase text size",
                    enabled = fontSizeSp < MAX_FONT_SIZE_SP,
                    onClick = { onFontSizeChanged(fontSizeSp + 1) }
                )
            }

            Spacer(modifier = Modifier.height(EpaperDimensions.PaddingLarge))
            EpaperDivider()
            Spacer(modifier = Modifier.height(EpaperDimensions.PaddingLarge))

            SectionLabel("Page theme")

            Row {
                ReaderTheme.entries.forEach { theme ->
                    Spacer(modifier = Modifier.width(EpaperDimensions.PaddingSmall))
                    EpaperButton(
                        text = theme.label,
                        style = if (theme == readerTheme) {
                            EpaperButtonStyle.PRIMARY
                        } else {
                            EpaperButtonStyle.OUTLINE
                        },
                        onClick = { onReaderThemeChanged(theme) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = NearBlack
    )
    Spacer(modifier = Modifier.height(EpaperDimensions.PaddingSmall))
}

/**
 * A single tappable list row.
 *
 * No ripple indication: e-paper cannot animate it, and a lingering pressed state would
 * ghost on the panel until the next full refresh.
 */
@Composable
private fun ReaderRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .heightIn(min = EpaperDimensions.MinTouchTarget)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NearBlack,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkGray
                )
            }
        }
    }
}
