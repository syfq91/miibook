package com.miireader.data.opds

import com.miireader.core.error.AppError
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6: network failures against a real (local) HTTP server.
 *
 * `ReadiumOpdsHttpClient` is the only production code that touches the network. It is
 * exercised here over loopback so status-code mapping and connection failures are
 * verified with a genuine socket rather than a fake: the user-facing wording of every
 * failure mode is part of the contract (Definition of Done: "network errors produce
 * useful messages").
 *
 * Timeouts are not simulated: Readium's default request timeout is far longer than a
 * unit test should wait, so that path stays covered only by the `HttpError.Timeout`
 * mapping in code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadiumOpdsHttpClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: ReadiumOpdsHttpClient

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        client = ReadiumOpdsHttpClient()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun serve(status: Int, path: String = "/feed", body: String, contentType: String = "application/atom+xml") {
        server.createContext(path) { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private suspend fun networkError(block: suspend () -> Unit): AppError.NetworkError {
        try {
            block()
        } catch (error: CatalogException) {
            val appError = error.error
            assertTrue(
                "expected NetworkError but was $appError",
                appError is AppError.NetworkError
            )
            return appError as AppError.NetworkError
        }
        fail("expected a CatalogException but the call succeeded")
        throw AssertionError("unreachable")
    }

    // -------------------------------------------------------------------- tests

    @Test
    fun `a successful response returns the body unchanged`() = runTest {
        val payload = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>"""
        serve(status = 200, body = payload)

        val bytes = client.get(url("/feed"))

        assertEquals(payload, bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `a redirect to another path is followed`() = runTest {
        val payload = "<feed>redirected</feed>"
        serve(status = 200, path = "/target", body = payload)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        val bytes = client.get(url("/redirect"))

        assertEquals(payload, bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `a 404 reports the status to the user`() = runTest {
        serve(status = 404, body = "Not Found", contentType = "text/html")

        val error = networkError { client.get(url("/feed")) }

        assertTrue(
            "message should name the HTTP status, was: ${error.message}",
            error.message.contains("404")
        )
    }

    @Test
    fun `a 500 reports the status to the user`() = runTest {
        serve(status = 500, body = "Boom", contentType = "text/plain")

        val error = networkError { client.get(url("/feed")) }

        assertTrue(
            "message should name the HTTP status, was: ${error.message}",
            error.message.contains("500")
        )
    }

    @Test
    fun `an html error page served with 200 is not silently accepted as a feed`() = runTest {
        // The client hands the body to the parser, which must reject it — a 200 status
        // does not make arbitrary bytes a catalog.
        serve(
            status = 200,
            body = "<html><body>login required</body></html>",
            contentType = "text/html"
        )

        val body = client.get(url("/feed"))

        val result = OpdsFeedParser().parse(body, url("/feed"))
        assertTrue(
            "an HTML page must not parse as a catalog, got $result",
            result is OpdsFeedParser.Result.Failure
        )
    }

    @Test
    fun `an unreachable catalog reports a connection error`() = runTest {
        // Bind and immediately release a port so nothing is listening on it.
        val deadPort = ServerSocket(0).use { it.localPort }

        val error = networkError { client.get("http://127.0.0.1:$deadPort/feed") }

        assertNotNull(error.message)
        assertTrue(
            "message should suggest the connection is the problem, was: ${error.message}",
            error.message.contains("reach", ignoreCase = true) ||
                error.message.contains("connection", ignoreCase = true)
        )
    }

    @Test
    fun `an address without a scheme is rejected before any request`() = runTest {
        val error = networkError { client.get("not a valid address") }

        assertTrue(
            "message should say the address is wrong, was: ${error.message}",
            error.message.contains("valid address", ignoreCase = true)
        )
    }
}
