# Plan: Semantic/AI-Engine-Level Caching

**GitHub Issue:** [#11](https://github.com/kcwms-org/kvcdr-carb-calculator-client/issues/11)

## Context

Currently `AnalysisResultCache` is a trivial single-item in-memory handoff between `CaptureViewModel` and `ResultViewModel` — it has no key logic, no persistence, and is cleared after each ResultScreen visit. Every submission hits the backend AI engine, even when the user re-submits functionally identical food descriptions or re-photographs the same meal.

The goal is a persistent, description-keyed cache that short-circuits the full presign → upload → analyze pipeline when a recent matching result exists locally.

---

## Scope and Limitations

- **Text submissions with a description are cached.** Cache key = normalize(description) + SHA-256 hash.
- **Image-only submissions (no description) are never cached.** No reliable client-side image identity exists without a heavy perceptual hash library.
- **Image + description submissions are text-keyed.** The image is treated as supplementary context; the description drives cache lookup.
- **True semantic equivalence is not solved.** Two descriptions that use different words for the same dish (e.g. "Waffle House All Star" vs "all star breakfast") will only hit the same cache key if they share the same sorted token set after normalization. Word-sort matching handles case/punctuation/order variation, not synonym substitution.
- **TTL: 24 hours.** Entries older than 24h are treated as misses and evicted by a periodic WorkManager job.

---

## Cache Key Strategy

```
normalizeCacheKey(description):
  1. lowercase
  2. strip all non-alphanumeric/whitespace characters
  3. split on whitespace, sort tokens alphabetically, rejoin with single space
  4. SHA-256 hex of the resulting canonical string
```

Uses only `java.security.MessageDigest` — no new dependencies.

---

## New Files

| File | Purpose |
|---|---|
| `data/local/db/AnalysisCacheEntity.kt` | Room entity: `id`, `textKey`, `createdAt`, `foodItemsJson`, `totalCarbs`, `foodDescription` |
| `data/local/db/AnalysisCacheDao.kt` | `insert`, `findByTextKey`, `deleteOlderThan`, `deleteAll` |
| `data/repository/AnalysisCacheRepository.kt` | `lookup(description)`, `store(description, result)`, `purgeExpired()`, key logic |
| `workers/AnalysisCachePurgeWorker.kt` | WorkManager worker calling `purgeExpired()` on 24h schedule |
| `test/.../AnalysisCacheRepositoryTest.kt` | Unit tests: key normalization, TTL, hit/miss |
| `androidTest/.../AnalysisCacheDaoTest.kt` | Instrumented DB tests |

---

## Modified Files

### `domain/model/Models.kt`
- Add `val fromCache: Boolean = false` to `AnalysisResult`

### `data/repository/CarbRepository.kt`
- Inject `AnalysisCacheRepository`
- Add `fromCache: Boolean = false` to `AnalyzeFoodResult`
- In `analyzeFood()`: before presign/upload, if `cleanDescription != null`, call `analysisCacheRepository.lookup(cleanDescription)`. On hit, return early with `fromCache = true` and skip submission logging.
- After successful network call, call `analysisCacheRepository.store(cleanDescription, result)` if description present.

### `data/local/db/CarbCalculatorDatabase.kt`
- Bump version 3 → 4
- Add `AnalysisCacheEntity` to entities list
- Add `abstract fun analysisCacheDao(): AnalysisCacheDao`
- Add `MIGRATION_3_4` (CREATE TABLE + CREATE INDEX for `analysis_cache`)

### `di/DatabaseModule.kt`
- Add `.addMigrations(MIGRATION_3_4)`
- Add `provideAnalysisCacheDao()` Hilt provider

### `ui/result/ResultViewModel.kt`
- Add `val fromCache: Boolean = false` to `ResultUiState`
- In `init`, set `fromCache = result?.fromCache ?: false`

### `ui/result/ResultScreen.kt`
- When `uiState.fromCache == true`, render a `SuggestionChip` (icon: `Icons.Default.OfflinePin`, label: "Served from local cache") as the first item in the `LazyColumn`

### `CarbCalculatorApp.kt`
- Enqueue `AnalysisCachePurgeWorker` via `WorkManager.enqueueUniquePeriodicWork` (24h interval, `KEEP` policy)

### Test updates
- `CarbRepositoryTest`: add `AnalysisCacheRepository` mock; add cache hit (no API call), cache miss (API called + stored), image-only bypass tests
- `CaptureViewModelTest`: add cache-hit flow test
- `ResultViewModelTest`: add `fromCache = true` propagation test

---

## Decision Log

| Question | Decision |
|---|---|
| Perceptual image hash | Skipped — false-positive risk too high; complexity not worth it |
| Cache location | New `AnalysisCacheRepository` + new Room table (not in `SubmissionLogRepository`) |
| Submission log on cache hit | Skip — submission log tracks API submissions only |
| Backend `cached` flag | Ignored for client cache purposes (signals server-side cache) |

---

## Verification

```bash
# Build
docker compose run --rm build ./gradlew assembleDebug

# Unit tests
docker compose run --rm build ./gradlew test

# Instrumented tests (device required)
docker compose run --rm build ./gradlew connectedAndroidTest
```

Manual smoke test:
1. Submit a food description → result screen shows no cache chip
2. Go back, re-submit the same description (different case/punctuation) → result screen shows "Served from local cache" chip, appears instantly
3. Submit an image with no description → no cache chip (bypassed)
4. Force-advance clock past 24h (or reduce TTL in debug) → re-submission hits network again
