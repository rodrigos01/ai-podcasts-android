# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Android (Kotlin, Jetpack Compose) client for the "AI Podcasts" backend — an app that lets a user generate fictional-host podcast episodes (and audiobook chapters) from source material via an LLM/TTS pipeline, then stream and listen to them. This repo is the **client only**; the backend is a separate Node/TypeScript service (its API is documented in [docs/backend_docs.md](docs/backend_docs.md) and is the source of truth for request/response shapes, auth, and the audio-streaming contract).

- `applicationId` / package root: `com.rodrigos01.aipodcasts`
- `minSdk` 26, `targetSdk`/`compileSdk` 35, Java/Kotlin target 21
- Default backend base URL is hardcoded in `ApiClient.DEFAULT_BASE_URL` (Cloud Run); it can be overridden at runtime (see Settings screen / `SettingsRepository`).

## Commands

All commands run from the repo root. On Windows use `gradlew.bat`; `gradlew` (no extension) works from Git Bash.

```bash
# Build debug APK
./gradlew assembleDebug

# Run all JVM unit tests (app/src/test)
./gradlew testDebugUnitTest

# Run a single test class or method
./gradlew testDebugUnitTest --tests "com.rodrigos01.aipodcasts.PodcastModelAndLogicTest"
./gradlew testDebugUnitTest --tests "com.rodrigos01.aipodcasts.PodcastModelAndLogicTest.testAudioStreamUrlBuilder"

# Lint
./gradlew lint

# Install debug build on a connected device/emulator
./gradlew installDebug
```

There are currently no instrumented (`androidTest`) tests, only JVM unit tests under `app/src/test`. `unitTests.isReturnDefaultValues = true` is set, so unmocked Android SDK calls return defaults instead of throwing in unit tests.

Two files required for the app to build/sign but not committed in a template sense (already present locally, treat as secrets): `app/google-services.json` (Firebase config) and `app/ai-audio-book-*.json` (Firebase App Distribution service credentials, referenced from `app/build.gradle.kts`'s release `firebaseAppDistribution` block).

## Architecture

### Layering

`ui/` (Compose screens + per-screen ViewModels) → `data/repository/` → CQRS-lite: queries read directly from Firestore (`podcasts` named database via `data/firestore/PodcastFirestoreDataSource`), while commands/mutations/wizards go through `data/api/` (Retrofit) or `data/drive/` (Google Drive), backed by `data/model/` (Moshi data classes matching document and API shapes). There is no DI framework — singletons are wired by hand in `AIPodcastsApplication.onCreate()` and accessed via `AIPodcastsApplication.instance.<repository>` from ViewModels/Composables.

- **`data/firestore/*`**: `PodcastFirestoreDataSource` connects to the named Firestore database (`"podcasts"`), performing direct reads for podcasts (`podcasts/{podcastId}`), sources (`podcasts/{podcastId}/sources/{sourceId}`), and episodes (`podcasts/{podcastId}/episodes/{episodeId}`). `FirestoreMappers` converts Firestore `DocumentSnapshot`s into domain models, handling document-id fallback and type conversions.
- **`data/api/ApiClient`**: single Retrofit/OkHttp/Moshi instance. Base URL is mutable at runtime (`currentBaseUrl`) via an interceptor that rewrites the request URL's scheme/host/port — this is how the Settings screen lets a user point the app at a different backend without rebuilding. `AuthInterceptor` attaches the Firebase ID token to every request. `buildAudioStreamUrl()` constructs the direct streaming URL (token as query param, optional `t=<seconds>` resume offset) — this URL is handed straight to ExoPlayer, not called through Retrofit, per the backend's streaming contract.
- **`data/repository/*`**: one repository per backend resource area (`PodcastRepository`, `EpisodeRepository`, `SourceRepository`, `AuthRepository`, `SettingsRepository` for the base-URL override, `PlaybackPositionRepository` for per-episode resume position in `SharedPreferences`). Implements CQRS-lite by delegating queries to `PodcastFirestoreDataSource` and commands to `PodcastApiService`.
- **`ui/screens/<feature>/`**: each feature has a `*Screen.kt` (Compose) + `*ViewModel.kt` pair. Navigation graph and routes are centralized in `ui/navigation/NavGraph.kt` and `Screen.kt` — check there to see how screens are wired together and what arguments they take, rather than inferring from a single screen file.

### Audio playback pipeline (Media3/ExoPlayer)

Two files under `player/`:

- **`PodcastPlaybackService`** (`MediaSessionService`): owns the actual `ExoPlayer` + `MediaSession`, alive independent of any Activity (foreground playback, lock-screen controls). Builds its `DefaultMediaSourceFactory` on a plain `DefaultHttpDataSource.Factory` — the `/audio/stream` endpoint now serves a single, standard seekable Ogg/Opus file (the backend used to stream chained per-chunk Ogg bitstreams, which required a client-side de-chaining `DataSource` shim; that workaround was removed once the backend started serving one continuous stream, see git history for `ChainedOggDataSource` if resurrecting context on this is ever needed).
- **`PodcastAudioController`**: the app-facing façade used by ViewModels/Composables. Connects to the service via a `MediaController` (Media3's client/service split), exposes `StateFlow`s (`isPlaying`, `currentPositionMs`, `durationMs`, `playbackSpeed`, `currentEpisode`, etc.), and drives resume-from-saved-position by building the stream URL with `?t=<seconds>` and tracking a `streamStartOffsetMs` so displayed position/seek targets stay correct relative to where the underlying stream actually starts. Persists playback position to `PlaybackPositionRepository` on play/pause/progress-tick/track-end.

### Auth & Google Drive import

`AuthRepository` wraps Firebase Auth (Google sign-in via Credential Manager / `googleid`); the resulting ID token is what `AuthInterceptor` forwards to the backend and what `ApiClient.buildAudioStreamUrl` embeds for the audio element. `data/drive/GoogleDriveHelper` + `util/FileUtils` support importing a source document directly from Google Drive/Docs (extracting a file ID from various URI forms, then downloading content) as an alternative to uploading a local file, feeding into `SourceRepository`.
