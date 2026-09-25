package com.rodrigos01.aipodcasts

import com.google.firebase.Timestamp
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import java.util.Date
import com.rodrigos01.aipodcasts.data.firestore.FirestoreMappers
import com.rodrigos01.aipodcasts.data.firestore.PodcastFirestoreDataSource
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeRequest
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeResponse
import com.rodrigos01.aipodcasts.data.model.CreatePodcastRequest
import com.rodrigos01.aipodcasts.data.model.CreateSourceRequest
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeCreateInput
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeProgress
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.EpisodeRepository
import com.rodrigos01.aipodcasts.data.repository.PodcastRepository
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Response

class FirestoreMappersAndCQRSLogicTest {

    @Test
    fun testPodcastMappingWithFullData() {
        val data = mapOf(
            "id" to "custom-pod-id",
            "title" to "Tech Talk",
            "description" to "All about software",
            "structure" to "# Intro\n# Main\n# Outro",
            "hosts" to listOf(
                mapOf("id" to "h1", "name" to "Alice", "voice" to "Puck", "persona" to "Engineer"),
                mapOf("id" to "h2", "name" to "Bob", "voice" to "Charon", "persona" to "Architect")
            ),
            "createdAt" to "2026-09-25T12:00:00Z"
        )

        val podcast = FirestoreMappers.mapToPodcast(id = "doc-id-123", data = data)
        assertEquals("custom-pod-id", podcast.id)
        assertEquals("Tech Talk", podcast.title)
        assertEquals("All about software", podcast.description)
        assertEquals("# Intro\n# Main\n# Outro", podcast.structure)
        assertEquals(2, podcast.hosts.size)
        assertEquals("Alice", podcast.hosts[0].name)
        assertEquals("Puck", podcast.hosts[0].voice)
        assertEquals("2026-09-25T12:00:00Z", podcast.createdAt)
    }

    @Test
    fun testPodcastMappingFallbackToDocIdWhenFieldMissing() {
        val data = mapOf(
            "title" to "Indie Devs",
            "description" to "Stories of solo makers",
            "structure" to "Casual chat"
        )

        val podcast = FirestoreMappers.mapToPodcast(id = "doc-fallback-id", data = data)
        assertEquals("doc-fallback-id", podcast.id)
        assertEquals("Indie Devs", podcast.title)
        assertTrue(podcast.hosts.isEmpty())
        assertNull(podcast.createdAt)
    }

    @Test
    fun testSourceMapping() {
        val data = mapOf(
            "id" to "s1",
            "podcastId" to "p1",
            "title" to "Research Paper",
            "contents" to "Extracted text content here",
            "sourceType" to "pdf"
        )

        val source = FirestoreMappers.mapToSource(id = "doc-s1", data = data, defaultPodcastId = "fallback-p1")
        assertEquals("s1", source.id)
        assertEquals("p1", source.podcastId)
        assertEquals("Research Paper", source.title)
        assertEquals("Extracted text content here", source.contents)
        assertEquals("pdf", source.sourceType)

        // Fallback when id and podcastId are absent from data map
        val minimalData = mapOf(
            "title" to "Notes",
            "contents" to "Quick notes"
        )
        val fallbackSource = FirestoreMappers.mapToSource(id = "doc-s2", data = minimalData, defaultPodcastId = "p-fallback")
        assertEquals("doc-s2", fallbackSource.id)
        assertEquals("p-fallback", fallbackSource.podcastId)
        assertNull(fallbackSource.sourceType)
    }

    @Test
    fun testEpisodeMapping() {
        val data = mapOf(
            "id" to "ep1",
            "podcastId" to "pod1",
            "title" to "Episode 1: The Beginning",
            "topics" to "Origins, Motivations",
            "length" to "medium",
            "sourceIds" to listOf("s1", "s2"),
            "participantHostIds" to listOf("h1"),
            "guests" to listOf(
                mapOf("name" to "Guest Scientist", "voice" to "Kore", "persona" to "Expert")
            ),
            "productionNotes" to "Emphasize early challenges",
            "status" to "ready",
            "progress" to mapOf(
                "stage" to "Synthesis",
                "wordCount" to 4500L, // Firestore numbers often come as Long
                "totalWords" to 5000L
            ),
            "transcript" to "Alice: Hello world!",
            "ttsPrompt" to "Speak clearly",
            "error" to null,
            "createdAt" to "2026-09-25T12:00:00Z"
        )

        val episode = FirestoreMappers.mapToEpisode(id = "doc-ep1", data = data)
        assertEquals("ep1", episode.id)
        assertEquals("pod1", episode.podcastId)
        assertEquals("Episode 1: The Beginning", episode.title)
        assertEquals("medium", episode.length)
        assertEquals(listOf("s1", "s2"), episode.sourceIds)
        assertEquals(listOf("h1"), episode.participantHostIds)
        assertEquals(1, episode.guests.size)
        assertEquals("Guest Scientist", episode.guests[0].name)
        assertEquals("ready", episode.status)
        assertNotNull(episode.progress)
        assertEquals(4500, episode.progress?.wordCount)
        assertEquals(5000, episode.progress?.totalWords)
        assertEquals("Alice: Hello world!", episode.transcript)
        assertEquals("2026-09-25T12:00:00Z", episode.createdAt)
    }

