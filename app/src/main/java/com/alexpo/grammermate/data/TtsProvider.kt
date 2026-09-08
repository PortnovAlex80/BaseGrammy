package com.alexpo.grammermate.data

import android.app.Application

class TtsProvider private constructor(application: Application) {
    val ttsEngine: TtsEngine = TtsEngine(application)

    companion object {
        @Volatile
        private var instance: TtsProvider? = null

        fun getInstance(application: Application): TtsProvider {
            return instance ?: synchronized(this) {
                instance ?: TtsProvider(application).also { instance = it }
            }
        }
    }
}
