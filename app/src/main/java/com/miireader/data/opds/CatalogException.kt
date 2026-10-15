package com.miireader.data.opds

import com.miireader.core.error.AppError

/**
 * A [Throwable] that carries an [AppError] across suspension boundaries.
 *
 * [AppError] is a sealed interface rather than a `Throwable`, so it cannot be thrown.
 * Throwing is still the most ergonomic way to unwind a deep call stack — an HTTP client
 * failing three layers down should not have to thread a `Result` through every signature.
 * This wrapper lets the failure stay an exception in transit and be unpacked back into an
 * [AppError] at the screen boundary, which is where user-facing wording is decided.
 */
class CatalogException(
    val error: AppError,
    cause: Throwable? = null
) : Exception(error.message, cause)

/** Throws this [AppError] as a [CatalogException]. */
internal fun AppError.asException(): CatalogException = CatalogException(this)
