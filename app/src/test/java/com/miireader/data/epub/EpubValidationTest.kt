package com.miireader.data.epub

import android.content.Context
import com.miireader.test.EpubFixtures
import com.miireader.test.TestDispatchers
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Hardens the import/open paths against malformed, large and feature-rich EPUBs.
 *
 * Robolectric is required because Readium builds `Url` values on top of `android.net.Uri`.
 * Every fixture is generated in memory by [EpubFixtures] and written to a temp file under
 * the app cache dir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubValidationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(testDispatcher)

    private lateinit var context: Context
    private lateinit var workDir: File
    private lateinit var extractor: ReadiumEpubMetadataExtractor
    private lateinit var repository: ReadiumEpubPublicationRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        workDir = File(context.cacheDir, "epub-validation").apply {
            deleteRecursively()
            mkdirs()
        }
        extractor = ReadiumEpubMetadataExtractor(context, dispatchers)
        repository = ReadiumEpubPublicationRepository(context, dispatchers)
    }

    /** Writes [bytes] under the cache work dir and returns the absolute path. */
    private fun writeFixture(name: String, bytes: ByteArray): String =
        File(workDir, name).also { it.writeBytes(bytes) }.absolutePath

    /**
     * Asserts that [block] fails with a [PublicationOpenException] whose message is
     * user-facing (non-blank, no exception class names, no stack-trace frames).
     */
    private suspend fun expectUserFacingRejection(block: suspend () -> Unit): String {
        val error: Throwable? = try {
            block()
            null
        } catch (t: Throwable) {
            t
        }
        assertTrue(
            "expected PublicationOpenException but got $error",
            error is PublicationOpenException
        )
        val message = error!!.message.orEmpty()
        assertTrue("message was blank", message.isNotBlank())
        assertFalse("message leaks exception wording: $message", message.contains("Exception"))
        assertFalse("message leaks a stack frame: $message", message.contains("com.miireader"))
        assertFalse("message leaks a stack frame: $message", message.contains("\tat "))
        return message
    }

    @Test
    fun `a valid epub exposes its title and author`() = runTest(testDispatcher) {
        val title = "The Odyssey of Testing"
        val author = "Ada Q. Example"
        val path = writeFixture(
            "valid.epub",
            EpubFixtures.minimalEpub(
                title = title,
                author = author,
                chapters = listOf("chapter-1" to "<p>Telemachus sets out.</p>")
            )
        )

        val metadata = extractor.extractFrom(path)

        assertEquals(title, metadata.title)
        assertEquals(author, metadata.author)
    }

    @Test
    fun `random bytes are rejected with a user-facing message`() = runTest(testDispatcher) {
        val path = writeFixture("random.bin", ByteArray(512) { (it * 31 % 251).toByte() })

        val message = expectUserFacingRejection { extractor.extractFrom(path) }
        // The message must describe the problem, not the failure mechanism.
        assertTrue("unexpected message: $message", message.length < 200)
    }

    @Test
    fun `a zip that is not an epub is rejected`() = runTest(testDispatcher) {
        val path = writeFixture("not-an-epub.zip", EpubFixtures.zipWithoutContainer())

        expectUserFacingRejection { extractor.extractFrom(path) }
    }

    @Test
    fun `an empty file is rejected`() = runTest(testDispatcher) {
        val path = writeFixture("empty.epub", ByteArray(0))

        expectUserFacingRejection { extractor.extractFrom(path) }
    }

    @Test
    fun `a plain text document with an epub name is rejected`() = runTest(testDispatcher) {
        val path = writeFixture(
            "plain-text.epub",
            "Just plain text pretending to be an EPUB archive.".toByteArray()
        )

        expectUserFacingRejection { extractor.extractFrom(path) }
    }

    @Test
    fun `a missing file is rejected`() = runTest(testDispatcher) {
        val path = File(workDir, "missing.epub").absolutePath // never created

        expectUserFacingRejection { repository.openPublication(path) }
    }

    @Test
    fun `a large book with a long chapter list opens with every chapter`() =
        runTest(testDispatcher) {
            val chapters = (1..150).map { index ->
                "chapter-%03d".format(index) to "<p>Chapter $index body.</p>"
            }
            val path = writeFixture(
                "large.epub",
                EpubFixtures.minimalEpub(
                    title = "A Very Long Book",
                    author = "Prolific Author",
                    chapters = chapters
                )
            )

            val publication = repository.openPublication(path)
            try {
                assertEquals(chapters.size, publication.readingOrder.size)
                assertEquals(chapters.size, publication.tableOfContents.size)
            } finally {
                // Publication is not AutoCloseable — never use `use { }`.
                publication.close()
            }
        }

    @Test
    fun `an epub with images and embedded fonts opens`() = runTest(testDispatcher) {
        val path = writeFixture(
            "assets.epub",
            EpubFixtures.epubWithAssets(
                title = "Fully Illustrated",
                author = "Visual Author"
            )
        )

        // The zip really carries the asset entries.
        val entryNames = mutableSetOf<String>()
        ZipInputStream(File(path).inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames += entry.name
            }
        }
        assertTrue(entryNames.contains("OEBPS/images/cover.png"))
        assertTrue(entryNames.contains("OEBPS/fonts/body.woff2"))

        val publication = repository.openPublication(path)
        try {
            assertTrue(publication.readingOrder.isNotEmpty())
            assertTrue(publication.tableOfContents.isNotEmpty())

            val resourceHrefs = publication.resources.map { it.href.toString() }
            assertTrue(
                "resources were $resourceHrefs",
                resourceHrefs.any { it.endsWith("images/cover.png") }
            )
            assertTrue(
                "resources were $resourceHrefs",
                resourceHrefs.any { it.endsWith("fonts/body.woff2") }
            )
            assertNotNull(publication.metadata.title)
        } finally {
            publication.close()
        }
    }
}
