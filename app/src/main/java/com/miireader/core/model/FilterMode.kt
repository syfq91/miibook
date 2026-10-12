package com.miireader.core.model

enum class FilterMode(val label: String) {
    ALL("All"),
    RECENT("Recent"),
    UNREAD("Unread");

    companion object {
        fun fromName(name: String?): FilterMode {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: ALL
        }
    }
}
