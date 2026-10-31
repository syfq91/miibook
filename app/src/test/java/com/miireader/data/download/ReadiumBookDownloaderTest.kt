package com.miireader.data.download

import com.miireader.data.opds.CatalogCredentials
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the production downloader over loopback: the Authorization header on a
 * protected catalog, and the status-to-message mapping a user actually sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadiumBookDownloaderTest {

    private lateinit var server: HttpServer
    private lateinit var downloader: ReadiumBookDownloader
    private lateinit var target: File

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        downloader = ReadiumBookDownloader()
        target = File.createTempFile("miireader-download", ".epub").also { it.deleteOnExit() }
    }

    @After
    fun tearDown() {
        server.stop(0)
        target.delete()
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun `credentials are sent and the body is written to disk`() = runTest {
        var authorization: String? = null
        val body = "epub bytes".toByteArray()
        server.createContext("/book") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.responseHeaders.add("Content-Length", body.size.toString())
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        var lastTotal = -1L
        downloader.downloadTo(
            url("/book"),
            target,
            CatalogCredentials("reader", "s3cret")
        ) { _, total -> lastTotal = total }

        assertEquals("Basic cmVhZGVyOnMzY3JldA==", authorization)
        assertArrayEquals(body, target.readBytes())
        assertEquals(body.size.toLong(), lastTotal)
    }

    @Test
    fun `a public catalog sends no authorization header`() = runTest {
        var authorization: String? = null
        val body = "epub bytes".toByteArray()
        server.createContext("/open-book") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        downloader.downloadTo(url("/open-book"), target) { _, _ -> }

        assertNull(authorization)
    }

    @Test
    fun `a 401 after credentials were supplied says they were rejected`() = runTest {
        server.createContext("/wrong-password") { exchange ->
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        try {
            downloader.downloadTo(
                url("/wrong-password"),
                target,
                CatalogCredentials("reader", "wrong")
            ) { _, _ -> }
            fail("expected the download to fail")
        } catch (error: IOException) {
            assertTrue(
                "message should say the credentials were rejected, was: ${error.message}",
                error.message.orEmpty().contains("rejected", ignoreCase = true)
            )
        }
    }

    @Test
    fun `a 401 names the missing credentials instead of a bare status`() = runTest {
        server.createContext("/protected") { exchange ->
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        try {
            downloader.downloadTo(url("/protected"), target) { _, _ -> }
            fail("expected the download to fail")
        } catch (error: IOException) {
            assertTrue(
                "message should mention credentials, was: ${error.message}",
                error.message.orEmpty().contains("username", ignoreCase = true) &&
                    error.message.orEmpty().contains("password", ignoreCase = true)
            )
        }
    }
}
