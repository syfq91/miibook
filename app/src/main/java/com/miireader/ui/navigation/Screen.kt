package com.miireader.ui.navigation

sealed class Screen(val route: String) {
    data object Bookshelf : Screen("bookshelf")
    data object Settings : Screen("settings")
    data object Catalog : Screen("catalog")
    data object Downloads : Screen("downloads")

    /**
     * Reader for a single book. The id is a route argument so each book gets its own
     * ViewModel scope and its own navigator.
     */
    data object Reader : Screen("reader/{bookId}") {
        const val ARG_BOOK_ID = "bookId"
        fun create(bookId: String) = "reader/$bookId"
    }
}
