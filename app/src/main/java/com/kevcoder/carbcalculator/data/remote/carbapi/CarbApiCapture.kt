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
    fun appendOperation(reqHeaders: String, respHeaders: String, respBody: String?) {
        // Append request headers
        requestHeaders = if (requestHeaders.isNullOrBlank()) {
            reqHeaders
        } else {
            requestHeaders + "\n\n" + reqHeaders
        }

        // Append response headers
        responseHeaders = if (responseHeaders.isNullOrBlank()) {
            respHeaders
        } else {
            responseHeaders + "\n\n" + respHeaders
        }

        // Append response body
        if (respBody != null && respBody.isNotBlank()) {
            responseBody = if (responseBody.isNullOrBlank()) {
                respBody
            } else {
                responseBody + "\n\n" + respBody
            }
        }
    }
}
