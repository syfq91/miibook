package com.miireader.data.epub

import android.content.Context
import com.miireader.core.dispatchers.CoroutineDispatchers
import java.io.File
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * [EpubMetadataExtractor] backed by the Readium Kotlin Toolkit.
 *
 * Opens the publication with Readium's [PublicationOpener], reads title/author
 * metadata, then closes the publication so no file handles leak. All work is
 * dispatched to the IO dispatcher.
 */
class ReadiumEpubMetadataExtractor(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers
) : EpubMetadataExtractor {

    override suspend fun extractFrom(filePath: String): EpubMetadata = withContext(dispatchers.io) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            throw PublicationOpenException("The selected file could not be found or is empty.")
        }

        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val parser = DefaultPublicationParser(context, httpClient, assetRetriever, null)
        val opener = PublicationOpener(parser)

        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false))
            .fold(
                onSuccess = { it },
                onFailure = {
                    throw PublicationOpenException(
                        "The selected file could not be read as a publication."
                    )
                }
            )

        val publication = opener.open(asset, allowUserInteraction = false)
            .fold(
                onSuccess = { it },
                onFailure = {
                    throw PublicationOpenException(
                        "The file is not a supported publication. Only EPUB files can be imported."
                    )
                }
            )

        try {
            val metadata = publication.metadata
            val title = metadata.title?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension.ifBlank { "Untitled" }
            val author = metadata.authors.firstOrNull()?.toString()?.takeIf { it.isNotBlank() }
                ?: "Unknown Author"

            EpubMetadata(title = title, author = author)
        } finally {
            publication.close()
        }
    }
}
