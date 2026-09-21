package com.wakecapture.app.capture

import com.wakecapture.app.data.CaptureDao
import com.wakecapture.app.data.CaptureEntity
import com.wakecapture.app.data.CaptureSource
import com.wakecapture.app.data.PersistenceState
import com.wakecapture.app.data.RecordingFileStore
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureRepository @Inject constructor(
    private val captureDao: CaptureDao,
    private val fileStore: RecordingFileStore
) {
    fun getAllCaptures(): Flow<List<CaptureEntity>> = captureDao.getAllCaptures()

    suspend fun getById(id: String): CaptureEntity? = captureDao.getById(id)

    fun observeById(id: String): Flow<CaptureEntity?> = captureDao.observeById(id)

    fun createFilePath(): String = fileStore.createFilePath()

    fun hasAvailableStorage(): Boolean = fileStore.hasAvailableStorage()

    suspend fun saveCapture(
        id: String = UUID.randomUUID().toString(),
        filePath: String,
        durationMs: Long,
        source: CaptureSource,
        state: PersistenceState = PersistenceState.SAVED,
        sampleRate: Int? = 44100,
        channels: Int? = 1
    ): CaptureEntity {
        require(fileStore.fileExists(filePath)) { "Audio file does not exist: $filePath" }
        require(fileStore.fileSize(filePath) > 0) { "Audio file is empty: $filePath" }

        val entity = CaptureEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            filePath = filePath,
            state = state,
            createdFrom = source,
            sampleRate = sampleRate,
            channels = channels
        )
        captureDao.insert(entity)
        return entity
    }

    suspend fun markInterrupted(
        id: String,
        filePath: String,
        durationMs: Long,
        source: CaptureSource
    ) {
        val entity = CaptureEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            filePath = filePath,
            state = PersistenceState.INTERRUPTED,
            createdFrom = source
        )
        captureDao.insert(entity)
    }

    fun writeBreadcrumb(filePath: String) = fileStore.writeBreadcrumb(filePath)
    fun clearBreadcrumb() = fileStore.clearBreadcrumb()
    fun readOrphanedBreadcrumb(): String? = fileStore.readOrphanedBreadcrumb()

    suspend fun deleteCapture(id: String) {
        val capture = captureDao.getById(id) ?: return
        fileStore.deleteFile(capture.filePath)
        captureDao.deleteById(id)
    }
}
