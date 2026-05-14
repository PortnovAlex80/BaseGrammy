package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Stores user profile information (name, preferences, etc.)
 */
interface ProfileStore {
    fun load(): UserProfile
    fun save(profile: UserProfile)
    fun clear()
}

class ProfileStoreImpl(private val context: Context) : ProfileStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "profile.yaml")

    override fun load(): UserProfile {
        if (!file.exists()) {
            return UserProfile()
        }

        try {
            val raw = yaml.load<Any>(file.readText()) ?: return UserProfile()
            val data = (raw as? Map<*, *>) ?: return UserProfile()

            return UserProfile(
                userName = data["userName"] as? String ?: "GrammarMateUser",
                welcomeDialogAttempts = (data["welcomeDialogAttempts"] as? Number)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return UserProfile()
        }
    }

    override fun save(profile: UserProfile) {
        baseDir.mkdirs()

        val payload = linkedMapOf(
            "userName" to profile.userName,
            "welcomeDialogAttempts" to profile.welcomeDialogAttempts
        )

        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    override fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }
}

data class UserProfile(
    val userName: String = "GrammarMateUser",
    val welcomeDialogAttempts: Int = 0
)