    @Test
    fun testEpisodeStatusResponseMapping() {
        val data = mapOf(
            "status" to "streamable",
            "progress" to mapOf(
                "stage" to "chunk_2",
                "wordCount" to 2100,
                "totalWords" to 4200
            ),
            "error" to null,
            "generatedAudioSeconds" to 145.5
        )

        val status = FirestoreMappers.mapToEpisodeStatusResponse(data)
        assertEquals("streamable", status.status)
        assertEquals("chunk_2", status.progress?.stage)
        assertEquals(2100, status.progress?.wordCount)
        assertEquals(4200, status.progress?.totalWords)
        assertNull(status.error)
        assertEquals(145.5, status.generatedAudioSeconds ?: 0.0, 0.001)
    }

    @Test
    fun testPodcastRepositoryRoutesQueriesToFirestoreAndCommandsToApi() {
        runBlocking {
            val mockApi = mock(PodcastApiService::class.java)
            val mockFirestoreSource = mock(PodcastFirestoreDataSource::class.java)

            val testPodcast = Podcast(
                id = "p-100",
                title = "Test Show",
                description = "Desc",
                structure = "Structure"
            )

            // Query: should hit Firestore data source
            `when`(mockFirestoreSource.getPodcasts()).thenReturn(listOf(testPodcast))
            `when`(mockFirestoreSource.getPodcast("p-100")).thenReturn(testPodcast)

            val repo = PodcastRepository(
                api = mockApi,
                firestoreDataSource = mockFirestoreSource
            )

            val podcasts = repo.getPodcasts()
            assertEquals(1, podcasts.size)
            assertEquals("Test Show", podcasts[0].title)
            verify(mockFirestoreSource).getPodcasts()

            val singlePodcast = repo.getPodcast("p-100")
            assertEquals("Test Show", singlePodcast.title)
            verify(mockFirestoreSource).getPodcast("p-100")

            // Command: createPodcast should hit Retrofit API
            val createRequest = CreatePodcastRequest(
                title = "New Show",
                description = "New Desc",
                structure = "New Struct",
                hosts = listOf(Host(name = "Host A", voice = "Puck", persona = "P1"))
            )
            `when`(mockApi.createPodcast(createRequest)).thenReturn(testPodcast)

            val created = repo.createPodcast(
                title = "New Show",
                description = "New Desc",
                structure = "New Struct",
                hosts = listOf(Host(name = "Host A", voice = "Puck", persona = "P1"))
            )
            assertEquals(testPodcast, created)
            verify(mockApi).createPodcast(createRequest)

            // Command: deletePodcast should hit Retrofit API
            `when`(mockApi.deletePodcast("p-100")).thenReturn(Response.success(Unit))
            val deleteSuccess = repo.deletePodcast("p-100")
            assertTrue(deleteSuccess)
            verify(mockApi).deletePodcast("p-100")
        }
    }

    @Test
    fun testSourceRepositoryRoutesQueriesToFirestoreAndCommandsToApi() {
        runBlocking {
            val mockApi = mock(PodcastApiService::class.java)
            val mockFirestoreSource = mock(PodcastFirestoreDataSource::class.java)

            val testSource = Source(id = "s-1", podcastId = "p-1", title = "Doc", contents = "Text")

            `when`(mockFirestoreSource.getSources("p-1")).thenReturn(listOf(testSource))
            `when`(mockFirestoreSource.getSource("p-1", "s-1")).thenReturn(testSource)

            val repo = SourceRepository(
                api = mockApi,
                firestoreDataSource = mockFirestoreSource
            )

            // Query: Firestore
            val sources = repo.getSources("p-1")
            assertEquals(1, sources.size)
            assertEquals("Doc", sources[0].title)
            verify(mockFirestoreSource).getSources("p-1")

            val singleSource = repo.getSource("p-1", "s-1")
            assertEquals("Doc", singleSource.title)
            verify(mockFirestoreSource).getSource("p-1", "s-1")

            // Command: createTextSource -> API
            val createRequest = CreateSourceRequest(title = "New Doc", contents = "Sample")
            `when`(mockApi.createSource("p-1", createRequest)).thenReturn(testSource)

            val created = repo.createTextSource("p-1", "New Doc", "Sample")
            assertEquals(testSource, created)
            verify(mockApi).createSource("p-1", createRequest)

            // Command: deleteSource -> API
            `when`(mockApi.deleteSource("p-1", "s-1")).thenReturn(Response.success(Unit))
            val deleted = repo.deleteSource("p-1", "s-1")
            assertTrue(deleted)
            verify(mockApi).deleteSource("p-1", "s-1")
        }
    }

