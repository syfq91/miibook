package com.miireader

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.navigation.compose.rememberNavController
import com.miireader.di.ViewModelFactory
import com.miireader.ui.navigation.AppNavigation
import com.miireader.ui.theme.MiiReaderTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * Single activity for the whole app. It extends [FragmentActivity] rather than
 * `ComponentActivity` so it can host Readium's [EpubNavigatorFragment].
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // EpubNavigatorFragment cannot be reconstructed by the system: its constructor is
        // internal and it holds a live `Publication` that cannot be serialized. Install a
        // factory that yields an inert placeholder during restoration instead of crashing
        // with "could not find Fragment constructor". This must happen before
        // super.onCreate(), which is where FragmentManager performs restoration.
        supportFragmentManager.fragmentFactory = RestoringFragmentFactory()
        super.onCreate(savedInstanceState)

        // The placeholder serves no purpose once restoration is over: it renders nothing
        // useful and its view would be created during the upcoming onStart(). Remove it
        // now, while its view does not exist yet, so the real navigator can be created
        // fresh by ReaderScreen from the position persisted in Room.
        if (savedInstanceState != null) {
            removeAllFragments()
        }

        enableEdgeToEdge()

        val app = application as MiiReaderApplication
        val container = app.container

        setContent {
            val viewModelFactory = remember { ViewModelFactory(container) }
            val preferences by container.userPreferencesRepository.preferences.collectAsState(
                initial = com.miireader.data.preferences.UserPreferences()
            )

            MiiReaderTheme(warmBackground = preferences.warmBackground) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        viewModelFactory = viewModelFactory
                    )
                }
            }
        }
    }

    private fun removeAllFragments() {
        val transaction = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.forEach { transaction.remove(it) }
        transaction.commitNow()
    }

    /**
     * Forwards hardware page-turn keys to the open reader.
     *
     * Key events reach the activity before the view hierarchy, so this is the only place
     * that can reliably capture buttons which do not move focus. [PageTurnKeys] ignores
     * everything except PAGE_UP/PAGE_DOWN and does nothing when no reader is open.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val pageTurnKeys = (application as MiiReaderApplication).container.pageTurnKeys
        if (pageTurnKeys.handles(event.keyCode)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (pageTurnKeys.dispatch(event.keyCode)) return true
                // Consume the matching UP too, so no other component sees a half-handled key.
                KeyEvent.ACTION_UP -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Returns a placeholder [EpubNavigatorFragment] when the system asks to restore one,
 * and delegates every other fragment type to the default implementation.
 *
 * Readium documents this exact pattern via [EpubNavigatorFragment.createDummyFactory].
 */
private class RestoringFragmentFactory : FragmentFactory() {
    private val dummyFactory = EpubNavigatorFragment.createDummyFactory()

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return if (className == EpubNavigatorFragment::class.java.name) {
            dummyFactory.instantiate(classLoader, className)
        } else {
            super.instantiate(classLoader, className)
        }
    }
}
