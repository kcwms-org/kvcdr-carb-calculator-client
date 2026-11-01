# Issue #51 — fails to use the correct api endpoint

**Status:** Done — merged via PR #53

## Summary

A user configured the Carb Calculator API URL in Settings to
`https://kvcdr-carb-caluator-app-hiehc.ondigitalocean.app/`, but the request
capture log shows the analyze call being sent to
`POST http://localhost:3000/analyze` — the placeholder base URL, not the
configured endpoint. Either the request is genuinely going to the wrong host, or
(more likely) the debug capture is recording the pre-rewrite URL while the real
network call is fine. Both possibilities need to be resolved so the app
demonstrably hits the configured endpoint and the capture log proves it.

## Root cause / context

The carb API Retrofit instance uses a dynamic base URL mechanism in
`di/NetworkModule.kt`:

- `provideCarbOkHttpClient` builds an `OkHttpClient` with two application
  interceptors, in this order:
  1. `CarbApiCaptureInterceptor` — records the request line + headers + body
     into `CarbApiCapture` for the debug/log screen.
  2. `HttpLoggingInterceptor`.
- `provideCarbApiService` then calls `client.newBuilder().addInterceptor(dynamicUrlInterceptor)`
  and builds Retrofit with placeholder `baseUrl("http://localhost:3000/")`.

Because `newBuilder().addInterceptor(...)` **appends**, the final application
interceptor chain order is:

```
CarbApiCaptureInterceptor  →  HttpLoggingInterceptor  →  dynamicUrlInterceptor  →  (network)
```

So `CarbApiCaptureInterceptor` sees the request **before** `dynamicUrlInterceptor`
rewrites scheme/host/port. It captures the placeholder `http://localhost:3000/analyze`.
This fully explains the symptom in the issue (the capture text is exactly the
placeholder URL with the real `text`/`datetime`/`image` parts).

### Secondary bug (path stripping)

`dynamicUrlInterceptor` only rewrites `.scheme()`, `.host()`, `.port()`:

```kotlin
val newUrl = original.url.newBuilder()
    .scheme(parsedBase.scheme)
    .host(parsedBase.host)
    .port(parsedBase.port)
    .build()
```

If a user configures a URL that includes a path prefix (e.g.
`https://host/api`), that prefix is silently dropped — requests go to
`https://host/analyze` instead of `https://host/api/analyze`. The DigitalOcean
URL in this issue has no path prefix, so this is not the direct cause here, but
it is a latent correctness bug in the same code and should be fixed together.

## Proposed approach

1. **Fix interceptor ordering so the capture reflects the real URL.**
   Move the dynamic base URL rewrite so it runs *before* the capture
   interceptor. Cleanest option: build the dynamic URL interceptor inside
   `provideCarbOkHttpClient` (or a dedicated `@Provides` that returns it) and add
   it to the client builder **first**, before `CarbApiCaptureInterceptor`. This
   requires injecting `AppPreferencesDataStore` into `provideCarbOkHttpClient`
   (already available as a Hilt binding).
   - Resulting order: `dynamicUrlInterceptor → CarbApiCaptureInterceptor →
     HttpLoggingInterceptor → network`.
   - Remove the now-duplicate `client.newBuilder().addInterceptor(dynamicUrlInterceptor)`
     in `provideCarbApiService`.

2. **Preserve any configured path prefix.** In the rewrite, instead of only
   setting scheme/host/port, resolve the request path against the configured
   base URL. Approach: take `parsedBase` (the configured URL, normalized with a
   trailing `/`), and build the new URL as
   `parsedBase.newBuilder().addPathSegments(original.url.encodedPath.trimStart('/'))`
   plus copy the original query params. Verify this produces
   `https://host/analyze` for a base with no path and
   `https://host/api/analyze` for a base of `https://host/api`.

3. **Normalize/validate the configured URL on save.** In
   `SettingsRepository.saveApiUrl` (or `AppPreferencesDataStore.saveCarbApiUrl`),
   trim whitespace and reject a value that is not a valid `http(s)` URL, so a
   stray space or missing scheme can't silently fall back to the placeholder.
   (There is already a `validateApiUrl` used only for the import/backup path —
   consider reusing it.)

4. **Update `AboutScreen.kt` changelog** with a new patch version entry
   describing the fix (per CLAUDE.md changelog rule).

### Files to touch

- `app/src/main/java/com/kevcoder/carbcalculator/di/NetworkModule.kt` — interceptor
  ordering + path-preserving rewrite.
- `app/src/main/java/com/kevcoder/carbcalculator/data/repository/SettingsRepository.kt`
  and/or `data/local/datastore/AppPreferencesDataStore.kt` — URL normalization on save.
- `app/src/main/java/com/kevcoder/carbcalculator/ui/about/AboutScreen.kt` — changelog entry.
- Tests: add a unit test for the dynamic URL rewrite logic (extract it into a
  testable function/class so it can be exercised without Hilt). Cover: no-path
  base, path-prefix base, base with/without trailing slash, query params
  preserved.

## Risks & open questions

- **Is the real network call already correct?** Very likely yes (the dynamic
  interceptor runs last today, so the socket connection uses the right host).
  If confirmed, the user-visible bug is purely the misleading capture log — the
  fix is still worth doing because the capture is the only debugging surface.
  The implementer should confirm by checking `HttpLoggingInterceptor` output vs.
  the capture text, or by pointing the app at a known endpoint.
- `runBlocking { dataStore.carbApiUrl.first() }` on every request stays as-is
  (already the existing pattern for both carb and dexcom clients); not in scope
  to change here.
- `toHttpUrl()` throws if the configured string is not a valid URL. Step 3
  (validation on save) mitigates this, but the interceptor should also guard
  with a fallback to `DEFAULT_CARB_API_URL` rather than crashing the request.
- No auto-stash was needed — working tree was clean at Step 0.

## Acceptance criteria

- With a custom API URL set in Settings (including one with a path prefix), the
  request capture log shows that exact host (and path prefix) for the `analyze`
  call — not `http://localhost:3000`.
- The analyze request actually reaches the configured endpoint (verified via
  logging interceptor / a real round-trip).
- Saving an invalid or whitespace-padded URL in Settings is rejected or
  normalized, never silently ignored.
- New unit test for the URL-rewrite logic passes; `docker compose run --rm build
  ./gradlew test` is green.
- `AboutScreen.kt` has a new changelog entry for this fix.
