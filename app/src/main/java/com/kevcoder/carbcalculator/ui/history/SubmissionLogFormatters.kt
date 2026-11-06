package com.kevcoder.carbcalculator.ui.history

import com.kevcoder.carbcalculator.domain.model.SubmissionLog

/**
 * Plain-text summary of a submission, matching what History shows on screen
 * (description, items with carbs, total). Leaves out the date/time.
 */
fun SubmissionLog.toSummaryText(): String = buildList {
    foodDescription?.takeIf { it.isNotBlank() }?.let { add(it) }
    if (status == "success") {
        items.forEach { add("${it.name} - ${it.estimatedCarbs}g") }
        totalCarbs?.let { add("Total carbs: ${it}g") }
    }
    errorMessage?.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString("\n")
