package com.miireader.core.error

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Centralized application error handler for propagating user-visible error notifications
 * across the UI layers without crashing or leaving corrupt state.
 */
interface ErrorHandler {
    val errors: SharedFlow<AppError>
    suspend fun emitError(error: AppError)
    fun tryEmitError(error: AppError): Boolean
}

class DefaultErrorHandler : ErrorHandler {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 8)
    override val errors: SharedFlow<AppError> = _errors.asSharedFlow()

    override suspend fun emitError(error: AppError) {
        _errors.emit(error)
    }

    override fun tryEmitError(error: AppError): Boolean {
        return _errors.tryEmit(error)
    }
}
