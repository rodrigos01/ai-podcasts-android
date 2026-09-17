# AI Podcast API

A REST API with two products built on the same LLM/TTS backend: **Podcasts**, where fictional hosts and guests improvise a real conversation from a prompt, and **Audiobooks**, where a director LLM turns a book's own text into a fully-cast, faithfully-narrated performance. Both synthesize their scripts to streamable audio.

Product behavior is fully described in [specs.md](specs.md) (Podcasts) and [audiobook-specs.md](audiobook-specs.md) (Audiobooks); this document covers how to run the service and how to call it.

## Table of contents

- [Tech stack](#tech-stack)
- [Setup](#setup)
- [Authentication](#authentication)
- **[Podcasts](#podcasts)**
    - [How it works, in short](#how-it-works-in-short)
    - [API reference](#api-reference)
    - [Data model](#data-model)
- **[Audiobooks](#audiobooks)**
    - [How it works, in short](#how-it-works-in-short-1)
    - [API reference](#api-reference-1)
    - [Data model](#data-model-1)
- [Known limitations](#known-limitations)

## Podcasts

### How it works, in short

1. **Create a podcast** via a 3-option wizard: describe what you want, pick (and iteratively revise) one of three generated concepts — title, description, structure, and fictional hosts with voices and personas.
2. **Upload source material** (text or PDF) for a podcast — background reading the hosts and guests will actually reference.
3. **Create an episode** via a single-draft wizard: point it at some sources, get a draft (title, topics, production notes, an optional guest), revise it, confirm it.
4. Confirming an episode kicks off **generation in the background**: two independent LLM agents (2 hosts, or 1 host + 1 guest — never more, never fewer) hold a real conversation turn by turn, a "producer" LLM turns the transcript into a TTS direction sheet, and the transcript is chunked for synthesis. Poll a status endpoint until it's `ready`.
5. **Stream the audio** from a single endpoint that behaves like a normal seekable audio file once fully generated, and like a live/growing stream (playable, but not seekable ahead of what exists yet) while still being synthesized.

## Tech stack

- Node 20+, TypeScript, Express
- Firebase Firestore (two **named databases**, not the default one — `podcasts` and `audiobooks`, see Setup) + Firebase Storage, via `firebase-admin`
- Google Gemini `gemini-3.8-flash` for text (via `@google/genai`) and `gemini-3.1-flash-tts-preview` for speech (via `@google-cloud/text-to-speech`'s `streamingSynthesize`, for real incremental audio streaming at a much cheaper cost basis than the same model through `@google/genai`'s Interactions API)
- zod for request validation and for validating every piece of LLM-generated JSON before it's trusted
- vitest for unit tests

## Setup

### 1. Firebase project

You need a Firebase/GCP project with:
- **Two Firestore databases** — Podcasts and Audiobooks each use their own *named* database (`podcasts` and `audiobooks` by default, neither is `(default)`), since their data shapes are unrelated. Create both if they don't exist:
  ```bash
  firebase firestore:databases:create podcasts --location=<your-region>
  firebase firestore:databases:create audiobooks --location=<your-region>
  ```
- A **Storage bucket**, registered with Firebase (a plain GCS bucket won't show up in the Firebase console's Storage tab or be usable here until it's linked) — shared by both products, path-prefixed per product/resource:
  ```bash
  gcloud services enable firebasestorage.googleapis.com --project <your-project>
  gcloud storage buckets create gs://<your-bucket> --project <your-project> --location=<region> --uniform-bucket-level-access
  # then link it to Firebase:
  curl -X POST -H "Authorization: Bearer $(gcloud auth application-default print-access-token)" \
    "https://firebasestorage.googleapis.com/v1beta/projects/<your-project>/buckets/<your-bucket>:addFirebase"
  ```
- A **service account key** (JSON) with Firestore + Storage access, downloaded locally.

### 2. Environment

Create a `.env` file (never commit it):

```bash
GEMINI_API_KEY=...
FIREBASE_PROJECT_ID=...
FIREBASE_SERVICE_ACCOUNT_PATH=./path-to-service-account.json
FIREBASE_STORAGE_BUCKET=your-bucket-name
FIRESTORE_DATABASE_ID=podcasts
AUDIOBOOKS_FIRESTORE_DATABASE_ID=audiobooks
PORT=3000
```

### 3. Install & run

```bash
npm install
npm run dev      # tsx watch, restarts on file changes
```

```bash
npm run build && npm start   # compiled/production
npm test                     # unit tests — fast, no network calls
npm run smoke                 # scripts/smoke-test.ts — drives the full real Podcast HTTP flow end to end
npx tsx scripts/smoke-test-audiobook.ts   # same, for the full Audiobook flow
```

`npm test` is safe to run anytime. The smoke tests and any real usage of the wizard/generation/audio endpoints make real, billed calls to Gemini — be deliberate with how often you run a full episode or chapter through generation.

## Authentication

Every `/podcasts` and `/audiobooks` route (including everything nested under them — sources, episodes/chapters, audio) requires a **Firebase Auth ID token**. This backend never handles credentials itself: your client signs in with the Firebase Auth SDK directly (email/password, anonymous, or any provider you enable on the project), then sends the resulting ID token on every request:

```
Authorization: Bearer <firebase-id-token>
```

For the endpoints meant to be handed straight to a media player (`.../audio/stream`), a plain `<audio src="...">` can't attach custom headers — pass the token as a query param instead: `.../audio/stream?token=<firebase-id-token>`. The header is checked first if both are present.

Podcasts and Audiobook Titles (and everything nested under either) are private to the user who created them — a resource that exists but belongs to someone else looks identical to a `404`.

`/health` and `/voices` don't require auth.

### API reference

All request/response bodies are JSON unless noted.

#### Health & reference data

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Liveness check |
| GET | `/voices` | The 30 available TTS voice IDs, with gender and character trait |

#### Podcasts

| Method | Path | Description |
|---|---|---|
| POST | `/podcasts/wizard/options` | Generate 3 podcast concept options from a prompt |
| POST | `/podcasts/wizard/revise` | Revise one option or all three, from a free-text instruction |
| POST | `/podcasts` | Confirm a (possibly user-edited) option — creates the podcast |
| GET | `/podcasts` | List all podcasts |
| GET | `/podcasts/:podcastId` | Get one podcast |
| PATCH | `/podcasts/:podcastId` | Edit title/description/structure/hosts (affects future episodes only) |
| DELETE | `/podcasts/:podcastId` | Delete a podcast and everything under it (episodes, sources, cached audio) |

**`POST /podcasts/wizard/options`**
```json
// request
{ "prompt": "A podcast where two friends review obscure kitchen gadgets.", "sourceMaterial": "optional inspiration text" }

// response
{
  "options": [
    {
      "title": "...", "description": "...", "structure": "... (markdown)",
      "hosts": [{ "name": "...", "voice": "Puck", "persona": "..." }],
      "predictedChanges": ["...", "...", "..."]
    }
    // x3
  ]
}
```

**`POST /podcasts/wizard/revise`**
```json
// request — omit targetIndex to revise all 3 at once
{ "options": [ /* the 3 options as returned above */ ], "targetIndex": 0, "instruction": "Make this option more comedic" }
// response: same shape as /wizard/options
```

**`POST /podcasts`** — body is `{ title, description, structure, hosts: [{name, voice, persona}] }` (drop `predictedChanges` from a chosen option). Returns `201` with the created podcast, including a generated `id` and per-host `id`s.

**`PATCH /podcasts/:podcastId`** — any subset of `{title, description, structure, hosts}`. When editing `hosts`, include each existing host's `id` to keep it stable (episodes reference hosts by id); omit `id` on a new host.

#### Sources

| Method | Path | Description |
|---|---|---|
| POST | `/podcasts/:podcastId/sources` | Add a source — JSON `{title, contents}`, or multipart file upload |
| GET | `/podcasts/:podcastId/sources` | List sources for a podcast |
| GET | `/podcasts/:podcastId/sources/:sourceId` | Get one source |
| DELETE | `/podcasts/:podcastId/sources/:sourceId` | Delete a source |

To upload a file, `POST` multipart form-data with a `file` field (PDF only — text is extracted server-side); an optional `title` field overrides the default (the filename). Otherwise, send JSON: `{"title": "...", "contents": "..."}`.

#### Episodes

| Method | Path | Description |
|---|---|---|
| POST | `/podcasts/:podcastId/episodes/wizard/options` | Generate a single episode draft from sources (+ optional prompt) |
| POST | `/podcasts/:podcastId/episodes/wizard/revise` | Revise the draft from a free-text instruction |
| POST | `/podcasts/:podcastId/episodes` | Confirm a draft — creates the episode and starts generation (`202`) |
| GET | `/podcasts/:podcastId/episodes` | List episodes |
| GET | `/podcasts/:podcastId/episodes/:episodeId` | Get one episode (includes transcript/ttsPrompt once ready) |
| GET | `/podcasts/:podcastId/episodes/:episodeId/status` | Lightweight status poll (no transcript payload) |
| PATCH | `/podcasts/:podcastId/episodes/:episodeId` | Edit title/topics/productionNotes |
| DELETE | `/podcasts/:podcastId/episodes/:episodeId` | Delete an episode and its cached audio |
| POST | `/podcasts/:podcastId/episodes/:episodeId/regenerate` | Restart generation for a stuck/failed episode (from scratch) |

**`POST /podcasts/:podcastId/episodes/wizard/options`**
```json
// request
{ "sourceIds": ["<source-id>"], "prompt": "optional steering prompt" }

// response
{
  "draft": {
    "title": "...", "topics": "...", "productionNotes": "...",
    "guests": [{ "name": "...", "voice": "Kore", "persona": "..." }],
    "predictedChanges": ["...", "...", "..."]
  }
}
```

**`POST /podcasts/:podcastId/episodes`** (confirm)
```json
{
  "title": "...",
  "topics": "...",
  "length": "short",            // "short" | "medium" | "long"
  "sourceIds": ["<source-id>"],
  "participantHostIds": ["<host-id>"],
  "guests": [{ "name": "...", "voice": "Kore", "persona": "..." }],
  "productionNotes": "..."
}
```

**Important constraint**: `participantHostIds.length + guests.length` must equal exactly **2** — every episode is voiced by either 2 hosts or 1 host + 1 guest, never more or fewer. A single-host podcast therefore requires a guest on every episode.

Episode length word/time targets:

| Length | Words | Approx. time |
|---|---|---|
| `short` | 3500-5000 | 20-35 min |
| `medium` | 6500-8000 | 40-50 min |
| `long` | 8000-9000 | ~50-65 min |

**Episode status lifecycle**: `generating` → `ready` (or `failed`, with `error` set). Poll `/status` (returns `{status, progress, error}`, where `progress` includes the current stage and running word count) rather than the full episode while waiting.

#### Audio

| Method | Path | Description |
|---|---|---|
| GET | `/podcasts/:podcastId/episodes/:episodeId/audio/stream` | Stream the episode's audio |

This is a single audio resource for the whole episode (not per-chunk), designed to be pointed at directly by a standard `<audio>` element or a native mobile player:

- **Once fully generated**: behaves like a normal static audio file — proper `Content-Length`, `Accept-Ranges: bytes`, full seek support via `Range` requests.
- **While still generating**: served as `audio/wav` over `Transfer-Encoding: chunked` (no `Content-Length`, since the final size isn't known yet). Playback can start immediately and can be paused/resumed, but cannot be scrubbed ahead of what's actually been generated. A `Range: bytes=N-` request resumes precisely from `N` if that's already been generated; if not, it triggers generation of whatever's needed to reach it.
- Audio generation is genuinely on-demand — the first request for a given episode's stream is what triggers TTS synthesis (in chunks, cached from then on), not episode confirmation. Expect real latency (tens of seconds per chunk) the first time any given episode is streamed.

**Resuming from a saved position**: pass `?t=<seconds>` to start the stream from a playback position your app already has (e.g. the user exited the player and came back) — `GET .../audio/stream?t=754.2`. This is the recommended way to resume by time: the server converts it to the right byte offset internally, so your client never needs to know this app's underlying audio format. It behaves exactly like an equivalent `Range` byte-request (206 with `Content-Range` once the episode is fully generated; a chunked continuation, generating on demand, if not) — if a `Range` header is present on the same request, it takes precedence over `t`.

### Data model

- **Podcast**: `title`, `description`, `structure` (markdown), `hosts[]` (each with `id`, `name`, `voice`, `persona`).
- **Source**: `title`, `contents` (extracted plain text), `sourceType`.
- **Episode**: `title`, `topics`, `length`, `sourceIds[]`, `participantHostIds[]`, `guests[]`, `productionNotes`, `status`, `progress`, `transcript`, `ttsPrompt`, `ttsChunks[]` (internal chunk boundaries), `condensedSummaries` (per-host continuity notes carried into future episodes), `error`.

## Audiobooks

### How it works, in short

1. **Create a Title** by directly entering a name and optional description — no generation step, since there's nothing to generate from yet. Its Cast (speaking characters, each with a name/persona/voice) starts empty.
2. **Upload a Chapter's source material** (text or PDF) — unlike a Podcast's source material, this text *is* the content that gets performed, essentially word-for-word.
3. **Generate a chapter draft**: one analysis pass over the source text returns a suggested chapter name (verbatim from the source's own title line, if it has one), a synopsis, a scene breakdown, and the characters detected — cross-referenced against the Title's existing Cast so known characters keep their persona/voice. Edit anything in the draft, then confirm it.
4. Confirming a chapter commits any new/edited characters into the Title's Cast and kicks off **generation in the background**: a single director LLM converts each scene's fixed source text into a performable, speaker-tagged script — under a **Faithful Narration** constraint (no altering the original wording, beyond dropping a bare "he said"/"she said") — then deterministically renders each scene's TTS prompt and chunks it for synthesis. Poll a status endpoint until it's `ready`.
5. **Stream the audio** from the same kind of endpoint as a Podcast episode — a normal seekable file once fully generated, a live/growing stream (playable, not scrubbable ahead of what exists) while still being synthesized — except spanning all of a chapter's scenes in order as one continuous stream.

### API reference

All request/response bodies are JSON unless noted.

#### Titles

| Method | Path | Description |
|---|---|---|
| POST | `/audiobooks` | Create a Title — `{title, description?}`, no generation |
| GET | `/audiobooks` | List all Titles |
| GET | `/audiobooks/:titleId` | Get one Title (includes its Cast) |
| PATCH | `/audiobooks/:titleId` | Edit name/description/Cast (affects future chapters only) |
| DELETE | `/audiobooks/:titleId` | Delete a Title and everything under it (chapters, sources, cached audio) |

**`PATCH /audiobooks/:titleId`** — any subset of `{title, description, cast}`. When editing `cast`, include each existing member's `id` to keep it stable (chapters reference cast members by id); omit `id` on a new member.

#### Sources

| Method | Path | Description |
|---|---|---|
| POST | `/audiobooks/:titleId/sources` | Add a source — JSON `{title, contents}`, or multipart file upload |
| GET | `/audiobooks/:titleId/sources` | List sources for a Title |
| GET | `/audiobooks/:titleId/sources/:sourceId` | Get one source |
| DELETE | `/audiobooks/:titleId/sources/:sourceId` | Delete a source |

Same upload convention as the Podcast side: multipart `file` field (PDF only, text extracted server-side) with an optional `title` field, or JSON `{"title": "...", "contents": "..."}`. Unlike a Podcast's shared source pool, a Chapter references exactly one source — this is the text it performs, not background reading.

#### Chapters

| Method | Path | Description |
|---|---|---|
| POST | `/audiobooks/:titleId/chapters/draft` | Analyze a source into a chapter draft (name, synopsis, scenes, characters) |
| POST | `/audiobooks/:titleId/chapters` | Confirm a draft — creates the chapter and starts generation (`202`) |
| GET | `/audiobooks/:titleId/chapters` | List chapters |
| GET | `/audiobooks/:titleId/chapters/:chapterId` | Get one chapter |
| GET | `/audiobooks/:titleId/chapters/:chapterId/status` | Lightweight status poll (no script payload) |
| DELETE | `/audiobooks/:titleId/chapters/:chapterId` | Delete a chapter and its cached audio |
| POST | `/audiobooks/:titleId/chapters/:chapterId/regenerate` | Restart generation for a stuck/failed chapter (from scratch) |

**`POST /audiobooks/:titleId/chapters/draft`**
```json
// request
{ "sourceId": "<source-id>" }

// response
{
  "draft": {
    "name": "...", "synopsis": "...",
    "scenes": [
      { "name": "...", "description": "...", "directorNotes": "...", "sampleContext": "...", "sourceStartOffset": 0, "sourceEndOffset": 512 }
    ],
    "characters": [{ "id": "optional-if-already-in-cast", "name": "...", "voice": "Kore", "persona": "..." }]
  }
}
```

**`POST /audiobooks/:titleId/chapters`** (confirm) — body is `{ sourceId, draft }`, where `draft` is the (possibly user-edited) draft returned above. Returns `202` with the created chapter.

There is no revise-by-instruction step here (unlike the Podcast wizards) — the client edits the draft's fields directly before confirming, and the source text itself is never editable.

**Chapter status lifecycle**: `generating` → `ready` (or `failed`, with `error` set). Poll `/status` (returns `{status, progress, error}`, where `progress` is `{currentSceneIndex, totalScenes}`) rather than the full chapter while waiting.

#### Audio

| Method | Path | Description |
|---|---|---|
| GET | `/audiobooks/:titleId/chapters/:chapterId/audio/stream` | Stream the chapter's audio |

Same mechanics as the Podcast side's `/audio/stream` (see Podcasts → Audio above), including `?t=<seconds>` resume-by-time and `Range` support — one continuous stream spanning all of a chapter's scenes in order, generated and cached on demand.

### Data model

- **Title**: `title`, `description`, `cast[]` (each with `id`, `name`, `voice`, `persona`) — every speaking character/narrator in the book, built up entirely through confirmed chapters.
- **Source**: `title`, `contents` (extracted plain text), `sourceType` — same shape as a Podcast source, but a Chapter references exactly one.
- **Chapter**: `name`, `synopsis`, `sourceId`, `castIds[]`, `status`, `progress`, `error`.
- **Scene** (nested under a Chapter): `name`, `description`, `directorNotes`, `sampleContext`, `script` (the faithful, speaker-tagged rendering of this scene's span of the source text), `ttsPrompt` (Scene/Director's Notes/Sample Context blocks plus one Audio Profile block per character speaking in it).

## Known limitations

- No automatic resume if the process restarts mid-episode/chapter-generation; the transcript/scripts-so-far are preserved, but `/regenerate` restarts from scratch rather than continuing.
- `GET /podcasts` and `GET /audiobooks` (list) scan every record in the database and filter by owner in memory, rather than a Firestore-indexed query — fine at today's scale, but worth revisiting if the number of users/records grows significantly.
- **Faithful Narration is enforced by prompt instruction only** — audiobook-specs.md calls for a post-generation validation/diff check against the source text too, but that hasn't been built; informal testing found Gemini reliably preserves wording, but there's no automated guarantee.
- Every call to the wizard/draft, generation, and audio endpoints makes real, billed calls to the Gemini API.
