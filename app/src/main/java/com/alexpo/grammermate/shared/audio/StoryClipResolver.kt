package com.alexpo.grammermate.shared.audio

import java.io.File

/** Audio-side port for locating an optional pre-rendered chapter clip. */
fun interface StoryClipResolver {
    fun resolveChapterAudio(packId: String, storyFile: String?): File?
}
