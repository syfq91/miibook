package com.miireader.data.epub

import android.content.Context
import com.miireader.core.dispatchers.CoroutineDispatchers
import java.io.File
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Opens an EPUB file as a live [Publication] for the reader to render.
 *
 * Unlike [EpubMetadataExtractor], which opens and immediately closes a publication to
 * read metadata, the returned publication stays open for as long as the reader screen is
 * showing it. **The caller owns the publication and must close it** when the screen goes
 * away; see `ReaderViewModel.onCleared`.
 */
interface EpubPublicationRepository {
    /**
     * Opens the EPUB at [filePath].
     *
     * @throws PublicationOpenException with a user-facing message when the file is
     * missing, empty, or not a supported publication.
     */
    suspend fun openPublication(filePath: String): Publication
}

/**
 * [EpubPublicationRepository] backed by the Readium Kotlin Toolkit.
 */
class ReadiumEpubPublicationRepository(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers
) : EpubPublicationRepository {

    override suspend fun openPublication(filePath: String): Publication =
        withContext(dispatchers.io) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile || file.length() == 0L) {
                throw PublicationOpenException(
                    "The book file could not be found. It may have been moved or deleted."
                )
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
                            "The book file could not be read as a publication."
                        )
                    }
                )

            opener.open(asset, allowUserInteraction = false)
                .fold(
                    onSuccess = { it },
                    onFailure = {
                        throw PublicationOpenException(
                            "This file is not a supported publication."
                        )
                    }
                )
        }
}
