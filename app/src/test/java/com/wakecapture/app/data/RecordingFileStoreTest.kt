package com.wakecapture.app.data

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingFileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileStore: RecordingFileStore

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        val filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        fileStore = RecordingFileStore(context)
    }

    @Test
    fun `createFilePath returns path under captures directory`() {
        val path = fileStore.createFilePath()
        assertThat(path).contains("captures")
        assertThat(path).endsWith(".m4a")
    }

    @Test
    fun `createFilePath creates unique paths`() {
        val path1 = fileStore.createFilePath()
        val path2 = fileStore.createFilePath()
        assertThat(path1).isNotEqualTo(path2)
    }

    @Test
    fun `fileExists returns false for nonexistent file`() {
        assertThat(fileStore.fileExists("/nonexistent/file.m4a")).isFalse()
    }

    @Test
    fun `fileExists returns true for existing file`() {
        val file = tempFolder.newFile("test.m4a")
        assertThat(fileStore.fileExists(file.absolutePath)).isTrue()
    }

    @Test
    fun `fileSize returns correct size`() {
        val file = tempFolder.newFile("sized.m4a")
        file.writeBytes(ByteArray(1024))
        assertThat(fileStore.fileSize(file.absolutePath)).isEqualTo(1024)
    }

    @Test
    fun `deleteFile removes the file`() {
        val file = tempFolder.newFile("delete_me.m4a")
        assertThat(file.exists()).isTrue()
        assertThat(fileStore.deleteFile(file.absolutePath)).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `hasAvailableStorage checks usable space`() {
        // The temp folder should have plenty of space on a dev machine
        val result = fileStore.hasAvailableStorage()
        assertThat(result).isTrue()
    }
}
