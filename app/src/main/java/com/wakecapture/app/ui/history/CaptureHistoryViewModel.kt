package com.wakecapture.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakecapture.app.capture.CaptureRepository
import com.wakecapture.app.data.CaptureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CaptureHistoryViewModel @Inject constructor(
    repository: CaptureRepository
) : ViewModel() {

    val captures: StateFlow<List<CaptureEntity>> = repository.getAllCaptures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
