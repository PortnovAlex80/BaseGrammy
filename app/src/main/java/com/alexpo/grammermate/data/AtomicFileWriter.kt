package com.alexpo.grammermate.data

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

object AtomicFileWriter {
    fun writeText(file: File, text: String, charset: Charset = Charsets.UTF_8) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(text.toByteArray(charset))
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) {
            tempFile.delete()
            error("Failed to replace ${file.absolutePath}")
        }
        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            error("Failed to finalize ${file.absolutePath}")
        }
    }
}