    @Test
    fun testEpisodeRepositoryRoutesQueriesToFirestoreAndCommandsToApi() {
        runBlocking {
            val mockApi = mock(PodcastApiService::class.java)
            val mockFirestoreSource = mock(PodcastFirestoreDataSource::class.java)

            val testEpisode = Episode(
                id = "ep-1",
                podcastId = "p-1",
                title = "Episode 1",
                topics = "Topics",
                status = "ready"
            )
            val testStatus = EpisodeStatusResponse(status = "ready")

            `when`(mockFirestoreSource.getEpisodes("p-1")).thenReturn(listOf(testEpisode))
            `when`(mockFirestoreSource.getEpisode("p-1", "ep-1")).thenReturn(testEpisode)
            `when`(mockApi.getEpisodeStatus("p-1", "ep-1")).thenReturn(testStatus)

            val repo = EpisodeRepository(
                api = mockApi,
                firestoreDataSource = mockFirestoreSource
            )

            // Query: Firestore
            val episodes = repo.getEpisodes("p-1")
            assertEquals(1, episodes.size)
            verify(mockFirestoreSource).getEpisodes("p-1")

            val episode = repo.getEpisode("p-1", "ep-1")
            assertEquals("Episode 1", episode.title)
            verify(mockFirestoreSource).getEpisode("p-1", "ep-1")

            // Status: Firestore query for status / generatedAudioSeconds
            `when`(mockFirestoreSource.getEpisodeStatus("p-1", "ep-1")).thenReturn(testStatus)
            val status = repo.getEpisodeStatus("p-1", "ep-1")
            assertEquals("ready", status.status)
            verify(mockFirestoreSource).getEpisodeStatus("p-1", "ep-1")

            // Command: createEpisodes -> API
            val input = EpisodeCreateInput(
                title = "New Ep",
                topics = "Top",
                length = "short",
                sourceIds = listOf("s1"),
                participantHostIds = listOf("h1", "h2"),
                productionNotes = "Notes"
            )
            val createRequest = CreateEpisodeRequest(episodes = listOf(input))
            `when`(mockApi.createEpisodes("p-1", createRequest)).thenReturn(
                CreateEpisodeResponse(episodes = listOf(testEpisode))
            )

            val createdList = repo.createEpisodes("p-1", listOf(input))
            assertEquals(1, createdList.size)
            verify(mockApi).createEpisodes("p-1", createRequest)

            // Command: deleteEpisode -> API
            `when`(mockApi.deleteEpisode("p-1", "ep-1")).thenReturn(Response.success(Unit))
            val deleted = repo.deleteEpisode("p-1", "ep-1")
            assertTrue(deleted)
            verify(mockApi).deleteEpisode("p-1", "ep-1")
        }
    }

    @Test
    fun testPodcastFirestoreDataSourceReturnsEmptyWhenUnauthenticated() {
        runBlocking {
            val mockFirestore = mock(com.google.firebase.firestore.FirebaseFirestore::class.java)
            val mockAuth = mock(com.google.firebase.auth.FirebaseAuth::class.java)
            `when`(mockAuth.currentUser).thenReturn(null)

            val dataSource = PodcastFirestoreDataSource(mockFirestore, mockAuth)
            val podcasts = dataSource.getPodcasts()
            assertTrue(podcasts.isEmpty())
        }
    }

