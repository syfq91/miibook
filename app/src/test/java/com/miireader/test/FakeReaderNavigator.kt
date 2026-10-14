package com.miireader.test

import com.miireader.ui.reader.ReaderNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.shared.publication.Locator

/**
 * [ReaderNavigator] double that records what the reader asked it to do.
 *
 * Mirrors the real navigator in the one way that matters for tests: mutating
 * [emit] changes `currentLocator`, which the ViewModel observes.
 */
class FakeReaderNavigator(
    initialLocator: Locator
) : ReaderNavigator {

    private val _currentLocator = MutableStateFlow(initialLocator)
    override val currentLocator: StateFlow<Locator> = _currentLocator.asStateFlow()

    val requestedLocators = mutableListOf<Locator>()
    var forwardCount: Int = 0
        private set
    var backwardCount: Int = 0
        private set

    override fun go(locator: Locator): Boolean {
        requestedLocators += locator
        _currentLocator.value = locator
        return true
    }

    override fun goForward(): Boolean {
        forwardCount++
        return true
    }

    override fun goBackward(): Boolean {
        backwardCount++
        return true
    }

    /** Simulates the reader paging to [locator] on its own. */
    fun emit(locator: Locator) {
        _currentLocator.value = locator
    }
}
