package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache to pass AnalysisResult and HTTP capture data from CaptureViewModel
 * to ResultViewModel without serializing large data through nav arguments.
 */
@Singleton
class AnalysisResultCache @Inject constructor() {
    private var cached: AnalysisResult? = null
    private var requestHeaders: String? = null
    private var responseHeaders: String? = null
    private var responseBody: String? = null

    fun put(
        result: AnalysisResult,
        requestHeaders: String?,
        responseHeaders: String?,
        responseBody: String?,
    ) {
        cached = result
        this.requestHeaders = requestHeaders
        this.responseHeaders = responseHeaders
        this.responseBody = responseBody
    }

    fun get(): AnalysisResult? = cached
    fun getRequestHeaders(): String? = requestHeaders
    fun getResponseHeaders(): String? = responseHeaders
    fun getResponseBody(): String? = responseBody

    fun clear() {
        cached = null
        requestHeaders = null
        responseHeaders = null
        responseBody = null
    }
}
