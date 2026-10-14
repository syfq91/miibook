package com.miireader.ui.reader

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.core.error.AppError
import com.miireader.core.error.ErrorHandler
import com.miireader.core.input.PageTurnKeys
import com.miireader.core.model.ReaderTheme
import com.miireader.data.epub.EpubPublicationRepository
import com.miireader.data.epub.PublicationOpenException
import com.miireader.data.epub.decodeLocator
import com.miireader.data.epub.encodeToJsonString
import com.miireader.data.epub.isSamePositionAs
import com.miireader.data.local.entity.BookmarkEntity
import com.miireader.data.local.entity.ReadingProgressEntity
import com.miireader.data.preferences.UserPreferencesRepository
import com.miireader.data.repository.BookRepository
import com.miireader.data.repository.BookmarkRepository
import com.miireader.data.repository.ProgressRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * A publication opened for reading, together with everything the screen needs that can
 * only be derived from it.
 *
 * The [publication] is owned by the ViewModel that created the session and is closed in
 * `ReaderViewModel.onCleared`. `Publication` is deliberately *not* `AutoCloseable`, so it
 * must never be used with Kotlin's `use { }`.
 */
data class ReaderSession(
    val publication: Publication,
    val toc: List<TocItem>,
    val initialLocator: Locator?
)

/**
 * Drives the reader screen: opens the book, restores the last position, tracks the
 * navigator as the reader pages through it, and persists progress and bookmarks.
 *
 * The screen owns no reading logic; it only forwards gestures here and renders [uiState].
 *
 * @param bookId the book being read, taken from the navigation route.
 */
