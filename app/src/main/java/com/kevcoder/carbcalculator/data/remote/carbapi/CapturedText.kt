package com.kevcoder.carbcalculator.data.remote.carbapi

/**
 * Keeps captured HTTP text (request/response headers and bodies) small enough to store,
 * show, and copy. The carb API returns the photo as base64 inside the JSON response,
 * which is over 1 MB and crashed the History screen when expanded (issue #58).
 */
object CapturedText {

    /** Max chars kept per captured value when stored. */
    const val MAX_STORED_CHARS = 64_000

    /** Max chars rendered in an expanded History section. */
    const val MAX_DISPLAY_CHARS = 20_000

    /** Max chars put on the clipboard (Binder transactions fail around 1 MB). */
    const val MAX_CLIPBOARD_CHARS = 200_000

    /** Base64 runs at least this long are replaced with a placeholder. */
    const val MIN_BASE64_RUN = 1_000

    // A long run of base64 characters. JSON may escape '/' as "\/", so '\' is allowed too.
    // A single character class (no alternation) keeps Java's regex engine from recursing
    // per character, which would overflow the stack on 1 MB+ input.
    private val base64Run = Regex("""[A-Za-z0-9+/=\\]{$MIN_BASE64_RUN,}""")

    /** Replaces long base64 runs (e.g. embedded images) with "[base64 data: N chars]". */
    fun redactBase64(text: String): String {
        if (text.length < MIN_BASE64_RUN) return text
        return base64Run.replace(text) { "[base64 data: ${it.value.length} chars]" }
    }

    /** Cuts [text] to [maxChars], adding a note with the original length. */
    fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n… [truncated, ${text.length} chars total]"
    }

    /** Redacts base64 data, then caps the length. */
    fun sanitize(text: String, maxChars: Int = MAX_STORED_CHARS): String =
        truncate(redactBase64(text), maxChars)
}
