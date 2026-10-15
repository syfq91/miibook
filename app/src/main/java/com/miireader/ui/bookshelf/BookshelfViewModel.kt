package com.miireader.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miireader.core.model.FilterMode
import com.miireader.core.model.SortOrder
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.entity.ReadingProgressEntity
import com.miireader.data.preferences.UserPreferencesRepository
import com.miireader.data.repository.BookImporter
import com.miireader.data.repository.BookRepository
import com.miireader.data.repository.ImportResult
import com.miireader.data.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel(
    private val bookRepository: BookRepository,
    private val bookImporter: BookImporter,
    private val progressRepository: ProgressRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isImporting = MutableStateFlow(false)

    private val booksWithProgressFlow = combine(
        bookRepository.getAllBooks(),
        progressRepository.getAllProgress()
    ) { books: List<BookEntity>, progressList: List<ReadingProgressEntity> ->
        val progressMap = progressList.associateBy { it.bookId }
        books.map { book ->
            BookItemUi(
                id = book.id,
                title = book.title,
                author = book.author,
                progressPercentage = progressMap[book.id]?.progressPercentage ?: 0f,
                lastOpenedDate = book.lastOpenedDate,
                importDate = book.importDate
            )
        }
    }

    private data class SearchState(
        val query: String,
        val isSearchActive: Boolean,
        val errorMessage: String?,
        val isImporting: Boolean
    )

    private val searchStateFlow = combine(
        _searchQuery,
        _isSearchActive,
        _errorMessage,
        _isImporting
    ) { query, isSearchActive, errorMessage, isImporting ->
        SearchState(query, isSearchActive, errorMessage, isImporting)
    }

    val uiState: StateFlow<BookshelfUiState> = combine(
        booksWithProgressFlow,
        userPreferencesRepository.preferences,
        searchStateFlow
    ) { allBooks, prefs, searchState ->
        val filteredBooks = allBooks.filter { book ->
            val matchesQuery = searchState.query.isBlank() ||
                    book.title.contains(searchState.query, ignoreCase = true) ||
                    book.author.contains(searchState.query, ignoreCase = true)

            val matchesFilter = when (prefs.filterMode) {
                FilterMode.ALL -> true
                FilterMode.RECENT -> (book.lastOpenedDate != null && book.lastOpenedDate > 0)
                FilterMode.UNREAD -> book.progressPercentage < 0.05f
            }

            matchesQuery && matchesFilter
        }

        val sortedBooks = when (prefs.sortOrder) {
            SortOrder.LAST_OPENED -> filteredBooks.sortedWith(
                compareByDescending<BookItemUi> { it.lastOpenedDate ?: 0L }
                    .thenByDescending { it.importDate }
            )
            SortOrder.TITLE -> filteredBooks.sortedBy { it.title.lowercase() }
            SortOrder.AUTHOR -> filteredBooks.sortedBy { it.author.lowercase() }
        }

        BookshelfUiState(
            books = sortedBooks,
            searchQuery = searchState.query,
            isSearchActive = searchState.isSearchActive,
            filterMode = prefs.filterMode,
            sortOrder = prefs.sortOrder,
            isLoading = false,
            isImporting = searchState.isImporting,
            errorMessage = searchState.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookshelfUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onToggleSearch(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun onFilterSelected(filter: FilterMode) {
        viewModelScope.launch {
            userPreferencesRepository.setFilterMode(filter)
        }
    }

    fun onSortOrderSelected(order: SortOrder) {
        viewModelScope.launch {
            userPreferencesRepository.setSortOrder(order)
        }
    }

    fun onDeleteBook(bookId: String) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBookById(bookId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete book: ${e.message}"
            }
        }
    }

    /**
     * Imports an EPUB picked via the Storage Access Framework. Shows a static
     * "importing" indicator while the copy/parse runs, surfaces a user-facing
     * message on failure or duplicate, and lets the library flow update on success.
     */
    fun importBook(uri: Uri) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            try {
                when (val result = bookImporter.importBook(uri)) {
                    is ImportResult.Success -> {
                        // Library flow refreshes automatically via Room.
                    }
                    is ImportResult.Duplicate -> {
                        _errorMessage.value =
                            "\"${result.existingBook.title}\" is already in your library."
                    }
                    is ImportResult.Failure -> {
                        _errorMessage.value = result.reason
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Import failed: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
