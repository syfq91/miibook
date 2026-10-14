package com.miireader.data.repository

import com.miireader.data.local.entity.BookEntity

/**
 * Outcome of an attempt to import a single EPUB into the library.
 */
sealed interface ImportResult {
    /** The book was imported and is now available in the library. */
    data class Success(val book: BookEntity) : ImportResult

    /** A book with identical content already exists in the library. */
    data class Duplicate(val existingBook: BookEntity) : ImportResult

    /** The file could not be imported. [reason] is user-facing. */
    data class Failure(val reason: String) : ImportResult
}
