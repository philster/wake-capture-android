package com.wakecapture.app.capture

import com.wakecapture.app.data.CaptureDao
import com.wakecapture.app.data.CaptureEntity
import com.wakecapture.app.data.CaptureSource
import com.wakecapture.app.data.PersistenceState
import com.wakecapture.app.data.RecordingFileStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class CaptureRepositoryTest {

    private lateinit var captureDao: CaptureDao
    private lateinit var fileStore: RecordingFileStore
    private lateinit var repository: CaptureRepository

    @Before
    fun setup() {
        captureDao = mockk(relaxed = true)
        fileStore = mockk(relaxed = true)
        repository = CaptureRepository(captureDao, fileStore)
    }

    @Test
    fun `createFilePath delegates to fileStore`() {
        val expected = "/data/captures/test.m4a"
        every { fileStore.createFilePath() } returns expected
        assertThat(repository.createFilePath()).isEqualTo(expected)
    }

    @Test
    fun `hasAvailableStorage delegates to fileStore`() {
        every { fileStore.hasAvailableStorage() } returns true
        assertThat(repository.hasAvailableStorage()).isTrue()

        every { fileStore.hasAvailableStorage() } returns false
        assertThat(repository.hasAvailableStorage()).isFalse()
    }

    @Test
    fun `saveCapture creates entity with correct state`() = runTest {
        every { fileStore.fileExists(any()) } returns true
        every { fileStore.fileSize(any()) } returns 1024

        val entitySlot = slot<CaptureEntity>()
        coEvery { captureDao.insert(capture(entitySlot)) } returns Unit

        val result = repository.saveCapture(
            id = "test-id",
            filePath = "/data/captures/test.m4a",
            durationMs = 5000,
            source = CaptureSource.TILE
        )

        assertThat(result.id).isEqualTo("test-id")
        assertThat(result.state).isEqualTo(PersistenceState.SAVED)
        assertThat(result.durationMs).isEqualTo(5000)
        assertThat(result.createdFrom).isEqualTo(CaptureSource.TILE)
        coVerify { captureDao.insert(any()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `saveCapture throws when file does not exist`() = runTest {
        every { fileStore.fileExists(any()) } returns false
        repository.saveCapture(
            filePath = "/nonexistent.m4a",
            durationMs = 1000,
            source = CaptureSource.APP
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `saveCapture throws when file is empty`() = runTest {
        every { fileStore.fileExists(any()) } returns true
        every { fileStore.fileSize(any()) } returns 0
        repository.saveCapture(
            filePath = "/empty.m4a",
            durationMs = 1000,
            source = CaptureSource.APP
        )
    }

    @Test
    fun `markInterrupted saves with INTERRUPTED state`() = runTest {
        val entitySlot = slot<CaptureEntity>()
        coEvery { captureDao.insert(capture(entitySlot)) } returns Unit

        repository.markInterrupted(
            id = "int-id",
            filePath = "/data/captures/interrupted.m4a",
            durationMs = 3000,
            source = CaptureSource.TILE
        )

        coVerify { captureDao.insert(match { it.state == PersistenceState.INTERRUPTED }) }
    }

    @Test
    fun `deleteCapture removes file and database record`() = runTest {
        val entity = CaptureEntity(
            id = "del-id",
            createdAt = System.currentTimeMillis(),
            durationMs = 5000,
            filePath = "/data/captures/delete.m4a",
            state = PersistenceState.SAVED,
            createdFrom = CaptureSource.APP
        )
        coEvery { captureDao.getById("del-id") } returns entity
        every { fileStore.deleteFile(entity.filePath) } returns true

        repository.deleteCapture("del-id")

        coVerify { fileStore.deleteFile(entity.filePath) }
        coVerify { captureDao.deleteById("del-id") }
    }

    @Test
    fun `deleteCapture does nothing for nonexistent id`() = runTest {
        coEvery { captureDao.getById("nope") } returns null
        repository.deleteCapture("nope")
        coVerify(exactly = 0) { fileStore.deleteFile(any()) }
        coVerify(exactly = 0) { captureDao.deleteById(any()) }
    }
}
