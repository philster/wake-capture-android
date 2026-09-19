package com.wakecapture.app.capture.service

interface AudioRecorder {
    fun initialize(outputPath: String)
    fun start()
    fun stop()
    fun release()
    val isRecording: Boolean
}
