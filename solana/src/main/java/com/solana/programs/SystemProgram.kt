package com.solana.programs

import com.solana.core.AccountMeta
import com.solana.core.PublicKey
import com.solana.core.TransactionInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

object SystemProgram : Program() {
    val PROGRAM_ID = PublicKey("11111111111111111111111111111111")
    const val PROGRAM_INDEX_CREATE_ACCOUNT = 0
    const val PROGRAM_INDEX_TRANSFER = 2

    private fun uint32ToByteArrayLE(value: Long, out: ByteArray, offset: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        out[offset + 2] = ((value shr 16) and 0xFF).toByte()
        out[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun int64ToByteArrayLE(value: Long, out: ByteArray, offset: Int) {
        ByteBuffer.wrap(out, offset, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
    }

    @JvmStatic
    fun transfer(
        fromPublicKey: PublicKey,
        toPublickKey: PublicKey,
        lamports: Long
    ): TransactionInstruction {
        val keys = ArrayList<AccountMeta>()
        keys.add(AccountMeta(fromPublicKey, true, true))
        keys.add(AccountMeta(toPublickKey, false, true))

        // 4 byte instruction index + 8 bytes lamports
        val data = ByteArray(4 + 8)
        uint32ToByteArrayLE(PROGRAM_INDEX_TRANSFER.toLong(), data, 0)
        int64ToByteArrayLE(lamports, data, 4)
        return createTransactionInstruction(PROGRAM_ID, keys, data)
    }

    fun createAccount(
        fromPublicKey: PublicKey, newAccountPublickey: PublicKey,
        lamports: Long, space: Long, programId: PublicKey
    ): TransactionInstruction {
        val keys = ArrayList<AccountMeta>()
        keys.add(AccountMeta(fromPublicKey, true, true))
        keys.add(AccountMeta(newAccountPublickey, true, true))
        val data = ByteArray(4 + 8 + 8 + 32)
        uint32ToByteArrayLE(PROGRAM_INDEX_CREATE_ACCOUNT.toLong(), data, 0)
        int64ToByteArrayLE(lamports, data, 4)
        int64ToByteArrayLE(space, data, 12)
        System.arraycopy(programId.toByteArray(), 0, data, 20, 32)
        return createTransactionInstruction(PROGRAM_ID, keys, data)
    }
}