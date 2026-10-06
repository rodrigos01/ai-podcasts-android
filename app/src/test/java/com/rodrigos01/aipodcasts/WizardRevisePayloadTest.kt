package com.rodrigos01.aipodcasts

import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.api.parseErrorBody
import com.rodrigos01.aipodcasts.data.firestore.PodcastFirestoreDataSource
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.data.model.EpisodeSuggestion
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardSuggestionsResponse
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class WizardRevisePayloadTest {

    // The API validates productionNotes as min(1).optional(), so "" is a 400 while an omitted field is fine.
    @Test
    fun reviseOmitsBlankProductionNotes() = runBlocking {
        // A suspend function that returns without suspending can be faked with a plain Answer.
        var sentRequest: EpisodeWizardReviseRequest? = null
        val api = mock(PodcastApiService::class.java) { invocation ->
            if (invocation.method.name == "reviseEpisodeSuggestions") {
                sentRequest = invocation.arguments[1] as EpisodeWizardReviseRequest
                EpisodeWizardSuggestionsResponse(suggestions = emptyList())
            } else null
        }
        val repo = EpisodeRepository(api, mock(PodcastFirestoreDataSource::class.java))

        val drafts = listOf(
            EpisodeDraft(title = "A", topics = "t", productionNotes = null),
            EpisodeDraft(title = "B", topics = "t", productionNotes = "   "),
            EpisodeDraft(title = "C", topics = "t", productionNotes = " keep ")
        )
        repo.reviseEpisodeSuggestions("p1", listOf(EpisodeSuggestion(drafts)), "short", 0, null, "more fun")

        val sent = sentRequest!!.suggestions.single().episodes
        assertNull(sent[0].productionNotes)
        assertNull(sent[1].productionNotes)
        assertEquals("keep", sent[2].productionNotes)
    }

    @Test
    fun validationErrorBodyIsReadable() {
        val body = """{"error":"ValidationError","message":"Request failed validation","details":[
            {"path":["suggestions",0,"episodes",0,"productionNotes"],"message":"Too small"}]}"""
        assertEquals(
            "Request failed validation — suggestions.0.episodes.0.productionNotes: Too small",
            parseErrorBody(body)
        )
        assertNull(parseErrorBody("not json"))
        assertNull(parseErrorBody(null))
    }
}
