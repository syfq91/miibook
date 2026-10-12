package com.miireader.ui.navigation

sealed class Screen(val route: String) {
    data object Bookshelf : Screen("bookshelf")
    data object Settings : Screen("settings")
    data object Catalog : Screen("catalog")
}
