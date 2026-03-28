package com.kevcoder.carbcalculator.data.remote.carbapi

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the raw HTTP exchange (headers + body) for the most-recent carb API call.
 * Populated by [CarbApiCaptureInterceptor] and consumed by [CarbRepository].
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
}
