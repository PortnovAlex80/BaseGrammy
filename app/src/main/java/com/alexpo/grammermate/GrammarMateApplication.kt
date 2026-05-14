package com.alexpo.grammermate

import android.app.Application

/**
 * Custom Application class that holds the AppContainer for dependency injection.
 *
 * NOTE: Must be registered in AndroidManifest.xml via android:name=".GrammarMateApplication"
 * on the <application> tag. This is done separately from the architecture migration.
 */
class GrammarMateApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
