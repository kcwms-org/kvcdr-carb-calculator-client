package com.kevcoder.carbcalculator.data.remote.carbapi

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accumulates all HTTP exchanges (requests and responses) with full headers and body.
 * Populated by [CarbApiCaptureInterceptor] and consumed by view models for logging.
 *
 * The capture covers POST /analyze (the only outbound carb-API call).
 */
@Singleton
class CarbApiCapture @Inject constructor() {
    @Volatile var requestHeaders: String? = null
    @Volatile var responseHeaders: String? = null
    @Volatile var responseBody: String? = null

    fun clear() {
        requestHeaders = null
        responseHeaders = null
        responseBody = null
    }

    /**
     * Accumulate a new HTTP operation (request + response).
     * If operations already exist, appends with a separator.
     */
    fun appendOperation(rawReqHeaders: String, rawRespHeaders: String, rawRespBody: String?) {
        // Redact embedded images and cap sizes so stored logs stay small (issue #58).
        val reqHeaders = CapturedText.sanitize(rawReqHeaders)
        val respHeaders = CapturedText.sanitize(rawRespHeaders)
        val respBody = rawRespBody?.let { CapturedText.sanitize(it) }

        // Append request headers
        requestHeaders = if (requestHeaders.isNullOrBlank()) {
            reqHeaders
        } else {
            CapturedText.truncate(requestHeaders + "\n\n" + reqHeaders, CapturedText.MAX_STORED_CHARS)
        }

        // Append response headers
        responseHeaders = if (responseHeaders.isNullOrBlank()) {
            respHeaders
        } else {
            CapturedText.truncate(responseHeaders + "\n\n" + respHeaders, CapturedText.MAX_STORED_CHARS)
        }

        // Append response body
        if (respBody != null && respBody.isNotBlank()) {
            responseBody = if (responseBody.isNullOrBlank()) {
                respBody
            } else {
                CapturedText.truncate(responseBody + "\n\n" + respBody, CapturedText.MAX_STORED_CHARS)
            }
        }
    }
}
