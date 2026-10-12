package com.miireader.core.model

enum class SortOrder(val label: String) {
    LAST_OPENED("Recent"),
    TITLE("Title"),
    AUTHOR("Author");

    companion object {
        fun fromName(name: String?): SortOrder {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: LAST_OPENED
        }
    }
}
