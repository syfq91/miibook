package com.miireader.core.input

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hardware page-turn key plumbing (Phase 5: physical button navigation).
 *
 * This covers the JVM-testable half of the chain: [PageTurnKeys] decides which key codes
 * reach the reader and whether the matching ACTION_UP is swallowed. The rest of the chain
 * (`MainActivity.dispatchKeyEvent` -> `ReaderViewModel.onNavigatorReady` handler) is
 * covered by `ReaderViewModelTest`; dispatching a real `KeyEvent` through an Activity
 * needs instrumentation and has **not** been verified on hardware.
 */
class PageTurnKeysTest {

    @Test
    fun `nothing is handled while no reader is listening`() {
        val keys = PageTurnKeys()

        assertFalse(keys.handles(KeyEvent.KEYCODE_PAGE_DOWN))
        assertFalse(keys.handles(KeyEvent.KEYCODE_PAGE_UP))
        assertFalse(keys.dispatch(KeyEvent.KEYCODE_PAGE_DOWN))
    }

    @Test
    fun `only page up and page down are recognised`() {
        val keys = PageTurnKeys()
        keys.register { true }

        listOf(
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN
        ).forEach { code -> assertTrue("key $code should be a page key", keys.handles(code)) }

        listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_TAB
        ).forEach { code -> assertFalse("key $code must fall through", keys.handles(code)) }
        assertFalse(keys.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun `dispatch forwards the key and reports whether it was consumed`() {
        val keys = PageTurnKeys()
        val seen = mutableListOf<Int>()
        keys.register { keyCode ->
            seen += keyCode
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        }

        assertTrue(keys.dispatch(KeyEvent.KEYCODE_PAGE_DOWN))
        assertFalse(keys.dispatch(KeyEvent.KEYCODE_PAGE_UP))

        assertTrue(seen.containsAll(listOf(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_UP)))
    }

    @Test
    fun `unregistering releases the keys again`() {
        val keys = PageTurnKeys()
        keys.register { true }
        assertTrue(keys.dispatch(KeyEvent.KEYCODE_PAGE_DOWN))

        keys.unregister()

        assertFalse(keys.handles(KeyEvent.KEYCODE_PAGE_DOWN))
        assertFalse(keys.dispatch(KeyEvent.KEYCODE_PAGE_DOWN))
    }
}
