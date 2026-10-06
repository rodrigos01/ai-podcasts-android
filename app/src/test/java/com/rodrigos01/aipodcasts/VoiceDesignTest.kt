package com.rodrigos01.aipodcasts

import com.rodrigos01.aipodcasts.data.firestore.FirestoreMappers
import com.rodrigos01.aipodcasts.data.model.DesignedVoice
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceDesignTest {

    private fun voices(vararg ids: String) = ids.map { DesignedVoice(it, "https://api/voices/$it/preview") }

    @Test
    fun mappersCarryVoiceFields() {
        val podcast = FirestoreMappers.mapToPodcast(
            "p1",
            mapOf(
                "hosts" to listOf(
                    mapOf("id" to "h1", "name" to "Maya", "voice" to "warm", "persona" to "p",
                        "voicePrompt" to "Name: Maya", "resolvedVoiceId" to "voice_1")
                )
            )
        )
        assertEquals("Name: Maya", podcast.hosts[0].voicePrompt)
        assertEquals("voice_1", podcast.hosts[0].resolvedVoiceId)

        val episode = FirestoreMappers.mapToEpisode(
            "e1",
            mapOf("guests" to listOf(mapOf("id" to "g1", "name" to "Sam", "voice" to "v", "persona" to "p")))
        )
        assertEquals("g1", episode.guests[0].id)
        assertNull(episode.guests[0].resolvedVoiceId)
    }

    @Test
    fun regenerateRedesignsFromTheCurrentPromptAndConfirmReportsThatPrompt() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val calls = mutableListOf<Pair<String, String>>()
        var batch = 0
        val controller = VoiceDesignController(scope, { session, prompt ->
            calls += session to prompt
            batch++
            voices("a$batch", "b$batch", "c$batch")
        })
        var picked: Pair<String, String>? = null
        controller.open("session-1", "Maya", "first prompt") { id, prompt -> picked = id to prompt }

        controller.design()
        assertEquals(listOf("a1", "b1", "c1"), controller.uiState.value!!.candidates.map { it.voiceId })

        controller.select("b1")
        controller.onPromptChanged("  edited prompt ")
        controller.design() // regenerate

        val state = controller.uiState.value!!
        assertEquals(listOf("session-1" to "first prompt", "session-1" to "edited prompt"), calls)
        assertEquals(listOf("a2", "b2", "c2"), state.candidates.map { it.voiceId })
        assertNull("selection is cleared when candidates are replaced", state.selectedVoiceId)

        controller.confirm() // nothing selected: no-op
        assertNull(picked)
        controller.select("c2")
        controller.confirm()
        assertEquals("c2" to "edited prompt", picked)
        assertNull(controller.uiState.value)
    }

    @Test
    fun failedDesignKeepsPreviousCandidatesAndReportsTheError() {
        val scope = TestScope(UnconfinedTestDispatcher())
        var fail = false
        val controller = VoiceDesignController(scope, { _, _ ->
            if (fail) throw IllegalStateException("Voice design failed, try again") else voices("a")
        })
        controller.open("s", "Maya", "prompt") { _, _ -> }
        controller.design()
        fail = true
        controller.design()

        val state = controller.uiState.value!!
        assertFalse(state.isDesigning)
        assertTrue(state.candidates.isNotEmpty())
        assertEquals("Voice design failed, try again", state.errorMessage)
    }

    @Test
    fun blankPromptIsNotSent() {
        val scope = TestScope(UnconfinedTestDispatcher())
        var called = false
        val controller = VoiceDesignController(scope, { _, _ -> called = true; voices("a") })
        controller.open("s", "Maya", "   ") { _, _ -> }
        controller.design()
        assertFalse(called)
    }

    @Test
    fun currentVoiceIsOfferedPreselectedAndKeepingItIsANoOp() {
        val scope = TestScope(UnconfinedTestDispatcher())
        val controller = VoiceDesignController(
            scope,
            { _, _ -> voices("n1", "n2", "n3") },
            previewUrlFor = { "https://api/voices/$it/preview" }
        )
        var picked: Pair<String, String>? = null
        controller.open("episode-1", "Sam", "prompt", currentVoiceId = "voice_cur") { id, p -> picked = id to p }

        var state = controller.uiState.value!!
        assertEquals("voice_cur", state.currentVoice?.voiceId)
        assertEquals("https://api/voices/voice_cur/preview", state.currentVoice?.previewUrl)
        assertEquals("voice_cur", state.selectedVoiceId)

        controller.design()
        state = controller.uiState.value!!
        assertEquals("regenerating keeps the current voice selected", "voice_cur", state.selectedVoiceId)

        controller.select("n2")
        controller.select("voice_cur")
        controller.confirm()
        assertNull("keeping the current voice must not report a pick", picked)
        assertNull(controller.uiState.value)

        controller.open("episode-1", "Sam", "prompt", currentVoiceId = "voice_cur") { id, p -> picked = id to p }
        controller.design()
        controller.select("n3")
        controller.confirm()
        assertEquals("n3" to "prompt", picked)
    }
}
