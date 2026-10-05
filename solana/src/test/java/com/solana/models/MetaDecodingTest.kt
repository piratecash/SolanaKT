package com.solana.models

import com.solana.api.Meta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeMeta_errAsString_keepsString() {
        val meta = decode(metaJson(err = "\"AccountInUse\""))

        assertEquals(JsonPrimitive("AccountInUse"), meta.err)
    }

    @Test
    fun decodeMeta_errAsObject_keepsObject() {
        val meta = decode(metaJson(err = """{"InstructionError": [0, "Custom"]}"""))

        val err = meta.err
        assertTrue(err is JsonObject)
        assertEquals(2, (err as JsonObject).getValue("InstructionError").jsonArray.size)
    }

    @Test
    fun decodeMeta_listFieldsNull_decodesAsNull() {
        val meta = decode(
            metaJson(
                listFields = """
                    "innerInstructions": null,
                    "preTokenBalances": null,
                    "postTokenBalances": null,
                """
            )
        )

        assertNull(meta.innerInstructions)
        assertNull(meta.preTokenBalances)
        assertNull(meta.postTokenBalances)
    }

    @Test
    fun decodeMeta_listFieldsAbsent_decodesAsNull() {
        val meta = decode(metaJson())

        assertNull(meta.innerInstructions)
        assertNull(meta.preTokenBalances)
        assertNull(meta.postTokenBalances)
    }

    private fun decode(metaJson: String) = json.decodeFromString(Meta.serializer(), metaJson)

    private fun metaJson(err: String = "null", listFields: String = "") = """
        {
            "err": $err,
            "fee": 5000,
            $listFields
            "postBalances": [],
            "preBalances": [],
            "status": { "Ok": null }
        }
    """
}
