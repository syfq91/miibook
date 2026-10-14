package com.miireader.core.input

import android.view.KeyEvent

/**
 * Bridges hardware page-turn keys to the open reader.
 *
 * `MainActivity.dispatchKeyEvent` forwards key events here, and the reader registers a
 * handler while it is on screen. When no reader is open nothing is registered, so the
 * keys fall through to Android untouched.
 *
 * Only [KeyEvent.KEYCODE_PAGE_UP] and [KeyEvent.KEYCODE_PAGE_DOWN] are handled. Many
 * e-paper readers let their hardware buttons emit these. Volume keys are deliberately
 * *not* consumed: some devices let the user map their page buttons to volume, but
 * swallowing volume would break audio playback and ringer control for everyone else.
 */
class PageTurnKeys {

    private var handler: ((keyCode: Int) -> Boolean)? = null

    /** Registers [handler]; it returns true when it consumed the key. */
    fun register(handler: (keyCode: Int) -> Boolean) {
        this.handler = handler
    }

    fun unregister() {
        handler = null
    }

    /**
     * True when [keyCode] is a page-turn key and a reader is currently listening.
     *
     * Used to swallow the matching ACTION_UP as well, so no other component ever sees
     * half of a key gesture that the reader already consumed.
     */
    fun handles(keyCode: Int): Boolean =
        handler != null &&
            (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN)

    /** Forwards [keyCode] to the registered handler. Returns true when consumed. */
    fun dispatch(keyCode: Int): Boolean {
        if (!handles(keyCode)) return false
        return handler?.invoke(keyCode) ?: false
    }
}
