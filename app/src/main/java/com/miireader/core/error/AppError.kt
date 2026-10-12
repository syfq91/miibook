package com.miireader.core.error

/**
 * Domain-level error hierarchy representing application errors that can occur during
 * library management, persistence, reading, or catalog browsing.
 */
sealed interface AppError {
    val message: String
    val cause: Throwable?

    data class StorageError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError

    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError

    data class BookParseError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError

    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError

    data class GeneralError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError
}
