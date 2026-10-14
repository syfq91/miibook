package com.miireader.test

import com.miireader.data.epub.EpubPublicationRepository
import com.miireader.data.epub.PublicationOpenException
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Builds a bare publication for tests.
 *
 * Reading-order entries are generated from [tableOfContents] (mirroring what an EPUB
 * parser produces), because `Publication.locatorFromLink` only resolves a link when the
 * targeted resource is present in the reading order *and* carries a media type. Without
 * that, table of contents navigation silently does nothing.
 */
fun testPublication(
    title: String = "Test Book",
    tableOfContents: List<Link> = emptyList()
): Publication = Publication(
    Manifest(
        metadata = Metadata(localizedTitle = LocalizedString(title)),
        readingOrder = tableOfContents.map { link ->
            Link(link.href, mediaType = MediaType.XHTML, title = link.title)
        },
        tableOfContents = tableOfContents
    )
)

/** Builds a link pointing at [href], for a publication's table of contents. */
fun testTocLink(href: String, title: String): Link =
    Link(requireNotNull(Url(href)), title = title)

/** Builds a locator for [href], optionally carrying progression information. */
fun testLocator(
    href: String,
    title: String? = null,
    progression: Double? = null,
    totalProgression: Double? = null
): Locator = Locator(
    href = requireNotNull(Url(href)),
    mediaType = MediaType.XHTML,
    title = title,
    locations = Locator.Locations(
        progression = progression,
        totalProgression = totalProgression
    )
)

/**
 * [EpubPublicationRepository] double.
 *
 * @param error thrown instead of returning a publication, to exercise the failure path.
 */
class FakeEpubPublicationRepository(
    private val publication: () -> Publication = { testPublication() },
    private val error: PublicationOpenException? = null
) : EpubPublicationRepository {

    var openCount: Int = 0
        private set

    override suspend fun openPublication(filePath: String): Publication {
        error?.let { throw it }
        openCount++
        return publication()
    }
}
