package com.alexpo.grammermate.data

import java.io.File

/** Validates pack-supplied names before they can become filesystem paths. */
internal object PackPaths {
    fun identifier(value: String, label: String): String {
        require(value.isNotBlank() && value != "." && value != ".." &&
            value.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }) {
            "Invalid $label: $value"
        }
        return value
    }

    fun relative(value: String): String {
        require(value.isNotBlank() && !value.startsWith('/') && !value.contains('\\') &&
            !value.contains(':') && value.none(Char::isISOControl) &&
            value.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid pack-relative path: $value"
        }
        return value
    }

    fun inside(root: File, relativePath: String): File {
        relative(relativePath)
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) {
            "Path leaves pack root: $relativePath"
        }
        return target
    }
}
