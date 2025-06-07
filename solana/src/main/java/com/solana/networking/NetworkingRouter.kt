package com.solana.networking

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection

interface NetworkingRouter : JsonRpcDriver {
    val endpoint: RPCEndpoint
}

class HttpNetworkingRouter(
    override val endpoint: RPCEndpoint,
) : NetworkingRouter {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun <R> makeRequest(
        request: RpcRequest,
        resultSerializer: KSerializer<R>
    ): RpcResponse<R> =
        suspendCancellableCoroutine { continuation ->
            try {
                val url = endpoint.url
                with(url.openConnection() as HttpURLConnection) {
                    // config
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    requestMethod = "POST"
                    doOutput = true

                    // cancellation
                    continuation.invokeOnCancellation { disconnect() }

                    // send request body
                    outputStream.write(
                        json.encodeToString(RpcRequest.serializer(), request).toByteArray()
                    )
                    outputStream.close()

                    when (responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val responseString =
                                    inputStream.bufferedReader().use { it.readText() }

                                val decoded = json.decodeFromString(
                                    RpcResponse.serializer(resultSerializer), responseString
                                )
                                continuation.resumeWith(
                                    Result.success(decoded)
                                )
                            } catch (ex: SerializationException) {
                                continuation.resume(
                                    RpcResponse<R>(
                                        error = RpcError(
                                            code = -1,
                                            message = ex.message ?: "Unknown error"
                                        )
                                    )
                                ) {}
                            }
                        }

                        else -> {
                            val errorString = errorStream.bufferedReader().use { it.readText() }
                            continuation.resume(
                                RpcResponse<R>(
                                    error = RpcError(
                                        code = -1,
                                        message = errorString
                                    )
                                )
                            ) {}
                        }
                    }
                }
            } catch (ex: Exception) {
                continuation.resume(
                    RpcResponse<R>(
                        error = RpcError(
                            code = -1,
                            message = ex.message ?: "Unknown error"
                        )
                    )
                ) {}
            }
        }
}
