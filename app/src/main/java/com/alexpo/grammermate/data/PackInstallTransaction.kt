package com.alexpo.grammermate.data

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.UUID

/** One pack installation, including its derived files and registry publication. */
internal object PackInstallTransaction {
    private const val PREFIX = ".pack-install-"
    internal var afterWriteForTest: ((Int) -> Unit)? = null

    @Synchronized
    fun install(base: File, stagedPack: File, packId: String, files: Map<File, File>, registry: String, newDirectories: Map<File, File> = emptyMap(), removals: List<File> = emptyList()) {
        // Android can expose filesDir through /data/user/0 while canonical targets use
        // /data/data. Journal paths must be relative to the same physical root.
        val root = base.canonicalFile
        recover(root)
        val pack = PackPaths.inside(root, "packs/${PackPaths.identifier(packId, "packId")}")
        val journal = File(root, PREFIX + UUID.randomUUID()).apply { mkdirs() }
        val targets = (files.keys.toList() + removals + File(root, "packs.yaml"))
            .map(File::getCanonicalFile).distinct()
        try {
            val entries = targets.mapIndexed { index, target ->
                val relative = target.relativeTo(root).invariantSeparatorsPath
                require(PackPaths.inside(root, relative) == target)
                require(!target.exists() || target.isFile) { "Destination is not a file: $relative" }
                if (target.exists()) AtomicFileWriter.copyAtomic(target, File(journal, "before/$index"))
                mapOf("path" to relative, "existed" to target.exists(), "backup" to "before/$index")
            }
            val directories = newDirectories.keys.map { target ->
                val relative = target.canonicalFile.relativeTo(root).invariantSeparatorsPath
                require(PackPaths.inside(root, relative) == target.canonicalFile && !target.exists())
                relative
            }
            val plan = mapOf(
                "pack" to pack.relativeTo(root).invariantSeparatorsPath,
                "packExisted" to pack.exists(),
                "files" to entries,
                "directories" to directories
            )
            AtomicFileWriter.writeText(File(journal, "plan.yaml"), Yaml().dump(plan))
            removals.forEach { check(!it.exists() || it.delete()) { "Cannot remove stale derived file" } }
            files.entries.forEachIndexed { index, (target, source) ->
                AtomicFileWriter.copyAtomic(source, target)
                afterWriteForTest?.invoke(index)
            }
            newDirectories.forEach { (target, source) ->
                target.parentFile?.mkdirs()
                check(source.renameTo(target)) { "Cannot publish prepared directory: ${target.name}" }
            }
            afterWriteForTest?.invoke(files.size)
            if (pack.exists()) check(pack.renameTo(File(journal, "previous-pack"))) { "Cannot retain previous pack" }
            check(stagedPack.renameTo(pack)) { "Cannot publish prepared pack" }
            AtomicFileWriter.writeText(File(root, "packs.yaml"), registry)
            AtomicFileWriter.writeText(File(journal, "committed"), "ok")
            journal.deleteRecursively()
        } catch (failure: Throwable) {
            // If rollback fails its journal remains available to the next startup.
            try { rollBack(root, journal) } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            throw failure
        }
    }

    /** Runs before LessonStore creates any cached readers. */
    @Synchronized
    fun recover(base: File) {
        val root = base.canonicalFile
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith(PREFIX) }?.forEach { journal ->
            if (File(journal, "committed").exists()) journal.deleteRecursively()
            else rollBack(root, journal)
        }
    }

    private fun rollBack(base: File, journal: File) {
        val planFile = File(journal, "plan.yaml")
        if (!planFile.exists()) {
            // No durable plan means no live mutations have started.
            journal.deleteRecursively()
            return
        }
        val plan = Yaml().load<Map<String, Any>>(planFile.readText())
        val entries = plan["files"] as List<*>
        entries.forEach { value ->
            val entry = value as Map<*, *>
            val target = PackPaths.inside(base, entry["path"] as String)
            if (entry["existed"] == true) {
                AtomicFileWriter.copyAtomic(PackPaths.inside(journal, entry["backup"] as String), target)
            } else if (target.exists()) check(target.delete()) { "Cannot roll back ${target.name}" }
        }
        (plan["directories"] as? List<*>)?.forEach { relative ->
            val target = PackPaths.inside(base, relative as String)
            check(!target.exists() || target.deleteRecursively()) { "Cannot remove incomplete directory" }
        }
        val pack = PackPaths.inside(base, plan["pack"] as String)
        val previous = File(journal, "previous-pack")
        if (previous.exists()) {
            check(!pack.exists() || pack.deleteRecursively()) { "Cannot remove failed pack" }
            check(previous.renameTo(pack)) { "Cannot restore previous pack" }
        } else if (plan["packExisted"] == false && pack.exists()) {
            check(pack.deleteRecursively()) { "Cannot remove incomplete new pack" }
        }
        check(journal.deleteRecursively()) { "Cannot clear recovered installation journal" }
    }
}
