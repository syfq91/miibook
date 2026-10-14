package com.miireader.ui.reader

import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/**
 * The slice of navigator behaviour the reader actually uses.
 *
 * Readium's `OverflowableNavigator` exposes the whole WebView surface — the publication
 * view, input listeners, overflow state — almost none of which the reader needs, and all
 * of which would have to be faked in tests. Depending on this interface instead keeps
 * `ReaderViewModel` testable with a handful of lines.
 *
 * The host adapts the real [org.readium.r2.navigator.epub.EpubNavigatorFragment] to it.
 */
interface ReaderNavigator {
    /** The navigator's current position; emits as the reader pages through the book. */
    val currentLocator: StateFlow<Locator>

    /** Moves to [locator]. Returns false if it could not be resolved. */
    fun go(locator: Locator): Boolean

    /** Advances one page in the reading progression direction. */
    fun goForward(): Boolean

    /** Goes back one page in the reading progression direction. */
    fun goBackward(): Boolean
}

/**
 * Adapts Readium's [OverflowableNavigator] to [ReaderNavigator].
 *
 * Page turns are always requested unanimated: e-paper cannot render a transition and
 * Readium's own accessibility guidance is to let the app disable animation.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadiumReaderNavigator(
    private val delegate: OverflowableNavigator
) : ReaderNavigator {

    override val currentLocator: StateFlow<Locator>
        get() = delegate.currentLocator

    override fun go(locator: Locator): Boolean = delegate.go(locator, animated = false)

    override fun goForward(): Boolean = delegate.goForward(animated = false)

    override fun goBackward(): Boolean = delegate.goBackward(animated = false)
}
