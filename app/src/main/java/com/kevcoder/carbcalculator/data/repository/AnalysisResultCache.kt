package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache to pass AnalysisResult from CaptureViewModel to ResultViewModel
 * without serializing large data through nav arguments.
 */
@Singleton
class AnalysisResultCache @Inject constructor() {
    private var cached: AnalysisResult? = null

    fun put(result: AnalysisResult) {
        cached = result
    }

    fun get(): AnalysisResult? = cached

    fun clear() {
        cached = null
    }
}
