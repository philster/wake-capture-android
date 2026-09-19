package com.wakecapture.app.processing

interface TranscriptionService {
    suspend fun transcribe(audioFilePath: String): Result<String>
}
