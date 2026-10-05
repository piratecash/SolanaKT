package com.solana.networking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface NetworkingRouter : JsonRpcDriver {
    val endpoint: RPCEndpoint
}

data class HttpRpcResponse(
    val status: Int,
    val body: String,
    val retryAfter: Long?,
)

private val rpcJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Throws on transport failure; cancelling the caller disconnects the request. */
suspend fun postJsonRpc(url: URL, body: String): HttpRpcResponse =
    suspendCancellableCoroutine { continuation ->
        try {
            with(url.openConnection() as HttpURLConnection) {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                requestMethod = "POST"
                doOutput = true

                continuation.invokeOnCancellation { disconnect() }

                outputStream.write(body.toByteArray())
                outputStream.close()

                val status = responseCode
                // A bodyless error response has no error stream.
                val stream = if (status == HttpURLConnection.HTTP_OK) inputStream else errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val retryAfter = getHeaderField(HttpNetworkingRouter.RETRY_AFTER_HEADER)?.toLongOrNull()
                continuation.resume(HttpRpcResponse(status, text, retryAfter))
            }
        } catch (ex: Exception) {
            continuation.resumeWithException(ex)
        }
    }

/** Throws when [body] does not decode: [SerializationException], or any exception of a custom serializer. */
fun <R> decodeRpcResponse(
    body: String,
    resultSerializer: KSerializer<R>,
    retryAfter: Long?,
): RpcResponse<R> =
    rpcJson.decodeFromString(RpcResponse.serializer(resultSerializer), body)
        .also { it.retryAfter = retryAfter }

class HttpNetworkingRouter(
    override val endpoint: RPCEndpoint,
    private val errorListener: NetworkRequestErrorListener? = null,
) : NetworkingRouter {

    companion object {
        const val RETRY_AFTER_HEADER = "Retry-After"
        private const val UNKNOWN_ERROR = "Unknown error"
    }

    override suspend fun <R> makeRequest(
        request: RpcRequest,
        resultSerializer: KSerializer<R>
    ): RpcResponse<R> {
        val url = endpoint.url
        return try {
            val response = postJsonRpc(url, rpcJson.encodeToString(RpcRequest.serializer(), request))
            if (response.status == HttpURLConnection.HTTP_OK) {
                decodeOrFailure(response, resultSerializer)
            } else {
                failure(response.body, response.retryAfter)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            reportError(request, url, ex)
            failure(ex.message ?: UNKNOWN_ERROR, retryAfter = null)
        }
    }

    private fun <R> decodeOrFailure(
        response: HttpRpcResponse,
        resultSerializer: KSerializer<R>
    ): RpcResponse<R> = try {
        decodeRpcResponse(response.body, resultSerializer, response.retryAfter)
    } catch (ex: SerializationException) {
        failure(ex.message ?: UNKNOWN_ERROR, response.retryAfter)
    }

    private fun <R> failure(message: String, retryAfter: Long?) = RpcResponse<R>(
        error = RpcError(code = -1, message = message).also { it.retryAfter = retryAfter }
    )

    private fun reportError(request: RpcRequest, url: URL, ex: Exception) {
        errorListener.emitSafely {
            NetworkRequestError(
                method = request.method,
                url = url.toString(),
                host = url.host,
                resolvedIps = url.resolveHostAddresses(),
                throwable = ex
            )
        }
    }
}
