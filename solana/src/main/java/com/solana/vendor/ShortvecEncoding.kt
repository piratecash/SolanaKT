package com.solana.vendor

object ShortvecEncoding {
    @JvmStatic
    fun encodeLength(len: Int): ByteArray {
        val out = ByteArray(10)
        var remLen = len
        var cursor = 0
        while (true) {
            var elem = remLen and 0x7f
            remLen = remLen shr 7
            if (remLen == 0) {
                out[cursor] = elem.toByte()
                break
            } else {
                elem = elem or 0x80
                out[cursor] = elem.toByte()
                cursor += 1
            }
        }
        val bytes = ByteArray(cursor + 1)
        System.arraycopy(out, 0, bytes, 0, cursor + 1)
        return bytes
    }
}