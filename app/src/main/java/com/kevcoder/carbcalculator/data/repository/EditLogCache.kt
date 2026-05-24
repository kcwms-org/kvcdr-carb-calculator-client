package com.kevcoder.carbcalculator.data.repository

import com.kevcoder.carbcalculator.domain.model.CarbLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditLogCache @Inject constructor() {
    private var originalLogId: Long? = null
    private var log: CarbLog? = null

    fun put(originalLogId: Long?, log: CarbLog) {
        this.originalLogId = originalLogId
        this.log = log
    }

    fun get(): CarbLog? = log
    fun getOriginalLogId(): Long? = originalLogId

    fun clear() {
        log = null
        originalLogId = null
    }
}
