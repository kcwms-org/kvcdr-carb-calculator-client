package com.kevcoder.carbcalculator.data.remote.carbapi

import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that captures all HTTP operations (requests and responses)
 * with full headers and body. Accumulates them in [CarbApiCapture] for logging.
 *
 * The response body is peeked so downstream handlers (Retrofit, etc.) can still read it normally.
 */
@Singleton
class CarbApiCaptureInterceptor @Inject constructor(
    private val capture: CarbApiCapture,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestSummary = buildRequestSummary(request)

        val response = chain.proceed(request)

        val responseSummary = buildString {
            append("${response.protocol} ${response.code} ${response.message}\n")
            response.headers.forEach { (name, value) -> append("$name: $value\n") }
        }.trimEnd()

        // Peek response body — leaves it readable for downstream handlers
        val responseBody = try {
            response.peekBody(Long.MAX_VALUE).string()
        } catch (_: Exception) {
            null
        }

        // Accumulate all HTTP operations instead of replacing
        capture.appendOperation(requestSummary, responseSummary, responseBody)

        return response
    }

    private fun buildRequestSummary(request: okhttp3.Request): String = buildString {
        append("${request.method} ${request.url}\n")
        request.headers.forEach { (name, value) -> append("$name: $value\n") }

        val body = request.body ?: return@buildString
        val contentType = body.contentType()

        // Emit Content-Type if not already in headers (OkHttp adds it via body)
        if (request.header("Content-Type") == null && contentType != null) {
            append("Content-Type: $contentType\n")
        }

        if (body is MultipartBody) {
            append("\n")
            body.parts.forEach { part ->
                val partContentType = part.body.contentType()
                val partHeaders = part.headers

                append("-- part --\n")
                partHeaders?.forEach { (name, value) -> append("$name: $value\n") }

                if (partContentType != null && partContentType.type == "image") {
                    // Replace binary with a placeholder showing the size
                    val size = part.body.contentLength()
                    val sizeDesc = if (size >= 0) "$size bytes" else "unknown size"
                    append("Content-Type: $partContentType\n")
                    append("[image data: $sizeDesc]\n")
                } else {
                    // Text part — safe to read
                    val buf = Buffer()
                    try {
                        part.body.writeTo(buf)
                        append("Content-Type: $partContentType\n")
                        append(buf.readUtf8())
                        append("\n")
                    } catch (_: Exception) {
                        append("[unreadable]\n")
                    }
                }
            }
        }
    }.trimEnd()
}
