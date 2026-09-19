package com.wakecapture.app.di

import com.wakecapture.app.capture.service.AudioRecorder
import com.wakecapture.app.capture.service.MediaRecorderAudioRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindAudioRecorder(impl: MediaRecorderAudioRecorder): AudioRecorder
}
