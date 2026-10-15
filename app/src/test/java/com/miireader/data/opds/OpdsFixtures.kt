package com.miireader.data.opds

/**
 * Hand-written OPDS fixtures.
 *
 * These are deliberately minimal but structurally real: they follow the OPDS 1 Atom and
 * OPDS 2 JSON shapes that Readium's `OPDS1Parser`/`OPDS2Parser` expect, so the parser
 * tests exercise the actual Readium code path rather than a stub.
 *
 * Note that OPDS 1 expresses sub-sections as *entries without acquisition links* — a feed
 * level `<link>` is a self/search descriptor and never becomes navigation. The fixture
 * below includes one of each so that distinction stays covered.
 *
 * Relative hrefs are included on purpose — resolving them against the feed's base URL is
 * the part of `mapFeed` most likely to silently break.
 */
object OpdsFixtures {

    val BASE_URL = "https://catalog.example.org/opds/root"

    /** An OPDS 1 Atom catalog: one sub-section entry and one acquisition entry. */
    val OPDS1_ATOM: ByteArray = ("""
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:opds="http://opds-spec.org/2010/catalog">
          <id>urn:uuid:root-feed</id>
          <title>Example Catalog</title>
          <updated>2024-01-01T00:00:00Z</updated>
          <link rel="self" href="/opds/root" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
          <entry>
            <id>urn:uuid:new-releases</id>
            <title>New Releases</title>
            <updated>2024-01-01T00:00:00Z</updated>
            <link rel="http://opds-spec.org/subsection" href="/opds/new" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
          </entry>
          <entry>
            <id>urn:uuid:moby-dick</id>
            <title>Moby Dick</title>
            <updated>2024-01-01T00:00:00Z</updated>
            <author><name>Herman Melville</name></author>
            <summary>A man vs. whale saga.</summary>
            <link rel="http://opds-spec.org/acquisition" href="/download/moby-dick.epub" type="application/epub+zip"/>
            <link rel="http://opds-spec.org/acquisition" href="/download/moby-dick.pdf" type="application/pdf"/>
          </entry>
        </feed>
    """).trimIndent().toByteArray()

    /** An OPDS 2 JSON catalog with a navigation link and one publication. */
    val OPDS2_JSON: ByteArray = ("""
        {
          "metadata": {"title": "Example Catalog 2"},
          "links": [
            {"rel": "self", "href": "/opds2/root", "type": "application/opds+json"}
          ],
          "navigation": [
            {"rel": "http://opds-spec.org/sort/new", "href": "/opds2/new", "type": "application/opds+json", "title": "New Releases"}
          ],
          "publications": [
            {
              "metadata": {
                "title": "Moby Dick",
                "author": [{"name": "Herman Melville"}],
                "description": "A man vs. whale saga."
              },
              "links": [
                {"rel": "http://opds-spec.org/acquisition", "href": "/download/moby-dick.epub", "type": "application/epub+zip"}
              ]
            }
          ]
        }
    """).trimIndent().toByteArray()

    /** Not a feed in either format. */
    val NOT_A_FEED: ByteArray = "<html><body>404</body></html>".trimIndent().toByteArray()

    /** An OPDS 1 entry document (single publication) rather than a feed. */
    val OPDS1_ENTRY: ByteArray = ("""
        <?xml version="1.0" encoding="UTF-8"?>
        <entry xmlns="http://www.w3.org/2005/Atom"
               xmlns:opds="http://opds-spec.org/2010/catalog">
          <id>urn:uuid:moby-dick</id>
          <title>Moby Dick</title>
          <updated>2024-01-01T00:00:00Z</updated>
          <author><name>Herman Melville</name></author>
          <link rel="http://opds-spec.org/acquisition" href="/download/moby-dick.epub" type="application/epub+zip"/>
        </entry>
    """).trimIndent().toByteArray()
}
