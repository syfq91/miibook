package com.miireader.core.model

/**
 * Theme applied to the book content rendered by the navigator.
 *
 * This is separate from the app's own `warmBackground` preference: it controls what the
 * page itself looks like (Readium's `Theme`), while `warmBackground` controls the
 * library and settings screens.
 */
enum class ReaderTheme(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SEPIA("Sepia");

    companion object {
        fun fromName(name: String?): ReaderTheme {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: LIGHT
        }
    }
}
