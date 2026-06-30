package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException

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

        try {
            AtomicFileWriter.writeText(file, yaml.dump(payload))
            Log.i("ProfileStore", "Successfully saved user profile: ${file.name} (${file.length()} bytes)")
        } catch (e: IOException) {
            Log.e("ProfileStore", "Failed to save user profile: ${file.name}", e)
            throw e
        } catch (e: Exception) {
            Log.e("ProfileStore", "Unexpected error saving user profile: ${file.name}", e)
            throw IOException("Failed to save user profile", e)
        }
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
