package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Prepares all content and indexes before publishing an installation transaction. */
internal class PackImporter(
    private val context: Context,
    private val baseDir: File,
    private val packsDir: File,
    private val packsStore: YamlListStore,
    private val languagesStore: YamlListStore,
    private val storiesStore: YamlListStore,
    private val vocabStore: YamlListStore
) {
    fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack =
        (resolver.openInputStream(uri) ?: error("Cannot open zip")).use(::importPackFromStream)

    fun importPackFromAssets(assetPath: String): LessonPack = context.assets.open(assetPath).use(::importPackFromStream)

    fun readPackManifestFromAssets(assetPath: String): LessonPackManifest {
        ZipInputStream(context.assets.open(assetPath)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") return LessonPackManifest.fromJson(zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        error("Manifest not found")
    }

    fun importLessonFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Pair<Lesson, List<ParseError>> {
        val content = (resolver.openInputStream(uri) ?: error("Cannot open CSV")).bufferedReader().use { it.readText() }
        val parsed = CsvParser.parseLesson(content.byteInputStream())
        val (title, _) = parsed.data ?: error("CSV has no valid lesson cards")
        return createUserLesson(languageId, title, content, draft = false) to parsed.errors
    }

    fun createEmptyLesson(languageId: String, title: String): Lesson =
        createUserLesson(languageId, title.trim().ifBlank { "Lesson" }, title.trim().ifBlank { "Lesson" }, draft = true)

    private fun createUserLesson(languageId: String, title: String, content: String, draft: Boolean, migratedId: String? = null): Lesson = synchronized(INSTALL_LOCK) {
        PackPaths.identifier(languageId, "languageId")
        // Re-importing the same personal title replaces only that personal course.
        val previous = packsStore.read().firstNotNullOfOrNull { entry ->
            val candidate = entry["packId"] as? String ?: return@firstNotNullOfOrNull null
            if (!candidate.startsWith("USER_") || entry["languageId"] != languageId || !(entry["displayName"] as? String).equals(title, ignoreCase = true)) return@firstNotNullOfOrNull null
            runCatching { LessonPackManifest.fromJson(File(packsDir, "$candidate/manifest.json").readText()) }
                .getOrNull()?.takeIf { it.lessons.singleOrNull()?.type in setOf("personal", "draft") }
        }
        val id = migratedId ?: previous?.lessons?.single()?.lessonId ?: UUID.randomUUID().toString()
        val packId = if (migratedId != null) "USER_$id" else previous?.packId ?: "USER_$id"
        val stage = newStagingDir()
        try {
            AtomicFileWriter.writeText(File(stage, "$id.csv"), content)
            val manifest = JSONObject().put("schemaVersion", 1).put("packId", packId)
                .put("packVersion", "1").put("language", languageId).put("displayName", title)
                .put("lessons", JSONArray().put(JSONObject().put("lessonId", id).put("order", 1)
                    .put("file", "$id.csv").put("title", title).put("type", if (draft) "draft" else "personal")))
            AtomicFileWriter.writeText(File(stage, "manifest.json"), manifest.toString())
            installPrepared(stage)
            val cards = if (draft) emptyList() else CsvParser.parseLesson(content.byteInputStream()).data!!.second
                .mapIndexed { index, card -> card.copy(id = "${id}_$index", sourcePackId = packId, sourceLessonId = id) }
            return Lesson(LessonId(id), LanguageId(languageId), title, cards)
        } finally {
            stage.deleteRecursively()
        }
    }

    /** Upgrade only the UUID-index format written by the former standalone CSV/create actions. */
    fun migrateLegacyPersonalLessons(): Boolean = synchronized(INSTALL_LOCK) {
        val marker = File(baseDir, "personal_packs_v1.done")
        if (marker.exists()) return@synchronized false
        var failed = false
        val owned = packsStore.read().flatMap { entry ->
            val id = entry["packId"] as? String ?: return@flatMap emptyList<String>()
            val manifest = runCatching {
                LessonPackManifest.fromJson(PackPaths.inside(packsDir, "$id/manifest.json").readText())
            }.getOrNull() ?: return@flatMap emptyList<String>()
            manifest.lessons.map { it.lessonId } + manifest.chapters.flatMap { it.lessons }
        }.toMutableSet()
        var migrated = false
        File(baseDir, "lessons").listFiles()?.filter { it.name.endsWith("_index.yaml") }?.forEach { index ->
            val language = index.name.removeSuffix("_index.yaml")
            YamlListStore(Yaml(), index).read().forEach entryLoop@ { entry ->
                val id = entry["id"] as? String ?: return@entryLoop
                if (id in owned || runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false).not()) return@entryLoop
                val fileName = entry["file"] as? String ?: return@entryLoop
                if (fileName != "lesson_$id.csv") return@entryLoop
                val source = PackPaths.inside(baseDir, "lessons/$language/$fileName")
                if (!source.isFile) return@entryLoop
                val text = source.readText()
                val title = entry["title"] as? String ?: "Lesson"
                val draft = text.trim() == title.trim()
                if (!draft && CsvParser.parseLesson(text.byteInputStream()).data == null) return@entryLoop
                // A failed installation preserves the old record and is retried on next startup.
                runCatching { createUserLesson(language, title, text, draft, migratedId = id) }
                    .onSuccess { owned += id; migrated = true }
                    .onFailure { failed = true; android.util.Log.w("PackImporter", "Cannot migrate personal lesson $id", it) }
            }
        }
        if (!failed) AtomicFileWriter.writeText(marker, "ok")
        migrated
    }

    private fun newStagingDir(): File = File(packsDir, "tmp_${UUID.randomUUID()}").apply { mkdirs() }

    private fun importPackFromStream(input: InputStream): LessonPack {
        val stage = newStagingDir()
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val path = entry.name.removeSuffix("/")
                    val target = PackPaths.inside(stage, path)
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return installPrepared(stage)
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun installPrepared(stage: File): LessonPack = synchronized(INSTALL_LOCK) {
        PackInstallTransaction.recover(baseDir)
        invalidateIndexes()
        val manifestFile = File(stage, "manifest.json")
        require(manifestFile.isFile) { "Manifest not found" }
        val manifest = LessonPackManifest.fromJson(manifestFile.readText())
        val language = manifest.language.lowercase().trim()
        val derived = File(baseDir, ".pack-derived-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val yaml = Yaml()
            val writes = linkedMapOf<File, File>()
            val newDirectories = linkedMapOf<File, File>()
            fun write(relative: String, source: File) { writes[PackPaths.inside(baseDir, relative)] = source }
            fun writeText(relative: String, text: String) {
                val source = File(derived, UUID.randomUUID().toString())
                AtomicFileWriter.writeText(source, text)
                write(relative, source)
            }
            fun listText(items: List<Map<String, Any>>): String = yaml.dump(mapOf("schemaVersion" to 1, "items" to items))
            fun required(relative: String): File = PackPaths.inside(stage, relative).also {
                require(it.isFile) { "Missing required pack file: $relative" }
            }

            manifest.chapters.mapNotNull { it.storyFile }.forEach { required("stories/$it") }
            manifest.lessons.mapNotNull { it.grammarChip }.forEach { required(it) }
            // Validate every mandatory lesson before any installed file changes.
            val explicit = manifest.lessons.associateBy { it.lessonId }
            val lessonEntries = (manifest.lessons.filter { it.type != "verb_drill" } +
                manifest.chapters.flatMap { it.lessons }.filterNot(explicit::containsKey).map {
                    LessonPackLesson(it, 0, null, "$it.csv")
                }).distinctBy { it.lessonId }
            val indexFile = File(baseDir, "lessons/${language}_index.yaml")
            val index = YamlListStore(yaml, indexFile).read().toMutableList()
            for (lesson in lessonEntries) {
                val source = required(lesson.file)
                val parsed = CsvParser.parseLesson(source.inputStream())
                require(parsed.data != null || lesson.type == "draft") { "Invalid lesson file: ${lesson.file}" }
                val title = lesson.title ?: parsed.data?.first ?: lesson.lessonId
                val name = "lesson_${lesson.lessonId}.csv"
                write("lessons/$language/$name", source)
                index.removeAll { it["id"] == lesson.lessonId }
                index += mapOf("id" to lesson.lessonId, "title" to title, "file" to name)
            }
            writeText("lessons/${language}_index.yaml", listText(index))

            manifest.verbDrill?.files?.forEach { name ->
                val source = required(name)
                require(source.bufferedReader().use { VerbDrillCsvParser.parse(it).data } != null) { "Invalid verb drill: $name" }
                write("drills/${manifest.packId}/verb_drill/${source.name}", source)
            }
            manifest.vocabDrill?.files?.forEach { name ->
                val source = required(name)
                require(ItalianDrillVocabParser.parse(source.inputStream(), source.name).isNotEmpty()) { "Invalid vocab drill: $name" }
                write("drills/${manifest.packId}/vocab_drill/${source.name}", source)
            }
            manifest.backgroundVocab?.let { section ->
                val source = required(section.file)
                require(BgVocabCsvParser.parse(source.inputStream()).isNotEmpty()) { "Invalid background vocabulary: ${section.file}" }
                write("drills/${manifest.packId}/bg_vocab/${source.name}", source)
                section.audioDir?.let { relative ->
                    val audio = PackPaths.inside(stage, relative)
                    // Audio can be installed separately; missing clips retain TTS fallback.
                    val destination = PackPaths.inside(baseDir, "drills/${manifest.packId}/bg_vocab/audio")
                    if (audio.isDirectory && !destination.exists()) {
                        // Same-filesystem rename avoids fsync/copy for thousands of fresh clips.
                        newDirectories[destination] = audio
                    } else {
                        audio.listFiles()?.filter(File::isFile)?.forEach { clip ->
                            val target = PackPaths.inside(destination, clip.name)
                            if (!target.exists() || target.length() != clip.length()) writes[target] = clip
                        }
                    }
                }
            }

            val stories = storiesStore.read().toMutableList()
            stage.walkTopDown().filter { it.isFile && it.extension.equals("json", true) && it.name != "manifest.json" }.forEach { source ->
                val story = StoryQuizParser.parse(source.readText()).data ?: return@forEach
                PackPaths.identifier(story.storyId, "storyId")
                PackPaths.identifier(story.lessonId.value, "story lessonId")
                val name = "${story.storyId}.json"
                write("stories/$name", source)
                stories.removeAll { it["storyId"] == story.storyId && it["lessonId"] == story.lessonId.value && it["phase"] == story.phase.name }
                stories += mapOf("storyId" to story.storyId, "lessonId" to story.lessonId.value,
                    "phase" to story.phase.name, "languageId" to language, "file" to name)
            }
            writeText("stories/stories.yaml", listText(stories))

            val vocab = vocabStore.read().toMutableList()
            stage.walkTopDown().filter { it.isFile && it.extension.equals("csv", true) && it.nameWithoutExtension.startsWith("vocab_", true) }.forEach { source ->
                val lesson = source.nameWithoutExtension.removePrefix("vocab_")
                if (lesson.isBlank() || VocabCsvParser.parse(source.inputStream()).data == null) return@forEach
                PackPaths.identifier(lesson, "vocab lessonId")
                write("vocab/$language/${source.name}", source)
                vocab.removeAll { it["lessonId"] == lesson && it["languageId"] == language }
                vocab += mapOf("lessonId" to lesson, "languageId" to language, "file" to source.name)
            }
            writeText("vocab/vocab.yaml", listText(vocab))

            val languages = languagesStore.read().toMutableList()
            if (languages.none { it["id"] == language }) languages += mapOf("id" to language, "name" to language.uppercase())
            writeText("lessons/languages.yaml", listText(languages))
            val now = System.currentTimeMillis()
            val registry = packsStore.read().filterNot { it["packId"] == manifest.packId }.toMutableList()
            val entry = mutableMapOf<String, Any>("packId" to manifest.packId, "packVersion" to manifest.packVersion,
                "languageId" to language, "importedAt" to now)
            manifest.displayName?.let { entry["displayName"] = it }
            registry += entry
            val removals = listOf("verb_drill", "vocab_drill", "bg_vocab").flatMap { type ->
                PackPaths.inside(baseDir, "drills/${manifest.packId}/$type").listFiles()?.filter {
                    it.isFile && it.extension.equals("csv", true) && it.canonicalFile !in writes
                }.orEmpty()
            }
            PackInstallTransaction.install(baseDir, stage, manifest.packId, writes, listText(registry), newDirectories, removals)
            LessonPack(PackId(manifest.packId), manifest.packVersion, LanguageId(language), now, manifest.displayName)
        } finally {
            invalidateIndexes()
            derived.deleteRecursively()
        }
    }

    private fun invalidateIndexes() {
        packsStore.invalidateCache()
        languagesStore.invalidateCache()
        storiesStore.invalidateCache()
        vocabStore.invalidateCache()
    }

    companion object { private val INSTALL_LOCK = Any() }
}
