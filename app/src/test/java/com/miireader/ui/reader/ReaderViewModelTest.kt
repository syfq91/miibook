package com.miireader.ui.reader

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.core.error.DefaultErrorHandler
import com.miireader.core.input.PageTurnKeys
import com.miireader.core.model.ReaderTheme
import com.miireader.data.epub.PublicationOpenException
import com.miireader.data.epub.encodeToJsonString
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.entity.ReadingProgressEntity
import com.miireader.data.repository.ProgressRepository
import com.miireader.test.FakeBookRepository
import com.miireader.test.FakeBookmarkRepository
import com.miireader.test.FakeEpubPublicationRepository
import com.miireader.test.FakeProgressRepository
import com.miireader.test.FakeReaderNavigator
import com.miireader.test.FakeUserPreferencesRepository
import com.miireader.test.TestDispatchers
import com.miireader.test.testLocator
import com.miireader.test.testPublication
import com.miireader.test.testTocLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    private lateinit var bookRepository: FakeBookRepository
    private lateinit var publicationRepository: FakeEpubPublicationRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var bookmarkRepository: FakeBookmarkRepository
    private lateinit var preferencesRepository: FakeUserPreferencesRepository
    private lateinit var errorHandler: DefaultErrorHandler
    private lateinit var pageTurnKeys: PageTurnKeys

    private val book = BookEntity(
        id = "b1",
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        filePath = "/books/hobbit.epub",
        importDate = 1000L,
        lastOpenedDate = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        bookRepository = FakeBookRepository(listOf(book))
        publicationRepository = FakeEpubPublicationRepository(
            publication = {
                testPublication(
                    title = "The Hobbit",
                    tableOfContents = listOf(
                        testTocLink("ch1.xhtml", "Chapter 1"),
                        testTocLink("ch2.xhtml", "Chapter 2")
                    )
                )
            }
        )
        progressRepository = FakeProgressRepository()
        bookmarkRepository = FakeBookmarkRepository()
        preferencesRepository = FakeUserPreferencesRepository()
        errorHandler = DefaultErrorHandler()
        pageTurnKeys = PageTurnKeys()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------- construction

    private fun dispatchers(): CoroutineDispatchers = TestDispatchers(dispatcher)

    private fun createViewModel(
        books: FakeBookRepository = bookRepository,
        publications: FakeEpubPublicationRepository = publicationRepository,
        progress: ProgressRepository = progressRepository
    ) = ReaderViewModel(
        bookId = book.id,
        bookRepository = books,
        publicationRepository = publications,
        progressRepository = progress,
        bookmarkRepository = bookmarkRepository,
        userPreferencesRepository = preferencesRepository,
        dispatchers = dispatchers(),
        errorHandler = errorHandler,
        pageTurnKeys = pageTurnKeys
    )

    /**
     * `uiState` is only kept up to date while it has a subscriber, so every test must
     * subscribe before it can read `uiState.value`.
     *
     * Assert against `viewModel.uiState.value` rather than values taken from the collected
     * stream: `stateIn` conflates, so a collector sees updates late while `.value` is always
     * the latest combined state once the scheduler has been advanced.
     */
    private fun TestScope.activate(viewModel: ReaderViewModel) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        dispatcher.scheduler.advanceUntilIdle()
    }

    // ---------------------------------------------------------------- opening

    @Test
    fun `opening a book exposes its title and table of contents`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("The Hobbit", state.bookTitle)
        assertEquals(listOf("Chapter 1", "Chapter 2"), state.toc.map { it.title })
        assertEquals(listOf(0, 0), state.toc.map { it.depth })
        assertNotNull(state.toc[0].locator)
    }

    @Test
    fun `opening a book records it as recently opened`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val updated = bookRepository.getBookById(book.id)
        assertNotNull(updated?.lastOpenedDate)
    }

    @Test
    fun `a saved position is restored when the book is opened`() = runTest(dispatcher) {
        val savedLocator = testLocator(
            href = "ch2.xhtml",
            title = "Chapter 2",
            progression = 0.25,
            totalProgression = 0.42
        )
        progressRepository = FakeProgressRepository(
            listOf(
                ReadingProgressEntity(
                    bookId = book.id,
                    locatorJson = savedLocator.encodeToJsonString(),
                    progressPercentage = 42f
                )
            )
        )

        val viewModel = createViewModel(progress = progressRepository)
        activate(viewModel)

        val state = viewModel.uiState.value
        assertEquals("Chapter 2", state.chapterTitle)
        assertEquals(42, state.progressPercent)
        assertEquals(
            savedLocator.href,
            viewModel.session.value?.initialLocator?.href
        )
    }

    @Test
    fun `a book that is no longer in the library fails with a user-facing message`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(books = FakeBookRepository())
            activate(viewModel)

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            assertNull(viewModel.session.value)
        }

    @Test
    fun `a file that cannot be opened fails with the thrown message`() = runTest(dispatcher) {
        val message = "The book file could not be found. It may have been moved or deleted."
        val viewModel = createViewModel(
            publications = FakeEpubPublicationRepository(
                error = PublicationOpenException(message)
            )
        )
        activate(viewModel)

        val state = viewModel.uiState.value
        assertEquals(message, state.errorMessage)
        assertFalse(state.isLoading)
        assertNull(viewModel.session.value)
    }

    // ---------------------------------------------------------------- position

    @Test
    fun `paging to a new position persists reading progress`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val navigator = FakeReaderNavigator(testLocator("ch1.xhtml", "Chapter 1"))
        viewModel.onNavigatorReady(navigator)
        advanceUntilIdle()

        navigator.emit(
            testLocator("ch1.xhtml", "Chapter 1", progression = 0.5, totalProgression = 0.5)
        )
        advanceUntilIdle()

        val saved = progressRepository.getProgressForBook(book.id)
        assertNotNull(saved)
        assertEquals(50, saved?.progressPercentage?.toInt())
        assertTrue(requireNotNull(saved).locatorJson.isNotEmpty())
        assertEquals(50, viewModel.uiState.value.progressPercent)
        assertEquals("Chapter 1", viewModel.uiState.value.chapterTitle)
    }

    @Test
    fun `an unchanged position is not persisted repeatedly`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val locator = testLocator("ch1.xhtml", "Chapter 1", totalProgression = 0.5)
        val navigator = FakeReaderNavigator(locator)
        viewModel.onNavigatorReady(navigator)
        // The navigator emits its current position as soon as it is attached.
        advanceUntilIdle()
        assertEquals(1, progressRepository.saveCount)

        // Re-emitting the identical position must not write again.
        navigator.emit(locator)
        advanceUntilIdle()
        assertEquals(1, progressRepository.saveCount)

        navigator.emit(testLocator("ch1.xhtml", "Chapter 1", totalProgression = 0.6))
        advanceUntilIdle()
        assertEquals(2, progressRepository.saveCount)
    }

    // ---------------------------------------------------------------- bookmarks

    @Test
    fun `toggling at the current position adds a bookmark and then removes it`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            activate(viewModel)

            val navigator = FakeReaderNavigator(
                testLocator("ch1.xhtml", "Chapter 1", progression = 0.2)
            )
            viewModel.onNavigatorReady(navigator)
            advanceUntilIdle()

            viewModel.toggleBookmarkForCurrentPosition()
            advanceUntilIdle()

            var bookmarks = bookmarkRepository.getBookmarksForBook(book.id).first()
            assertEquals(1, bookmarks.size)
            assertEquals("Chapter 1", bookmarks.first().chapterTitle)
            assertTrue(viewModel.uiState.value.isCurrentPositionBookmarked)
            assertEquals(1, viewModel.uiState.value.bookmarks.size)

            viewModel.toggleBookmarkForCurrentPosition()
            advanceUntilIdle()

            bookmarks = bookmarkRepository.getBookmarksForBook(book.id).first()
            assertTrue(bookmarks.isEmpty())
            assertFalse(viewModel.uiState.value.isCurrentPositionBookmarked)
        }

    @Test
    fun `a different position gets its own bookmark`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val navigator =
            FakeReaderNavigator(testLocator("ch1.xhtml", "Chapter 1", progression = 0.1))
        viewModel.onNavigatorReady(navigator)
        advanceUntilIdle()

        viewModel.toggleBookmarkForCurrentPosition()
        advanceUntilIdle()

        navigator.emit(testLocator("ch1.xhtml", "Chapter 1", progression = 0.9))
        advanceUntilIdle()
        viewModel.toggleBookmarkForCurrentPosition()
        advanceUntilIdle()

        assertEquals(2, bookmarkRepository.getBookmarksForBook(book.id).first().size)
        assertEquals(2, viewModel.uiState.value.bookmarks.size)
    }

    @Test
    fun `removing a bookmark by id deletes it`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        viewModel.onNavigatorReady(FakeReaderNavigator(testLocator("ch1.xhtml", "Chapter 1")))
        advanceUntilIdle()

        viewModel.toggleBookmarkForCurrentPosition()
        advanceUntilIdle()

        val bookmark = bookmarkRepository.getBookmarksForBook(book.id).first().first()
        viewModel.removeBookmark(bookmark.id)
        advanceUntilIdle()

        assertTrue(bookmarkRepository.getBookmarksForBook(book.id).first().isEmpty())
    }

    // ---------------------------------------------------------------- navigation

    @Test
    fun `selecting a table of contents entry moves the navigator there`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val navigator = FakeReaderNavigator(testLocator("ch1.xhtml", "Chapter 1"))
        viewModel.onNavigatorReady(navigator)
        advanceUntilIdle()

        val target = viewModel.session.value!!.toc.first { it.title == "Chapter 2" }
        viewModel.goToTocItem(target)
        advanceUntilIdle()

        assertEquals(listOf(target.locator), navigator.requestedLocators)
    }

    @Test
    fun `a locator requested before the navigator is ready is applied once it is`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            activate(viewModel)

            val target = viewModel.session.value!!.toc.first { it.title == "Chapter 2" }
            viewModel.goToTocItem(target)

            val navigator = FakeReaderNavigator(testLocator("ch1.xhtml", "Chapter 1"))
            viewModel.onNavigatorReady(navigator)
            advanceUntilIdle()

            assertEquals(listOf(target.locator), navigator.requestedLocators)
        }

    @Test
    fun `page turn keys work only while a navigator is attached`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        val navigator = FakeReaderNavigator(testLocator("ch1.xhtml", "Chapter 1"))
        viewModel.onNavigatorReady(navigator)
        advanceUntilIdle()

        assertTrue(pageTurnKeys.dispatch(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(1, navigator.forwardCount)
        assertTrue(pageTurnKeys.dispatch(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(1, navigator.backwardCount)

        viewModel.onNavigatorDisposed()
        advanceUntilIdle()

        assertFalse(pageTurnKeys.dispatch(KeyEvent.KEYCODE_PAGE_DOWN))
    }

    // ---------------------------------------------------------------- settings

    @Test
    fun `changing font size and theme updates the stored preferences`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        viewModel.onFontSizeChanged(24)
        viewModel.onReaderThemeChanged(ReaderTheme.SEPIA)
        advanceUntilIdle()

        val prefs = preferencesRepository.preferences.first()
        assertEquals(24, prefs.fontSizeSp)
        assertEquals(ReaderTheme.SEPIA, prefs.readerTheme)

        val state = viewModel.uiState.value
        assertEquals(24, state.fontSizeSp)
        assertEquals(ReaderTheme.SEPIA, state.readerTheme)
    }

    @Test
    fun `toggling the toolbar flips its visibility`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        activate(viewModel)

        assertTrue(viewModel.uiState.value.toolbarVisible)
        viewModel.toggleToolbar()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.toolbarVisible)
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    fun `clearing the view model releases the publication`() = runTest(dispatcher) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createViewModel() as T
        }

        val viewModel = ViewModelProvider(store, factory)[ReaderViewModel::class.java]
        activate(viewModel)

        assertNotNull(viewModel.session.value)

        store.clear()
        advanceUntilIdle()

        assertNull(viewModel.session.value)
    }
}
