package com.miireader.data.epub

/**
 * Metadata extracted from an EPUB publication during import.
 */
data class EpubMetadata(
    val title: String,
    val author: String,
    val coverPath: String? = null
)

/**
 * Thrown when a file cannot be parsed as a publication, or the publication is
 * unsupported. The [message] is user-facing and safe to display.
 */
class PublicationOpenException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Extracts descriptive metadata (title, author, cover) from an EPUB file.
 *
 * Implementations open the publication with the EPUB engine, read its metadata,
 * and are responsible for closing any underlying resources before returning.
 */
interface EpubMetadataExtractor {
    suspend fun extractFrom(filePath: String): EpubMetadata
}
