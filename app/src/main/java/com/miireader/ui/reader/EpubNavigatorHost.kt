package com.miireader.ui.reader

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.miireader.R
import com.miireader.core.model.ReaderTheme
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme as NavigatorTheme
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.ExperimentalReadiumApi

/** Tag used for the navigator fragment so it can be found, replaced, and removed. */
private const val NAVIGATOR_TAG = "miireader.epub_navigator"

/**
 * Font size (sp) that renders the publication at its own natural size.
 *
 * Readium expresses font size as a multiplier of the publication's CSS rather than an
 * absolute point size, so the preference is converted by dividing by this baseline.
 */
private const val BASE_FONT_SIZE_SP = 18.0

private fun ReaderTheme.toNavigatorTheme(): NavigatorTheme = when (this) {
    ReaderTheme.LIGHT -> NavigatorTheme.LIGHT
    ReaderTheme.DARK -> NavigatorTheme.DARK
    ReaderTheme.SEPIA -> NavigatorTheme.SEPIA
}

@OptIn(ExperimentalReadiumApi::class)
private fun buildPreferences(fontSizeSp: Int, readerTheme: ReaderTheme): EpubPreferences =
    EpubPreferences(
        fontSize = fontSizeSp / BASE_FONT_SIZE_SP,
        theme = readerTheme.toNavigatorTheme()
    )

/**
 * Hosts Readium's [EpubNavigatorFragment] inside Compose.
 *
 * The fragment is always built fresh from [session] instead of being restored: its
 * constructor is internal, it holds a live `Publication`, and Readium documents that a
 * restored instance must be torn down before it resumes. The Activity installs
 * `EpubNavigatorFragment.createDummyFactory()` so restoration itself does not crash;
 * this host replaces whatever is in the container.
 *
 * The transaction is deferred with [View.post] until the container is attached. Adding a
 * fragment before its container exists makes FragmentManager throw
 * "No view found for id", and the fragment's view would not be created beforehand.
 */
@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class)
@Composable
fun EpubNavigatorHost(
    session: ReaderSession,
    fontSizeSp: Int,
    readerTheme: ReaderTheme,
    onNavigatorReady: (ReaderNavigator) -> Unit,
    onNavigatorDisposed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fragmentManager = context.findFragmentActivity().supportFragmentManager
    val classLoader = context.classLoader

    // The values below are read from callbacks that run outside composition (a View post
    // and a lifecycle observer), both of which fire only once.
    val sessionState = rememberUpdatedState(session)
    val fontSizeState = rememberUpdatedState(fontSizeSp)
    val themeState = rememberUpdatedState(readerTheme)
    val readyState = rememberUpdatedState(onNavigatorReady)
    val disposedState = rememberUpdatedState(onNavigatorDisposed)

    val navigatorRef = remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // The container's factory posts the fragment transaction; this stops it from running
    // if the screen went away before the container was ever attached.
    val released = remember { mutableStateOf(false) }

    // Apply font size / theme changes to an already-running navigator. Initial values are
    // applied when the fragment is created, so this only acts on later changes.
    LaunchedEffect(fontSizeSp, readerTheme) {
        navigatorRef.value?.submitPreferences(
            buildPreferences(fontSizeState.value, themeState.value)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            released.value = true
            disposedState.value()
            navigatorRef.value = null

            val existing = fragmentManager.findFragmentByTag(NAVIGATOR_TAG)
            if (existing != null && !fragmentManager.isStateSaved) {
                fragmentManager.beginTransaction().remove(existing).commitNow()
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            FragmentContainerView(viewContext).apply {
                id = R.id.reader_navigator_container
                post {
                    if (released.value || fragmentManager.isStateSaved) return@post

                    val fragment = attachNavigator(
                        fragmentManager = fragmentManager,
                        classLoader = classLoader,
                        session = sessionState.value,
                        fontSizeSp = fontSizeState.value,
                        readerTheme = themeState.value
                    )
                    navigatorRef.value = fragment
                    registerWhenReady(fragment) { readyState.value(it) }
                }
            }
        }
    )
}

/**
 * Builds a fresh navigator for [session], replacing anything already in the container.
 *
 * @return the fragment that was added.
 */
@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class)
private fun attachNavigator(
    fragmentManager: FragmentManager,
    classLoader: ClassLoader,
    session: ReaderSession,
    fontSizeSp: Int,
    readerTheme: ReaderTheme
): EpubNavigatorFragment {
    val factory = EpubNavigatorFactory(session.publication).createFragmentFactory(
        initialLocator = session.initialLocator,
        initialPreferences = buildPreferences(fontSizeSp, readerTheme),
        configuration = EpubNavigatorFragment.Configuration {
            // Drive font size with WebView textZoom instead of the Readium CSS
            // `--USER__fontSize` variable: Readium documents textZoom as working with more
            // publications, including poorly authored ones. The trade-off is that page
            // width is not re-adjusted when the size changes.
            useReadiumCssFontSize = false
        }
    )

    val fragment = factory.instantiate(
        classLoader,
        EpubNavigatorFragment::class.java.name
    ) as EpubNavigatorFragment

    val transaction = fragmentManager.beginTransaction()
    fragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { transaction.remove(it) }
    transaction
        .replace(R.id.reader_navigator_container, fragment, NAVIGATOR_TAG)
        .commitNow()

    return fragment
}

/**
 * Invokes [onReady] once the fragment's view exists.
 *
 * [Lifecycle.addObserver] replays the current state, so this fires immediately when the
 * fragment is already started and later otherwise. A fragment's view is always created
 * before it reaches STARTED.
 */
@OptIn(ExperimentalReadiumApi::class)
private fun registerWhenReady(
    fragment: EpubNavigatorFragment,
    onReady: (ReaderNavigator) -> Unit
) {
    val navigator = ReadiumReaderNavigator(fragment)

    if (fragment.view != null) {
        onReady(navigator)
        return
    }

    val observer = object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_START) {
                source.lifecycle.removeObserver(this)
                onReady(navigator)
            }
        }
    }
    fragment.lifecycle.addObserver(observer)
}
