package com.wakecapture.app.processing

interface SummarizationService {
    suspend fun summarize(text: String): Result<String>
}
