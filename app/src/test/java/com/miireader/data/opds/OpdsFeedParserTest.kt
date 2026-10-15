package com.miireader.data.opds

import com.miireader.core.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric is required even though nothing here touches an Activity: Readium's URL and
 * feed types are built on `android.net.Uri`, which is a stub that throws in a plain JVM
 * test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpdsFeedParserTest {

    private val parser = OpdsFeedParser()

    @Test
    fun `parses an opds 1 atom feed`() {
        val result = parser.parse(OpdsFixtures.OPDS1_ATOM, OpdsFixtures.BASE_URL)

        val feed = (result as? OpdsFeedParser.Result.Success)?.feed
        assertNotNull("expected a parsed feed, got $result", feed)

        assertEquals("Example Catalog", feed!!.title)
        assertEquals(1, feed.entries.size)

        val entry = feed.entries.first()
        assertEquals("Moby Dick", entry.title)
        assertEquals("Herman Melville", entry.author)
        assertEquals("A man vs. whale saga.", entry.summary)
        assertEquals(2, entry.acquisitions.size)
    }

    @Test
    fun `resolves relative acquisition hrefs against the base url`() {
        val result = parser.parse(OpdsFixtures.OPDS1_ATOM, OpdsFixtures.BASE_URL)

        val entry = (result as OpdsFeedParser.Result.Success).feed.entries.first()
        val hrefs = entry.acquisitions.map { it.href }

        assertTrue(
            "expected absolute hrefs, got $hrefs",
            hrefs.all { it.startsWith("https://") }
        )
        assertTrue(hrefs.any { it.endsWith("moby-dick.epub") })
        assertTrue(hrefs.any { it.endsWith("moby-dick.pdf") })
    }

    @Test
    fun `carries acquisition media types through lowercase`() {
        val result = parser.parse(OpdsFixtures.OPDS1_ATOM, OpdsFixtures.BASE_URL)

        val entry = (result as OpdsFeedParser.Result.Success).feed.entries.first()
        val types = entry.acquisitions.mapNotNull { it.mediaType }

        assertTrue("expected an epub media type, got $types", types.any { it.contains("epub") })
        assertTrue("expected a pdf media type, got $types", types.any { it.contains("pdf") })
    }

    @Test
    fun `parses an opds 2 json feed`() {
        val result = parser.parse(OpdsFixtures.OPDS2_JSON, OpdsFixtures.BASE_URL)

        val feed = (result as? OpdsFeedParser.Result.Success)?.feed
        assertNotNull("expected a parsed feed, got $result", feed)

        assertEquals("Example Catalog 2", feed!!.title)
        assertEquals(1, feed.entries.size)
        assertEquals("Moby Dick", feed.entries.first().title)
        assertTrue("expected navigation links", feed.navigation.isNotEmpty())
    }

    @Test
    fun `surfaces feed navigation links`() {
        val result = parser.parse(OpdsFixtures.OPDS1_ATOM, OpdsFixtures.BASE_URL)

        val feed = (result as OpdsFeedParser.Result.Success).feed
        assertTrue(
            "expected at least one navigation link, got ${feed.navigation}",
            feed.navigation.isNotEmpty()
        )
    }

    @Test
    fun `rejects a payload that is not a feed`() {
        val result = parser.parse(OpdsFixtures.NOT_A_FEED, OpdsFixtures.BASE_URL)

        val failure = result as? OpdsFeedParser.Result.Failure
        assertNotNull("expected a failure, got $result", failure)
        assertTrue(failure!!.error is AppError.BookParseError)
    }

    @Test
    fun `rejects a single-entry document as a non-browsable catalog`() {
        val result = parser.parse(OpdsFixtures.OPDS1_ENTRY, OpdsFixtures.BASE_URL)

        val failure = result as? OpdsFeedParser.Result.Failure
        assertNotNull("expected a failure for an entry document, got $result", failure)
        assertTrue(failure!!.error is AppError.BookParseError)
    }

    @Test
    fun `rejects an unusable base url`() {
        val result = parser.parse(OpdsFixtures.OPDS1_ATOM, "")

        val failure = result as? OpdsFeedParser.Result.Failure
        assertNotNull("expected a failure for a blank base url, got $result", failure)
        assertTrue(failure!!.error is AppError.NetworkError)
    }

    @Test
    fun `handles a payload with leading whitespace`() {
        val padded = ByteArray(1) { ' '.code.toByte() } + OpdsFixtures.OPDS2_JSON

        val result = parser.parse(padded, OpdsFixtures.BASE_URL)

        assertTrue(
            "expected whitespace-padded json to parse, got $result",
            result is OpdsFeedParser.Result.Success
        )
    }

    @Test
    fun `handles an empty payload`() {
        val result = parser.parse(ByteArray(0), OpdsFixtures.BASE_URL)

        assertTrue(
            "expected an empty payload to fail, got $result",
            result is OpdsFeedParser.Result.Failure
        )
    }
}
