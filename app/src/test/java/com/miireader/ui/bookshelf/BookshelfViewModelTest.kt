package com.miireader.ui.bookshelf

import com.miireader.core.error.DefaultErrorHandler
import com.miireader.core.model.FilterMode
import com.miireader.core.model.SortOrder
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.entity.ReadingProgressEntity
import com.miireader.test.FakeBookRepository
import com.miireader.test.FakeProgressRepository
import com.miireader.test.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var bookRepository: FakeBookRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var preferencesRepository: FakeUserPreferencesRepository
    private lateinit var errorHandler: DefaultErrorHandler
    private lateinit var viewModel: BookshelfViewModel

    private val book1 = BookEntity(
        id = "b1",
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        filePath = "/path/hobbit.epub",
        importDate = 1000L,
        lastOpenedDate = 3000L
    )

    private val book2 = BookEntity(
        id = "b2",
        title = "Animal Farm",
        author = "George Orwell",
        filePath = "/path/animal_farm.epub",
        importDate = 2000L,
        lastOpenedDate = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        bookRepository = FakeBookRepository(listOf(book1, book2))
        progressRepository = FakeProgressRepository(
            listOf(
                ReadingProgressEntity(bookId = "b1", locatorJson = "{}", progressPercentage = 0.45f)
            )
        )
        preferencesRepository = FakeUserPreferencesRepository()
        errorHandler = DefaultErrorHandler()

        viewModel = BookshelfViewModel(
            bookRepository = bookRepository,
            progressRepository = progressRepository,
            userPreferencesRepository = preferencesRepository,
            errorHandler = errorHandler
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun booksAreLoadedWithProgress() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.books.size)

        val hobbit = state.books.find { it.id == "b1" }
        assertEquals(0.45f, hobbit?.progressPercentage)

        val animalFarm = state.books.find { it.id == "b2" }
        assertEquals(0.0f, animalFarm?.progressPercentage)

        collectJob.cancel()
    }

    @Test
    fun searchQueryFiltersBooks() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("orwell")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.books.size)
        assertEquals("Animal Farm", state.books.first().title)

        collectJob.cancel()
    }

    @Test
    fun unreadFilterShowsOnlyUnreadBooks() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(FilterMode.UNREAD)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.books.size)
        assertEquals("Animal Farm", state.books.first().title)

        collectJob.cancel()
    }

    @Test
    fun sortOrderAlphabeticalByTitle() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSortOrderSelected(SortOrder.TITLE)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Animal Farm", state.books[0].title)
        assertEquals("The Hobbit", state.books[1].title)

        collectJob.cancel()
    }

    @Test
    fun deleteBookRemovesItFromLibrary() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteBook("b1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.books.size)
        assertEquals("b2", state.books.first().id)

        collectJob.cancel()
    }
}
