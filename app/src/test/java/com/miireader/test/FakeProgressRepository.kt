package com.miireader.test

import com.miireader.data.local.entity.ReadingProgressEntity
import com.miireader.data.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeProgressRepository(
    initialProgress: List<ReadingProgressEntity> = emptyList()
) : ProgressRepository {

    private val _progress = MutableStateFlow(initialProgress)

    /** Number of writes accepted, so tests can assert on persistence behaviour. */
    var saveCount: Int = 0
        private set

    override fun getProgressForBookFlow(bookId: String): Flow<ReadingProgressEntity?> {
        return _progress.map { list -> list.find { it.bookId == bookId } }
    }

    override suspend fun getProgressForBook(bookId: String): ReadingProgressEntity? {
        return _progress.value.find { it.bookId == bookId }
    }

    override fun getAllProgress(): Flow<List<ReadingProgressEntity>> {
        return _progress.asStateFlow()
    }

    override suspend fun saveProgress(progress: ReadingProgressEntity) {
        saveCount++
        val current = _progress.value.filter { it.bookId != progress.bookId }
        _progress.value = current + progress
    }

    override suspend fun deleteProgressForBook(bookId: String) {
        _progress.value = _progress.value.filter { it.bookId != bookId }
    }
}
