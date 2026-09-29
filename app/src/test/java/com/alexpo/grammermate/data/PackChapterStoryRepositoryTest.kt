package com.alexpo.grammermate.data

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class PackChapterStoryRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun asset_fallback_caches_story_in_its_pack_directory() {
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        every { context.assets } returns assets
        every { assets.open("grammarmate/packs/pack-a/stories/chapter_1.md") } answers {
            ByteArrayInputStream("Pack story".toByteArray())
        }
        val repo = PackChapterStoryRepository(context, temp.root) { null }
        val cachedFile = File(temp.root, "pack-a/stories/chapter_1.md")

        assertEquals("Pack story", repo.getChapterStory("pack-a", "chapter_1.md"))
        assertEquals("Pack story", cachedFile.readText())
        assertEquals("Pack story", repo.getChapterStory("pack-a", "chapter_1.md"))
        verify(exactly = 1) { assets.open("grammarmate/packs/pack-a/stories/chapter_1.md") }
    }
}
