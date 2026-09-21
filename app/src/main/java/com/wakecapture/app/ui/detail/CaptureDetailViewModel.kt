package com.wakecapture.app.ui.detail

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakecapture.app.capture.CaptureRepository
import com.wakecapture.app.data.CaptureEntity
import com.wakecapture.app.data.RecordingFileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CaptureRepository,
    private val fileStore: RecordingFileStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val captureId: String = savedStateHandle["captureId"]!!

    val capture: StateFlow<CaptureEntity?> = repository.observeById(captureId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _fileExists = MutableStateFlow(false)
    val fileExists: StateFlow<Boolean> = _fileExists.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        viewModelScope.launch {
            capture.collect { entity ->
                _fileExists.value = entity != null && fileStore.fileExists(entity.filePath)
            }
        }
    }

    fun togglePlayback(filePath: String) {
        if (_isPlaying.value) {
            stopPlayback()
            return
        }

        if (!requestAudioFocus()) {
            _playbackError.value = "playback_failed"
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    _playbackError.value = "playback_failed"
                    true
                }
                prepare()
                start()
            }
            _isPlaying.value = true
        } catch (e: Exception) {
            releasePlayer()
            abandonAudioFocus()
            _playbackError.value = "playback_failed"
        }
    }

    fun dismissPlaybackError() {
        _playbackError.value = null
    }

    fun delete() {
        viewModelScope.launch { repository.deleteCapture(captureId) }
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stopPlayback()
                }
            }
            .build()
        audioFocusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        releasePlayer()
        abandonAudioFocus()
        _isPlaying.value = false
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onCleared() {
        releasePlayer()
        abandonAudioFocus()
        super.onCleared()
    }
}
