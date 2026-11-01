package com.kevcoder.carbcalculator.data.remote.carbapi

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Helpers for the user-configurable Carb Calculator API base URL.
 *
 * Retrofit is built with a placeholder base URL; [rewrite] swaps that placeholder for the
 * configured base URL at request time, keeping any path prefix on the configured URL
 * (e.g. `https://host/api` + `/analyze` → `https://host/api/analyze`).
 */
object CarbApiUrl {

    /** Base URL Retrofit is built with; requests to it get rewritten to the configured URL. */
    const val PLACEHOLDER_BASE_URL = "http://localhost:3000/"

    private val placeholder = PLACEHOLDER_BASE_URL.toHttpUrlOrNull()!!

    /** True if [url] was built against [PLACEHOLDER_BASE_URL] and still needs rewriting. */
    fun isPlaceholder(url: HttpUrl): Boolean =
        url.scheme == placeholder.scheme && url.host == placeholder.host && url.port == placeholder.port

    /**
     * Trims whitespace and trailing slashes and checks that [raw] is an absolute http(s) URL.
     * Returns the cleaned URL, or null if it is not valid.
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val parsed = trimmed.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        return trimmed
    }

    /** Parses [raw] into a base [HttpUrl] that ends with `/`, or null if it is not valid. */
    fun toBaseHttpUrl(raw: String): HttpUrl? =
        normalize(raw)?.let { "$it/".toHttpUrlOrNull() }

    /**
     * Resolves the path and query of [original] (built against Retrofit's root placeholder)
     * onto [base], keeping [base]'s scheme, host, port and path prefix.
     */
    fun rewrite(original: HttpUrl, base: HttpUrl): HttpUrl {
        val relativePath = original.encodedPath.trimStart('/')
        val builder = base.newBuilder()
        if (relativePath.isNotEmpty()) builder.addEncodedPathSegments(relativePath)
        return builder
            .encodedQuery(original.encodedQuery)
            .build()
    }
}
