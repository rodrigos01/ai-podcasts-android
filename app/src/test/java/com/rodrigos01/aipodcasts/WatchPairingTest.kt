package com.rodrigos01.aipodcasts

import com.rodrigos01.aipodcasts.watch.PairTokenMessage
import com.rodrigos01.aipodcasts.watch.PairingApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPairingTest {

    @Test
    fun parseCustomToken_readsTokenFromPairResponse() {
        assertEquals("abc.def", PairingApi.parseCustomToken("""{"customToken":"abc.def"}"""))
    }

    @Test
    fun parseCustomToken_failsWhenTokenMissingOrBlank() {
        assertTrue(runCatching { PairingApi.parseCustomToken("{}") }.isFailure)
        assertTrue(runCatching { PairingApi.parseCustomToken("""{"customToken":""}""") }.isFailure)
    }

    @Test
    fun pairTokenMessage_encodesOnlyThePresentField() {
        val ok = JSONObject(PairTokenMessage(customToken = "tok").encode().decodeToString())
        assertEquals("tok", ok.getString("customToken"))
        assertFalse(ok.has("error"))

        val failed = JSONObject(PairTokenMessage(error = "Not signed in on phone").encode().decodeToString())
        assertEquals("Not signed in on phone", failed.getString("error"))
        assertFalse(failed.has("customToken"))
    }

    @Test
    fun pairingEndpoint_isTheAudioBookPairRoute() {
        assertEquals("/api/auth/pair", PairingApi.PAIR_PATH)
        assertTrue(PairingApi.PAIRING_BASE_URL.startsWith("https://ai-audio-book-api-"))
    }
}
