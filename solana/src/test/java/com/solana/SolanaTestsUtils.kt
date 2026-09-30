package com.solana

import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import com.solana.networking.socket.SolanaSocket
import java.net.URL

object SolanaTestsUtils {
    val RPC_URL: String = checkNotNull(System.getenv("SOLANA_RPC_URL")) {
        "SOLANA_RPC_URL is not set; run tests through Gradle's :solana:test task"
    }
}

fun SolanaTestsUtils.generateSolanaConnection() =
    Solana(
        HttpNetworkingRouter(
            RPCEndpoint.custom(
                URL(RPC_URL),
                URL(RPC_URL),
                Network.devnet
            )
        )
    )

fun SolanaTestsUtils.generateSolanaSocket() = SolanaSocket(
    RPCEndpoint.custom(
        URL(RPC_URL),
        URL(RPC_URL),
        Network.devnet
    ),
    enableDebugLogs = true
)