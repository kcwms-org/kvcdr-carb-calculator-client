package com.kevcoder.carbcalculator.data.remote.carbapi

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbApiUrlTest {

    private val analyze = "${CarbApiUrl.PLACEHOLDER_BASE_URL}analyze".toHttpUrl()

    private fun rewrite(base: String, original: okhttp3.HttpUrl = analyze): String =
        CarbApiUrl.rewrite(original, CarbApiUrl.toBaseHttpUrl(base)!!).toString()

    @Test
    fun `base without path`() {
        assertEquals(
            "https://kvcdr-carb-caluator-app-hiehc.ondigitalocean.app/analyze",
            rewrite("https://kvcdr-carb-caluator-app-hiehc.ondigitalocean.app/"),
        )
    }

    @Test
    fun `base with path prefix keeps the prefix`() {
        assertEquals("https://host.example/api/analyze", rewrite("https://host.example/api"))
        assertEquals("https://host.example/api/analyze", rewrite("https://host.example/api/"))
    }

    @Test
    fun `custom port is kept`() {
        assertEquals("http://10.0.0.5:8080/analyze", rewrite("http://10.0.0.5:8080"))
    }

    @Test
    fun `query params are preserved`() {
        val original = "${CarbApiUrl.PLACEHOLDER_BASE_URL}logs?limit=10&q=a%20b".toHttpUrl()
        assertEquals(
            "https://host.example/api/logs?limit=10&q=a%20b",
            rewrite("https://host.example/api", original),
        )
    }

    @Test
    fun `isPlaceholder only matches the placeholder base`() {
        assertTrue(CarbApiUrl.isPlaceholder(analyze))
        assertFalse(CarbApiUrl.isPlaceholder("https://host.example/presign".toHttpUrl()))
        assertFalse(CarbApiUrl.isPlaceholder("http://localhost:8080/analyze".toHttpUrl()))
    }

    @Test
    fun `normalize trims whitespace and trailing slashes`() {
        assertEquals("https://host.example/api", CarbApiUrl.normalize("  https://host.example/api//  "))
    }

    @Test
    fun `normalize rejects invalid URLs`() {
        assertNull(CarbApiUrl.normalize(""))
        assertNull(CarbApiUrl.normalize("   "))
        assertNull(CarbApiUrl.normalize("host.example"))
        assertNull(CarbApiUrl.normalize("ftp://host.example"))
        assertNull(CarbApiUrl.normalize("not a url"))
    }
}