class ReaderViewModel(
    private val bookId: String,
    private val bookRepository: BookRepository,
    private val publicationRepository: EpubPublicationRepository,
    private val progressRepository: ProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatchers: CoroutineDispatchers,
    private val errorHandler: ErrorHandler,
    private val pageTurnKeys: PageTurnKeys
) : ViewModel() {

    /**
     * Everything derived from the book itself plus the live position. Kept private so it
     * can be folded into [uiState] alongside preferences and bookmarks.
     */
    private data class ContentState(
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
        val bookTitle: String = "",
        val chapterTitle: String = "",
        val progressPercent: Int = 0,
        val currentLocator: Locator? = null,
        val toc: List<TocItem> = emptyList(),
        val toolbarVisible: Boolean = true
    )

    private val _content = MutableStateFlow(ContentState())
    private val _session = MutableStateFlow<ReaderSession?>(null)

    /** The open publication, or null until opening succeeds (and after it is closed). */
    val session: StateFlow<ReaderSession?> = _session.asStateFlow()

    private var publication: Publication? = null
    private var navigator: ReaderNavigator? = null
    private var positionJob: Job? = null
    private var lastPersistedLocatorJson: String? = null
    private var pendingLocator: Locator? = null

    private val bookmarks: Flow<List<BookmarkItem>> =
        bookmarkRepository.getBookmarksForBook(bookId).map { entities ->
            entities.mapNotNull { entity ->
                decodeLocator(entity.locatorJson)?.let { locator ->
                    BookmarkItem(
                        id = entity.id,
                        title = entity.title.orEmpty(),
                        chapterTitle = entity.chapterTitle,
                        createdAt = entity.createdAt,
                        locator = locator
                    )
                }
            }
        }

    val uiState: StateFlow<ReaderUiState> = combine(
        userPreferencesRepository.preferences,
        bookmarks,
        _content
    ) { prefs, bookmarkItems, content ->
        val current = content.currentLocator
        ReaderUiState(
            isLoading = content.isLoading,
            errorMessage = content.errorMessage,
            bookId = bookId,
            bookTitle = content.bookTitle,
            chapterTitle = content.chapterTitle,
            progressPercent = content.progressPercent,
            isCurrentPositionBookmarked = current != null && bookmarkItems.any {
                it.locator.isSamePositionAs(current)
            },
            bookmarks = bookmarkItems,
            toc = content.toc,
            fontSizeSp = prefs.fontSizeSp,
            readerTheme = prefs.readerTheme,
            toolbarVisible = content.toolbarVisible
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderUiState(bookId = bookId)
    )

    init {
        openBook()
    }

    private fun openBook() {
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                    ?: throw PublicationOpenException("This book is no longer in your library.")

                val savedProgress = progressRepository.getProgressForBook(bookId)
                val restoredLocator = decodeLocator(savedProgress?.locatorJson)

                val opened = publicationRepository.openPublication(book.filePath)
                publication = opened

                val toc = withContext(dispatchers.io) { buildTableOfContents(opened) }
                _session.value = ReaderSession(
                    publication = opened,
                    toc = toc,
                    initialLocator = restoredLocator
                )
                _content.update {
                    it.copy(
                        isLoading = false,
                        bookTitle = book.title,
                        chapterTitle = restoredLocator?.title ?: book.title,
                        progressPercent = progressOf(restoredLocator)
                            ?: savedProgress?.progressPercentage?.toInt()?.coerceIn(0, 100)
                            ?: 0,
                        currentLocator = restoredLocator,
                        toc = toc
                    )
                }

                // Reflect the open in the library so the "Recent" sort is meaningful.
                bookRepository.updateBook(book.copy(lastOpenedDate = System.currentTimeMillis()))
            } catch (e: PublicationOpenException) {
                fail(e.message ?: "The book could not be opened.")
            } catch (e: Exception) {
                errorHandler.tryEmitError(
                    AppError.BookParseError("Failed to open book", e)
                )
                fail("The book could not be opened.")
            }
        }
    }

    private fun fail(message: String) {
        _content.update { it.copy(isLoading = false, errorMessage = message) }
    }

    // ---------------------------------------------------------------- navigator

    /**
     * Called by the screen once the navigator fragment has been added and its view
     * exists. From that point the navigator is the source of truth for the position.
     */
    fun onNavigatorReady(navigator: ReaderNavigator) {
        this.navigator = navigator

        pendingLocator?.let {
            navigator.go(it)
            pendingLocator = null
        }

        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            navigator.currentLocator.collect { locator -> onPositionChanged(locator) }
        }

        // Hardware page-turn buttons (e.g. KEYCODE_PAGE_DOWN on e-paper readers).
        pageTurnKeys.register { keyCode ->
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN -> {
                    goForward()
                    true
                }
                KeyEvent.KEYCODE_PAGE_UP -> {
                    goBackward()
                    true
                }
                else -> false
            }
        }
    }

    /** Called when the navigator leaves the screen; stops position tracking. */
    fun onNavigatorDisposed() {
        positionJob?.cancel()
        positionJob = null
        navigator = null
        pageTurnKeys.unregister()
    }

    private fun onPositionChanged(locator: Locator) {
        val percent = progressOf(locator) ?: _content.value.progressPercent
        _content.update {
            it.copy(
                currentLocator = locator,
                chapterTitle = locator.title ?: it.chapterTitle,
                progressPercent = percent
            )
        }
        persistProgress(locator, percent)
    }

    private fun persistProgress(locator: Locator, percent: Int) {
        val json = locator.encodeToJsonString()
        if (json == lastPersistedLocatorJson) return
        lastPersistedLocatorJson = json

        viewModelScope.launch {
            progressRepository.saveProgress(
                ReadingProgressEntity(
                    bookId = bookId,
                    locatorJson = json,
                    progressPercentage = percent.toFloat()
                )
            )
        }
    }

    private fun progressOf(locator: Locator?): Int? =
        locator?.locations?.totalProgression
            ?.let { (it * 100).roundToInt().coerceIn(0, 100) }

    // ---------------------------------------------------------------- navigation

    /** Moves the navigator to [locator]. Queued if the navigator is not attached yet. */
    fun goToLocator(locator: Locator) {
        val current = navigator
        if (current != null) {
            current.go(locator)
        } else {
            pendingLocator = locator
        }
    }

    fun goToBookmark(bookmark: BookmarkItem) = goToLocator(bookmark.locator)

    fun goToTocItem(item: TocItem) {
        item.locator?.let(::goToLocator)
    }

    /** Steps one page forward/backward through the current resource or across chapters. */
    fun goForward() {
        navigator?.goForward()
    }

    fun goBackward() {
        navigator?.goBackward()
    }

    // ---------------------------------------------------------------- bookmarks

    fun toggleBookmarkForCurrentPosition() {
        val locator = _content.value.currentLocator ?: return
        val chapterTitle = _content.value.chapterTitle
        val bookTitle = _content.value.bookTitle

        viewModelScope.launch {
            val existing = bookmarkRepository.getBookmarksForBook(bookId).first()
                .firstOrNull { decodeLocator(it.locatorJson)?.isSamePositionAs(locator) == true }

            if (existing != null) {
                bookmarkRepository.deleteBookmarkById(existing.id)
            } else {
                bookmarkRepository.addBookmark(
                    BookmarkEntity(
                        bookId = bookId,
                        locatorJson = locator.encodeToJsonString(),
                        title = bookTitle,
                        chapterTitle = locator.title ?: chapterTitle
                    )
                )
            }
        }
    }

    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch { bookmarkRepository.deleteBookmarkById(bookmarkId) }
    }

    // ---------------------------------------------------------------- chrome

    fun toggleToolbar() {
        _content.update { it.copy(toolbarVisible = !it.toolbarVisible) }
    }

    fun onFontSizeChanged(sizeSp: Int) {
        viewModelScope.launch { userPreferencesRepository.setFontSizeSp(sizeSp) }
    }

    fun onReaderThemeChanged(theme: ReaderTheme) {
        viewModelScope.launch { userPreferencesRepository.setReaderTheme(theme) }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCleared() {
        positionJob?.cancel()
        positionJob = null
        navigator = null
        pageTurnKeys.unregister()

        // Publication is not AutoCloseable: close explicitly, never with `use { }`.
        publication?.close()
        publication = null
        _session.value = null
        super.onCleared()
    }

    private fun buildTableOfContents(publication: Publication): List<TocItem> {
        val items = mutableListOf<TocItem>()

        fun walk(links: List<Link>, depth: Int) {
            links.forEach { link ->
                val title = link.title?.takeIf { it.isNotBlank() } ?: link.href.toString()
                items += TocItem(
                    key = "${items.size}-$depth-${link.href}",
                    title = title,
                    depth = depth,
                    locator = publication.locatorFromLink(link)
                )
                walk(link.children, depth + 1)
            }
        }

        walk(publication.tableOfContents, depth = 0)
        return items
    }
}
