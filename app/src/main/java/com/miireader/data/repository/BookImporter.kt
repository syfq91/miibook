package com.miireader.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.epub.EpubMetadataExtractor
import com.miireader.data.epub.PublicationOpenException
import com.miireader.data.local.entity.BookEntity
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.withContext

/**
 * Imports EPUB files from the Storage Access Framework into the library.
 *
 * The pipeline is:
 *  1. Copy the picked document from its content:// URI into app-private storage,
 *     so the book stays readable offline regardless of the source provider's
 *     persisted URI permissions.
 *  2. Extract title/author metadata by opening the publication with the EPUB engine.
 *  3. Compute a content checksum and skip the import if an identical book exists.
 *  4. Persist the resulting [BookEntity] to the library.
 *
 * Any partially-written file is removed if the import fails, so the private
 * storage directory never accumulates orphaned copies.
 */
interface BookImporter {
    suspend fun importBook(uri: Uri): ImportResult
}

class DefaultBookImporter(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val metadataExtractor: EpubMetadataExtractor,
    private val dispatchers: CoroutineDispatchers
) : BookImporter {

    private val booksDir: File
        get() = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }

    override suspend fun importBook(uri: Uri): ImportResult = withContext(dispatchers.io) {
        // Copy the source document into app-private storage first. We give it a
        // temporary name until the metadata is successfully read.
        val tempFile = File.createTempFile("import_", ".epub", booksDir)
        val displayName = queryDisplayName(uri)
        try {
            copyUriToFile(uri, tempFile)

            if (tempFile.length() == 0L) {
                return@withContext ImportResult.Failure("The selected file is empty.")
            }

            // Reject files that are not parseable publications (wrong type,
            // corrupt archive, DRM we cannot open, etc.) before touching the DB.
            val metadata = try {
                metadataExtractor.extractFrom(tempFile.absolutePath)
            } catch (e: PublicationOpenException) {
                return@withContext ImportResult.Failure(e.message ?: "The file could not be opened.")
            }

            val checksum = computeSha256(tempFile)
            bookRepository.getBookByChecksum(checksum)?.let { existing ->
                tempFile.delete()
                return@withContext ImportResult.Duplicate(existing)
            }

            val finalFile = File(booksDir, "${System.currentTimeMillis()}.epub")
            if (!tempFile.renameTo(finalFile)) {
                // Fall back to a copy if rename fails across devices/mounts.
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            val book = BookEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = metadata.title,
                author = metadata.author,
                filePath = finalFile.absolutePath,
                importDate = System.currentTimeMillis(),
                contentChecksum = checksum
            )
            bookRepository.insertBook(book)
            ImportResult.Success(book)
        } catch (e: IOException) {
            tempFile.delete()
            ImportResult.Failure("Could not read the selected file: ${e.message}")
        } catch (e: SecurityException) {
            tempFile.delete()
            ImportResult.Failure("Permission to read the selected file was denied.")
        } catch (e: Exception) {
            tempFile.delete()
            ImportResult.Failure(
                displayName?.let { "Failed to import \"$it\": ${e.message}" }
                    ?: "Failed to import the selected file: ${e.message}"
            )
        }
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected file.")
        input.use { stream ->
            target.outputStream().use { out ->
                stream.copyTo(out)
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
