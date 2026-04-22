package com.solana.networking

import java.net.InetAddress
import java.net.URL

fun interface NetworkRequestErrorListener {
    fun onRequestError(error: NetworkRequestError)
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