    @Test
    fun testRepositoriesExposeReactiveFlows() {
        runBlocking {
            val mockApi = mock(PodcastApiService::class.java)
            val mockFirestoreSource = mock(PodcastFirestoreDataSource::class.java)

            val testPodcast = Podcast(id = "p-1", title = "Show", description = "Desc", structure = "")
            val testEpisode = Episode(id = "e-1", podcastId = "p-1", title = "Ep 1", status = "ready", generatedAudioSeconds = 42.5)
            val testSource = Source(id = "s-1", podcastId = "p-1", title = "Source", contents = "Text")

            `when`(mockFirestoreSource.getPodcastsFlow()).thenReturn(flowOf(listOf(testPodcast)))
            `when`(mockFirestoreSource.getPodcastFlow("p-1")).thenReturn(flowOf(testPodcast))
            `when`(mockFirestoreSource.getEpisodesFlow("p-1")).thenReturn(flowOf(listOf(testEpisode)))
            `when`(mockFirestoreSource.getEpisodeFlow("p-1", "e-1")).thenReturn(flowOf(testEpisode))
            `when`(mockFirestoreSource.getSourcesFlow("p-1")).thenReturn(flowOf(listOf(testSource)))

            val podcastRepo = PodcastRepository(mockApi, mockFirestoreSource)
            val episodeRepo = EpisodeRepository(mockApi, mockFirestoreSource)
            val sourceRepo = SourceRepository(mockApi, mockFirestoreSource)

            assertEquals(listOf(testPodcast), podcastRepo.getPodcastsFlow().first())
            assertEquals(testPodcast, podcastRepo.getPodcastFlow("p-1").first())

            assertEquals(listOf(testEpisode), episodeRepo.getEpisodesFlow("p-1").first())
            val epResult = episodeRepo.getEpisodeFlow("p-1", "e-1").first()
            assertEquals("e-1", epResult?.id)
            assertEquals(42.5, epResult?.generatedAudioSeconds ?: 0.0, 0.001)

            assertEquals(listOf(testSource), sourceRepo.getSourcesFlow("p-1").first())
        }
    }

    @Test
    fun testParseCreatedAtMillis() {
        // Timestamp
        val ts = Timestamp(1727280000L, 0)
        assertEquals(1727280000000L, FirestoreMappers.parseCreatedAtMillis(ts))

        // java.util.Date
        val date = Date(1727280000000L)
        assertEquals(1727280000000L, FirestoreMappers.parseCreatedAtMillis(date))

        // Long Number
        assertEquals(1727280000000L, FirestoreMappers.parseCreatedAtMillis(1727280000000L))

        // Double Number
        assertEquals(1727280000000L, FirestoreMappers.parseCreatedAtMillis(1727280000000.0))

        // ISO-8601 String
        assertEquals(
            java.time.Instant.parse("2026-09-25T16:00:00Z").toEpochMilli(),
            FirestoreMappers.parseCreatedAtMillis("2026-09-25T16:00:00Z")
        )

        // Stringified epoch millis
        assertEquals(1727280000000L, FirestoreMappers.parseCreatedAtMillis("1727280000000"))

        // Null and invalid inputs fallback to 0L
        assertEquals(0L, FirestoreMappers.parseCreatedAtMillis(null))
        assertEquals(0L, FirestoreMappers.parseCreatedAtMillis("not-a-date"))
        assertEquals(0L, FirestoreMappers.parseCreatedAtMillis(emptyMap<String, Any>()))
    }

    @Test
    fun testEpisodesSortedByCreatedAtDescending() {
        val epOld = Episode(
            id = "ep-old",
            podcastId = "p-1",
            title = "Old Episode",
            createdAt = "2026-09-20T10:00:00Z"
        )
        val epMid = Episode(
            id = "ep-mid",
            podcastId = "p-1",
            title = "Mid Episode",
            createdAt = "2026-09-23T10:00:00Z"
        )
        val epNew = Episode(
            id = "ep-new",
            podcastId = "p-1",
            title = "New Episode",
            createdAt = Timestamp(1790400000L, 0) // far future timestamp
        )
        val epNoDateA = Episode(
            id = "ep-nodate-a",
            podcastId = "p-1",
            title = "No Date A",
            createdAt = null
        )
        val epNoDateB = Episode(
            id = "ep-nodate-b",
            podcastId = "p-1",
            title = "No Date B",
            createdAt = null
        )

        val unsorted = listOf(epMid, epNoDateA, epOld, epNew, epNoDateB)
        val sorted = unsorted.sortedWith(
            compareByDescending<Episode> { FirestoreMappers.parseCreatedAtMillis(it.createdAt) }
                .thenByDescending { it.id }
        )

        val sortedIds = sorted.map { it.id }
        assertEquals(
            listOf("ep-new", "ep-mid", "ep-old", "ep-nodate-b", "ep-nodate-a"),
            sortedIds
        )
    }
}
