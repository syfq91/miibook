package com.miireader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.miireader.data.local.dao.BookDao
import com.miireader.data.local.dao.BookmarkDao
import com.miireader.data.local.dao.CatalogDao
import com.miireader.data.local.dao.DownloadDao
import com.miireader.data.local.dao.ReadingProgressDao
import com.miireader.data.local.DownloadEntity
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.entity.BookmarkEntity
import com.miireader.data.local.entity.CatalogEntity
import com.miireader.data.local.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        CatalogEntity::class,
        DownloadEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun catalogDao(): CatalogDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        private const val DATABASE_NAME = "miireader.db"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration(dropAllTables = true).build()
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
