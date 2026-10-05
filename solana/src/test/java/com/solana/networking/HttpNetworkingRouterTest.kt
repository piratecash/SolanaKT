package com.solana.networking

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL

class HttpNetworkingRouterTest {

    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun makeRequest_validBody_returnsDecodedResult() = runBlocking {
        val url = serve(200, """{"jsonrpc":"2.0","result":42,"id":"1"}""", retryAfter = "3")

        val response = makeRequest(url)

        assertEquals(JsonPrimitive(42), response.result)
        assertNull(response.error)
        assertEquals(3L, response.retryAfter)
    }

    @Test
    fun makeRequest_undecodableBody_returnsRpcErrorMinusOne() = runBlocking {
        val url = serve(200, "not json", retryAfter = "5")

        val error = makeRequest(url).error

        assertEquals(-1, error?.code)
        assertEquals(5L, error?.retryAfter)
    }

    @Test
    fun makeRequest_http429_returnsRpcErrorWithBodyAndRetryAfter() = runBlocking {
        val url = serve(429, "Too many requests", retryAfter = "7")

        val error = makeRequest(url).error

        assertEquals(-1, error?.code)
        assertEquals("Too many requests", error?.message)
        assertEquals(7L, error?.retryAfter)
    }

    @Test
    fun makeRequest_connectionRefused_returnsRpcErrorAndNotifiesListener() = runBlocking {
        val url = URL("http://127.0.0.1:${freePort()}")
        val reported = mutableListOf<NetworkRequestError>()

        val error = makeRequest(url) { reported += it }.error

        assertEquals(-1, error?.code)
        assertNull(error?.retryAfter)
        assertEquals(listOf("getHealth"), reported.map { it.method })
    }

    @Test
    fun postJsonRpc_errorStatus_returnsStatusBodyAndRetryAfter() = runBlocking {
        val url = serve(503, "unavailable", retryAfter = "9")

        val response = postJsonRpc(url, "{}")

        assertEquals(HttpRpcResponse(503, "unavailable", 9L), response)
    }

    @Test
    fun postJsonRpc_bodylessErrorStatus_returnsStatusAndRetryAfter() = runBlocking {
        val url = serve(429, "", retryAfter = "60")

        val response = postJsonRpc(url, "{}")

        assertEquals(HttpRpcResponse(429, "", 60L), response)
    }

    @Test
    fun postJsonRpc_sendsBodyAsJsonPost() = runBlocking {
        var received: Triple<String, String?, String>? = null
        val url = serve(200, "ok", retryAfter = null) { method, contentType, body ->
            received = Triple(method, contentType, body)
        }

        val response = postJsonRpc(url, """{"a":1}""")

        assertEquals(HttpRpcResponse(200, "ok", null), response)
        assertEquals(Triple("POST", "application/json; charset=utf-8", """{"a":1}"""), received)
    }

    @Test
    fun decodeRpcResponse_badBody_throwsSerializationException() {
        assertThrows(SerializationException::class.java) {
            decodeRpcResponse("[]", JsonElement.serializer(), null)
        }
    }

    private suspend fun makeRequest(
        url: URL,
        listener: NetworkRequestErrorListener? = null,
    ): RpcResponse<JsonElement> {
        val endpoint = RPCEndpoint.custom(url, url, Network.mainnetBeta)
        return HttpNetworkingRouter(endpoint, listener)
            .makeRequest(RpcRequest(method = "getHealth"), JsonElement.serializer())
    }

    private fun serve(
        status: Int,
        body: String,
        retryAfter: String?,
        onRequest: (method: String, contentType: String?, body: String) -> Unit = { _, _, _ -> },
    ): URL {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/") { exchange ->
            val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
            onRequest(exchange.requestMethod, exchange.requestHeaders.getFirst("Content-Type"), requestBody)
            retryAfter?.let { exchange.responseHeaders.add(HttpNetworkingRouter.RETRY_AFTER_HEADER, it) }
            val bytes = body.toByteArray()
            // -1 means "no body"; 0 would mean chunked.
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return URL("http://127.0.0.1:${httpServer.address.port}/")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
