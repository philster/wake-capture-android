package com.wakecapture.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val capturesDir: File
        get() = File(context.filesDir, "captures").also { it.mkdirs() }

    fun createFilePath(): String {
        val fileName = "${UUID.randomUUID()}.m4a"
        return File(capturesDir, fileName).absolutePath
    }

    fun fileExists(path: String): Boolean = File(path).exists()

    fun fileSize(path: String): Long = File(path).length()

    fun deleteFile(path: String): Boolean = File(path).delete()

    fun hasAvailableStorage(requiredBytes: Long = MINIMUM_STORAGE_BYTES): Boolean {
        return capturesDir.usableSpace >= requiredBytes
    }

    private val breadcrumbFile: File
        get() = File(context.filesDir, BREADCRUMB_FILENAME)

    fun writeBreadcrumb(filePath: String) {
        breadcrumbFile.writeText(filePath)
    }

    fun clearBreadcrumb() {
        breadcrumbFile.delete()
    }

    fun readOrphanedBreadcrumb(): String? {
        val file = breadcrumbFile
        if (!file.exists()) return null
        val path = file.readText().trim()
        return path.ifEmpty { null }
    }

    companion object {
        const val MINIMUM_STORAGE_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val BREADCRUMB_FILENAME = "recording_breadcrumb"
    }
}
