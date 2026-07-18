package com.solana.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmitSafelyTest {

    private fun sampleError() = NetworkRequestError(
        method = "getAccountInfo",
        url = "https://example.test/x",
        host = "example.test",
        resolvedIps = emptyList(),
        throwable = RuntimeException("boom")
    )

    @Test
    fun emitSafely_throwingListener_doesNotPropagate() {
        val listener = NetworkRequestErrorListener { throw RuntimeException("listener boom") }

        // Must not throw — a throwing listener must not interrupt the continuation resume.
        listener.emitSafely { sampleError() }
    }

    @Test
    fun emitSafely_nullListener_doesNotBuildError() {
        var built = false
        val listener: NetworkRequestErrorListener? = null

        listener.emitSafely {
            built = true
            sampleError()
        }

        assertFalse(built)
    }

    @Test
    fun emitSafely_workingListener_receivesBuiltError() {
        var received: NetworkRequestError? = null
        val listener = NetworkRequestErrorListener { received = it }

        listener.emitSafely { sampleError() }

        assertEquals("getAccountInfo", received?.method)
    }
}
