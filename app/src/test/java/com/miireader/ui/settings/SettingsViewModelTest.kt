package com.miireader.ui.settings

import com.miireader.core.model.SortOrder
import com.miireader.platform.epaper.DefaultEpaperController
import com.miireader.test.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var preferencesRepository: FakeUserPreferencesRepository
    private lateinit var epaperController: DefaultEpaperController
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = FakeUserPreferencesRepository()
        epaperController = DefaultEpaperController(
            deviceManufacturer = "Onyx",
            deviceModel = "BOOX Palma",
            deviceBrand = "Onyx"
        )
        viewModel = SettingsViewModel(preferencesRepository, epaperController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ePaperDeviceMetadataIsDetected() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEpaperDevice)
        assertEquals("Onyx", viewModel.uiState.value.deviceManufacturer)
        assertEquals("BOOX Palma", viewModel.uiState.value.deviceModel)

        collectJob.cancel()
    }

    @Test
    fun warmBackgroundToggleUpdatesState() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.warmBackground)

        viewModel.onWarmBackgroundChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.warmBackground)
        collectJob.cancel()
    }

    @Test
    fun fontSizeChangeUpdatesState() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(18, viewModel.uiState.value.fontSizeSp)

        viewModel.onFontSizeChanged(24)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(24, viewModel.uiState.value.fontSizeSp)
        collectJob.cancel()
    }

    @Test
    fun animationsToggleUpdatesState() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.animationsEnabled)

        viewModel.onAnimationsToggled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.animationsEnabled)
        collectJob.cancel()
    }

    @Test
    fun refreshIntervalChangeUpdatesState() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.refreshIntervalPages)

        viewModel.onRefreshIntervalChanged(20)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.refreshIntervalPages)
        collectJob.cancel()
    }

    @Test
    fun sortOrderChangeUpdatesState() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortOrder.LAST_OPENED, viewModel.uiState.value.defaultSortOrder)

        viewModel.onSortOrderChanged(SortOrder.TITLE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortOrder.TITLE, viewModel.uiState.value.defaultSortOrder)
        collectJob.cancel()
    }
}
