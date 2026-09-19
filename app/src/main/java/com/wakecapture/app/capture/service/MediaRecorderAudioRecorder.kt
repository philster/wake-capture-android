package com.wakecapture.app.capture.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaRecorderAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var _isRecording = false

    override val isRecording: Boolean get() = _isRecording

    override fun initialize(outputPath: String) {
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(BIT_RATE)
            try {
                setAudioChannels(CHANNELS_MONO)
            } catch (e: Exception) {
                Log.w(TAG, "Mono not supported, falling back to stereo", e)
                setAudioChannels(CHANNELS_STEREO)
            }
            setOutputFile(outputPath)
            prepare()
        }

        recorder = mr
    }

    override fun start() {
        recorder?.start()
        _isRecording = true
    }

    override fun stop() {
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error stopping recorder", e)
        }
        _isRecording = false
    }

    override fun release() {
        _isRecording = false
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder", e)
        }
        recorder = null
    }

    companion object {
        private const val TAG = "MediaRecorderAudio"
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
        private const val CHANNELS_MONO = 1
        private const val CHANNELS_STEREO = 2
    }
}
