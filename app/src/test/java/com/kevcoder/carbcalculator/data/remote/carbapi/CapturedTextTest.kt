package com.kevcoder.carbcalculator.data.remote.carbapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedTextTest {

    private val base64 = "/9j/4AAQSkZJRgABAQAAAQABAAD+".repeat(5_000) // ~140k chars

    private val responseJson = """{"items":[{"name":"Plain oatmeal","carbs_grams":27.0}],""" +
        """"total_carbs_grams":28.0,"images":[{"data":"$base64","mime_type":"image/jpeg"}]}"""

    @Test
    fun `base64 image in response is replaced and other fields kept`() {
        val result = CapturedText.redactBase64(responseJson)

        assertFalse(result.contains(base64))
        assertTrue(result.contains("[base64 data: ${base64.length} chars]"))
        assertTrue(result.contains(""""name":"Plain oatmeal""""))
        assertTrue(result.contains(""""total_carbs_grams":28.0"""))
        assertTrue(result.contains(""""mime_type":"image/jpeg""""))
    }

    @Test
    fun `json-escaped slashes are still treated as base64`() {
        val escaped = base64.replace("/", "\\/")
        val result = CapturedText.redactBase64("""{"data":"$escaped"}""")

        assertEquals("""{"data":"[base64 data: ${escaped.length} chars]"}""", result)
    }

    @Test
    fun `short text is unchanged`() {
        val text = "HTTP/1.1 200 OK\ncontent-type: application/json\n\n{\"total_carbs_grams\":12.5}"
        assertEquals(text, CapturedText.sanitize(text))
    }

    @Test
    fun `long text without base64 is truncated with a note`() {
        val text = "word ".repeat(30_000) // 150k chars, spaces break any base64 run
        val result = CapturedText.sanitize(text, maxChars = 1_000)

        assertTrue(result.startsWith(text.take(1_000)))
        assertTrue(result.endsWith("[truncated, ${text.length} chars total]"))
        assertTrue(result.length < 1_100)
    }

    @Test
    fun `sanitize keeps a large photo response small`() {
        val result = CapturedText.sanitize(responseJson)
        assertTrue(result.length < 1_000)
    }

    @Test
    fun `capture stores redacted and capped values`() {
        val capture = CarbApiCapture()
        capture.appendOperation("POST https://host/analyze", "h2 200", responseJson)

        val body = capture.responseBody!!
        assertFalse(body.contains(base64))
        assertTrue(body.contains("Plain oatmeal"))

        val huge = "x y ".repeat(50_000)
        capture.appendOperation("POST https://host/analyze", "h2 200", huge)
        assertTrue(capture.responseBody!!.length <= CapturedText.MAX_STORED_CHARS + 100)
    }

    @Test
    fun `capture keeps null body as null`() {
        val capture = CarbApiCapture()
        capture.appendOperation("GET https://host/", "h2 204", null)
        assertNull(capture.responseBody)
    }
}
