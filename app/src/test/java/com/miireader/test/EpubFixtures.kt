package com.miireader.test

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds EPUB 3 byte arrays entirely in memory, so tests never need fixture files on disk.
 *
 * The generated archives are spec-valid where it matters to Readium: the `mimetype`
 * entry is the first entry and is STORED (not deflated), `META-INF/container.xml`
 * points at `OEBPS/content.opf`, and every chapter is wired through both the manifest
 * and the spine with an XHTML navigation document (`properties="nav"`) listing them.
 */
object EpubFixtures {

    private data class Chapter(val id: String, val bodyHtml: String)

    /** Placeholder PNG signature + IHDR header bytes; content is irrelevant to parsing. */
    private val defaultImageBytes: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    )

    /** Fake WOFF2 payload (starts with the `wOF2` signature); content is irrelevant. */
    private val defaultFontBytes: ByteArray = "wOF2fake-font-bytes-for-tests".toByteArray()

    /**
     * A spec-valid EPUB 3 with one XHTML document per chapter.
     *
     * @param chapters (chapter id, chapter body HTML). The id is used as the manifest id,
     * the spine idref and the file name (`OEBPS/<id>.xhtml`); the HTML is placed inside
     * the `<body>` of a well-formed XHTML document and used as the nav label.
     */
    fun minimalEpub(
        title: String,
        author: String,
        chapters: List<Pair<String, String>>
    ): ByteArray = buildEpub(
        title = title,
        author = author,
        chapters = chapters.map { (id, bodyHtml) -> Chapter(id, bodyHtml) },
        extraManifestItems = emptyList(),
        extraFiles = emptyMap()
    )

    /**
     * An EPUB with a single chapter that references `OEBPS/images/cover.png` via `<img>`,
     * plus `OEBPS/fonts/body.woff2` declared in the manifest with
     * `properties="embedded-font"` and media type `font/woff2`.
     */
    fun epubWithAssets(
        title: String,
        author: String,
        imageBytes: ByteArray = defaultImageBytes,
        fontBytes: ByteArray = defaultFontBytes
    ): ByteArray = buildEpub(
        title = title,
        author = author,
        chapters = listOf(
            Chapter(
                id = "illustrated-chapter",
                bodyHtml = "<p>An illustrated chapter.</p>" +
                    "<img src=\"images/cover.png\" alt=\"Cover image\"/>"
            )
        ),
        extraManifestItems = listOf(
            "<item id=\"cover-image\" href=\"images/cover.png\" media-type=\"image/png\"/>",
            "<item id=\"body-font\" href=\"fonts/body.woff2\" media-type=\"font/woff2\"" +
                " properties=\"embedded-font\"/>"
        ),
        extraFiles = mapOf(
            "OEBPS/images/cover.png" to imageBytes,
            "OEBPS/fonts/body.woff2" to fontBytes
        )
    )

    /** A plain zip archive that contains no `container.xml` (i.e. not an EPUB). */
    fun zipWithoutContainer(): ByteArray = zipOf(
        "readme.txt" to "This is a plain zip archive, not an EPUB.".toByteArray(),
        "data.bin" to byteArrayOf(1, 2, 3, 4, 5)
    )

    private fun buildEpub(
        title: String,
        author: String,
        chapters: List<Chapter>,
        extraManifestItems: List<String>,
        extraFiles: Map<String, ByteArray>
    ): ByteArray {
        require(chapters.isNotEmpty()) { "an EPUB needs at least one chapter" }

        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opf = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" """ +
                    """unique-identifier="pub-id" xml:lang="en">"""
            )
            appendLine("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
            appendLine("""    <dc:identifier id="pub-id">urn:uuid:miireader-test-fixture-0001</dc:identifier>""")
            appendLine("    <dc:title>${xml(title)}</dc:title>")
            appendLine("    <dc:creator>${xml(author)}</dc:creator>")
            appendLine("""    <dc:language>en</dc:language>""")
            appendLine("""    <meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>""")
            appendLine("""  </metadata>""")
            appendLine("""  <manifest>""")
            appendLine("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            chapters.forEach { chapter ->
                appendLine(
                    """    <item id="${xml(chapter.id)}" href="${xml(chapter.id)}.xhtml" """ +
                        """media-type="application/xhtml+xml"/>"""
                )
            }
            extraManifestItems.forEach { appendLine("    $it") }
            appendLine("""  </manifest>""")
            appendLine("""  <spine>""")
            chapters.forEach { appendLine("""    <itemref idref="${xml(it.id)}"/>""") }
            appendLine("""  </spine>""")
            appendLine("""</package>""")
        }

        val nav = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<html xmlns="http://www.w3.org/1999/xhtml" """ +
                    """xmlns:epub="http://www.idpf.org/2007/ops" lang="en">"""
            )
            appendLine("""<head><title>Table of Contents</title></head>""")
            appendLine("""<body>""")
            appendLine("""<nav epub:type="toc" id="toc">""")
            appendLine("""<ol>""")
            chapters.forEach { chapter ->
                appendLine("""<li><a href="${xml(chapter.id)}.xhtml">${xml(chapter.id)}</a></li>""")
            }
            appendLine("""</ol>""")
            appendLine("""</nav>""")
            appendLine("""</body>""")
            appendLine("""</html>""")
        }

        val entries = mutableListOf(
            "mimetype" to "application/epub+zip".toByteArray(Charsets.US_ASCII),
            "META-INF/container.xml" to containerXml.toByteArray(Charsets.UTF_8),
            "OEBPS/content.opf" to opf.toByteArray(Charsets.UTF_8),
            "OEBPS/nav.xhtml" to nav.toByteArray(Charsets.UTF_8)
        )
        chapters.forEach { chapter ->
            entries += "OEBPS/${chapter.id}.xhtml" to xhtmlDocument(chapter.id, chapter.bodyHtml)
        }
        entries += extraFiles.map { (path, bytes) ->
            // extraFiles are keyed with the full zip path (e.g. OEBPS/images/cover.png).
            if (path.startsWith("OEBPS/")) path to bytes else "OEBPS/$path" to bytes
        }

        return zipOf(*entries.toTypedArray())
    }

    private fun xhtmlDocument(title: String, bodyHtml: String): ByteArray = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head><title>${xml(title)}</title></head>
        <body>${bodyHtml.trim()}</body>
        </html>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /**
     * Writes [entries] as a zip. The `mimetype` entry (if present) is written first and
     * STORED — as the EPUB container spec requires — everything else is deflated.
     */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                if (name == "mimetype") {
                    val crcValue = CRC32().apply { update(data) }.value
                    zip.putNextEntry(
                        ZipEntry(name).apply {
                            method = ZipEntry.STORED
                            size = data.size.toLong()
                            compressedSize = data.size.toLong()
                            crc = crcValue
                        }
                    )
                } else {
                    zip.putNextEntry(ZipEntry(name))
                }
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
