package com.miireader.ui.bookshelf

import com.miireader.core.model.FilterMode
import com.miireader.core.model.SortOrder

data class BookItemUi(
    val id: String,
    val title: String,
    val author: String,
    val progressPercentage: Float = 0.0f,
    val lastOpenedDate: Long? = null,
    val importDate: Long = 0L
)

data class BookshelfUiState(
    val books: List<BookItemUi> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val filterMode: FilterMode = FilterMode.ALL,
    val sortOrder: SortOrder = SortOrder.LAST_OPENED,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)
