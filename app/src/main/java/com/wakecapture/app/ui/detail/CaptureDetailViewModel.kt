package com.wakecapture.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakecapture.app.capture.CaptureRepository
import com.wakecapture.app.data.CaptureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CaptureRepository
) : ViewModel() {

    private val captureId: String = savedStateHandle["captureId"]!!

    val capture: StateFlow<CaptureEntity?> = repository.observeById(captureId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete() {
        viewModelScope.launch { repository.deleteCapture(captureId) }
    }
}
