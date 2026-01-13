package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Stores user profile information (name, preferences, etc.)
 */
class ProfileStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "profile.yaml")

    fun load(): UserProfile {
        if (!file.exists()) {
            return UserProfile()
        }

        try {
            val raw = yaml.load<Any>(file.readText()) ?: return UserProfile()
            val data = (raw as? Map<*, *>) ?: return UserProfile()

            return UserProfile(
                userName = data["userName"] as? String ?: "GrammarMateUser"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return UserProfile()
        }
    }

    fun save(profile: UserProfile) {
        baseDir.mkdirs()

        val payload = linkedMapOf(
            "userName" to profile.userName
        )

        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }
}

data class UserProfile(
    val userName: String = "GrammarMateUser"
)
