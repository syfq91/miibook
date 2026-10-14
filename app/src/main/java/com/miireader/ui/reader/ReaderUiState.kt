package com.miireader.ui.reader

import com.miireader.core.model.ReaderTheme
import org.readium.r2.shared.publication.Locator

/**
 * A single row in the table of contents.
 *
 * The target is stored as a [Locator] rather than a raw href so the caller can hand it
 * straight to the navigator without needing the `Publication` to resolve it again.
 */
data class TocItem(
    /** Stable key for lazy list item identity. */
    val key: String,
    val title: String,
    val depth: Int,
    val locator: Locator?
)

/** A saved bookmark, already decoded for display. */
data class BookmarkItem(
    val id: String,
    val title: String,
    val chapterTitle: String?,
    val createdAt: Long,
    val locator: Locator
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    /** User-facing message shown instead of the book when opening failed. */
    val errorMessage: String? = null,
    val bookId: String = "",
    val bookTitle: String = "",
    /** Title of the chapter/section currently on screen. */
    val chapterTitle: String = "",
    /** Whole-number percentage (0-100) through the publication. */
    val progressPercent: Int = 0,
    /** True when a bookmark already exists at the position on screen. */
    val isCurrentPositionBookmarked: Boolean = false,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val toc: List<TocItem> = emptyList(),
    val fontSizeSp: Int = 18,
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    /** While false the top toolbar is hidden; the bottom bar button brings it back. */
    val toolbarVisible: Boolean = true
)
