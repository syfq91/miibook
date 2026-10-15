package com.miireader.test

import com.miireader.data.epub.EpubMetadata
import com.miireader.data.epub.EpubMetadataExtractor
import com.miireader.data.epub.PublicationOpenException

/**
 * [EpubMetadataExtractor] that reports canned metadata.
 *
 * [rejectAll] stands in for a file the EPUB engine refuses to open — a truncated
 * download, a PDF renamed to .epub, or DRM.
 */
class FakeEpubMetadataExtractor : EpubMetadataExtractor {

    var metadata: EpubMetadata = EpubMetadata(title = "Downloaded Book", author = "An Author")

    /** When true, every path fails to open. */
    var rejectAll: Boolean = false

    /** Every path handed to [extractFrom], in order. */
    val openedPaths = mutableListOf<String>()

    override suspend fun extractFrom(filePath: String): EpubMetadata {
        openedPaths += filePath
        if (rejectAll) {
            throw PublicationOpenException("This file could not be opened as a book.")
        }
        return metadata
    }
}
