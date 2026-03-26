package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.domain.model.AnalysisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache to pass AnalysisResult and submissionId from CaptureViewModel
 * to ResultViewModel without serializing large data through nav arguments.
 */
@Singleton
class AnalysisResultCache @Inject constructor() {
    private var cached: AnalysisResult? = null
    private var submissionId: Long? = null

    fun put(result: AnalysisResult) {
        cached = result
    }

    fun putSubmissionId(id: Long) {
        submissionId = id
    }

    fun get(): AnalysisResult? = cached

    fun getSubmissionId(): Long? = submissionId

    fun clear() {
        cached = null
        submissionId = null
    }
}
