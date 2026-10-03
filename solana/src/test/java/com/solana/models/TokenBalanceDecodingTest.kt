package com.solana.models

import com.solana.api.Meta
import com.solana.api.TokenBalance
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TokenBalanceDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeMeta_tokenBalanceWithOwner_returnsOwnerWallet() {
        val metaJson = """
            {
                "err": null,
                "fee": 5000,
                "innerInstructions": [],
                "preTokenBalances": [],
                "postTokenBalances": [
                    {
                        "accountIndex": 1,
                        "mint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                        "owner": "OWNER_WALLET",
                        "programId": "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                        "uiTokenAmount": {
                            "amount": "1493856",
                            "decimals": 6,
                            "uiAmount": 1.493856,
                            "uiAmountString": "1.493856"
                        }
                    }
                ],
                "postBalances": [],
                "preBalances": [],
                "status": { "Ok": null }
            }
        """.trimIndent()

        val meta = json.decodeFromString(Meta.serializer(), metaJson)

        assertEquals("OWNER_WALLET", meta.postTokenBalances[0].owner)
    }

    @Test
    fun decodeTokenBalance_withoutOwner_returnsNullOwner() {
        val tokenBalanceJson = """
            {
                "accountIndex": 1,
                "mint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                "uiTokenAmount": {
                    "amount": "1493856",
                    "decimals": 6,
                    "uiAmount": 1.493856,
                    "uiAmountString": "1.493856"
                }
            }
        """.trimIndent()

        val tokenBalance = json.decodeFromString(TokenBalance.serializer(), tokenBalanceJson)

        assertNull(tokenBalance.owner)
    }
}
