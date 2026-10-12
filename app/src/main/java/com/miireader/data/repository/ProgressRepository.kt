package com.miireader.data.repository

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.local.dao.ReadingProgressDao
import com.miireader.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface ProgressRepository {
    fun getProgressForBookFlow(bookId: String): Flow<ReadingProgressEntity?>
    suspend fun getProgressForBook(bookId: String): ReadingProgressEntity?
    fun getAllProgress(): Flow<List<ReadingProgressEntity>>
    suspend fun saveProgress(progress: ReadingProgressEntity)
    suspend fun deleteProgressForBook(bookId: String)
}

class DefaultProgressRepository(
    private val progressDao: ReadingProgressDao,
    private val dispatchers: CoroutineDispatchers
) : ProgressRepository {

    override fun getProgressForBookFlow(bookId: String): Flow<ReadingProgressEntity?> {
        return progressDao.getProgressForBookFlow(bookId).flowOn(dispatchers.io)
    }

    override suspend fun getProgressForBook(bookId: String): ReadingProgressEntity? {
        return withContext(dispatchers.io) {
            progressDao.getProgressForBook(bookId)
        }
    }

    override fun getAllProgress(): Flow<List<ReadingProgressEntity>> {
        return progressDao.getAllProgress().flowOn(dispatchers.io)
    }

    override suspend fun saveProgress(progress: ReadingProgressEntity) {
        withContext(dispatchers.io) {
            progressDao.upsertProgress(progress)
        }
    }

    override suspend fun deleteProgressForBook(bookId: String) {
        withContext(dispatchers.io) {
            progressDao.deleteProgressForBook(bookId)
        }
    }
}
