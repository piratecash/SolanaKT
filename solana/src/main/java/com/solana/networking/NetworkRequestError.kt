package com.solana.networking

import java.net.InetAddress
import java.net.URL

fun interface NetworkRequestErrorListener {
    fun onRequestError(error: NetworkRequestError)
}

/**
 * Safe, lazy emission: the [buildError] lambda (which may resolve DNS) runs only
 * when a listener is installed, and any Throwable from the listener is swallowed
 * so diagnostics can never break the observed network error path.
 */
internal inline fun NetworkRequestErrorListener?.emitSafely(buildError: () -> NetworkRequestError) {
    val listener = this ?: return
    try {
        listener.onRequestError(buildError())
    } catch (_: Throwable) {
    }
}

data class NetworkRequestError(
    val method: String,
    val url: String,
    val host: String,
    val resolvedIps: List<String>,
    val throwable: Throwable
)

internal fun URL.resolveHostAddresses(): List<String> = try {
    InetAddress.getAllByName(host)
        .mapNotNull { it.hostAddress }
        .distinct()
} catch (_: Throwable) {
    emptyList()
}
