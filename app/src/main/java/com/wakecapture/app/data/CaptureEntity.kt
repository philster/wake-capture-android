package com.wakecapture.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PersistenceState {
    SAVED,
    INTERRUPTED,
    FAILED
}

enum class TranscriptStatus {
    NONE,
    PENDING,
    DONE,
    FAILED
}

enum class CaptureSource {
    TILE,
    WIDGET,
    APP,
    ASSISTANT
}

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val durationMs: Long,
    val filePath: String,
    val mimeType: String = "audio/mp4",
    val codec: String = "AAC",
    val sampleRate: Int? = 44100,
    val channels: Int? = 1,
    val state: PersistenceState,
    val transcriptStatus: TranscriptStatus = TranscriptStatus.NONE,
    val title: String? = null,
    val tags: String? = null,
    val createdFrom: CaptureSource
)
