package com.kevcoder.carbcalculator.ui.history

import com.kevcoder.carbcalculator.domain.model.FoodItem
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubmissionLogFormattersTest {

    private fun submission(
        description: String? = "The oatmeal is plain",
        status: String = "success",
        items: List<FoodItem> = listOf(
            FoodItem(name = "Plain oatmeal", estimatedCarbs = 27.0f),
            FoodItem(name = "Black coffee", estimatedCarbs = 0.0f),
        ),
        totalCarbs: Float? = 27.0f,
        errorMessage: String? = null,
    ) = SubmissionLog(
        id = 1L,
        carbLogId = 1L,
        requestTimestamp = 1_762_430_520_000L,
        imagePath = null,
        imageSizeBytes = null,
        foodDescription = description,
        status = status,
        items = items,
        totalCarbs = totalCarbs,
        errorMessage = errorMessage,
        responseTimestamp = null,
        requestHeaders = null,
        responseHeaders = null,
        responseBody = null,
    )

    @Test
    fun `summary lists description, items and total`() {
        assertEquals(
            "The oatmeal is plain\nPlain oatmeal - 27.0g\nBlack coffee - 0.0g\nTotal carbs: 27.0g",
            submission().toSummaryText(),
        )
    }

    @Test
    fun `summary leaves out the date and time`() {
        val text = submission().toSummaryText()
        assertFalse(text.contains("2025") || text.contains("AM") || text.contains("PM"))
    }

    @Test
    fun `summary skips missing description and total`() {
        assertEquals(
            "Plain oatmeal - 27.0g\nBlack coffee - 0.0g",
            submission(description = null, totalCarbs = null).toSummaryText(),
        )
    }

    @Test
    fun `error summary shows description and error, not items`() {
        assertEquals(
            "Pizza\nHTTP 500",
            submission(description = "Pizza", status = "error", totalCarbs = null, errorMessage = "HTTP 500")
                .toSummaryText(),
        )
    }
}
